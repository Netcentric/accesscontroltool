/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.impl;

import static biz.netcentric.cq.tools.actool.history.AcInstallationLog.msHumanReadable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Enumeration;
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
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.aceinstaller.AceBeanInstaller;
import biz.netcentric.cq.tools.actool.aceservice.AceService;
import biz.netcentric.cq.tools.actool.api.AcInstallationService;
import biz.netcentric.cq.tools.actool.api.InstallationLog;
import biz.netcentric.cq.tools.actool.authorizableinstaller.AuthorizableCreatorException;
import biz.netcentric.cq.tools.actool.authorizableinstaller.AuthorizableInstallerService;
import biz.netcentric.cq.tools.actool.configmodel.AcConfiguration;
import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.configmodel.AcesConfig;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizablesConfig;
import biz.netcentric.cq.tools.actool.configreader.ConfigFilesRetriever;
import biz.netcentric.cq.tools.actool.configreader.ConfigReader;
import biz.netcentric.cq.tools.actool.configreader.ConfigurationMerger;
import biz.netcentric.cq.tools.actool.dumpservice.ConfigDumpService;
import biz.netcentric.cq.tools.actool.helper.AcHelper;
import biz.netcentric.cq.tools.actool.helper.AccessControlUtils;
import biz.netcentric.cq.tools.actool.helper.AclBean;
import biz.netcentric.cq.tools.actool.helper.Constants;
import biz.netcentric.cq.tools.actool.helper.PurgeHelper;
import biz.netcentric.cq.tools.actool.helper.QueryHelper;
import biz.netcentric.cq.tools.actool.history.AcHistoryService;
import biz.netcentric.cq.tools.actool.history.AcInstallationLog;
import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;

@Service
@Component(metatype = true, label = "AC Installation Service", description = "Service that installs groups & ACEs according to textual configuration files")
public class AcInstallationServiceImpl implements AcInstallationService, AcInstallationServiceInternal, AceService {
    private static final Logger LOG = LoggerFactory.getLogger(AcInstallationServiceImpl.class);

    private static final String LEGACY_CONFIG_PID = "biz.netcentric.cq.tools.actool.aceservice.impl.AceServiceImpl";

    static final String PROPERTY_CONFIGURATION_PATH = "AceService.configurationPath";
    static final String PROPERTY_INTERMEDIATE_SAVES = "intermediateSaves";

    @Reference
    AuthorizableInstallerService authorizableCreatorService;

    @Reference(target = "(component.name=biz.netcentric.cq.tools.actool.aceinstaller.AceBeanInstallerClassic)")
    AceBeanInstaller aceBeanInstallerClassic;

    @Reference(target = "(component.name=biz.netcentric.cq.tools.actool.aceinstaller.AceBeanInstallerIncremental)")
    AceBeanInstaller aceBeanInstallerIncremental;

    @Reference
    private SlingRepository repository;

    @Reference
    AcHistoryService acHistoryService;

    @Reference
    private ConfigDumpService dumpservice;

    @Reference
    private ConfigReader configReader;

    @Reference
    private ConfigurationMerger configurationMerger;

    @Reference
    private ConfigFilesRetriever configFilesRetriever;

    @Reference
    private ConfigurationAdmin configAdmin;

    @Property(label = "Configuration storage path", description = "CRX path where ACE configuration gets stored", name = AcInstallationServiceImpl.PROPERTY_CONFIGURATION_PATH, value = "")
    private String configuredAcConfigurationRootPath;

    @Property(label = "Use intermedate saves", description = "Saves ACLs for each path individually - this can be used to avoid problems with large changesets and MongoDB (OAK-5557), however the rollback is disabled then.", name = AcInstallationServiceImpl.PROPERTY_INTERMEDIATE_SAVES, value = "")
    private boolean intermediateSaves;

    @Activate
    public void activate(Map<String, Object> properties) throws Exception {

        Dictionary<String, Object> legacyProps = configAdmin.getConfiguration(LEGACY_CONFIG_PID).getProperties();
        if (legacyProps != null) {
            properties = new HashMap<String, Object>(properties);
            Enumeration<String> keysEnum = legacyProps.keys();
            while (keysEnum.hasMoreElements()) {
                String key = keysEnum.nextElement();
                properties.put(key, legacyProps.get(key));
            }
        }

        LOG.debug("Activated AceService!");
        configuredAcConfigurationRootPath = PropertiesUtil.toString(properties.get(PROPERTY_CONFIGURATION_PATH), "");
        LOG.info("Conifg " + PROPERTY_CONFIGURATION_PATH + "=" + configuredAcConfigurationRootPath);
        intermediateSaves = PropertiesUtil.toBoolean(properties.get(PROPERTY_INTERMEDIATE_SAVES), false);
        LOG.info("Conifg " + PROPERTY_INTERMEDIATE_SAVES + "=" + intermediateSaves);
    }

