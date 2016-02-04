/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.aceservice.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.ValueFormatException;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.StopWatch;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.principal.PrincipalManager;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.jcr.api.SlingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.aceservice.AceService;
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableCreatorException;
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableCreatorService;
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableInstallationHistory;
import biz.netcentric.cq.tools.actool.configreader.ConfigFilesRetriever;
import biz.netcentric.cq.tools.actool.configreader.ConfigReader;
import biz.netcentric.cq.tools.actool.configreader.ConfigurationMerger;
import biz.netcentric.cq.tools.actool.dumpservice.Dumpservice;
import biz.netcentric.cq.tools.actool.helper.AcHelper;
import biz.netcentric.cq.tools.actool.helper.AccessControlUtils;
import biz.netcentric.cq.tools.actool.helper.AceBean;
import biz.netcentric.cq.tools.actool.helper.AclBean;
import biz.netcentric.cq.tools.actool.helper.PurgeHelper;
import biz.netcentric.cq.tools.actool.helper.QueryHelper;
import biz.netcentric.cq.tools.actool.installationhistory.AcHistoryService;
import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;

@Service
@Component(metatype = true, label = "AC Installation Service", description = "Service that installs groups & ACEs according to textual configuration files")
public class AceServiceImpl implements AceService {
    private static final Logger LOG = LoggerFactory.getLogger(AceServiceImpl.class);

    static final String PROPERTY_CONFIGURATION_PATH = "AceService.configurationPath";

    @Reference
    AuthorizableCreatorService authorizableCreatorService;

    @Reference
    private SlingRepository repository;

    @Reference
    AcHistoryService acHistoryService;

    @Reference
    private Dumpservice dumpservice;

    @Reference
    private ConfigReader configReader;

    @Reference
    private ConfigurationMerger configurationMerger;

    @Reference
    private ConfigFilesRetriever configFilesRetriever;

    private boolean isExecuting = false;

    @Property(label = "Configuration storage path",
            description = "enter CRX path where ACE configuration gets stored",
            name = AceServiceImpl.PROPERTY_CONFIGURATION_PATH, value = "")
    private String configurationPath;

    @Activate
    public void activate(@SuppressWarnings("rawtypes") final Map properties)
            throws Exception {
        LOG.debug("Activated AceService!");
        modified(properties);
    }

    @Modified
    public void modified(@SuppressWarnings("rawtypes") final Map properties) {
        LOG.debug("Modified AceService!");
        configurationPath = PropertiesUtil.toString(properties.get(PROPERTY_CONFIGURATION_PATH), "");
    }

    // FIXME: Why is this called installConfigurationFromYamlList if it doesn't use YAML?
    private void installConfigurationFromYamlList(
            final List mergedConfigurations, AcInstallationHistoryPojo history,
            final Session session,
            Set<AuthorizableInstallationHistory> authorizableHistorySet,
            Map<String, Set<AceBean>> repositoryDumpAceMap) throws Exception {

        // FIXME: putting different types of configuration objects in a heterogeneous list is pretty bad
        Map<String, LinkedHashSet<AuthorizableConfigBean>> authorizablesMapfromConfig = (Map<String, LinkedHashSet<AuthorizableConfigBean>>) mergedConfigurations
                .get(0);
        Map<String, Set<AceBean>> aceMapFromConfig = (Map<String, Set<AceBean>>) mergedConfigurations
                .get(1);

        if (aceMapFromConfig == null) {
            String message = "ace config not found in YAML file! installation aborted!";
            LOG.error(message);
            throw new IllegalArgumentException(message);
        }

        Set<String> authorizablesToRemoveAcesFor = getAuthorizablesToRemoveAcesFor(authorizablesMapfromConfig);

        removeAcesForAuthorizables(history, session, authorizablesToRemoveAcesFor, repositoryDumpAceMap);
        installAuthorizables(history, authorizableHistorySet, authorizablesMapfromConfig);
        installAces(history, session, aceMapFromConfig);
    }

