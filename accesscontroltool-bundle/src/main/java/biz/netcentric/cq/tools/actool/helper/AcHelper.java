/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.helper;

import java.security.Principal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import javax.jcr.AccessDeniedException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.ValueFormatException;
import javax.jcr.query.InvalidQueryException;
import javax.jcr.query.Query;
import javax.jcr.query.QueryResult;

import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.comparators.AcePermissionComparator;
import biz.netcentric.cq.tools.actool.dumpservice.Dumpservice;
import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;

public class AcHelper {

    private AcHelper() {
    }

    public static final Logger LOG = LoggerFactory.getLogger(AcHelper.class);

    public static int ACE_ORDER_DENY_ALLOW = 1;
    public static int ACE_ORDER_NONE = 2;
    public static int ACE_ORDER_ALPHABETICAL = 3;

    public static int PRINCIPAL_BASED_ORDER = 1;
    public static int PATH_BASED_ORDER = 2;

    public static AceBean getAceBean(final AceWrapper ace)
            throws ValueFormatException, IllegalStateException,
            RepositoryException {
        AceBean aceBean = new AceBean();

        aceBean.setPermission(ace.isAllow() ? "allow" : "deny");
        aceBean.setJcrPath(ace.getJcrPath());
        aceBean.setPrincipal(ace.getPrincipal().getName());
        aceBean.setPrivilegesString(ace.getPrivilegesString());
        aceBean.setRepGlob(ace.getRestrictionAsString("rep:glob"));

        return aceBean;
    }

    public static String getBlankString(final int nrOfBlanks) {
        return StringUtils.repeat(" ", nrOfBlanks);
    }

    /** Method which installs all ACE contained in the configurations. if an ACL is already existing in CRX the ACEs from the config get
     * merged into the ACL (the ones from config overwrite the ones in CRX) ACEs belonging to groups which are not contained in any
     * configuration don't get altered
     *
     * @param pathBasedAceMapFromConfig map containing the ACE data from the merged configurations path based
     * @param authorizablesSet set which contains all group names contained in the configurations
     * @param session
     * @param out
     * @param history history object
     * @throws Exception */

    public static void installPathBasedACEs(
            final Map<String, Set<AceBean>> pathBasedAceMapFromConfig,
            final Session session,
            final AcInstallationHistoryPojo history) throws Exception {

        Set<String> paths = pathBasedAceMapFromConfig.keySet();
        LOG.info("Paths in merged config = {}", paths);

        history.addVerboseMessage("found: " + paths.size()
                + "  paths in merged config");

        // loop through all nodes from config
        for (String path : paths) {

            Set<AceBean> aceBeanSetFromConfig = pathBasedAceMapFromConfig
                    .get(path); // Set which holds the AceBeans of the current
            // path in configuration
            
            // order entries (denies in front of allows)
            Set<AceBean> orderedAceBeanSetFromConfig = new TreeSet<AceBean>(
                    new AcePermissionComparator());
            orderedAceBeanSetFromConfig.addAll(aceBeanSetFromConfig);

            // remove ACL of that path from ACLs from repo so that after the
            // loop has ended only paths are left which are not contained in
            // current config
            for (AceBean bean : orderedAceBeanSetFromConfig) {
            	AccessControlUtils.deleteAllEntriesForAuthorizableFromACL(session,
                        path, bean.getPrincipalName());
                String message = "deleted all ACEs of authorizable "
                        + bean.getPrincipalName()
                        + " from ACL of path: " + path;
                LOG.info(message);
                history.addVerboseMessage(message);
            }
            writeAcBeansToRepository(session, history, orderedAceBeanSetFromConfig);
        }
    }

    private static void writeAcBeansToRepository(final Session session,
            final AcInstallationHistoryPojo history,
            final Set<AceBean> aceBeanSetFromConfig)
            throws RepositoryException, UnsupportedRepositoryOperationException {

        // reset ACL in repo with permissions from merged ACL
        for (AceBean bean : aceBeanSetFromConfig) {

            LOG.debug("Writing bean to repository {}", bean);

            Principal currentPrincipal = getPrincipal(session, bean);

            if (currentPrincipal == null) {
                String errMessage = "Could not find definition for authorizable "
                        + bean.getPrincipalName()
                        + " in groups config while installing ACE for: "
                        + bean.getJcrPath()
                        + "! Skipped installation of ACEs for this authorizable!\n";
                LOG.error(errMessage);
                history.addError(errMessage);
                continue;

            } else {
                history.addVerboseMessage("starting installation of bean: \n"
                        + bean);
                // check if path exists in CRX
                if (session.itemExists(bean.getJcrPath())) {
                    bean.writeToRepository(session, currentPrincipal, history);
                } else {
                    String warningMessage = "path: "
                            + bean.getJcrPath()
                            + " doesn't exist in repository. Skipped installation of this ACE!";
                    LOG.warn(warningMessage);
                    history.addWarning(warningMessage);
                    continue;
                }

            }
        }
    }