    @Override
    public InstallationLog apply() {
        return apply(getConfiguredAcConfigurationRootPath(), null);
    }

    @Override
    public InstallationLog apply(String configurationRootPath) {
        return apply(configurationRootPath, null);
    }

    @Override
    public InstallationLog apply(String[] restrictedToPaths) {
        return apply(getConfiguredAcConfigurationRootPath(), restrictedToPaths);
    }

    @Override
    public InstallationLog apply(String configurationRootPath, String[] restrictedToPaths) {

        AcInstallationLog installLog = new AcInstallationLog();

        Session session = null;
        try {
            session = repository.loginService(Constants.USER_AC_SERVICE, null);
            Map<String, String> newestConfigurations = configFilesRetriever.getConfigFileContentFromNode(configurationRootPath, session);
            installConfigurationFiles(installLog, newestConfigurations, restrictedToPaths, session);
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
        } finally {
            if (session != null) {
                session.logout();
            }
        }
        return installLog;
    }

    /** Common entry point for JMX and install hook. */
    @Override
    public void installConfigurationFiles(AcInstallationLog installLog, Map<String, String> configurationFileContentsByFilename,
            String[] restrictedToPaths, Session session)
            throws Exception {

        String origThreadName = Thread.currentThread().getName();

        try {
            Thread.currentThread().setName(origThreadName + "-ACTool-Config-Worker");
            StopWatch sw = new StopWatch();
            sw.start();

            installLog.addMessage(LOG, "*** Applying AC Tool Configuration using v" + getVersion() + "... ");

            if (configurationFileContentsByFilename != null) {

                installLog.setConfigFileContentsByName(configurationFileContentsByFilename);

                AcConfiguration acConfiguration = configurationMerger.getMergedConfigurations(configurationFileContentsByFilename, installLog,
                        configReader, session);
                installLog.setAcConfiguration(acConfiguration);

                installMergedConfigurations(installLog, acConfiguration, restrictedToPaths, session);

                removeObsoleteAuthorizables(installLog, acConfiguration.getObsoleteAuthorizables(), session);

            }
            sw.stop();
            long executionTime = sw.getTime();
            LOG.info("Successfully applied AC Tool configuration in " + msHumanReadable(executionTime));
            installLog.setExecutionTime(executionTime);
        } catch (Exception e) {
            installLog.addError(e.toString()); // ensure exception is added to installLog before it's persisted in log in finally clause
            throw e; // handling is different depending on JMX or install hook case
        } finally {
            try {
                acHistoryService.persistHistory(installLog);
            } catch (Exception e) {
                LOG.warn("Could not persist history, e=" + e, e);
            }

            Thread.currentThread().setName(origThreadName);
        }

    }


    private void installAcConfiguration(
            AcConfiguration acConfiguration, AcInstallationLog installLog,
            Map<String, Set<AceBean>> repositoryDumpAceMap, String[] restrictedToPaths, Session session) throws Exception {

        if (acConfiguration.getAceConfig() == null) {
            String message = "ACE config not found in YAML file! installation aborted!";
            LOG.error(message);
            throw new IllegalArgumentException(message);
        }

        installAuthorizables(installLog, acConfiguration.getAuthorizablesConfig(), session);

        installAces(installLog, acConfiguration, repositoryDumpAceMap, restrictedToPaths, session);
    }

