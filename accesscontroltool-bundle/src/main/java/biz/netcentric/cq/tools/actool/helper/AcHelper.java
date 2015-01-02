/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.helper;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.security.Principal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

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
import org.apache.jackrabbit.api.security.principal.PrincipalManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.comparators.AcePermissionComparator;
import biz.netcentric.cq.tools.actool.configuration.CqActionsMapping;
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

        aceBean.setActions(CqActionsMapping.getCqActions(
                ace.getPrivilegesString()).split(","));
        aceBean.setAllow(ace.isAllow());
        aceBean.setJcrPath(ace.getJcrPath());
        aceBean.setPrincipal(ace.getPrincipal().getName());
        aceBean.setPrivilegesString(ace.getPrivilegesString());
        aceBean.setRepGlob(ace.getRestrictionAsString("rep:glob"));

        return aceBean;
    }

    public static String getBlankString(final int nrOfBlanks) {
        return StringUtils.repeat(" ", nrOfBlanks);
    }

    /**
     * Method which installs all ACE contained in the configurations. if an ACL
     * is already existing in CRX the ACEs from the config get merged into the
     * ACL (the ones from config overwrite the ones in CRX) ACEs belonging to
     * groups which are not contained in any configuration don't get altered
     * 
     * @param pathBasedAceMapFromConfig
     *            map containing the ACE data from the merged configurations
     *            path based
     * @param repositoryDumpedAceMap
     *            map containing the ACL data from the repository dump, path
     *            based
     * @param authorizablesSet
     *            set which contains all group names contained in the
     *            configurations
     * @param templateMappings 
     * @param session
     * @param out
     * @param history
     *            history object
     * @throws Exception
     */

    public static void installPathBasedACEs(
            final Map<String, Set<AceBean>> pathBasedAceMapFromConfig,
            final Map<String, Set<AceBean>> repositoryDumpedAceMap,
            final Set<String> authorizablesSet,
            Map<String, String> templateMappings,
            final Session session,
            final AcInstallationHistoryPojo history) throws Exception {

        Set<String> paths = pathBasedAceMapFromConfig.keySet();
        LOG.debug("Paths in merged config = {}", paths);

        history.addVerboseMessage("found: " + paths.size()
                + "  paths in merged config");
        history.addVerboseMessage("found: " + authorizablesSet.size()
                + " authorizables in merged config");

        // counters for history output
        long aclsProcessedCounter = 0;
        long aclBeansProcessed = 0;

        // loop through all nodes from config
        for (String path : paths) {

            Set<AceBean> aceBeanSetFromConfig = pathBasedAceMapFromConfig
                    .get(path); // Set which holds the AceBeans of the current
                                // path in configuration
            Set<AceBean> aceBeanSetFromRepo = repositoryDumpedAceMap.get(path); // Set
                                                                                // which
                                                                                // holds
                                                                                // the
                                                                                // AceBeans
                                                                                // of
                                                                                // the
                                                                                // current
                                                                                // path
                                                                                // in
                                                                                // dump
                                                                                // from
                                                                                // repository

            if (aceBeanSetFromRepo != null) {
                aclBeansProcessed += aceBeanSetFromConfig.size();
                history.addVerboseMessage("\n installing ACE: "
                        + aceBeanSetFromConfig.toString());
                
                LOG.debug("Installing ACE: {}", aceBeanSetFromConfig);

                // get merged ACL
                aceBeanSetFromConfig = getMergedACL(aceBeanSetFromConfig,
                        aceBeanSetFromRepo, authorizablesSet, history);

                // delete ACL in repo
                PurgeHelper.purgeAcl(session, path);
                aclsProcessedCounter++;
            }

            // remove ACL of that path from ACLs from repo so that after the
            // loop has ended only paths are left which are not contained in
            // current config
            repositoryDumpedAceMap.remove(path);
            resetAclInRepository(session, history, aceBeanSetFromConfig, templateMappings);
        }

        // loop through ACLs which are NOT contained in the configuration

        for (Map.Entry<String, Set<AceBean>> entry : repositoryDumpedAceMap
                .entrySet()) {
            Set<AceBean> acl = entry.getValue();
            for (AceBean aceBean : acl) {

                // if the ACL form repo contains an ACE regarding an
                // authorizable from the groups config then delete all ACEs from
                // this authorizable from current ACL

                if (authorizablesSet.contains(aceBean.getPrincipalName())) {
                    AccessControlUtils.deleteAuthorizableFromACL(session,
                            aceBean.getJcrPath(), aceBean.getPrincipalName());
                    String message = "deleted all ACEs of authorizable "
                            + aceBean.getPrincipalName()
                            + " from ACL of path: " + aceBean.getJcrPath();
                    LOG.info(message);
                    history.addVerboseMessage(message);
                }
            }
            aclsProcessedCounter++;
        }
        history.addVerboseMessage("processed: " + aclsProcessedCounter
                + " ACLs in total");
    }

    private static void resetAclInRepository(final Session session,
            final AcInstallationHistoryPojo history,
            final Set<AceBean> aceBeanSetFromConfig, 
            Map<String, String> templateMappings)
            throws RepositoryException, UnsupportedRepositoryOperationException {

        JackrabbitSession js = (JackrabbitSession) session;
        PrincipalManager pm = js.getPrincipalManager();

        // reset ACL in repo with permissions from merged ACL
        for (AceBean bean : aceBeanSetFromConfig) {

            Principal currentPrincipal = getLdapImportGroupPrincipal(session,
                    bean);

            if (currentPrincipal == null) {
                String warningMessage = "Could not find definition for authorizable "
                        + bean.getPrincipalName()
                        + " in groups config while installing ACE for: "
                        + bean.getJcrPath()
                        + "! Don't install ACEs for this authorizable!\n";
                LOG.warn(warningMessage);
                history.addWarning(warningMessage);
                continue;

            } else {
                history.addVerboseMessage("starting installation of bean: \n"
                        + bean);
                // check if path exists in CRX
                if (session.itemExists(bean.getJcrPath())) {
                    installBean(session, history, bean, currentPrincipal);
                } else {
                    // Create page if necessary
                    if (createPageIfNecessary(session, history, bean.getJcrPath(), templateMappings)) {
                        installBean(session, history, bean, currentPrincipal);
                    } else {
                        String warningMessage = "path: "
                                + bean.getJcrPath()
                                + " doesn't exist in repository. Cancelled installation for this ACE!";
                        LOG.warn(warningMessage);
                        history.addWarning(warningMessage);
                        continue;
                    }
                }

            }
        }
    }

    private static boolean createPageIfNecessary(Session session,
            AcInstallationHistoryPojo history, String jcrPath,
            Map<String, String> templateMappings) {
        
        for (String path : templateMappings.keySet()) {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + path);
            Path ioPath = FileSystems.getDefault().getPath(jcrPath);
            if (matcher.matches(ioPath)) {
                LOG.info("Creating page at {} using template {}.", jcrPath, templateMappings.get(path));
                return true;
            }
        }
        return false;
    }

    /**
     * Method that searches a group by nodename or by ldap attribute 'cn' inside
     * the rep:principal property of a group node. Serves as a fallback, in case
     * a group can't be resolved by principal manager by its name provided in
     * config file after ldap import
     * 
     * @param session
     * @param aceBean
     * @return found Principal or null
     * @throws InvalidQueryException
     * @throws RepositoryException
     */
    private static Principal getLdapImportGroupPrincipal(final Session session,
            final AceBean aceBean) throws InvalidQueryException,
            RepositoryException {
        Principal principal = null;
        String principalName = aceBean.getPrincipalName();
        JackrabbitSession js = (JackrabbitSession) session;
        PrincipalManager pm = js.getPrincipalManager();

        principal = pm.getPrincipal(principalName);

        if (principal == null) {
            String query = "/jcr:root/home/groups//*[fn:name() = '"
                    + principalName + "']";
            principal = getPrincipalbyQuery(query, session, pm);
            if (principal == null) {
                query = "/jcr:root/home/groups//*[(@jcr:primaryType = 'rep:Group') and jcr:like(@rep:principalName, 'cn="
                        + principalName + "%')]";
                principal = getPrincipalbyQuery(query, session, pm);
            }
        }
        return principal;
    }

    private static Principal getPrincipalbyQuery(
            final String queryStringGroups, final Session session,
            final PrincipalManager pm) throws InvalidQueryException,
            RepositoryException {

        Query queryGroups = session.getWorkspace().getQueryManager()
                .createQuery(queryStringGroups, Query.XPATH);
        QueryResult queryResultGroups = queryGroups.execute();
        NodeIterator nitGroups = queryResultGroups.getNodes();
        Set<String> groups = new TreeSet<String>();
        while (nitGroups.hasNext()) {
            Node node = nitGroups.nextNode();
            String tmp = node.getProperty("rep:principalName").getString();
            groups.add(tmp);
        }
        return pm.getPrincipal(groups.iterator().next());
    }

    private static void installBean(final Session session,
            final AcInstallationHistoryPojo history, AceBean bean,
            Principal currentPrincipal) throws RepositoryException,
            UnsupportedRepositoryOperationException {
        if (bean.getActions() != null) {

            // install actions
            history.addVerboseMessage("adding action for path: "
                    + bean.getJcrPath() + ", principal: "
                    + currentPrincipal.getName() + ", actions: "
                    + bean.getActionsString() + ", permission: "
                    + bean.getPermission());
            AccessControlUtils.addActions(session, bean, currentPrincipal,
                    history);

            // since CqActions.installActions() doesn't allow to set
            // jcr:privileges and globbing, this is done in a dedicated step

            if (StringUtils.isNotBlank(bean.getRepGlob())
                    || StringUtils.isNotBlank(bean.getPrivilegesString())) {
                AccessControlUtils.setPermissionAndRestriction(session, bean,
                        currentPrincipal.getName());
            }

        } else {
            AccessControlUtils.installPermissions(session, bean.getJcrPath(),
                    currentPrincipal, bean.isAllow(), bean.getRepGlob(),
                    bean.getPrivileges());
        }

    }

    /**
     * Method that merges an ACL from configuration in a ACL from CRX both
     * having the same parent. ACEs in CRX belonging to a group which is defined
     * in the configuration get replaced by ACEs from the configuration. Other
     * ACEs don't get changed.
     * 
     * @param aclfromConfig
     *            Set containing an ACL from configuration
     * @param aclFomRepository
     *            Set containing an ACL from repository dump
     * @param allAuthorizablesFromConfigsSet
     *            Set containing the names of all groups contained in the
     *            configurations(s)
     * @return merged Set
     */
    static Set<AceBean> getMergedACL(final Set<AceBean> aclfromConfig,
            final Set<AceBean> aclFomRepository,
            final Set<String> allAuthorizablesFromConfigsSet,
            final AcInstallationHistoryPojo history) {

        // build a Set which contains all authorizable ids from the current ACL
        // from config for the current node
        Set<String> authorizablesInAclFromConfig = new LinkedHashSet<String>();

        // Set for storage of the new ACL that'll replace the one in repo,
        // containing ordered ACEs (denies before allows)
        Set<AceBean> orderedMergedAceSet = new TreeSet<AceBean>(
                new AcePermissionComparator());

        for (AceBean aceBean : aclfromConfig) {
            authorizablesInAclFromConfig.add(aceBean.getPrincipalName());
            orderedMergedAceSet.add(aceBean);
        }
        LOG.info("authorizablesInAclFromConfig: {}",
                authorizablesInAclFromConfig);

        // loop through the ACL from repository
        for (AceBean aceBeanFromRepository : aclFomRepository) {
            // if the ACL from repo contains an authorizable from the groups
            // config but the ACL from the config does not - "delete" the
            // respective ACE by not adding it to the orderedMergedSet
            if (!authorizablesInAclFromConfig.contains(aceBeanFromRepository
                    .getPrincipalName())
                    && !allAuthorizablesFromConfigsSet
                            .contains(aceBeanFromRepository.getPrincipalName())) {
                // add the ACE from repo
                orderedMergedAceSet.add(aceBeanFromRepository);
                String message = "add following ACE to the merged ACL: "
                        + aceBeanFromRepository;
                LOG.debug(message);
            } else {
                String message = "following ACE bean doesn't get added to the merged ACL and thus deleted from repository: "
                        + aceBeanFromRepository;
                LOG.debug(message);
            }
        }
        return orderedMergedAceSet;
    }

    public static Map<String, Set<AceBean>> createAceMap(
            final SlingHttpServletRequest request, final int keyOrdering,
            final int aclOrdering, final String[] excludePaths,
            Dumpservice dumpservice) throws ValueFormatException,
            IllegalStateException, RepositoryException {
        Session session = request.getResourceResolver().adaptTo(Session.class);
        return dumpservice.createFilteredAclDumpMap(session, keyOrdering,
                aclOrdering, excludePaths).getAceDump();
    }

    /**
     * changes a group based ACE map into a path based ACE map
     * 
     * @param groupBasedAceMap
     * @param sorting
     *            specifies whether ACEs get sorted by permissions (all denies
     *            followed by all allows)
     * @return
     */
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
                && bean1.isAllow() == bean2.isAllow()
                && bean1.getRepGlob().equals(bean2.getRepGlob())
                && bean1.getPermission().equals(bean2.getPermission())
                && bean1Privileges.containsAll(bean2Privileges)) {
            return true;
        }
        return false;

    }
}