    private Set<String> getAuthorizablesToRemoveAcesFor(Map<String, LinkedHashSet<AuthorizableConfigBean>> authorizablesMapfromConfig) {
        Set<String> authorizablesToRemoveAcesFor = new HashSet<String>(authorizablesMapfromConfig.keySet());
        Set<String> authorizablesToBeMigrated = collectAuthorizablesToBeMigrated(authorizablesMapfromConfig);
        Collection<?> invalidAuthorizablesInConfig = CollectionUtils.intersection(authorizablesToRemoveAcesFor, authorizablesToBeMigrated);
        if (!invalidAuthorizablesInConfig.isEmpty()) {
            String message = "If migrateFrom feature is used, groups that shall be migrated from must not be present in regular configuration (offending groups: "
                    + invalidAuthorizablesInConfig + ")";
            LOG.error(message);
            throw new IllegalArgumentException(message);
        }
        authorizablesToRemoveAcesFor.addAll(authorizablesToBeMigrated);
        return authorizablesToRemoveAcesFor;
    }

    private Set<String> collectAuthorizablesToBeMigrated(Map<String, LinkedHashSet<AuthorizableConfigBean>> authorizablesMapfromConfig) {
        Set<String> authorizablesToBeMigrated = new HashSet<String>();
        for (String principalStr : authorizablesMapfromConfig.keySet()) {
            LinkedHashSet<AuthorizableConfigBean> authorizableConfigBeans = authorizablesMapfromConfig.get(principalStr);
            for (AuthorizableConfigBean authorizableConfigBean : authorizableConfigBeans) {
                String migrateFrom = authorizableConfigBean.getMigrateFrom();
                if (StringUtils.isNotBlank(migrateFrom)) {
                    authorizablesToBeMigrated.add(migrateFrom);
                }
            }
        }
        return authorizablesToBeMigrated;
    }

    private void removeAcesForAuthorizables(AcInstallationHistoryPojo history, Session session, Set<String> authorizablesSet,
            Map<String, Set<AceBean>> repositoryDumpAceMap) throws UnsupportedRepositoryOperationException, RepositoryException {
        // loop through all ACLs found in the repository
        for (Map.Entry<String, Set<AceBean>> entry : repositoryDumpAceMap.entrySet()) {
            Set<AceBean> acl = entry.getValue();
            for (AceBean aceBean : acl) {
                // if the ACL from repo contains an ACE regarding an
                // authorizable from the groups config then delete all ACEs from
                // this authorizable from current ACL
                if (authorizablesSet.contains(aceBean.getPrincipalName())) {
                    AccessControlUtils.deleteAllEntriesForAuthorizableFromACL(session,
                            aceBean.getJcrPath(), aceBean.getPrincipalName());
                    String message = "deleted all ACEs of authorizable "
                            + aceBean.getPrincipalName()
                            + " from ACL of path: " + aceBean.getJcrPath();
                    LOG.info(message);
                    history.addVerboseMessage(message);
                }
            }
        }
    }

    private void installAces(
            AcInstallationHistoryPojo history,
            final Session session,
            Map<String, Set<AceBean>> aceMapFromConfig) throws Exception {
        // --- installation of ACEs from configuration ---
        Map<String, Set<AceBean>> pathBasedAceMapFromConfig = AcHelper
                .getPathBasedAceMap(aceMapFromConfig,
                        AcHelper.ACE_ORDER_DENY_ALLOW);

        LOG.info("--- start installation of access control configuration ---");
        AcHelper.installPathBasedACEs(pathBasedAceMapFromConfig, session, history);
    }