    private void removeAcesForPathsNotInConfig(AcInstallationLog installLog, Session session, Set<String> principalsInConfig,
            Map<String, Set<AceBean>> repositoryDumpAceMap, AcesConfig aceBeansFromConfig,
            AuthorizablesConfig authorizablesConfig)
            throws UnsupportedRepositoryOperationException, RepositoryException {

        int countAcesCleaned = 0;
        int countPathsCleaned = 0;
        Set<String> relevantPathsForCleanup = getRelevantPathsForAceCleanup(principalsInConfig, repositoryDumpAceMap,
                aceBeansFromConfig);

        for (String relevantPath : relevantPathsForCleanup) {
            // delete ACE if principal *is* in config, but the path *is not* in config
            int countRemoved = AccessControlUtils.deleteAllEntriesForPrincipalsFromACL(session,
                    relevantPath, principalsInConfig.toArray(new String[principalsInConfig.size()]), authorizablesConfig);

            installLog.addMessage(LOG, "Cleaned (deleted) " + countRemoved + " ACEs of path " + relevantPath
                    + " from all ACEs for configured authorizables");
            if (countRemoved > 0) {
                countPathsCleaned++;
            }
            countAcesCleaned += countRemoved;
        }

        if (countAcesCleaned > 0) {
            installLog.addMessage(LOG, "Cleaned " + countAcesCleaned + " ACEs from " + countPathsCleaned
                    + " paths in repository (ACEs that belong to users in the AC Config, "
                    + "but resided at paths that are not contained in AC Config)");
        }

    }

