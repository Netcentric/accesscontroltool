/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.aceservice.impl;

import static biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo.msHumanReadable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
import biz.netcentric.cq.tools.actool.acls.AceBeanInstaller;
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableCreatorException;
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableCreatorService;
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableInstallationHistory;
import biz.netcentric.cq.tools.actool.configmodel.AcConfiguration;
import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.configreader.ConfigFilesRetriever;
import biz.netcentric.cq.tools.actool.configreader.ConfigReader;
import biz.netcentric.cq.tools.actool.configreader.ConfigurationMerger;
import biz.netcentric.cq.tools.actool.dumpservice.Dumpservice;
import biz.netcentric.cq.tools.actool.helper.AcHelper;
import biz.netcentric.cq.tools.actool.helper.AccessControlUtils;
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
    AceBeanInstaller aceBeanInstaller;

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
    public void activate(final Map<?,?> properties)
            throws Exception {
        LOG.debug("Activated AceService!");
        modified(properties);
    }

    @Modified
    public void modified(final Map<?,?> properties) {
        LOG.debug("Modified AceService!");
        configurationPath = PropertiesUtil.toString(properties.get(PROPERTY_CONFIGURATION_PATH), "");
    }

    private void installAcConfiguration(
            AcConfiguration acConfiguration, AcInstallationHistoryPojo history,
            final Session session,
            Set<AuthorizableInstallationHistory> authorizableHistorySet,
            Map<String, Set<AceBean>> repositoryDumpAceMap) throws Exception {

        Map<String, Set<AuthorizableConfigBean>> authorizablesMapfromConfig = acConfiguration.getAuthorizablesConfig();
        Map<String, Set<AceBean>> aceMapFromConfig = acConfiguration.getAceConfig();

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

    private Set<String> getAuthorizablesToRemoveAcesFor(Map<String, Set<AuthorizableConfigBean>> authorizablesMapfromConfig) {
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

    private Set<String> collectAuthorizablesToBeMigrated(Map<String, Set<AuthorizableConfigBean>> authorizablesMapfromConfig) {
        Set<String> authorizablesToBeMigrated = new HashSet<String>();
        for (String principalStr : authorizablesMapfromConfig.keySet()) {
            Set<AuthorizableConfigBean> authorizableConfigBeans = authorizablesMapfromConfig.get(principalStr);
            for (AuthorizableConfigBean authorizableConfigBean : authorizableConfigBeans) {
                String migrateFrom = authorizableConfigBean.getMigrateFrom();
                if (StringUtils.isNotBlank(migrateFrom)) {
                    authorizablesToBeMigrated.add(migrateFrom);
                }
            }
        }
        return authorizablesToBeMigrated;
    }

    private void removeAcesForAuthorizables(AcInstallationHistoryPojo history, Session session, Set<String> authorizablesInConfig,
            Map<String, Set<AceBean>> repositoryDumpAceMap) throws UnsupportedRepositoryOperationException, RepositoryException {
        // loop through all ACLs found in the repository
        for (Map.Entry<String, Set<AceBean>> entry : repositoryDumpAceMap.entrySet()) {
            Set<AceBean> existingAcl = entry.getValue();
            for (AceBean existingAce : existingAcl) {
                // if the ACL from repo contains an ACE regarding an
                // authorizable from the groups config then delete all ACEs from
                // this authorizable from current ACL
                if (authorizablesInConfig.contains(existingAce.getPrincipalName())) {
                    AccessControlUtils.deleteAllEntriesForAuthorizableFromACL(session,
                            existingAce.getJcrPath(), existingAce.getPrincipalName());
                    String message = "deleted all ACEs of authorizable "
                            + existingAce.getPrincipalName()
                            + " from ACL of path: " + existingAce.getJcrPath();
                    LOG.debug(message);
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

        String msg = "*** Starting installation of "+aceMapFromConfig.size()+" ACLs in content nodes...";
        LOG.info(msg);
        history.addMessage(msg);
        aceBeanInstaller.installPathBasedACEs(pathBasedAceMapFromConfig, session, history);
    }

    private void installAuthorizables(
            AcInstallationHistoryPojo history,
            Set<AuthorizableInstallationHistory> authorizableHistorySet,
            Map<String, Set<AuthorizableConfigBean>> authorizablesMapfromConfig)
                    throws RepositoryException, Exception {
        // --- installation of Authorizables from configuration ---

        String msg = "*** Starting installation of "+authorizablesMapfromConfig.size()+" authorizables...";
        LOG.info(msg);
        history.addMessage(msg);

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

        String message = "Finished installation of authorizables without errors";
        history.addMessage(message);
        LOG.info(message);
    }

    /** Executes the installation of the existing configurations - entry point for JMX execute() method. */
    @Override
    public AcInstallationHistoryPojo execute() {

        AcInstallationHistoryPojo history = new AcInstallationHistoryPojo();
        if (isExecuting) {
            history.addError("AC Tool is already executing.");
            return history;
        }

        Session session = null;

        Set<AuthorizableInstallationHistory> authorizableInstallationHistorySet = new LinkedHashSet<AuthorizableInstallationHistory>();

        try {
            session = repository.loginAdministrative(null);
            String rootPath = getConfigurationRootPath();
            Node rootNode = session.getNode(rootPath);
            Map<String, String> newestConfigurations = configFilesRetriever.getConfigFileContentFromNode(rootNode);
            installConfigurationFiles(session, history, newestConfigurations, authorizableInstallationHistorySet);
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
        }
        return history;
    }

    /** Common entry point for JMX and install hook. */
    @Override
    public void installConfigurationFiles(Session session, AcInstallationHistoryPojo history, Map<String, String> configurationFileContentsByFilename,
            Set<AuthorizableInstallationHistory> authorizableInstallationHistorySet)
                    throws Exception {

        String origThreadName = Thread.currentThread().getName();
        try {
            Thread.currentThread().setName(origThreadName + "-ACTool-Config-Worker");
            StopWatch sw = new StopWatch();
            sw.start();
            isExecuting = true;
            String message = "*** Applying AC Tool Configuration...";
            LOG.info(message);
            history.addMessage(message);

            if (configurationFileContentsByFilename != null) {

                AcConfiguration acConfiguration = configurationMerger.getMergedConfigurations(configurationFileContentsByFilename, history,
                        configReader);
                history.setAcConfiguration(acConfiguration);
                history.setConfigFileContentsByName(configurationFileContentsByFilename);

                installMergedConfigurations(history, session, authorizableInstallationHistorySet, acConfiguration);

                // if everything went fine (no exceptions), save the session
                // thus persisting the changed ACLs
                history.addVerboseMessage(
                        "Finished (transient) installation of access control configuration without errors, saving now...");
                session.save();
                history.addMessage("Persisted changes of ACLs");
            }
            sw.stop();
            long executionTime = sw.getTime();
            LOG.info("Successfully applied AC Tool configuration in "+ msHumanReadable(executionTime));
            history.setExecutionTime(executionTime);
        } catch (Exception e) {
            history.addError(e.toString()); // ensure exception is added to history before it's persisted in log in finally clause
            throw e; // handling is different depending on JMX or install hook case
        } finally {
            try {
                acHistoryService.persistHistory(history);
            } catch (Exception e) {
                LOG.warn("Could not persist history, e=" + e, e);
            }
            
            Thread.currentThread().setName(origThreadName);
            isExecuting = false;
        }

    }

    private void installMergedConfigurations(
            AcInstallationHistoryPojo history,
            Session session,
            Set<AuthorizableInstallationHistory> authorizableInstallationHistorySet,
            AcConfiguration acConfiguration) throws ValueFormatException,
            RepositoryException, Exception {

        String message = "Starting installation of merged configurations...";
        LOG.debug(message);
        history.addVerboseMessage(message);

        Map<String, Set<AceBean>> repositoryDumpAceMap = null;
        LOG.debug("Building dump from repository (to compare delta with config to be installed)");
        repositoryDumpAceMap = dumpservice.createAclDumpMap(
                session, AcHelper.PATH_BASED_ORDER,
                AcHelper.ACE_ORDER_NONE,
                dumpservice.getQueryExcludePaths()).getAceDump();

        installAcConfiguration(acConfiguration, history, session, authorizableInstallationHistorySet, repositoryDumpAceMap);

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
        AcConfiguration acConfiguration = configurationMerger.getMergedConfigurations(newestConfigurations, history, configReader);
        Set<String> allAuthorizablesFromConfig = acConfiguration.getAceConfig().keySet();
        return allAuthorizablesFromConfig;
    }

}