    private void installAuthorizables(
            AcInstallationHistoryPojo history,
            Set<AuthorizableInstallationHistory> authorizableHistorySet,
            Map<String, LinkedHashSet<AuthorizableConfigBean>> authorizablesMapfromConfig)
                    throws RepositoryException, Exception {
        // --- installation of Authorizables from configuration ---

        LOG.info("--- start installation of Authorizable Configuration ---");

        // create own session for installation of authorizables since these have
        // to be persisted in order
        // to have the principals available when installing the ACEs

        // therefore the installation of all ACEs from all configurations uses
        // an own session (which get passed as
        // parameter to this method), which only get saved when no exception was
        // thrown during the installation of the ACEs

        // in case of an exception during the installation of the ACEs the
        // performed installation of authorizables from config
        // has to be reverted using the rollback method
        Session authorizableInstallationSession = repository.loginAdministrative(null);
        try {
            // only save session if no exceptions occured
            AuthorizableInstallationHistory authorizableInstallationHistory = new AuthorizableInstallationHistory();
            authorizableHistorySet.add(authorizableInstallationHistory);
            authorizableCreatorService.createNewAuthorizables(
                    authorizablesMapfromConfig,
                    authorizableInstallationSession, history,
                    authorizableInstallationHistory);
            authorizableInstallationSession.save();
        } catch (Exception e) {
            throw e;
        } finally {
            if (authorizableInstallationSession != null) {
                authorizableInstallationSession.logout();
            }
        }

        String message = "Finished installation of groups configuration without errors";
        history.addMessage(message);
        LOG.info(message);
    }

    /** executes the installation of the existing configurations */
    @Override
    public AcInstallationHistoryPojo execute() {

        Session session = null;
        AcInstallationHistoryPojo history = new AcInstallationHistoryPojo();
        Set<AuthorizableInstallationHistory> authorizableInstallationHistorySet = new LinkedHashSet<AuthorizableInstallationHistory>();

        try {
            session = repository.loginAdministrative(null);
            String rootPath = getConfigurationRootPath();
            Node rootNode = session.getNode(rootPath);
            Map<String, String> newestConfigurations = configFilesRetriever.getConfigFileContentFromNode(rootNode);

            installNewConfigurations(session, history, newestConfigurations, authorizableInstallationHistorySet);
        } catch (AuthorizableCreatorException e) {
            history.addError(e.toString());
            // here no rollback of authorizables necessary since session wasn't
            // saved
        } catch (Exception e) {
            // in case an installation of an ACE configuration
            // threw an exception, logout from this session
            // otherwise changes made on the ACLs would get persisted

            session.logout();

            LOG.error("Exception in AceServiceImpl: {}", e);
            history.addError(e.toString());

            for (AuthorizableInstallationHistory authorizableInstallationHistory : authorizableInstallationHistorySet) {
                try {
                    String message = "performing authorizable installation rollback(s)";
                    LOG.info(message);
                    history.addMessage(message);
                    authorizableCreatorService.performRollback(repository,
                            authorizableInstallationHistory, history);
                } catch (RepositoryException e1) {
                    LOG.error("Exception: ", e1);
                }
            }
        } finally {
            session.logout();
            isExecuting = false;
            acHistoryService.persistHistory(history, configurationPath);

        }
        return history;
    }

    @Override
    public void installNewConfigurations(Session session,
            AcInstallationHistoryPojo history,
            Map<String, String> newestConfigurations, Set<AuthorizableInstallationHistory> authorizableInstallationHistorySet)
                    throws Exception {

        StopWatch sw = new StopWatch();
        sw.start();
        isExecuting = true;

        if (newestConfigurations != null) {

            List mergedConfigurations = configurationMerger.getMergedConfigurations(newestConfigurations, history, configReader);

            installMergedConfigurations(history, session,
                    authorizableInstallationHistorySet,
                    mergedConfigurations);

            // if everything went fine (no exceptions), save the session
            // thus persisting the changed ACLs
            history.addMessage("finished (transient) installation of access control configuration without errors!");
            session.save();
            history.addMessage("persisted changes of ACLs");
        }
        sw.stop();
        long executionTime = sw.getTime();
        LOG.info("installation of AccessControlConfiguration took: {} ms",
                executionTime);
        history.setExecutionTime(executionTime);
    }