    private Set<String> getRelevantPathsForAceCleanup(Set<String> authorizablesInConfig, Map<String, Set<AceBean>> repositoryDumpAceMap,
            AcesConfig aceBeansFromConfig) {
        // loop through all ACLs found in the repository
        Set<String> relevantPathsForCleanup = new HashSet<String>();
        for (Map.Entry<String, Set<AceBean>> entry : repositoryDumpAceMap.entrySet()) {
            Set<AceBean> existingAcl = entry.getValue();
            for (AceBean existingAceFromDump : existingAcl) {
                String jcrPath = existingAceFromDump.getJcrPath();
                String principalName = existingAceFromDump.getPrincipalName();

                if (aceBeansFromConfig.containsPath(jcrPath)) {
                    LOG.trace("Path {} is explicitly listed in config and hence that ACL is handled later, "
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



    private Set<String> getPrincipalNamesToRemoveAcesFor(AuthorizablesConfig authorizablesBeansfromConfig) {
        Set<String> principalsToRemoveAcesFor = authorizablesBeansfromConfig.getPrincipalNames();
        Set<String> principalsToBeMigrated = collectPrincipalsToBeMigrated(authorizablesBeansfromConfig);

        Collection<?> invalidPrincipalsInConfig = CollectionUtils.intersection(principalsToRemoveAcesFor, principalsToBeMigrated);
        if (!invalidPrincipalsInConfig.isEmpty()) {
            throw new IllegalArgumentException(
                    "If migrateFrom feature is used, groups that shall be migrated from must not be present in regular configuration (offending groups: "
                            + invalidPrincipalsInConfig + ")");
        }

        principalsToRemoveAcesFor.addAll(principalsToBeMigrated);
        return principalsToRemoveAcesFor;
    }

    Set<String> collectPrincipalsToBeMigrated(AuthorizablesConfig authorizablesBeansfromConfig) {
        Set<String> principalsToBeMigrated = new LinkedHashSet<String>();
        for (AuthorizableConfigBean authorizableConfigBean : authorizablesBeansfromConfig) {
            String migrateFrom = authorizableConfigBean.getMigrateFrom();
            if (StringUtils.isNotBlank(migrateFrom)) {

                if (StringUtils.equals(authorizableConfigBean.getPrincipalName(), authorizableConfigBean.getAuthorizableId())) {
                    // standard case principalName=authorizableId, we can use the migrateFrom property directly
                    principalsToBeMigrated.add(migrateFrom);
                } else {
                    // external id case: try to derive the correct principal name
                    String newPrincipalName = authorizableConfigBean.getPrincipalName();
                    String oldPrincipalName = newPrincipalName.replace(authorizableConfigBean.getAuthorizableId(), migrateFrom);
                    if (StringUtils.equals(newPrincipalName, oldPrincipalName)) {
                        throw new IllegalStateException("Could not derive old principal name from newPrincipalName=" + newPrincipalName
                                + " and authorizableId=" + authorizableConfigBean.getAuthorizableId() + " (oldPrincipalName="
                                + oldPrincipalName + " is equal to new principal name)");
                    }
                    principalsToBeMigrated.add(oldPrincipalName);
                }

            }
        }
        return principalsToBeMigrated;
    }

    private void installAces(AcInstallationLog installLog,
            AcConfiguration acConfiguration, Map<String, Set<AceBean>> repositoryDumpAceMap, String[] restrictedToPaths, Session session)
            throws Exception {

        AcesConfig aceBeansFromConfig = acConfiguration.getAceConfig();

        // --- installation of ACEs from configuration ---
        Map<String, Set<AceBean>> pathBasedAceMapFromConfig = AcHelper
                .getPathBasedAceMap(aceBeansFromConfig, AcHelper.ACE_ORDER_ACTOOL_BEST_PRACTICE);

        Set<String> principalsToRemoveAcesFor = getPrincipalNamesToRemoveAcesFor(acConfiguration.getAuthorizablesConfig());
        removeAcesForPathsNotInConfig(installLog, session, principalsToRemoveAcesFor, repositoryDumpAceMap, aceBeansFromConfig, acConfiguration.getAuthorizablesConfig());

        Map<String, Set<AceBean>> filteredPathBasedAceMapFromConfig = filterForRestrictedPaths(pathBasedAceMapFromConfig,
                restrictedToPaths, installLog);

        AceBeanInstaller aceBeanInstaller = acConfiguration.getGlobalConfiguration().getInstallAclsIncrementally()
                ? aceBeanInstallerIncremental : aceBeanInstallerClassic;


        installLog.addMessage(LOG, "*** Starting installation of " + collectAceCount(filteredPathBasedAceMapFromConfig) + " ACE configurations for "
                + filteredPathBasedAceMapFromConfig.size()
                + " paths in content nodes using strategy " + aceBeanInstaller.getClass().getSimpleName() + "...");

        aceBeanInstaller.installPathBasedACEs(filteredPathBasedAceMapFromConfig, session, installLog, principalsToRemoveAcesFor,
                intermediateSaves);

        // if everything went fine (no exceptions), save the session
        // thus persisting the changed ACLs
        if (session.hasPendingChanges()) {
            session.save();
            installLog.addMessage(LOG, "Persisted changes of ACLs");
        } else {
            installLog.addMessage(LOG, "No changes were made to ACLs (session has no pending changes)");
        }

    }

    private Map<String, Set<AceBean>> filterForRestrictedPaths(Map<String, Set<AceBean>> pathBasedAceMapFromConfig,
            String[] restrictedToPaths, AcInstallationLog installLog) {
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

        installLog.addMessage(LOG, "Will install AC Config at " + filteredPathBasedAceMapFromConfig.keySet().size()
                + " paths (skipping " + skipped + " due to paths restriction " + Arrays.toString(restrictedToPaths) + ")");

        return filteredPathBasedAceMapFromConfig;
    }

    private int collectAceCount(Map<String, Set<AceBean>> aceMapFromConfig) {
        int count = 0;
        for (Set<AceBean> acesForGroup : aceMapFromConfig.values()) {
            count += acesForGroup.size();
        }
        return count;
    }

    private void installAuthorizables(AcInstallationLog installLog, AuthorizablesConfig authorizablesMapfromConfig, Session session)
            throws RepositoryException, Exception {
        // --- installation of Authorizables from configuration ---

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();


        installLog.addMessage(LOG, "*** Starting installation of " + authorizablesMapfromConfig.size() + " authorizables...");

        try {
            // only save session if no exceptions occurred
            authorizableCreatorService.createNewAuthorizables(authorizablesMapfromConfig, session, installLog);

            if (intermediateSaves) {
                if (session.hasPendingChanges()) {
                    session.save();
                    installLog.addVerboseMessage(LOG, "Saved session after installing authorizables.");
                } else {
                    installLog.addVerboseMessage(LOG,
                            "After installing authorizables, intermediateSaves is turned on but there are no pending changes.");
                }
            }

        } catch (Exception e) {
            throw new AuthorizableCreatorException(e);
        }

        installLog.addMessage(LOG, "Finished installation of authorizables without errors in "
                + msHumanReadable(stopWatch.getTime()));
    }

    private void removeObsoleteAuthorizables(AcInstallationLog installLog, Set<String> obsoleteAuthorizables, Session session) {

        try {

            if (obsoleteAuthorizables.isEmpty()) {
                installLog.addVerboseMessage(LOG, "No obsolete authorizables configured");
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
                installLog.addMessage(LOG, "All configured " + obsoleteAuthorizablesAlreadyPurged.size()
                                + " obsolete authorizables have already been purged.");
                return;
            }

            installLog.addMessage(LOG, "*** Purging " + obsoleteAuthorizables.size() + " obsolete authorizables...  ");
            if (!obsoleteAuthorizablesAlreadyPurged.isEmpty()) {
                installLog.addMessage(LOG, "(" + obsoleteAuthorizablesAlreadyPurged.size() + " have been purged already)");
            }

            String purgeAuthorizablesResultMsg = purgeAuthorizables(obsoleteAuthorizables, session);
            installLog.addVerboseMessage(LOG, purgeAuthorizablesResultMsg); // this message is too long for regular log
            installLog.addMessage(LOG, "Successfully purged " + obsoleteAuthorizables);
        } catch (Exception e) {
            installLog.addError(LOG, "Could not purge obsolete authorizables " + obsoleteAuthorizables, e);
        }

    }

    private void installMergedConfigurations(
            AcInstallationLog installLog,
            AcConfiguration acConfiguration, String[] restrictedToPaths, Session session) throws ValueFormatException,
            RepositoryException, Exception {


        installLog.addVerboseMessage(LOG, "Starting installation of merged configurations...");

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        Map<String, Set<AceBean>> repositoryDumpAceMap = null;
        LOG.debug("Building dump from repository (to compare delta with config to be installed)");
        repositoryDumpAceMap = dumpservice.createAclDumpMap(AcHelper.PATH_BASED_ORDER,
                AcHelper.ACE_ORDER_NONE,
                new String[0], true, session).getAceDump();

        installLog.addMessage(LOG, "Retrieved existing ACLs from repository in " + msHumanReadable(stopWatch.getTime()));

        installAcConfiguration(acConfiguration, installLog, repositoryDumpAceMap, restrictedToPaths, session);

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
            message = "Deleted AccessControlList of node: " + path;
            AcInstallationLog installLog = new AcInstallationLog();
            installLog.addMessage(LOG, "purge method: purgeACL()");
            installLog.addMessage(LOG, message);
            acHistoryService.persistAcePurgeHistory(installLog);
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
            AcInstallationLog installLog = new AcInstallationLog();
            installLog.addMessage(LOG, "purge method: purgeACLs()");
            installLog.addMessage(LOG, message);
            acHistoryService.persistAcePurgeHistory(installLog);
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
            message = purgeAuthorizables(authorizabesFromConfigurations, session);
            AcInstallationLog installLog = new AcInstallationLog();
            installLog.addMessage(LOG, "purge method: purgAuthorizablesFromConfig()");
            installLog.addMessage(LOG, message);
            acHistoryService.persistAcePurgeHistory(installLog);
        } catch (Exception e) {
            message = "Exception while purging all authorizable from config: " + e;
            LOG.error(message, e);
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
            AcInstallationLog installLog = new AcInstallationLog();
            installLog.addMessage(LOG, "purge method: purgeAuthorizables()");
            installLog.addMessage(LOG, message);
            acHistoryService.persistAcePurgeHistory(installLog);
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
            String executionTime = AcInstallationLog.msHumanReadable(sw.getTime());
            message.append("Purged " + authorizableIds.size() + " authorizables in " + executionTime + "\n");

        } catch (Exception e) {
            message.append("Deletion of ACEs failed! reason: RepositoryException: " + e + "\n");
            LOG.error("Exception while purgin authorizables: " + e, e);
        }

        return message.toString();
    }

    private String deleteAuthorizable(final String authorizableId, final UserManager userManager) {
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
        AcInstallationLog history = new AcInstallationLog();
        Map<String, String> newestConfigurations = configFilesRetriever.getConfigFileContentFromNode(configuredAcConfigurationRootPath,
                session);
        AcConfiguration acConfiguration = configurationMerger.getMergedConfigurations(newestConfigurations, history, configReader, session);
        Set<String> allAuthorizablesFromConfig = acConfiguration.getAuthorizablesConfig().getAuthorizableIds();
        return allAuthorizablesFromConfig;
    }

    public String getVersion() {
        String bundleVersion = FrameworkUtil.getBundle(AcInstallationServiceImpl.class).getVersion().toString();
        return bundleVersion;
    }

    /* --- deprecated methods --- */
    @Override
    public AcInstallationHistoryPojo execute() {
        return apply();
    }

    @Override
    public AcInstallationHistoryPojo execute(String configurationRootPath) {
        return apply(configurationRootPath);
    }

    @Override
    public AcInstallationHistoryPojo execute(String[] restrictedToPaths) {
        return apply(restrictedToPaths);
    }

    @Override
    public AcInstallationHistoryPojo execute(String configurationRootPath, String[] restrictedToPaths) {
        return apply(configurationRootPath, restrictedToPaths);
    }

}
