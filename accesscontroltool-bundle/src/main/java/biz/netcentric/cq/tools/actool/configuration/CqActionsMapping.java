/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.jcr.security.AccessControlException;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.Privilege;

import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.api.security.authorization.PrivilegeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.helper.AceBean;

public class CqActionsMapping {

    public static final String ACTION_REPLICATE = "replicate";
    public static final String ACTION_ACL_EDIT = "acl_edit";
    public static final String ACTION_ACL_READ = "acl_read";
    public static final String ACTION_DELETE = "delete";
    public static final String ACTION_CREATE = "create";
    public static final String ACTION_MODIFY = "modify";
    public static final String ACTION_READ = "read";

    public static final String PRIVILEGE_JCR_REMOVE_NODE = "jcr:removeNode";
    public static final String PRIVILEGE_JCR_REMOVE_CHILD_NODES = "jcr:removeChildNodes";
    public static final String PRIVILEGE_JCR_ADD_CHILD_NODES = "jcr:addChildNodes";
    public static final String PRIVILEGE_JCR_MODIFY_PROPERTIES = "jcr:modifyProperties";
    public static final String PRIVILEGE_JCR_ALL = "jcr:all";
    public static final String PRIVILEGE_JCR_READ_ACCESS_CONTROL = "jcr:readAccessControl";
    public static final String PRIVILEGE_JCR_RETENTION_MANAGEMENT = "jcr:retentionManagement";
    public static final String PRIVILEGE_JCR_NODE_TYPE_DEFINITION_MANAGEMENT = "jcr:nodeTypeDefinitionManagement";
    public static final String PRIVILEGE_JCR_WRITE = "jcr:write";
    public static final String PRIVILEGE_JCR_NAMESPACE_MANAGEMENT = "jcr:namespaceManagement";
    public static final String PRIVILEGE_JCR_NODE_TYPE_MANAGEMENT = "jcr:nodeTypeManagement";
    public static final String PRIVILEGE_REP_PRIVILEGE_MANAGEMENT = "rep:privilegeManagement";
    public static final String PRIVILEGE_REP_USER_MANAGEMENT = "rep:userManagement";
    public static final String PRIVILEGE_REP_WRITE = "rep:write";
    public static final String PRIVILEGE_JCR_MODIFY_ACCESS_CONTROL = "jcr:modifyAccessControl";
    public static final String PRIVILEGE_JCR_READ = "jcr:read";
    public static final String PRIVILEGE_CRX_REPLICATE = "crx:replicate";
    public static final String PRIVILEGE_JCR_LOCK_MANAGEMENT = "jcr:lockManagement";
    public static final String PRIVILEGE_JCR_VERSION_MANAGEMENT = "jcr:versionManagement";
    public static final String PRIVILEGE_JCR_LIFECYCLE_MANAGEMENT = "jcr:lifecycleManagement";
    public static final String PRIVILEGE_JCR_WORKSPACE_MANAGEMENT = "jcr:workspaceManagement";

    static final public Map<String, List<String>> ACTIONS_MAP = new HashMap<String, List<String>>();

   
    static {
        
        ACTIONS_MAP.put(
                ACTION_READ,
                new ArrayList<String>(Arrays
                        .asList(new String[] { PRIVILEGE_JCR_READ })));
        ACTIONS_MAP.put(
                ACTION_MODIFY,
                new ArrayList<String>(Arrays.asList(new String[] {
                        PRIVILEGE_JCR_MODIFY_PROPERTIES,
                        PRIVILEGE_JCR_LOCK_MANAGEMENT,
                        PRIVILEGE_JCR_VERSION_MANAGEMENT })));
        ACTIONS_MAP.put(
                ACTION_CREATE,
                new ArrayList<String>(Arrays.asList(new String[] {
                        PRIVILEGE_JCR_ADD_CHILD_NODES,
                        PRIVILEGE_JCR_NODE_TYPE_MANAGEMENT })));
        ACTIONS_MAP.put(
                ACTION_DELETE,
                new ArrayList<String>(Arrays.asList(new String[] {
                        PRIVILEGE_JCR_REMOVE_CHILD_NODES,
                        PRIVILEGE_JCR_REMOVE_NODE })));
        ACTIONS_MAP
                .put(ACTION_ACL_READ,
                        new ArrayList<String>(
                                Arrays.asList(new String[] { PRIVILEGE_JCR_READ_ACCESS_CONTROL })));
        ACTIONS_MAP
                .put(ACTION_ACL_EDIT,
                        new ArrayList<String>(
                                Arrays.asList(new String[] { PRIVILEGE_JCR_MODIFY_ACCESS_CONTROL })));
        ACTIONS_MAP.put(
                ACTION_REPLICATE,
                new ArrayList<String>(Arrays
                        .asList(new String[] { PRIVILEGE_CRX_REPLICATE })));
    }

