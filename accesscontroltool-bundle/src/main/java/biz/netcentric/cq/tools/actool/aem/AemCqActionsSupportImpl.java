/*
 * (C) Copyright 2019 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.aem;

import java.security.AccessControlException;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.nodetype.NodeDefinition;
import javax.jcr.nodetype.NodeType;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.Privilege;

import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlManager;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.helper.AccessControlUtils;

@Component(service = AemCqActionsSupport.class)
public class AemCqActionsSupportImpl implements AemCqActionsSupport {
    private static final Logger LOG = LoggerFactory.getLogger(AemCqActionsSupportImpl.class);

    public enum CqActions {
        read, modify, create, delete, acl_read, acl_edit, replicate;
    }

    public static final String REPLICATE_PRIVILEGE = "{http://www.day.com/crx/1.0}replicate";


    public AemCqActions getCqActions(Session session) {

        try {
            return new AcToolCqActions(session);
        } catch (Exception e) {
            throw new IllegalStateException("Could not instantiate CqActions: " + e, e);
        }
    }

    public boolean definesContent(Node node) throws RepositoryException {
        return AcToolCqActions.definesContent(node);
    }
    
    public static class AcToolCqActions implements AemCqActions {

        private static final String CONTENT_RESTRICTION = "*/jcr:content*";

        private final Session session;
        private final Map<String, Set<Privilege>> map = new HashMap<>();

        public AcToolCqActions(Session session) throws RepositoryException {
            this.session = session;
            AccessControlManager acMgr = session.getAccessControlManager();
            map.put(CqActions.read.name(), getPrivilegeSet(Privilege.JCR_READ, acMgr));
            map.put(CqActions.modify.name(), getPrivilegeSet(new String[] {
                    Privilege.JCR_MODIFY_PROPERTIES,
                    Privilege.JCR_LOCK_MANAGEMENT,
                    Privilege.JCR_VERSION_MANAGEMENT }, acMgr));
            map.put(CqActions.create.name(), getPrivilegeSet(new String[] {
                    Privilege.JCR_ADD_CHILD_NODES,
                    Privilege.JCR_NODE_TYPE_MANAGEMENT }, acMgr));
            map.put(CqActions.delete.name(), getPrivilegeSet(new String[] {
                    Privilege.JCR_REMOVE_CHILD_NODES,
                    Privilege.JCR_REMOVE_NODE }, acMgr));
            map.put(CqActions.acl_read.name(), getPrivilegeSet(Privilege.JCR_READ_ACCESS_CONTROL, acMgr));
            map.put(CqActions.acl_edit.name(), getPrivilegeSet(Privilege.JCR_MODIFY_ACCESS_CONTROL, acMgr));

            try {
                map.put(CqActions.replicate.name(), getPrivilegeSet(REPLICATE_PRIVILEGE, acMgr));
            } catch (AccessControlException e) {
                LOG.warn("Replicate privilege not registered");
            }
        }

        public Set<Privilege> getPrivileges(String action) {
            return map.get(action);
        }

        public Collection<String> getAllowedActions(String nodePath, Set<Principal> principals) throws RepositoryException {
            AccessControlManager acMgr = session.getAccessControlManager();
            Collection<String> granted = new HashSet<>();
            Set<Privilege> privileges = getPrivileges(nodePath, principals, acMgr);

            for (Map.Entry<String, Set<Privilege>> e : map.entrySet()) {
                if (privileges.containsAll(e.getValue())) {
                    granted.add(e.getKey());
                }
            }

            if (definesContent(session.getNode(nodePath))) {
                String contentPath = nodePath + "/" + JcrConstants.JCR_CONTENT;
                if (granted.contains(CqActions.modify.name())) {
                    if (!session.nodeExists(contentPath)
                            || !getPrivileges(contentPath, principals, acMgr).containsAll(getPrivilegeSet("rep:write", acMgr))) {
                        granted.remove(CqActions.modify.name());
                    }
                }
            }

            return granted;
        }

        public void installActions(String nodePath, Principal principal, Map<String, Boolean> actionMap, Collection<String> inheritedAllows)
                throws RepositoryException {
            if (actionMap.isEmpty()) {
                return;
            }

            AccessControlManager acMgr = session.getAccessControlManager();
            JackrabbitAccessControlList acl = AccessControlUtils.getModifiableAcl(acMgr, nodePath);

            for (String action : actionMap.keySet()) {
                boolean isAllow = actionMap.get(action);
                Set<Privilege> privileges = map.get(action);
                if (privileges != null) {
                    acl.addEntry(principal, privileges.toArray(new Privilege[privileges.size()]), isAllow);
                } 
            }

            if (definesContent(session.getNode(nodePath))) {
                Map<String, Value> restrictions = null;
                for (String rName : acl.getRestrictionNames()) {
                    if (AceBean.RESTRICTION_NAME_GLOB.equals(rName)) {
                        Value v = session.getValueFactory().createValue(CONTENT_RESTRICTION, acl.getRestrictionType(rName));
                        restrictions = Collections.singletonMap(rName, v);
                        break;
                    }
                }
                if (restrictions == null) {
                    LOG.warn(
                            "Cannot install special permissions node with jcr:content primary item. rep:glob restriction not supported by AC model.");
                } else {
                    Set<Privilege> allowPrivs = new HashSet<Privilege>();
                    Set<Privilege> denyPrivs = new HashSet<Privilege>();

                    boolean modify;
                    if (actionMap.containsKey(CqActions.modify.name())) {
                        Collection<Privilege> contentModify = Arrays.asList(
                                acMgr.privilegeFromName(Privilege.JCR_NODE_TYPE_MANAGEMENT),
                                acMgr.privilegeFromName(Privilege.JCR_ADD_CHILD_NODES),
                                acMgr.privilegeFromName(Privilege.JCR_REMOVE_CHILD_NODES),
                                acMgr.privilegeFromName(Privilege.JCR_REMOVE_NODE));
                        if (actionMap.get(CqActions.modify.name())) {
                            allowPrivs.addAll(contentModify);
                        } else {
                            denyPrivs.addAll(contentModify);
                        }
                        modify = actionMap.get(CqActions.modify.name());
                    } else {
                        modify = inheritedAllows.contains(CqActions.modify.name());
                    }

                    if (!modify) {
                        if (actionMap.containsKey(CqActions.create.name()) && actionMap.get(CqActions.create.name())) {
                            denyPrivs.add(acMgr.privilegeFromName(Privilege.JCR_ADD_CHILD_NODES));
                            denyPrivs.add(acMgr.privilegeFromName(Privilege.JCR_NODE_TYPE_MANAGEMENT));
                        }
                        if (actionMap.containsKey(CqActions.delete.name()) && actionMap.get(CqActions.delete.name())) {
                            denyPrivs.add(acMgr.privilegeFromName(Privilege.JCR_REMOVE_CHILD_NODES));
                            denyPrivs.add(acMgr.privilegeFromName(Privilege.JCR_REMOVE_NODE));
                        }
                    } else {
                        if (actionMap.containsKey(CqActions.create.name()) && !actionMap.get(CqActions.create.name())) {
                            allowPrivs.add(acMgr.privilegeFromName(Privilege.JCR_ADD_CHILD_NODES));
                            allowPrivs.add(acMgr.privilegeFromName(Privilege.JCR_NODE_TYPE_MANAGEMENT));
                        }
                        if (actionMap.containsKey(CqActions.delete.name()) && !actionMap.get(CqActions.delete.name())) {
                            allowPrivs.add(acMgr.privilegeFromName(Privilege.JCR_REMOVE_CHILD_NODES));
                            allowPrivs.add(acMgr.privilegeFromName(Privilege.JCR_REMOVE_NODE));
                        }
                    }

                    if (!allowPrivs.isEmpty()) {
                        acl.addEntry(principal, allowPrivs.toArray(new Privilege[allowPrivs.size()]), true, restrictions);
                    }
                    if (!denyPrivs.isEmpty()) {
                        acl.addEntry(principal, denyPrivs.toArray(new Privilege[denyPrivs.size()]), false, restrictions);
                    }
                }
            }

            acMgr.setPolicy(nodePath, acl);
        }

        public static boolean definesContent(Node node) throws RepositoryException {
            NodeType nt = node.getPrimaryNodeType();
            for (NodeDefinition cnd : nt.getChildNodeDefinitions()) {
                if (JcrConstants.JCR_CONTENT.equals(cnd.getName())) {
                    return true;
                }
            }
            return false;
        }

        private static Set<Privilege> getPrivileges(String path, Set<Principal> principals, AccessControlManager acMgr)
                throws RepositoryException {
            Set<Privilege> privileges = new HashSet<>();
            Privilege[] privs;
            if (principals == null) {
                privs = acMgr.getPrivileges(path);
            } else {
                privs = ((JackrabbitAccessControlManager) acMgr).getPrivileges(path, principals);
            }
            for (Privilege priv : privs) {
                if (priv.isAggregate()) {
                    privileges.addAll(Arrays.asList(priv.getAggregatePrivileges()));
                } else {
                    privileges.add(priv);
                }
            }
            return privileges;
        }

        private static Set<Privilege> getPrivilegeSet(String privName, AccessControlManager acMgr) throws RepositoryException {
            Set<Privilege> privileges;
            Privilege p = acMgr.privilegeFromName(privName);
            if (p.isAggregate()) {
                privileges = new HashSet<>(Arrays.asList(p.getAggregatePrivileges()));
            } else {
                privileges = Collections.singleton(p);
            }
            return privileges;
        }

        private static Set<Privilege> getPrivilegeSet(String[] privNames, AccessControlManager acMgr) throws RepositoryException {
            Set<Privilege> privileges = new HashSet<>(privNames.length);
            for (String name : privNames) {
                Privilege p = acMgr.privilegeFromName(name);
                if (p.isAggregate()) {
                    privileges.addAll(Arrays.asList(p.getAggregatePrivileges()));
                } else {
                    privileges.add(p);
                }
            }
            return privileges;
        }
    }


}
