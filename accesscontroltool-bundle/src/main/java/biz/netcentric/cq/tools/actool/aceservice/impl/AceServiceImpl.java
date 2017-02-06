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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableCreatorException;
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableCreatorService;
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableInstallationHistory;
import biz.netcentric.cq.tools.actool.configmodel.AcConfiguration;
import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
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
    static final String PROPERTY_INTERMEDIATE_SAVES = "intermediateSaves";

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

    @Property(label = "Use intermedate saves", description = "Saves ACLs for each path individually - this can be used to avoid problems with large changesets and MongoDB (OAK-5557), however the rollback is disabled then.", name = AceServiceImpl.PROPERTY_INTERMEDIATE_SAVES, value = "")
    private boolean intermediateSaves;

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
        LOG.info("Conifg " + PROPERTY_CONFIGURATION_PATH + "=" + configurationPath);
        intermediateSaves = PropertiesUtil.toBoolean(properties.get(PROPERTY_INTERMEDIATE_SAVES), false);
        LOG.info("Conifg " + PROPERTY_INTERMEDIATE_SAVES + "=" + intermediateSaves);
    }

    @Override
    public AcInstallationHistoryPojo execute() {
        return execute(null);
    }

    @Override
    public AcInstallationHistoryPojo execute(String[] restrictedToPaths) {

        AcInstallationHistoryPojo history = new AcInstallationHistoryPojo();
        if (isExecuting) {
            history.addError("AC Tool is already executing.");
            return history;
        }

        Set<AuthorizableInstallationHistory> authorizableInstallationHistorySet = new LinkedHashSet<AuthorizableInstallationHistory>();
        try {
            String rootPath = getConfigurationRootPath();
            Map<String, String> newestConfigurations = configFilesRetriever.getConfigFileContentFromNode(rootPath);
            installConfigurationFiles(history, newestConfigurations, authorizableInstallationHistorySet, restrictedToPaths);
        } catch (AuthorizableCreatorException e) {
            history.addError(e.toString());
            // here no rollback of authorizables necessary since session wasn't
            // saved
        } catch (Exception e) {
            // in case an installation of an ACE configuration
            // threw an exception, logout from this session
            // otherwise changes made on the ACLs would get persisted

            LOG.error("Exception in AceServiceImpl: {}", e);
            history.addError(e.toString());

            if (!intermediateSaves) {
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
            } else {
                String message = "Rollback is disabled due to configuration option intermediateSaves=true";
                LOG.info(message);
                history.addMessage(message);
            }

        }
        return history;
    }

    /** Common entry point for JMX and install hook. */
    @Override
    public void installConfigurationFiles(AcInstallationHistoryPojo history, Map<String, String> configurationFileContentsByFilename,
            Set<AuthorizableInstallationHistory> authorizableInstallationHistorySet, String[] restrictedToPaths)
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

                history.setConfigFileContentsByName(configurationFileContentsByFilename);

                AcConfiguration acConfiguration = configurationMerger.getMergedConfigurations(configurationFileContentsByFilename, history,
                        configReader);
                history.setAcConfiguration(acConfiguration);

                installMergedConfigurations(history, authorizableInstallationHistorySet, acConfiguration, restrictedToPaths);

                // this runs as "own transaction" after session.save() of ACLs
                removeObsoleteAuthorizables(history, acConfiguration.getObsoleteAuthorizables());

            }
            sw.stop();
            long executionTime = sw.getTime();
            LOG.info("Successfully applied AC Tool configuration in " + msHumanReadable(executionTime));
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

    private void installAcConfiguration(
            AcConfiguration acConfiguration, AcInstallationHistoryPojo history,
            Set<AuthorizableInstallationHistory> authorizableHistorySet,
            Map<String, Set<AceBean>> repositoryDumpAceMap, String[] restrictedToPaths) throws Exception {

        if (acConfiguration.getAceConfig() == null) {
            String message = "ACE config not found in YAML file! installation aborted!";
            LOG.error(message);
            throw new IllegalArgumentException(message);
        }


        installAuthorizables(history, authorizableHistorySet, acConfiguration.getAuthorizablesConfig());

        installAces(history, acConfiguration, repositoryDumpAceMap, restrictedToPaths);
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

    private void removeAcesForPathsNotInConfig(AcInstallationHistoryPojo history, Session session, Set<String> authorizablesInConfig,
            Map<String, Set<AceBean>> repositoryDumpAceMap, Set<String> acePathsFromConfig)
            throws UnsupportedRepositoryOperationException, RepositoryException {

        int countAcesCleaned = 0;
        int countPathsCleaned = 0;
        Set<String> relevantPathsForCleanup = getRelevantPathsForAceCleanup(authorizablesInConfig, repositoryDumpAceMap,
                acePathsFromConfig);
        
        for (String relevantPath: relevantPathsForCleanup) {
            // delete ACE if authorizable *is* in config, but the path *is not* in config
            int countRemoved = AccessControlUtils.deleteAllEntriesForAuthorizableFromACL(session,
                    relevantPath, authorizablesInConfig.toArray(new String[authorizablesInConfig.size()]));
            String message = "Cleaned (deleted) " + countRemoved + " ACEs of path " + relevantPath
                    + " from all ACEs for configured authorizables";
            LOG.info(message);
            history.addMessage(message);
            if (countRemoved > 0) {
                countPathsCleaned++;
            }
            countAcesCleaned += countRemoved;
        }
        
        if (countAcesCleaned > 0) {
            String message = "Cleaned " + countAcesCleaned + " ACEs from " + countPathsCleaned
                    + " paths in repository (ACEs that belong to users in the AC Config, "
                    + "but resided at paths that are not contained in AC Config)";
            LOG.info(message);
            history.addMessage(message);
        }

    }

    private Set<String> getRelevantPathsForAceCleanup(Set<String> authorizablesInConfig, Map<String, Set<AceBean>> repositoryDumpAceMap,
            Set<String> acePathsFromConfig) {
        // loop through all ACLs found in the repository
        Set<String> relevantPathsForCleanup = new HashSet<String>();
        for (Map.Entry<String, Set<AceBean>> entry : repositoryDumpAceMap.entrySet()) {
            Set<AceBean> existingAcl = entry.getValue();
            for (AceBean existingAceFromDump : existingAcl) {
                String jcrPath = existingAceFromDump.getJcrPath();

                if (acePathsFromConfig.contains(jcrPath)) {
                    LOG.debug("Path {} is explicitly listed in config and hence that ACL is handled later, "
                            + "not preceding cleanup needed here", jcrPath);
                    continue;
                }

                if (!authorizablesInConfig.contains(existingAceFromDump.getPrincipalName())) {
                    LOG.debug("Authorizable {} is not contained in config, hence not cleaning its ACE from non-config-contained "
                            + "path {}", existingAceFromDump.getPrincipalName(), jcrPath);
                    continue;
                }
                relevantPathsForCleanup.add(jcrPath);
            }
        }
        return relevantPathsForCleanup;
    }

    private Set<String> collectJcrPaths(Map<String, Set<AceBean>> aceMapFromConfig) {
        Set<String> jcrPathsInAceConfig = new HashSet<String>();
        for (Set<AceBean> aces : aceMapFromConfig.values()) {
            for (AceBean aceBean : aces) {
                String path = aceBean.getJcrPath();
                jcrPathsInAceConfig.add(path);
            }
        }
        return jcrPathsInAceConfig;
    }

    boolean isRelevantPath(String path, String[] restrictedToPaths) {
        if (restrictedToPaths == null || restrictedToPaths.length == 0) {
            return true;
        }
        boolean isRelevant = false;
        for (String restrictedToPath : restrictedToPaths) {
            if (path.startsWith(restrictedToPath)) {
                isRelevant = true;
            }
        }
        return isRelevant;
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

    private void installAces(AcInstallationHistoryPojo history,
            AcConfiguration acConfiguration, Map<String, Set<AceBean>> repositoryDumpAceMap, String[] restrictedToPaths) throws Exception {


        Map<String, Set<AceBean>> aceMapFromConfig = acConfiguration.getAceConfig();

        // --- installation of ACEs from configuration ---
        Map<String, Set<AceBean>> pathBasedAceMapFromConfig = AcHelper
                .getPathBasedAceMap(aceMapFromConfig, AcHelper.ACE_ORDER_ACTOOL_BEST_PRACTICE);

        Session session = null;
        try {
            session = repository.loginAdministrative(null);

            Set<String> authorizablesToRemoveAcesFor = getAuthorizablesToRemoveAcesFor(acConfiguration.getAuthorizablesConfig());
            removeAcesForPathsNotInConfig(history, session, authorizablesToRemoveAcesFor, repositoryDumpAceMap,
                    collectJcrPaths(aceMapFromConfig));

            Map<String, Set<AceBean>> filteredPathBasedAceMapFromConfig = filterForRestrictedPaths(pathBasedAceMapFromConfig,
                    restrictedToPaths, history);

            String msg = "*** Starting installation of " + collectAceCount(filteredPathBasedAceMapFromConfig) + " ACLs for "
                    + filteredPathBasedAceMapFromConfig.size()
                    + " paths in content nodes...";
            LOG.info(msg);
            history.addMessage(msg);
            aceBeanInstaller.installPathBasedACEs(filteredPathBasedAceMapFromConfig, session, history, authorizablesToRemoveAcesFor,
                    intermediateSaves);

            // if everything went fine (no exceptions), save the session
            // thus persisting the changed ACLs
            history.addVerboseMessage(
                    "Finished (transient) installation of access control configuration without errors, saving now...");
            session.save();
            history.addMessage("Persisted changes of ACLs");
        } finally {
            if (session != null) {
                session.logout();
            }
        }


    }

    private Map<String, Set<AceBean>> filterForRestrictedPaths(Map<String, Set<AceBean>> pathBasedAceMapFromConfig,
            String[] restrictedToPaths, AcInstallationHistoryPojo history) {
        if (restrictedToPaths == null || restrictedToPaths.length == 0) {
            return pathBasedAceMapFromConfig;
        }

        Map<String, Set<AceBean>> filteredPathBasedAceMapFromConfig = new HashMap<String, Set<AceBean>>();
        for (final String path : pathBasedAceMapFromConfig.keySet()) {
            boolean isRelevant = isRelevantPath(path, restrictedToPaths);
            if(isRelevant) {
                filteredPathBasedAceMapFromConfig.put(path, pathBasedAceMapFromConfig.get(path));
            }
        }
        
        int skipped = pathBasedAceMapFromConfig.keySet().size() - filteredPathBasedAceMapFromConfig.keySet().size();
        String message = "Will install AC Config at " + filteredPathBasedAceMapFromConfig.keySet().size() + " paths (skipping " + skipped
                + " due to paths restriction " + Arrays.toString(restrictedToPaths) + ")";
        
        history.addMessage(message);
        LOG.info(message);

        return filteredPathBasedAceMapFromConfig;
    }

    private int collectAceCount(Map<String, Set<AceBean>> aceMapFromConfig) {
        int count = 0;
        for (Set<AceBean> acesForGroup : aceMapFromConfig.values()) {
            count += acesForGroup.size();
        }
        return count;
    }

    private void installAuthorizables(
            AcInstallationHistoryPojo history,
            Set<AuthorizableInstallationHistory> authorizableHistorySet,
            Map<String, Set<AuthorizableConfigBean>> authorizablesMapfromConfig)
                    throws RepositoryException, Exception {
        // --- installation of Authorizables from configuration ---

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

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

        String message = "Finished installation of authorizables without errors in "
                + AcInstallationHistoryPojo.msHumanReadable(stopWatch.getTime());
        history.addMessage(message);
        LOG.info(message);
    }

    private void removeObsoleteAuthorizables(AcInstallationHistoryPojo history, Set<String> obsoleteAuthorizables) {

        Session session = null;
        try {
            session = repository.loginAdministrative(null);

            if (obsoleteAuthorizables.isEmpty()) {
                history.addVerboseMessage("No obsolete authorizables configured");
                return;
            }
            
            UserManager userManager = AccessControlUtils.getUserManagerAutoSaveDisabled(session);

            Set<String> obsoleteAuthorizablesAlreadyPurged = new HashSet<String>();

            Iterator<String> obsoleteAuthorizablesIt = obsoleteAuthorizables.iterator();
            while (obsoleteAuthorizablesIt.hasNext()) {
                String obsoleteAuthorizableId = obsoleteAuthorizablesIt.next();
                Authorizable obsoleteAuthorizable = userManager.getAuthorizable(obsoleteAuthorizableId);
                if (obsoleteAuthorizable == null) {
                    obsoleteAuthorizablesAlreadyPurged.add(obsoleteAuthorizableId);
                    obsoleteAuthorizablesIt.remove();
                }
            }

            if (obsoleteAuthorizables.isEmpty()) {
                history.addMessage(
                        "All configured " + obsoleteAuthorizablesAlreadyPurged.size()
                                + " obsolete authorizables have already been purged.");
                return;
            }

            String msg = "*** Purging " + obsoleteAuthorizables.size() + " obsolete authorizables...  ";
            LOG.info(msg);
            history.addMessage(msg);
            if (!obsoleteAuthorizablesAlreadyPurged.isEmpty()) {
                history.addMessage("(" + obsoleteAuthorizablesAlreadyPurged.size() + " have been purged already)");
            }


            String purgeAuthorizablesResultMsg = purgeAuthorizables(obsoleteAuthorizables, session);
            LOG.info(purgeAuthorizablesResultMsg);
            history.addVerboseMessage(purgeAuthorizablesResultMsg); // this message is too long for regular log
            history.addMessage("Successfully purged " + obsoleteAuthorizables);
        } catch (Exception e) {
            String excMsg = "Could not purge obsolete authorizables " + obsoleteAuthorizables + ": " + e;
            history.addError(excMsg);
            LOG.error(excMsg, e);
        } finally {
            if (session != null) {
                session.logout();
            }
        }

    }

    private void installMergedConfigurations(
            AcInstallationHistoryPojo history,
            Set<AuthorizableInstallationHistory> authorizableInstallationHistorySet,
            AcConfiguration acConfiguration, String[] restrictedToPaths) throws ValueFormatException,
            RepositoryException, Exception {

        String message = "Starting installation of merged configurations...";
        LOG.debug(message);
        history.addVerboseMessage(message);

        Map<String, Set<AceBean>> repositoryDumpAceMap = null;
        LOG.debug("Building dump from repository (to compare delta with config to be installed)");
        repositoryDumpAceMap = dumpservice.createAclDumpMap(AcHelper.PATH_BASED_ORDER,
                AcHelper.ACE_ORDER_NONE,
                new String[0], true).getAceDump();

        installAcConfiguration(acConfiguration, history, authorizableInstallationHistorySet, repositoryDumpAceMap, restrictedToPaths);

    }

    @Override
    public boolean isReadyToStart() {
        String rootPath = getConfigurationRootPath();
        try {
            return !configFilesRetriever.getConfigFileContentFromNode(rootPath).isEmpty();

        } catch (Exception e) {
            LOG.warn("Could not retrieve config file content for root path " + configurationPath);
            return false;
        }

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
            UserManager userManager = AccessControlUtils.getUserManagerAutoSaveDisabled(session);
            PrincipalManager principalManager = ((JackrabbitSession) session).getPrincipalManager();

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

        Set<String> paths = new LinkedHashSet<String>();
        try {
            paths = configFilesRetriever.getConfigFileContentFromNode(configurationPath).keySet();
        } catch (Exception e) {
            LOG.warn("Could not retrieve config file content for root path " + configurationPath);
        }
        return paths;
    }

    public Set<String> getAllAuthorizablesFromConfig(Session session)
            throws Exception {
        AcInstallationHistoryPojo history = new AcInstallationHistoryPojo();
        Map<String, String> newestConfigurations = configFilesRetriever.getConfigFileContentFromNode(configurationPath);
        AcConfiguration acConfiguration = configurationMerger.getMergedConfigurations(newestConfigurations, history, configReader);
        Set<String> allAuthorizablesFromConfig = acConfiguration.getAceConfig().keySet();
        return allAuthorizablesFromConfig;
    }

}
