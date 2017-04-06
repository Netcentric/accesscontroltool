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
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.framework.FrameworkUtil;
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
import biz.netcentric.cq.tools.actool.helper.Constants;
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

    @Reference(target = "(component.name=biz.netcentric.cq.tools.actool.acls.AceBeanInstallerClassic)")
    AceBeanInstaller aceBeanInstallerClassic;

    @Reference(target = "(component.name=biz.netcentric.cq.tools.actool.acls.AceBeanInstallerIncremental)")
    AceBeanInstaller aceBeanInstallerIncremental;

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

    @Property(label = "Configuration storage path", description = "CRX path where ACE configuration gets stored", name = AceServiceImpl.PROPERTY_CONFIGURATION_PATH, value = "")
    private String configuredAcConfigurationRootPath;

    @Property(label = "Use intermedate saves", description = "Saves ACLs for each path individually - this can be used to avoid problems with large changesets and MongoDB (OAK-5557), however the rollback is disabled then.", name = AceServiceImpl.PROPERTY_INTERMEDIATE_SAVES, value = "")
    private boolean intermediateSaves;

    @Activate
    public void activate(final Map<?, ?> properties)
            throws Exception {
        LOG.debug("Activated AceService!");
        configuredAcConfigurationRootPath = PropertiesUtil.toString(properties.get(PROPERTY_CONFIGURATION_PATH), "");
        LOG.info("Conifg " + PROPERTY_CONFIGURATION_PATH + "=" + configuredAcConfigurationRootPath);
        intermediateSaves = PropertiesUtil.toBoolean(properties.get(PROPERTY_INTERMEDIATE_SAVES), false);
        LOG.info("Conifg " + PROPERTY_INTERMEDIATE_SAVES + "=" + intermediateSaves);
    }

    @Override
    public AcInstallationHistoryPojo execute() {
        return execute(getConfiguredAcConfigurationRootPath(), null);
    }

    @Override
    public AcInstallationHistoryPojo execute(String configurationRootPath) {
        return execute(configurationRootPath, null);
    }

    @Override
    public AcInstallationHistoryPojo execute(String[] restrictedToPaths) {
        return execute(getConfiguredAcConfigurationRootPath(), restrictedToPaths);
    }

    @Override
    public AcInstallationHistoryPojo execute(String configurationRootPath, String[] restrictedToPaths) {

        AcInstallationHistoryPojo history = new AcInstallationHistoryPojo();
        if (isExecuting) {
            history.addError("AC Tool is already executing.");
            return history;
        }

        Set<AuthorizableInstallationHistory> authorizableInstallationHistorySet = new LinkedHashSet<AuthorizableInstallationHistory>();
        Session session = null;
        try {
            session = repository.loginService(Constants.USER_AC_SERVICE, null);
            Map<String, String> newestConfigurations = configFilesRetriever.getConfigFileContentFromNode(configurationRootPath, session);
            installConfigurationFiles(history, newestConfigurations, authorizableInstallationHistorySet, restrictedToPaths, session);
        } catch (AuthorizableCreatorException e) {
            // exception was added to history in installConfigurationFiles() before it was saved
            LOG.warn("Exception during installation of authorizables (no rollback), e=" + e, e);
            // here no rollback of authorizables necessary since session wasn't
            // saved
        } catch (Exception e) {
            // in case an installation of an ACE configuration
            // threw an exception, logout from this session
            // otherwise changes made on the ACLs would get persisted

            LOG.error("Exception in AceServiceImpl: {}", e);
            // exception was added to history in installConfigurationFiles() before it was saved

            if (!intermediateSaves) {
                for (AuthorizableInstallationHistory authorizableInstallationHistory : authorizableInstallationHistorySet) {
                    try {
                        session.refresh(true); // reset the session that might have an inconsistent state
                        String message = "performing authorizable installation rollback(s)";
                        LOG.info(message);
                        history.addMessage(message);
                        authorizableCreatorService.performRollback(repository,
                                authorizableInstallationHistory, history, session);
                    } catch (RepositoryException e1) {
                        String rollbackErr = "Could not perform rollback: " + e1;
                        LOG.error(rollbackErr, e1);
                        history.addError(rollbackErr);
                    }
                }
            } else {
                String message = "Rollback is disabled due to configuration option intermediateSaves=true";
                LOG.info(message);
                history.addMessage(message);
            }

        } finally {
            if (session != null) {
                session.logout();
            }
        }
        return history;
    }

    /** Common entry point for JMX and install hook. */
    @Override
    public void installConfigurationFiles(AcInstallationHistoryPojo history, Map<String, String> configurationFileContentsByFilename,
            Set<AuthorizableInstallationHistory> authorizableInstallationHistorySet, String[] restrictedToPaths, Session session)
            throws Exception {

        String origThreadName = Thread.currentThread().getName();

        try {
            Thread.currentThread().setName(origThreadName + "-ACTool-Config-Worker");
            StopWatch sw = new StopWatch();
            sw.start();
            isExecuting = true;
            String bundleVersion = FrameworkUtil.getBundle(AceServiceImpl.class).getVersion().toString();
            String message = "*** Applying AC Tool Configuration using v" + bundleVersion + "... ";
            LOG.info(message);
            history.addMessage(message);

            if (configurationFileContentsByFilename != null) {

                history.setConfigFileContentsByName(configurationFileContentsByFilename);

                AcConfiguration acConfiguration = configurationMerger.getMergedConfigurations(configurationFileContentsByFilename, history,
                        configReader, session);
                history.setAcConfiguration(acConfiguration);

                installMergedConfigurations(history, authorizableInstallationHistorySet, acConfiguration, restrictedToPaths, session);

                removeObsoleteAuthorizables(history, acConfiguration.getObsoleteAuthorizables(), session);

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
            Map<String, Set<AceBean>> repositoryDumpAceMap, String[] restrictedToPaths, Session session) throws Exception {

        if (acConfiguration.getAceConfig() == null) {
            String message = "ACE config not found in YAML file! installation aborted!";
            LOG.error(message);
            throw new IllegalArgumentException(message);
        }

        installAuthorizables(history, authorizableHistorySet, acConfiguration.getAuthorizablesConfig(), session);

        installAces(history, acConfiguration, repositoryDumpAceMap, restrictedToPaths, session);
    }

    private void removeAcesForPathsNotInConfig(AcInstallationHistoryPojo history, Session session, Set<String> principalsInConfig,
            Map<String, Set<AceBean>> repositoryDumpAceMap, Set<String> acePathsFromConfig)
            throws UnsupportedRepositoryOperationException, RepositoryException {

        int countAcesCleaned = 0;
        int countPathsCleaned = 0;
        Set<String> relevantPathsForCleanup = getRelevantPathsForAceCleanup(principalsInConfig, repositoryDumpAceMap,
                acePathsFromConfig);

        for (String relevantPath : relevantPathsForCleanup) {
            // delete ACE if principal *is* in config, but the path *is not* in config
            int countRemoved = AccessControlUtils.deleteAllEntriesForPrincipalsFromACL(session,
                    relevantPath, principalsInConfig.toArray(new String[principalsInConfig.size()]));
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
                String principalName = existingAceFromDump.getPrincipalName();

                if (acePathsFromConfig.contains(jcrPath)) {
                    LOG.debug("Path {} is explicitly listed in config and hence that ACL is handled later, "
                            + "not preceding cleanup needed here", jcrPath);
                    continue;
                }

                if (!authorizablesInConfig.contains(principalName)) {
                    LOG.debug("Principal {} is not contained in config, hence not cleaning its ACE from non-config-contained "
                            + "path {}", principalName, jcrPath);
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
            if (path.matches("^" + restrictedToPath + "(/.*|$)")) {
                isRelevant = true;
            }
        }
        return isRelevant;
    }

    private Set<String> getPrincipalNamesToRemoveAcesFor(Map<String, Set<AuthorizableConfigBean>> authorizablesMapfromConfig) {
        Set<String> principalsToRemoveAcesFor = collectPrincipals(authorizablesMapfromConfig);
        Set<String> principalsToBeMigrated = collectPrincipalsToBeMigrated(authorizablesMapfromConfig);
        Collection<?> invalidPrincipalsInConfig = CollectionUtils.intersection(principalsToRemoveAcesFor, principalsToBeMigrated);
        if (!invalidPrincipalsInConfig.isEmpty()) {
            String message = "If migrateFrom feature is used, groups that shall be migrated from must not be present in regular configuration (offending groups: "
                    + invalidPrincipalsInConfig + ")";
            LOG.error(message);
            throw new IllegalArgumentException(message);
        }
        principalsToRemoveAcesFor.addAll(principalsToBeMigrated);
        return principalsToRemoveAcesFor;
    }

    private Set<String> collectPrincipalsToBeMigrated(Map<String, Set<AuthorizableConfigBean>> authorizablesMapfromConfig) {
        Set<String> principalsToBeMigrated = new HashSet<String>();
        for (String principalStr : authorizablesMapfromConfig.keySet()) {
            Set<AuthorizableConfigBean> authorizableConfigBeans = authorizablesMapfromConfig.get(principalStr);
            for (AuthorizableConfigBean authorizableConfigBean : authorizableConfigBeans) {
                String migrateFrom = authorizableConfigBean.getMigrateFrom();
                if (StringUtils.isNotBlank(migrateFrom)) {
                    principalsToBeMigrated.add(migrateFrom);
                }
            }
        }
        return principalsToBeMigrated;
    }

    private Set<String> collectPrincipals(Map<String, Set<AuthorizableConfigBean>> authorizablesMapfromConfig) {
        Set<String> principals = new HashSet<String>();
        for (String authorizableId : authorizablesMapfromConfig.keySet()) {
            Set<AuthorizableConfigBean> authorizableConfigBeans = authorizablesMapfromConfig.get(authorizableId);
            for (AuthorizableConfigBean authorizableConfigBean : authorizableConfigBeans) {
                principals.add(authorizableConfigBean.getPrincipalName());
            }
        }
        return principals;
    }

    private void installAces(AcInstallationHistoryPojo history,
            AcConfiguration acConfiguration, Map<String, Set<AceBean>> repositoryDumpAceMap, String[] restrictedToPaths, Session session)
            throws Exception {

        Map<String, Set<AceBean>> aceMapFromConfig = acConfiguration.getAceConfig();

        // --- installation of ACEs from configuration ---
        Map<String, Set<AceBean>> pathBasedAceMapFromConfig = AcHelper
                .getPathBasedAceMap(aceMapFromConfig, AcHelper.ACE_ORDER_ACTOOL_BEST_PRACTICE);

        Set<String> principalsToRemoveAcesFor = getPrincipalNamesToRemoveAcesFor(acConfiguration.getAuthorizablesConfig());
        removeAcesForPathsNotInConfig(history, session, principalsToRemoveAcesFor, repositoryDumpAceMap,
                collectJcrPaths(aceMapFromConfig));

        Map<String, Set<AceBean>> filteredPathBasedAceMapFromConfig = filterForRestrictedPaths(pathBasedAceMapFromConfig,
                restrictedToPaths, history);

        AceBeanInstaller aceBeanInstaller = acConfiguration.getGlobalConfiguration().getInstallAclsIncrementally()
                ? aceBeanInstallerIncremental : aceBeanInstallerClassic;

        String msg = "*** Starting installation of " + collectAceCount(filteredPathBasedAceMapFromConfig) + " ACE configurations for "
                + filteredPathBasedAceMapFromConfig.size()
                + " paths in content nodes using strategy " + aceBeanInstaller.getClass().getSimpleName() + "...";
        LOG.info(msg);
        history.addMessage(msg);

        aceBeanInstaller.installPathBasedACEs(filteredPathBasedAceMapFromConfig, session, history, principalsToRemoveAcesFor,
                intermediateSaves);

        // if everything went fine (no exceptions), save the session
        // thus persisting the changed ACLs
        if (session.hasPendingChanges()) {
            session.save();
            String msgSave = "Persisted changes of ACLs";
            history.addMessage(msgSave);
            LOG.info(msgSave);
        } else {
            String msgSave = "No changes were made to ACLs (session has no pending changes)";
            history.addMessage(msgSave);
            LOG.info(msgSave);
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
            if (isRelevant) {
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
            Map<String, Set<AuthorizableConfigBean>> authorizablesMapfromConfig, Session session)
            throws RepositoryException, Exception {
        // --- installation of Authorizables from configuration ---

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        String msg = "*** Starting installation of " + authorizablesMapfromConfig.size() + " authorizables...";
        LOG.info(msg);
        history.addMessage(msg);

        try {
            // only save session if no exceptions occurred
            AuthorizableInstallationHistory authorizableInstallationHistory = new AuthorizableInstallationHistory();
            authorizableHistorySet.add(authorizableInstallationHistory);
            authorizableCreatorService.createNewAuthorizables(
                    authorizablesMapfromConfig,
                    session, history,
                    authorizableInstallationHistory);
            session.save();
        } catch (Exception e) {
            throw new AuthorizableCreatorException(e);
        }

        String message = "Finished installation of authorizables without errors in "
                + AcInstallationHistoryPojo.msHumanReadable(stopWatch.getTime());
        history.addMessage(message);
        LOG.info(message);
    }

    private void removeObsoleteAuthorizables(AcInstallationHistoryPojo history, Set<String> obsoleteAuthorizables, Session session) {

        try {

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

        }

    }

    private void installMergedConfigurations(
            AcInstallationHistoryPojo history,
            Set<AuthorizableInstallationHistory> authorizableInstallationHistorySet,
            AcConfiguration acConfiguration, String[] restrictedToPaths, Session session) throws ValueFormatException,
            RepositoryException, Exception {

        String message = "Starting installation of merged configurations...";
        LOG.debug(message);
        history.addVerboseMessage(message);

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        Map<String, Set<AceBean>> repositoryDumpAceMap = null;
        LOG.debug("Building dump from repository (to compare delta with config to be installed)");
        repositoryDumpAceMap = dumpservice.createAclDumpMap(AcHelper.PATH_BASED_ORDER,
                AcHelper.ACE_ORDER_NONE,
                new String[0], true, session).getAceDump();

        String msg = "Retrieved existing ACLs from repository in " + msHumanReadable(stopWatch.getTime());
        LOG.info(msg);
        history.addMessage(msg);

        installAcConfiguration(acConfiguration, history, authorizableInstallationHistorySet, repositoryDumpAceMap, restrictedToPaths,
                session);

    }

    @Override
    public boolean isReadyToStart() {
        Session session = null;
        String rootPath = getConfiguredAcConfigurationRootPath();
        try {
            session = repository.loginService(Constants.USER_AC_SERVICE, null);
            boolean isReadyToStart = !configFilesRetriever.getConfigFileContentFromNode(rootPath, session).isEmpty();
            return isReadyToStart;
        } catch (Exception e) {
            LOG.warn("Could not retrieve config file content for root path " + configuredAcConfigurationRootPath);
            return false;
        } finally {
            if (session != null) {
                session.logout();
            }
        }
    }

    @Override
    public String purgeACL(String path) {
        Session session = null;
        String message = "";
        boolean flag = true;
        try {
            session = repository.loginService(Constants.USER_AC_SERVICE, null);
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
            session = repository.loginService(Constants.USER_AC_SERVICE, null);
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
            session = repository.loginService(Constants.USER_AC_SERVICE, null);

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
    public String purgeAuthorizables(String[] authorizableIds) {
        Session session = null;
        String message = "";
        try {
            session = repository.loginService(Constants.USER_AC_SERVICE, null);
            Set<String> authorizablesSet = new HashSet<String>(Arrays.asList(authorizableIds));
            message = purgeAuthorizables(authorizablesSet, session);
            AcInstallationHistoryPojo history = new AcInstallationHistoryPojo();
            history.addMessage("purge method: purgeAuthorizables()");
            history.addMessage(message);
            acHistoryService.persistAcePurgeHistory(history);
        } catch (RepositoryException e) {
            LOG.error("Exception: ", e);
            message = e.toString();
        } finally {
            if (session != null) {
                session.logout();
            }
        }
        return message;
    }

    private String purgeAuthorizables(Set<String> authorizableIds, final Session session) {

        StopWatch sw = new StopWatch();
        sw.start();

        StringBuilder message = new StringBuilder();

        try {
            // first the ACE entries have to be deleted

            Set<String> principalIds = new HashSet<String>();
            Set<AclBean> aclBeans = QueryHelper.getAuthorizablesAcls(session, authorizableIds, principalIds);
            String deleteAcesResultMsg = PurgeHelper.deleteAcesForPrincipalIds(session, principalIds, aclBeans);
            message.append(deleteAcesResultMsg);

            // then the authorizables can be deleted
            UserManager userManager = AccessControlUtils.getUserManagerAutoSaveDisabled(session);
            for (String authorizableId : authorizableIds) {
                String deleteResultMsg = deleteAuthorizable(authorizableId, userManager);
                message.append(deleteResultMsg);
            }

            session.save();

            sw.stop();
            String executionTime = msHumanReadable(sw.getTime());
            message.append("Purged " + authorizableIds.size() + " authorizables in " + executionTime + "\n");

        } catch (Exception e) {
            message.append("Deletion of ACEs failed! reason: RepositoryException: " + e + "\n");
            LOG.error("Exception while purgin authorizables: " + e, e);
        }

        return message.toString();
    }

    private String deleteAuthorizable(final String authorizableId,
            final UserManager userManager) {
        String message;
        try {
            Authorizable authorizable = userManager.getAuthorizable(authorizableId);
            if (authorizable != null) {
                authorizable.remove();
                message = "Deleted authorizable " + authorizableId + "\n";
            } else {
                message = "Could not delete authorizable '" + authorizableId + "' because it does not exist\n";
            }
        } catch (RepositoryException e) {
            message = "Error while deleting authorizable '" + authorizableId + "': e=" + e;
            LOG.warn("Error while deleting authorizable '" + authorizableId + "': e=" + e, e);
        }

        return message;
    }

    @Override
    public boolean isExecuting() {
        return isExecuting;
    }

    @Override
    public String getConfiguredAcConfigurationRootPath() {
        return configuredAcConfigurationRootPath;
    }

    @Override
    public Set<String> getCurrentConfigurationPaths() {

        Session session = null;
        Set<String> paths = new LinkedHashSet<String>();
        try {
            session = repository.loginService(Constants.USER_AC_SERVICE, null);
            paths = configFilesRetriever.getConfigFileContentFromNode(configuredAcConfigurationRootPath, session).keySet();
        } catch (Exception e) {
            LOG.warn("Could not retrieve config file content for root path " + configuredAcConfigurationRootPath + ": e=" + e, e);
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
        Map<String, String> newestConfigurations = configFilesRetriever.getConfigFileContentFromNode(configuredAcConfigurationRootPath,
                session);
        AcConfiguration acConfiguration = configurationMerger.getMergedConfigurations(newestConfigurations, history, configReader, session);
        Set<String> allAuthorizablesFromConfig = acConfiguration.getAceConfig().keySet();
        return allAuthorizablesFromConfig;
    }

}