    static final Logger LOG = LoggerFactory.getLogger(CqActionsMapping.class);
    

    public static String getCqActions(final Privilege[] jcrPrivileges, AccessControlManager aclManager) throws AccessControlException, RepositoryException {
        StringBuilder sb = new StringBuilder();
        for (Privilege p : jcrPrivileges) {
            sb.append(p.getName()).append(",");
        }
        return getCqActions(StringUtils.chomp(sb.toString()), aclManager);
    }

    public static String getCqActions(final String[] jcrPrivileges, AccessControlManager aclManager) throws AccessControlException, RepositoryException {
        StringBuilder sb = new StringBuilder();
        for (String s : jcrPrivileges) {
            sb.append(s).append(",");
        }
        return getCqActions(StringUtils.chomp(sb.toString()), aclManager);
    }

    /**
     * method that converts jcr:privileges to cq actions
     * 
     * @param jcrPrivilegesString
     *            comma separated String containing jcr:privileges
     * @return comma separated String containing the assigned cq actions
     * @throws RepositoryException 
     * @throws AccessControlException 
     */
    public static String getCqActions(final String jcrPrivilegesString, AccessControlManager aclManager) throws AccessControlException, RepositoryException {
        List<String> jcrPrivileges = new ArrayList<String>(
                Arrays.asList(jcrPrivilegesString.split(",")));

        
        // go through all aggregates to extend the list with all non-aggregate privileges
        for (String jcrPrivilege : jcrPrivilegesString.split(",")) {
        	Privilege privilege = aclManager.privilegeFromName(jcrPrivilege);
        	for (Privilege aggregatedPrivileges : privilege.getAggregatePrivileges()) {
        		jcrPrivileges.add(aggregatedPrivileges.getName());
        	}
        }

        // loop through keySet of cqActions. Remove successively all privileges
        // which are associated to a cq action from jcrPrivileges string
        // and add this actions name to actions string

        Set<String> cqActions = ACTIONS_MAP.keySet();
        String actionsString = "";

        for (String action : cqActions) {
            List<String> jcrPrivilegesFromMap = ACTIONS_MAP.get(action);
            if (jcrPrivileges.containsAll(jcrPrivilegesFromMap)) {
                jcrPrivileges.removeAll(jcrPrivilegesFromMap);
                actionsString = actionsString + action + ",";
            }
        }

        // remove last comma from actions string
        actionsString = StringUtils.chop(actionsString);

        if (actionsString.isEmpty()) {
            actionsString = "";
        }
        return actionsString;
    }


    
    public static AceBean getConvertedPrivilegeBean(AceBean bean) {
        Set<String> actions = new LinkedHashSet<String>();
        Set<String> privileges = new LinkedHashSet<String>();

        if (bean.getActions() != null) {
            actions = new LinkedHashSet<String>(Arrays.asList(bean.getActions()));
        }
        if (bean.getPrivileges() != null) {
            privileges = new LinkedHashSet<String>(
                    Arrays.asList(bean.getPrivileges()));
        }
        
        // convert cq:actions to their jcr:privileges
        for (String action : actions) {
        	if (ACTIONS_MAP.containsKey(action)) { // fix for possible NPE
              privileges.addAll(ACTIONS_MAP.get(action));
        	} else {
        	    LOG.warn("Unrecognized action: '{}' for bean {}", action, bean);
        	}
        }
        bean.clearActions();
        bean.setActionsStringFromConfig("");

        StringBuilder sb = new StringBuilder();
        for (String privilege : privileges) {
            sb.append(privilege).append(",");
        }

        bean.setPrivilegesString(StringUtils.chop(sb.toString()));

        return bean;

    }
}