    /** Method that searches a group by nodename or by ldap attribute 'cn' inside the rep:principal property of a group node. Serves as a
     * fallback, in case a group can't be resolved by principal manager by its name provided in config file after ldap import
     *
     * @param session
     * @param aceBean
     * @return found Principal or null
     * @throws InvalidQueryException
     * @throws RepositoryException */
    private static Principal getPrincipal(final Session session,
            final AceBean aceBean) throws InvalidQueryException,
            RepositoryException {
        Principal principal = null;
        String principalName = aceBean.getPrincipalName();

        principal = getPrincipalForName(session, principalName);

        if (principal == null) {
            String query = "/jcr:root/home/groups//*[(@jcr:primaryType = 'rep:Group') and jcr:like(@rep:principalName, 'cn="
                    + principalName + "%')]";
            LOG.debug("Fallback query did not return results for principalName={}, using second fallback query (ldap name): {}",
                    principalName, query);
            principal = getPrincipalByQuery(query, session);
        }

        LOG.debug("Returning {} for principal {}", principal, principalName);
        return principal;
    }

    private static Principal getPrincipalForName(final Session session, String principalName) throws AccessDeniedException,
    UnsupportedRepositoryOperationException, RepositoryException {
        Principal principal = null;
        // AEM 6.1 has potentially a delayed visibility of just created groups when using PrincipalManager, therefore using UserManager
        // Also see https://issues.apache.org/jira/browse/OAK-3228
        JackrabbitSession js = (JackrabbitSession) session;
        UserManager userManager = js.getUserManager();
        Authorizable authorizable = userManager.getAuthorizable(principalName);
        principal = authorizable != null ? authorizable.getPrincipal() : null;
        return principal;
    }

    private static Principal getPrincipalByQuery(final String queryStringGroups, final Session session) throws InvalidQueryException,
    RepositoryException {

        Query queryGroups = session.getWorkspace().getQueryManager().createQuery(queryStringGroups, Query.XPATH);
        QueryResult queryResultGroups = queryGroups.execute();
        NodeIterator nitGroups = queryResultGroups.getNodes();
        String principalName;
        if (!nitGroups.hasNext()) {
            LOG.debug("Executing query '{}' did not have any results", queryStringGroups);
            return null;
        }
        Node node = nitGroups.nextNode();

        if (node.hasProperty("rep:principalName")) {
            principalName = node.getProperty("rep:principalName").getString();
            Principal principal = getPrincipalForName(session, principalName);
            return principal;
        }
        LOG.debug("Group '{}' did not have a rep:principalName property", node.getPath());

        return null;
    }

    public static Map<String, Set<AceBean>> createAceMap(
            final SlingHttpServletRequest request, final int keyOrdering,
            final int aclOrdering, final String[] excludePaths,
            Dumpservice dumpservice) throws ValueFormatException,
            IllegalStateException, RepositoryException {
        Session session = request.getResourceResolver().adaptTo(Session.class);
        return dumpservice.createAclDumpMap(session, keyOrdering,
                aclOrdering, excludePaths).getAceDump();
    }

    /** changes a group based ACE map into a path based ACE map
     *
     * @param groupBasedAceMap
     * @param sorting specifies whether ACEs get sorted by permissions (all denies followed by all allows)
     * @return */
    public static Map<String, Set<AceBean>> getPathBasedAceMap(
            final Map<String, Set<AceBean>> groupBasedAceMap, final int sorting) {
        Map<String, Set<AceBean>> pathBasedAceMap = new HashMap<String, Set<AceBean>>(
                groupBasedAceMap.size());

        // loop through all Sets of groupBasedAceMap
        for (Entry<String, Set<AceBean>> entry : groupBasedAceMap.entrySet()) {
            String principal = entry.getKey();

            // get current Set of current principal
            Set<AceBean> tmpSet = entry.getValue();

            for (AceBean bean : tmpSet) {

                // set current principal
                bean.setPrincipal(principal);

                // if there isn't already a path key in pathBasedAceMap create a
                // new one and add new Set
                // with current ACE as first entry
                if (pathBasedAceMap.get(bean.getJcrPath()) == null) {

                    Set<AceBean> aceSet = null;
                    if (sorting == AcHelper.ACE_ORDER_NONE) {
                        aceSet = new HashSet<AceBean>();
                    } else if (sorting == AcHelper.ACE_ORDER_DENY_ALLOW) {
                        aceSet = new TreeSet<AceBean>(
                                new biz.netcentric.cq.tools.actool.comparators.AcePermissionComparator());
                    }

                    aceSet.add(bean);
                    pathBasedAceMap.put(bean.getJcrPath(), aceSet);
                    // add current ACE to Set
                } else {
                    pathBasedAceMap.get(bean.getJcrPath()).add(bean);
                }
            }
        }
        return pathBasedAceMap;
    }

    public static boolean isEqualBean(final AceBean bean1, final AceBean bean2) {
        Set<String> bean1Privileges = new HashSet<String>(Arrays.asList(bean1
                .getPrivilegesString().split(",")));
        Set<String> bean2Privileges = new HashSet<String>(Arrays.asList(bean2
                .getPrivilegesString().split(",")));

        if (bean1.getJcrPath().equals(bean2.getJcrPath())
                && bean1.getPrincipalName().equals(bean2.getPrincipalName())
                && (bean1.isAllow() == bean2.isAllow())
                && bean1.getRepGlob().equals(bean2.getRepGlob())
                && bean1.getPermission().equals(bean2.getPermission())
                && bean1Privileges.containsAll(bean2Privileges)) {
            return true;
        }
        return false;

    }
}