    private void installMergedConfigurations(
            AcInstallationHistoryPojo history,
            Session session,
            Set<AuthorizableInstallationHistory> authorizableInstallationHistorySet,
            List mergedConfigurations) throws ValueFormatException,
            RepositoryException, Exception {
        String message = "start installation of merged configurations";
        LOG.info(message);
        history.addMessage(message);

        Map<String, Set<AceBean>> repositoryDumpAceMap = null;
        LOG.info("start building dump from repository");
        repositoryDumpAceMap = dumpservice.createAclDumpMap(
                session, AcHelper.PATH_BASED_ORDER,
                AcHelper.ACE_ORDER_NONE,
                dumpservice.getQueryExcludePaths()).getAceDump();

        installConfigurationFromYamlList(mergedConfigurations, history,
                session, authorizableInstallationHistorySet,
                repositoryDumpAceMap);
    }

    @Override
    public boolean isReadyToStart() {
        String rootPath = getConfigurationRootPath();
        Session session = null;
        try {
            session = repository.loginAdministrative(null);
            Node rootNode = session.getNode(rootPath);
            return !configFilesRetriever.getConfigFileContentFromNode(rootNode).isEmpty();
        } catch (Exception e) {

        } finally {
            if (session != null) {
                session.logout();
            }
        }
        return false;
    }

    @Override
    public String purgeACL(String path) {
        Session session = null;
        String message = "";
        boolean flag = true;
        try {
            session = repository.loginAdministrative(null);
            PurgeHelper.purgeAcl(session, path);
            session.save();
        } catch (Exception e) {
            // TO DO: Logging
            flag = false;
            message = e.toString();
            LOG.error("Exception: ", e);
        } finally {
            if (session != null) {
                session.logout();
            }
        }
        if (flag) {
            // TODO: save purge history under current history node

            message = "Deleted AccessControlList of node: " + path;
            AcInstallationHistoryPojo history = new AcInstallationHistoryPojo();
            history.addMessage("purge method: purgeACL()");
            history.addMessage(message);
            acHistoryService.persistAcePurgeHistory(history);
            return message;
        }
        return "Deletion of ACL failed! Reason:" + message;
    }

    @Override
    public String purgeACLs(String path) {
        Session session = null;
        String message = "";
        boolean flag = true;
        try {
            session = repository.loginAdministrative(null);
            message = PurgeHelper.purgeACLs(session, path);
            AcInstallationHistoryPojo history = new AcInstallationHistoryPojo();
            history.addMessage("purge method: purgeACLs()");
            history.addMessage(message);
            acHistoryService.persistAcePurgeHistory(history);
            session.save();
        } catch (Exception e) {
            LOG.error("Exception: ", e);
            flag = false;
            message = e.toString();
        } finally {
            if (session != null) {
                session.logout();
            }
        }
        if (flag) {
            return message;
        }
        return "Deletion of ACL failed! Reason:" + message;
    }

    @Override
    public String purgeAuthorizablesFromConfig() {
        Session session = null;
        String message = "";
        try {
            session = repository.loginAdministrative(null);

            Set<String> authorizabesFromConfigurations = getAllAuthorizablesFromConfig(session);
            message = purgeAuthorizables(authorizabesFromConfigurations,
                    session);
            AcInstallationHistoryPojo history = new AcInstallationHistoryPojo();
            history.addMessage("purge method: purgAuthorizablesFromConfig()");
            history.addMessage(message);
            acHistoryService.persistAcePurgeHistory(history);
        } catch (RepositoryException e) {
            LOG.error("RepositoryException: ", e);
        } catch (Exception e) {
            LOG.error("Exception: ", e);
        } finally {
            if (session != null) {
                session.logout();
            }
        }
        return message;
    }

