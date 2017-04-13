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
            history.addError(LOG, "AC Tool is already executing.");
            return history;
        }

        Session session = null;
        try {
            session = repository.loginService(Constants.USER_AC_SERVICE, null);
            Map<String, String> newestConfigurations = configFilesRetriever.getConfigFileContentFromNode(configurationRootPath, session);
            installConfigurationFiles(history, newestConfigurations, restrictedToPaths, session);
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
        return history;
    }

    /** Common entry point for JMX and install hook. */
    @Override
    public void installConfigurationFiles(AcInstallationHistoryPojo history, Map<String, String> configurationFileContentsByFilename,
            String[] restrictedToPaths, Session session)
            throws Exception {

        String origThreadName = Thread.currentThread().getName();

        try {
            Thread.currentThread().setName(origThreadName + "-ACTool-Config-Worker");
            StopWatch sw = new StopWatch();
            sw.start();
            isExecuting = true;
            String bundleVersion = FrameworkUtil.getBundle(AceServiceImpl.class).getVersion().toString();

            history.addMessage(LOG, "*** Applying AC Tool Configuration using v" + bundleVersion + "... ");

            if (configurationFileContentsByFilename != null) {

                history.setConfigFileContentsByName(configurationFileContentsByFilename);

                AcConfiguration acConfiguration = configurationMerger.getMergedConfigurations(configurationFileContentsByFilename, history,
                        configReader, session);
                history.setAcConfiguration(acConfiguration);

                installMergedConfigurations(history, acConfiguration, restrictedToPaths, session);

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
            Map<String, Set<AceBean>> repositoryDumpAceMap, String[] restrictedToPaths, Session session) throws Exception {

        if (acConfiguration.getAceConfig() == null) {
            String message = "ACE config not found in YAML file! installation aborted!";
            LOG.error(message);
            throw new IllegalArgumentException(message);
        }

        installAuthorizables(history, acConfiguration.getAuthorizablesConfig(), session);

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

            history.addMessage(LOG, "Cleaned (deleted) " + countRemoved + " ACEs of path " + relevantPath
                    + " from all ACEs for configured authorizables");
            if (countRemoved > 0) {
                countPathsCleaned++;
            }
            countAcesCleaned += countRemoved;
        }

        if (countAcesCleaned > 0) {
            history.addMessage(LOG, "Cleaned " + countAcesCleaned + " ACEs from " + countPathsCleaned
                    + " paths in repository (ACEs that belong to users in the AC Config, "
                    + "but resided at paths that are not contained in AC Config)");
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

    private Set<String> collectJcrPaths(Set<AceBean> aceBeansFromConfig) {
        Set<String> jcrPathsInAceConfig = new HashSet<String>();
        for (AceBean aceBean : aceBeansFromConfig) {
            String path = aceBean.getJcrPath();
            jcrPathsInAceConfig.add(path);
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

        Set<AceBean> aceBeansFromConfig = acConfiguration.getAceConfig();

        // --- installation of ACEs from configuration ---
        Map<String, Set<AceBean>> pathBasedAceMapFromConfig = AcHelper
                .getPathBasedAceMap(aceBeansFromConfig, AcHelper.ACE_ORDER_ACTOOL_BEST_PRACTICE);

        Set<String> principalsToRemoveAcesFor = getPrincipalNamesToRemoveAcesFor(acConfiguration.getAuthorizablesConfig());
        removeAcesForPathsNotInConfig(history, session, principalsToRemoveAcesFor, repositoryDumpAceMap,
                collectJcrPaths(aceBeansFromConfig));

        Map<String, Set<AceBean>> filteredPathBasedAceMapFromConfig = filterForRestrictedPaths(pathBasedAceMapFromConfig,
                restrictedToPaths, history);

        AceBeanInstaller aceBeanInstaller = acConfiguration.getGlobalConfiguration().getInstallAclsIncrementally()
                ? aceBeanInstallerIncremental : aceBeanInstallerClassic;


        history.addMessage(LOG, "*** Starting installation of " + collectAceCount(filteredPathBasedAceMapFromConfig) + " ACE configurations for "
                + filteredPathBasedAceMapFromConfig.size()
                + " paths in content nodes using strategy " + aceBeanInstaller.getClass().getSimpleName() + "...");

        aceBeanInstaller.installPathBasedACEs(filteredPathBasedAceMapFromConfig, session, history, principalsToRemoveAcesFor,
                intermediateSaves);

        // if everything went fine (no exceptions), save the session
        // thus persisting the changed ACLs
        if (session.hasPendingChanges()) {
            session.save();
            history.addMessage(LOG, "Persisted changes of ACLs");
        } else {
            history.addMessage(LOG, "No changes were made to ACLs (session has no pending changes)");
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

        history.addMessage(LOG, "Will install AC Config at " + filteredPathBasedAceMapFromConfig.keySet().size()
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

    private void installAuthorizables(
            AcInstallationHistoryPojo history,
            Map<String, Set<AuthorizableConfigBean>> authorizablesMapfromConfig, Session session)
            throws RepositoryException, Exception {
        // --- installation of Authorizables from configuration ---

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();


        history.addMessage(LOG, "*** Starting installation of " + authorizablesMapfromConfig.size() + " authorizables...");

        try {
            // only save session if no exceptions occurred
            authorizableCreatorService.createNewAuthorizables(authorizablesMapfromConfig, session, history);

            if (intermediateSaves) {
                if (session.hasPendingChanges()) {
                    session.save();
                    history.addVerboseMessage(LOG, "Saved session after installing authorizables.");
                } else {
                    history.addVerboseMessage(LOG,
                            "After installing authorizables, intermediateSaves is turned on but there are no pending changes.");
                }
            }

        } catch (Exception e) {
            throw new AuthorizableCreatorException(e);
        }

        history.addMessage(LOG, "Finished installation of authorizables without errors in "
                + AcInstallationHistoryPojo.msHumanReadable(stopWatch.getTime()));
    }

    private void removeObsoleteAuthorizables(AcInstallationHistoryPojo history, Set<String> obsoleteAuthorizables, Session session) {

        try {

            if (obsoleteAuthorizables.isEmpty()) {
                history.addVerboseMessage(LOG, "No obsolete authorizables configured");
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
                history.addMessage(LOG, "All configured " + obsoleteAuthorizablesAlreadyPurged.size()
                                + " obsolete authorizables have already been purged.");
                return;
            }

            history.addMessage(LOG, "*** Purging " + obsoleteAuthorizables.size() + " obsolete authorizables...  ");
            if (!obsoleteAuthorizablesAlreadyPurged.isEmpty()) {
                history.addMessage(LOG, "(" + obsoleteAuthorizablesAlreadyPurged.size() + " have been purged already)");
            }

            String purgeAuthorizablesResultMsg = purgeAuthorizables(obsoleteAuthorizables, session);
            history.addVerboseMessage(LOG, purgeAuthorizablesResultMsg); // this message is too long for regular log
            history.addMessage(LOG, "Successfully purged " + obsoleteAuthorizables);
        } catch (Exception e) {
            history.addError(LOG, "Could not purge obsolete authorizables " + obsoleteAuthorizables, e);
        }

    }

    private void installMergedConfigurations(
            AcInstallationHistoryPojo history,
            AcConfiguration acConfiguration, String[] restrictedToPaths, Session session) throws ValueFormatException,
            RepositoryException, Exception {


        history.addVerboseMessage(LOG, "Starting installation of merged configurations...");

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        Map<String, Set<AceBean>> repositoryDumpAceMap = null;
        LOG.debug("Building dump from repository (to compare delta with config to be installed)");
        repositoryDumpAceMap = dumpservice.createAclDumpMap(AcHelper.PATH_BASED_ORDER,
                AcHelper.ACE_ORDER_NONE,
                new String[0], true, session).getAceDump();

        history.addMessage(LOG, "Retrieved existing ACLs from repository in " + msHumanReadable(stopWatch.getTime()));

        installAcConfiguration(acConfiguration, history, repositoryDumpAceMap, restrictedToPaths, session);

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
            history.addMessage(LOG, "purge method: purgeACL()");
            history.addMessage(LOG, message);
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
            history.addMessage(LOG, "purge method: purgeACLs()");
            history.addMessage(LOG, message);
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
            history.addMessage(LOG, "purge method: purgAuthorizablesFromConfig()");
            history.addMessage(LOG, message);
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
            history.addMessage(LOG, "purge method: purgeAuthorizables()");
            history.addMessage(LOG, message);
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
        Set<String> allAuthorizablesFromConfig = acConfiguration.getAuthorizablesConfig().keySet();
        return allAuthorizablesFromConfig;
    }

}
