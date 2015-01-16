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
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.security.Privilege;

import org.apache.commons.lang.StringUtils;
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
    public static final String PRIVILEGE_REP_WRITE = "rep:write";
    public static final String PRIVILEGE_JCR_MODIFY_ACCESS_CONTROL = "jcr:modifyAccessControl";
    public static final String PRIVILEGE_JCR_READ = "jcr:read";
    public static final String PRIVILEGE_CRX_REPLICATE = "crx:replicate";
    public static final String PRIVILEGE_JCR_LOCK_MANAGEMENT = "jcr:lockManagement";
    public static final String PRIVILEGE_JCR_VERSION_MANAGEMENT = "jcr:versionManagement";
    public static final String PRIVILEGE_JCR_LIFECYCLE_MANAGEMENT = "jcr:lifecycleManagement";
    public static final String PRIVILEGE_JCR_WORKSPACE_MANAGEMENT = "jcr:workspaceManagement";

    static final public Map<String, List<String>> ACTIONS_MAP = new HashMap<String, List<String>>();
    static final public Map<String, List<String>> PRIVILEGES_MAP = new HashMap<String, List<String>>();

    static List<String> repWriteList = new ArrayList<String>(
            Arrays.asList(new String[] { PRIVILEGE_REP_WRITE }));
    static List<String> jcrAllList = new ArrayList<String>(
            Arrays.asList(new String[] { PRIVILEGE_JCR_ALL }));
    static List<String> jcrWriteList = new ArrayList<String>(
            Arrays.asList(new String[] { PRIVILEGE_JCR_WRITE }));

    static List<String> jcrAggregatedPrivileges = new ArrayList<String>(
            Arrays.asList(new String[] { PRIVILEGE_JCR_ALL,
                    PRIVILEGE_JCR_WRITE, PRIVILEGE_REP_WRITE }));
    static List<String> jcrAllPrivileges = new ArrayList<String>(
            Arrays.asList(new String[] { PRIVILEGE_JCR_WORKSPACE_MANAGEMENT,
                    PRIVILEGE_JCR_LIFECYCLE_MANAGEMENT,
                    PRIVILEGE_JCR_VERSION_MANAGEMENT,
                    PRIVILEGE_JCR_LOCK_MANAGEMENT, PRIVILEGE_CRX_REPLICATE,
                    PRIVILEGE_JCR_READ, PRIVILEGE_JCR_MODIFY_ACCESS_CONTROL,
                    PRIVILEGE_REP_WRITE, PRIVILEGE_REP_PRIVILEGE_MANAGEMENT,
                    PRIVILEGE_JCR_NODE_TYPE_MANAGEMENT,
                    PRIVILEGE_JCR_NAMESPACE_MANAGEMENT, PRIVILEGE_JCR_WRITE,
                    PRIVILEGE_JCR_NODE_TYPE_DEFINITION_MANAGEMENT,
                    PRIVILEGE_JCR_RETENTION_MANAGEMENT,
                    PRIVILEGE_JCR_READ_ACCESS_CONTROL }));

    static {
        PRIVILEGES_MAP.put(
                PRIVILEGE_REP_WRITE,
                new ArrayList<String>(Arrays.asList(new String[] {
                        PRIVILEGE_JCR_MODIFY_PROPERTIES,
                        PRIVILEGE_JCR_ADD_CHILD_NODES,
                        PRIVILEGE_JCR_REMOVE_NODE,
                        PRIVILEGE_JCR_REMOVE_CHILD_NODES,
                        PRIVILEGE_JCR_NODE_TYPE_MANAGEMENT })));
        PRIVILEGES_MAP.put(
                PRIVILEGE_JCR_WRITE,
                new ArrayList<String>(Arrays.asList(new String[] {
                        PRIVILEGE_JCR_ADD_CHILD_NODES,
                        PRIVILEGE_JCR_REMOVE_NODE,
                        PRIVILEGE_JCR_MODIFY_PROPERTIES,
                        PRIVILEGE_JCR_REMOVE_CHILD_NODES })));
        PRIVILEGES_MAP.put(PRIVILEGE_JCR_ALL, jcrAllPrivileges);

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
    
    public static List<String> getJcrAggregatedPrivilegesList() {
        return jcrAggregatedPrivileges;
    }

    public static List<String> getJcrAllPrivilegesList() {
        return jcrAllPrivileges;
    }

    public static String getCqActions(final Privilege[] jcrPrivileges) {
        StringBuilder sb = new StringBuilder();
        for (Privilege p : jcrPrivileges) {
            sb.append(p.getName()).append(",");
        }
        return getCqActions(StringUtils.chomp(sb.toString()));
    }

    public static String getCqActions(final String[] jcrPrivileges) {
        StringBuilder sb = new StringBuilder();
        for (String s : jcrPrivileges) {
            sb.append(s).append(",");
        }
        return getCqActions(StringUtils.chomp(sb.toString()));
    }

    /**
     * method that converts jcr:privileges to cq actions
     * 
     * @param jcrPrivilegesString
     *            comma separated String containing jcr:privileges
     * @return comma separated String containing the assigned cq actions
     */
    public static String getCqActions(final String jcrPrivilegesString) {
        List<String> jcrPrivileges = new ArrayList<String>(
                Arrays.asList(jcrPrivilegesString.split(",")));

        // replace privilege jcr:all by the respective jcr:privileges
        if (jcrPrivileges.containsAll(jcrAllList)) {
            jcrPrivileges.removeAll(jcrAllList);
            jcrPrivileges.addAll(PRIVILEGES_MAP.get(PRIVILEGE_JCR_ALL));
        }

        // replace privilege rep:write by the respective jcr:privileges
        if (jcrPrivileges.containsAll(repWriteList)) {
            jcrPrivileges.removeAll(repWriteList);
            jcrPrivileges.addAll(PRIVILEGES_MAP.get(PRIVILEGE_REP_WRITE));
        }

        // replace privilege jcr:write by the respective jcr:privileges
        if (jcrPrivileges.containsAll(jcrWriteList)) {
            jcrPrivileges.removeAll(jcrWriteList);
            jcrPrivileges.addAll(PRIVILEGES_MAP.get(PRIVILEGE_JCR_WRITE));
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

    /**
     * Method that removes jcr:privileges covered by cq actions from a String
     * 
     * @param privilegesString
     *            comma separated String containing jcr:privileges
     * @param actionsString
     *            comma separated String containing cq actions
     * @return comma separated String containing jcr:privileges
     */
    public static String getStrippedPrivilegesString(
            final String privilegesString, final String actionsString) {
        List<String> actions = new ArrayList<String>(
                Arrays.asList(actionsString.split(",")));
        List<String> jcrPrivileges = new ArrayList<String>(
                Arrays.asList(privilegesString.split(",")));

        // replace privilege rep:write by the respective jcr:privileges
        if (jcrPrivileges.containsAll(repWriteList)) {
            jcrPrivileges.removeAll(repWriteList);
            jcrPrivileges.addAll(PRIVILEGES_MAP.get(PRIVILEGE_REP_WRITE));
        }
        // replace privilege jcr:write by the respective jcr:privileges
        if (jcrPrivileges.containsAll(jcrWriteList)) {
            jcrPrivileges.removeAll(jcrWriteList);
            jcrPrivileges.addAll(PRIVILEGES_MAP.get(PRIVILEGE_JCR_WRITE));
        }
        // Don't replace jcr:all

        for (String action : actions) {
            List<String> privilegesFromAction = ACTIONS_MAP.get(action);
            if (privilegesFromAction != null) {
                jcrPrivileges.removeAll(privilegesFromAction);
            }
        }

        // build new privileges String
        StringBuilder sb = new StringBuilder();
        for (String privilege : jcrPrivileges) {
            sb.append(privilege).append(",");
        }
        return StringUtils.chop(sb.toString());
    }

    /**
     * method that deletes jvr:privileges from a AceBean which are covered by cq
     * actions stored in the respective bean property
     * 
     * @param bean
     *            a AceBean
     * @return
     */
    public static AceBean getAlignedPermissionBean(final AceBean bean) {
        String alignedPrivileges = getStrippedPrivilegesString(
                bean.getPrivilegesString(), bean.getActionsString());
        bean.setPrivilegesString(alignedPrivileges);

        // in case privileges property contains "jcr:all" remove all actions,
        // since they're included
        if (alignedPrivileges.contains(PRIVILEGE_JCR_ALL)) {
            bean.clearActions();
        }
        return bean;

    }

    public static void getAggregatedPrivilegesBean(AceBean aceBean) {

        Set<String> privileges = new HashSet<String>(new ArrayList(
                Arrays.asList(aceBean.getPrivileges())));
        privileges = replaceAggregatedPrivileges(privileges, PRIVILEGE_JCR_ALL);
        privileges = replaceAggregatedPrivileges(privileges,
                PRIVILEGE_REP_WRITE);
        privileges = replaceAggregatedPrivileges(privileges,
                PRIVILEGE_JCR_WRITE);
        String privilegesString = "";
        for (String privilege : privileges) {
            privilegesString = privilegesString + privilege + ",";
        }

        aceBean.setPrivilegesString(StringUtils.chop(privilegesString));

    }

    private static Set<String> replaceAggregatedPrivileges(
            Set<String> jcrPrivileges, String aggregatedPrivilege) {
        if (jcrPrivileges.containsAll(PRIVILEGES_MAP.get(aggregatedPrivilege))) {
            jcrPrivileges.removeAll(PRIVILEGES_MAP.get(aggregatedPrivilege));
            jcrPrivileges.add(aggregatedPrivilege);
        }
        return jcrPrivileges;
    }

    public static AceBean getConvertedPrivilegeBean(AceBean bean) {
        Set<String> actions = new HashSet<String>();
        Set<String> privileges = new HashSet<String>();

        if (bean.getActions() != null) {
            actions = new HashSet<String>(Arrays.asList(bean.getActions()));
        }
        if (bean.getPrivileges() != null) {
            privileges = new HashSet<String>(
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
        // after converting all actions to privileges we can still have
        // aggregated privileges in the privileges variable
        // these also have to be replaced by their single jcr:privileges

        if (privileges.contains(PRIVILEGE_JCR_ALL)) {
            privileges.addAll(PRIVILEGES_MAP.get(PRIVILEGE_JCR_ALL));
        }
        if (privileges.contains(PRIVILEGE_JCR_WRITE)) {
            privileges.addAll(PRIVILEGES_MAP.get(PRIVILEGE_JCR_WRITE));
        }
        if (privileges.contains(PRIVILEGE_REP_WRITE)) {
            privileges.addAll(PRIVILEGES_MAP.get(PRIVILEGE_REP_WRITE));
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