    @Override
    public String purgeAuthorizables(String authorizableIds) {
        Session session = null;
        String message = "";
        try {
            try {
                session = repository.loginAdministrative(null);
                authorizableIds = authorizableIds.trim();
                Set<String> authorizablesSet = new HashSet<String>(
                        new ArrayList(Arrays.asList(authorizableIds.split(","))));
                message = purgeAuthorizables(authorizablesSet, session);
                AcInstallationHistoryPojo history = new AcInstallationHistoryPojo();
                history.addMessage("purge method: purgeAuthorizables()");
                history.addMessage(message);
                acHistoryService.persistAcePurgeHistory(history);
            } catch (RepositoryException e) {
                LOG.error("Exception: ", e);

            }
        } finally {
            if (session != null) {
                session.logout();
            }
        }
        return message;
    }

    private String purgeAuthorizables(Set<String> authorizableIds,
            final Session session) {

        StringBuilder message = new StringBuilder();
        String message2 = "";
        try {
            JackrabbitSession js = (JackrabbitSession) session;
            UserManager userManager = js.getUserManager();

            // Try do disable the autosave only in case if changes are automatically persisted
            if (userManager.isAutoSave()) {
                try {
                    userManager.autoSave(false);
                } catch (final UnsupportedRepositoryOperationException e) {
                    // check added for AEM 6.0
                    LOG.warn("disabling autoSave not possible with this user manager!");
                }
            }

            PrincipalManager principalManager = js.getPrincipalManager();

            for (String authorizableId : authorizableIds) {
                message.append(deleteAuthorizableFromHome(authorizableId,
                        userManager, principalManager));
            }

            Set<AclBean> aclBeans = QueryHelper.getAuthorizablesAcls(session,
                    authorizableIds);

            message.append(PurgeHelper.deleteAcesFromAuthorizables(session,
                    authorizableIds, aclBeans));
            session.save();
        } catch (RepositoryException e) {
            message2 = message2
                    + " deletion of ACEs failed! reason: RepositoryException: "
                    + e.toString();
            LOG.error("RepositoryException: ", e);
        } catch (Exception e) {
            LOG.error("Exception: ", e);
        }

        return message + message2;
    }

    private String deleteAuthorizableFromHome(final String authorizableId,
            final UserManager userManager,
            final PrincipalManager principalManager) {
        String message;
        if (principalManager.hasPrincipal(authorizableId)) {
            Authorizable authorizable;
            try {
                authorizable = userManager.getAuthorizable(authorizableId);
                authorizable.remove();
            } catch (RepositoryException e) {
                LOG.error("RepositoryException: ", e);
            }
            message = "removed authorizable: " + authorizableId
                    + " from /home\n";
        } else {
            message = "deletion of authorizable: " + authorizableId
                    + " from home failed! Reason: authorizable doesn't exist\n";
        }
        return message;
    }

    @Override
    public boolean isExecuting() {
        return isExecuting;
    }

    @Override
    public String getConfigurationRootPath() {
        return configurationPath;
    }

    @Override
    public Set<String> getCurrentConfigurationPaths() {

        Session session = null;
        Set<String> paths = new LinkedHashSet<String>();

        try {
            session = repository.loginAdministrative(null);
            Node rootNode = session.getNode(configurationPath);
            paths = configFilesRetriever.getConfigFileContentFromNode(rootNode).keySet();
        } catch (Exception e) {

        } finally {
            if (session != null) {
                session.logout();
            }
        }
        return paths;
    }

    public Set<String> getAllAuthorizablesFromConfig(Session session)
            throws Exception {
        AcInstallationHistoryPojo history = new AcInstallationHistoryPojo();
        Node rootNode = session.getNode(configurationPath);
        Map<String, String> newestConfigurations = configFilesRetriever.getConfigFileContentFromNode(rootNode);
        List mergedConfigurations = configurationMerger.getMergedConfigurations(
                newestConfigurations, history, configReader);
        return ((Map<String, Set<AceBean>>) mergedConfigurations.get(0)).keySet();
    }

}
