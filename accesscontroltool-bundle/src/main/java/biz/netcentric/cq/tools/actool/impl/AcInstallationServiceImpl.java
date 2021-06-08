/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.impl;

import static biz.netcentric.cq.tools.actool.helper.Constants.PRINCIPAL_EVERYONE;
import static biz.netcentric.cq.tools.actool.history.impl.PersistableInstallationLogger.msHumanReadable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.AccessDeniedException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.ValueFormatException;
import javax.jcr.security.AccessControlEntry;
import javax.jcr.security.AccessControlManager;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.aceinstaller.AceBeanInstaller;
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
import biz.netcentric.cq.tools.actool.helper.PurgeHelper;
import biz.netcentric.cq.tools.actool.helper.QueryHelper;
import biz.netcentric.cq.tools.actool.helper.runtime.RuntimeHelper;
import biz.netcentric.cq.tools.actool.history.AcHistoryService;
import biz.netcentric.cq.tools.actool.history.InstallationLogger;
import biz.netcentric.cq.tools.actool.history.impl.PersistableInstallationLogger;
import biz.netcentric.cq.tools.actool.impl.AcInstallationServiceImpl.Configuration;
import biz.netcentric.cq.tools.actool.slingsettings.ExtendedSlingSettingsService;

@Component
@Designate(ocd=Configuration.class)
public class AcInstallationServiceImpl implements AcInstallationService, AcInstallationServiceInternal {
    private static final Logger LOG = LoggerFactory.getLogger(AcInstallationServiceImpl.class);

    private static final String CONFIG_PID = "biz.netcentric.cq.tools.actool.impl.AcInstallationServiceImpl";
    private static final String LEGACY_CONFIG_PID = "biz.netcentric.cq.tools.actool.aceservice.impl.AceServiceImpl";
    private static final String LEGACY_PROPERTY_CONFIGURATION_PATH = "AceService.configurationPath";

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    AuthorizableInstallerService authorizableCreatorService;

    @Reference(target = "(component.name=biz.netcentric.cq.tools.actool.aceinstaller.AceBeanInstallerClassic)", policyOption = ReferencePolicyOption.GREEDY)
    AceBeanInstaller aceBeanInstallerClassic;

    @Reference(target = "(component.name=biz.netcentric.cq.tools.actool.aceinstaller.AceBeanInstallerIncremental)", policyOption = ReferencePolicyOption.GREEDY)
    AceBeanInstaller aceBeanInstallerIncremental;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private SlingRepository repository;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    AcHistoryService acHistoryService;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private ConfigDumpService dumpservice;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private ConfigReader configReader;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private ConfigurationMerger configurationMerger;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private ConfigFilesRetriever configFilesRetriever;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private ConfigurationAdmin configAdmin;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private AcConfigChangeTracker acConfigChangeTracker;
    
    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private ExtendedSlingSettingsService slingSettingsService;
    
    private List<String> configurationRootPaths;

    @ObjectClassDefinition(name = "AC Tool Installation Service", 
            description="Service that installs groups & ACEs according to textual configuration files",
            id = CONFIG_PID)
    protected static @interface Configuration {
        
        @AttributeDefinition(name="Configuration path(s)", description="JCR path(s) where the config files reside (usually it's just one, can be multiple for multitenant setups)")
        String[] configurationRootPaths() default {};
    }

    @Activate
    public void activate(Configuration configuration, BundleContext bundleContext) throws Exception {
        Dictionary<String, Object> configDict = configAdmin.getConfiguration(CONFIG_PID).getProperties();
        
        configurationRootPaths = new ArrayList<>();
        if(!ArrayUtils.isEmpty(configuration.configurationRootPaths())) {
            configurationRootPaths.addAll(Arrays.asList(configuration.configurationRootPaths()));
        } else {
            if (configDict != null && configDict.get(LEGACY_PROPERTY_CONFIGURATION_PATH)!=null) {
                // Fallback to old (non-array) root paths property
                LOG.warn("Using legacy property PID '{}' is deprecated, use 'configurationRootPaths' instead. ", LEGACY_PROPERTY_CONFIGURATION_PATH);
                configurationRootPaths.add(PropertiesUtil.toString(configDict.get(LEGACY_PROPERTY_CONFIGURATION_PATH), ""));
            }
        }

        // Fallback to old PID: only fall back to legacy config if new config does not exist
        if (configDict == null) {
            Dictionary<String, Object> legacyProps = configAdmin.getConfiguration(LEGACY_CONFIG_PID).getProperties();
            if (legacyProps != null) {
                LOG.warn("Using legacy configuration PID '{}'. Please remove this and switch to the new one with PID '{}',", LEGACY_CONFIG_PID, CONFIG_PID);
                configurationRootPaths = Arrays.asList(PropertiesUtil.toString(legacyProps.get(LEGACY_PROPERTY_CONFIGURATION_PATH), ""));
            }
        }

        LOG.info("Activated AC Tool at start level "+RuntimeHelper.getCurrentStartLevel(bundleContext) + " default config path: "+configurationRootPaths);

    }

    @Override
    public InstallationLog apply() {
        return apply(null, null);
    }
    
    @Override
    public InstallationLog apply(String configurationRootPath) {
        return apply(configurationRootPath, null);
    }

    @Override
    public InstallationLog apply(String[] restrictedToPaths) {
        return apply(null, restrictedToPaths);
    }

    @Override
    public InstallationLog apply(String configurationRootPath, String[] restrictedToPaths) {
        return apply(configurationRootPath, restrictedToPaths, false);
    }

    @Override
    public InstallationLog apply(String configurationRootPath, String[] restrictedToPaths, boolean skipIfConfigUnchanged) {

        if(StringUtils.isBlank(configurationRootPath)) {
            if(CollectionUtils.isEmpty(configurationRootPaths)) {
                throw new IllegalArgumentException("Configuration root path neither configured nor provided.");
            } else if(configurationRootPaths.size() == 1) {
                configurationRootPath = configurationRootPaths.get(0);
            } else {
                return applyMultipleConfigurations(restrictedToPaths, skipIfConfigUnchanged);
            }
        }
        
        PersistableInstallationLogger installLog = new PersistableInstallationLogger();
        Session session = null;
        try {
            session = repository.loginService(null, null);
            
            // get config file contents from JCR
            Map<String, String> configFiles;
            try {
                configFiles = configFilesRetriever.getConfigFileContentFromNode(configurationRootPath, session);
            } catch (Exception e) {
                installLog.addError("Could not retrieve configuration from path "+configurationRootPath+": "+e.getMessage(), e);
                persistHistory(installLog);
                return installLog;
            }

            // install config files
            installConfigurationFiles(installLog, configFiles, restrictedToPaths, session, skipIfConfigUnchanged);
            
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

    private InstallationLog applyMultipleConfigurations(String[] restrictedToPaths, boolean skipIfConfigUnchanged) {
        InstallationLogger overviewInstallLog = new PersistableInstallationLogger();
        overviewInstallLog.addMessage(LOG, "Applying multiple configs (this log only shows what was applied, check the individual logs for details)");
        for(String rootPath: configurationRootPaths) {
            overviewInstallLog.addMessage(LOG, "Applying config at root path "+rootPath);
            apply(rootPath, restrictedToPaths, skipIfConfigUnchanged);
        }
        return overviewInstallLog;
    }


    // called from install hook, skipIfConfigUnchanged always false
    @Override
    public void installConfigurationFiles(PersistableInstallationLogger installLog, Map<String, String> configurationFileContentsByFilename,
            String[] restrictedToPaths, Session session)
            throws Exception {
        installConfigurationFiles(installLog, configurationFileContentsByFilename, restrictedToPaths, session, false);
    }

    /** Common entry point for JMX and install hook */
    // TODO: should not be exported as using non-API class PersistableInstallationLogger / https://github.com/Netcentric/accesscontroltool/issues/394
    public void installConfigurationFiles(PersistableInstallationLogger installLog, Map<String, String> configurationFileContentsByFilename,
            String[] restrictedToPaths, Session session, boolean skipIfConfigUnchanged)
            throws Exception {

        boolean configsIdenticalToLastExecution = acConfigChangeTracker.configIsUnchangedComparedToLastExecution(configurationFileContentsByFilename, restrictedToPaths, session);
        if(skipIfConfigUnchanged && configsIdenticalToLastExecution) {
            installLog.addMessage(LOG, "Config files are identical to last execution");
            // returning outside of below try will not persist history (this is the desired behaviour for this case)
            return;
        }

        String origThreadName = Thread.currentThread().getName();
        try {
            

            Thread.currentThread().setName(origThreadName + "-ACTool-Config-Worker");
            StopWatch sw = new StopWatch();
            sw.start();

            installLog.addMessage(LOG, "*** Applying AC Tool Configuration...");
            installLog.addMessage(LOG, "Running with v" + getVersion() + " on instance id "+slingSettingsService.getSlingId() + (!ArrayUtils.isEmpty(restrictedToPaths) ? " with restricted paths: "+Arrays.asList(restrictedToPaths) : ""));

            if (configurationFileContentsByFilename != null) {

                installLog.setConfigFileContentsByName(configurationFileContentsByFilename);

                AcConfiguration acConfiguration = configurationMerger.getMergedConfigurations(configurationFileContentsByFilename, installLog,
                        configReader, session);

                installMergedConfigurations(installLog, acConfiguration, restrictedToPaths, session);

                ensureVirtualGroupsAreRemoved(installLog, acConfiguration, session);
                removeObsoleteAuthorizables(installLog, acConfiguration.getObsoleteAuthorizables(), session);

            }
            sw.stop();
            long executionTime = sw.getTime();
            installLog.setExecutionTime(executionTime);
            installLog.addMessage(LOG, "Successfully applied AC Tool configuration in " + msHumanReadable(executionTime));
        } catch (Exception e) {
            installLog.addError("Could not process yaml files", e); // ensure exception is added to installLog before it's persisted in log in finally clause
            throw e; // handling is different depending on JMX or install hook case
        } finally {
            persistHistory(installLog);
            Thread.currentThread().setName(origThreadName);
        }

    }

    private void persistHistory(PersistableInstallationLogger installLog) {
        try {
            acHistoryService.persistHistory(installLog);
        } catch (Exception e) {
            LOG.warn("Could not persist history, e=" + e, e);
        }
    }


    private void installAcConfiguration(
            AcConfiguration acConfiguration, InstallationLogger installLog,
            Map<String, Set<AceBean>> repositoryDumpAceMap, String[] restrictedToPaths, Session session) throws Exception {

        if (acConfiguration.getAceConfig() == null) {
            String message = "ACE config not found in YAML file! installation aborted!";
            LOG.error(message);
            throw new IllegalArgumentException(message);
        }

        installAuthorizables(installLog, acConfiguration, session);

        installAces(installLog, acConfiguration, repositoryDumpAceMap, restrictedToPaths, session);
    }

    private void removeAcesForPathsNotInConfig(InstallationLogger installLog, Session session, Set<String> principalsInConfig,
            Map<String, Set<AceBean>> repositoryDumpAceMap, AcConfiguration acConfiguration)
            throws UnsupportedRepositoryOperationException, RepositoryException {

        int countAcesCleaned = 0;
        int countPathsCleaned = 0;
        Set<String> relevantPathsForCleanup = getRelevantPathsForAceCleanup(principalsInConfig, repositoryDumpAceMap,
                acConfiguration.getAceConfig());

        for (String relevantPath : relevantPathsForCleanup) {
            Set<String> principalsToRemoveAcesForAtThisPath = acConfiguration.getAuthorizablesConfig()
                    .removeUnmanagedPrincipalNamesAtPath(relevantPath, principalsInConfig,
                            acConfiguration.getGlobalConfiguration().getDefaultUnmanagedAcePathsRegex());

            // delete ACE if principal *is* in config, but the path *is not* in config
            int countRemoved = AccessControlUtils.deleteAllEntriesForPrincipalsFromACL(session,
                    relevantPath, principalsToRemoveAcesForAtThisPath.toArray(new String[principalsToRemoveAcesForAtThisPath.size()]));

            if (countRemoved > 0) {
                countPathsCleaned++;
                installLog.addMessage(LOG,
                        "For paths not contained in the configuration: Cleaned " + countRemoved + " ACEs of path " + relevantPath + " from all ACEs for configured authorizables");
            }
            countAcesCleaned += countRemoved;
        }

        if (countAcesCleaned > 0) {
            installLog.addMessage(LOG,
                    "For paths not contained in the configuration: Cleaned " + countAcesCleaned + " ACEs from " + countPathsCleaned
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
            boolean isRegEx = StringUtils.containsAny(restrictedToPath, new char[] {'*', '^', '$', '+'});
            String regexStr = isRegEx ? restrictedToPath : "^" + restrictedToPath + "(/.*|$)";
            if (path.matches(regexStr)) {
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

    private void installAces(InstallationLogger installLog,
            AcConfiguration acConfiguration, Map<String, Set<AceBean>> repositoryDumpAceMap, String[] restrictedToPaths, Session session)
            throws Exception {

        // --- installation of ACEs from configuration ---
        Map<String, Set<AceBean>> pathBasedAceMapFromConfig = AcHelper
                .getPathBasedAceMap(acConfiguration.getAceConfig(), AcHelper.ACE_ORDER_ACTOOL_BEST_PRACTICE);

        Set<String> principalsToRemoveAcesFor = getPrincipalNamesToRemoveAcesFor(acConfiguration.getAuthorizablesConfig());
        removeAcesForPathsNotInConfig(installLog, session, principalsToRemoveAcesFor, repositoryDumpAceMap, acConfiguration);

        Map<String, Set<AceBean>> filteredPathBasedAceMapFromConfig = filterForRestrictedPaths(pathBasedAceMapFromConfig,
                restrictedToPaths, installLog);

        if (!filteredPathBasedAceMapFromConfig.isEmpty()) {
            AceBeanInstaller aceBeanInstaller = acConfiguration.getGlobalConfiguration().getInstallAclsIncrementally()
                    ? aceBeanInstallerIncremental
                    : aceBeanInstallerClassic;

            installLog.addMessage(LOG,
                    "*** Starting installation of " + collectAceCount(filteredPathBasedAceMapFromConfig) + " ACE configurations for "
                            + filteredPathBasedAceMapFromConfig.size() + " paths in content nodes using strategy "
                            + aceBeanInstaller.getClass().getSimpleName() + "...");

            aceBeanInstaller.installPathBasedACEs(filteredPathBasedAceMapFromConfig, acConfiguration, session, installLog,
                    principalsToRemoveAcesFor);
        } else {
            installLog.addMessage(LOG, "No relevant ACEs to install");
        }

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
            String[] restrictedToPaths, InstallationLogger installLog) {
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

    private void installAuthorizables(InstallationLogger installLog, AcConfiguration acConfiguration, Session session)
            throws RepositoryException, Exception {
        // --- installation of Authorizables from configuration ---

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        AuthorizablesConfig authorizablesConfig = acConfiguration.getAuthorizablesConfig();
        installLog.addMessage(LOG, "*** Starting installation of " + authorizablesConfig.size() + " authorizables from configuration...");

        try {
            // only save session if no exceptions occurred
            authorizableCreatorService.installAuthorizables(acConfiguration, authorizablesConfig, session, installLog);
        } catch (Exception e) {
            throw new AuthorizableCreatorException(e);
        }

        installLog.addMessage(LOG, "Finished installation of authorizables without errors in "
                + msHumanReadable(stopWatch.getTime()));
    }

    private Set<String> removeNonExistingAuthorizables(Set<String> authorizablesToCheck, Session session)
            throws AccessDeniedException, UnsupportedRepositoryOperationException, RepositoryException {
        Set<String> nonExistingAuthorizables = new HashSet<String>();

        UserManager userManager = AccessControlUtils.getUserManagerAutoSaveDisabled(session);
        Iterator<String> authorizablesIt = authorizablesToCheck.iterator();
        while (authorizablesIt.hasNext()) {
            String authorizableId = authorizablesIt.next();
            Authorizable authorizable = userManager.getAuthorizable(authorizableId);
            if (authorizable == null) {
                nonExistingAuthorizables.add(authorizableId);
                authorizablesIt.remove();
            }
        }
        return nonExistingAuthorizables;
    }

    private void removeObsoleteAuthorizables(InstallationLogger installLog, Set<String> obsoleteAuthorizables, Session session) {

        try {

            if (obsoleteAuthorizables.isEmpty()) {
                installLog.addVerboseMessage(LOG, "No obsolete authorizables configured");
                return;
            }

            Set<String> obsoleteAuthorizablesAlreadyPurged = removeNonExistingAuthorizables(obsoleteAuthorizables, session);

            if (obsoleteAuthorizables.isEmpty()) {
                installLog.addMessage(LOG, "All configured " + obsoleteAuthorizablesAlreadyPurged.size()
                                + " obsolete authorizables have already been purged.");
                return;
            }

            installLog.addMessage(LOG, "*** Purging " + obsoleteAuthorizables.size() + " obsolete authorizables...  ");
            if (!obsoleteAuthorizablesAlreadyPurged.isEmpty()) {
                installLog.addMessage(LOG, "(" + obsoleteAuthorizablesAlreadyPurged.size() + " have been purged already)");
            }

            purgeAuthorizables(obsoleteAuthorizables, session, installLog, true);
            installLog.addMessage(LOG, "Successfully purged " + obsoleteAuthorizables);
        } catch (Exception e) {
            installLog.addError(LOG, "Could not purge obsolete authorizables " + obsoleteAuthorizables, e);
        }

    }

    private void ensureVirtualGroupsAreRemoved(InstallationLogger installLog, AcConfiguration acConfiguration, Session session) {

        try {

            List<AuthorizableConfigBean> virtualGroups = acConfiguration.getVirtualGroups();
            Set<String> virtualGroupIds = new HashSet<>();
            for (AuthorizableConfigBean virtualGroup : virtualGroups) {
                virtualGroupIds.add(virtualGroup.getAuthorizableId());
            }

            if (virtualGroups.isEmpty()) {
                installLog.addVerboseMessage(LOG,
                        "No virtual groups are configured - no need to ensure virtual groups are removed from repo.");
                return;
            }

            removeNonExistingAuthorizables(virtualGroupIds, session);
            if (virtualGroupIds.isEmpty()) {
                installLog.addVerboseMessage(LOG, "No virtual groups exist in repo that would require purging.");
                return;
            }

            installLog.addMessage(LOG, "Purging " + virtualGroupIds.size()
                    + " virtual groups from repository (most likely they were non-virtual groups before)...");

            purgeAuthorizables(virtualGroupIds, session, installLog, true);
            installLog.addMessage(LOG, "Successfully purged virtual groups from repository: " + virtualGroupIds);

        } catch (Exception e) {
            installLog.addError(LOG, "Could not purge virtual groups", e);
        }

    }

    private void installMergedConfigurations(InstallationLogger installLog, AcConfiguration acConfiguration, 
            String[] restrictedToPaths, Session session) throws ValueFormatException,  RepositoryException, Exception {

        installLog.addVerboseMessage(LOG, "Starting installation of merged configurations...");

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        Map<String, Set<AceBean>> repositoryDumpAceMap = null;
        LOG.debug("Building dump from repository (to compare delta with config to be installed)");
        repositoryDumpAceMap = dumpservice.createAclDumpMap(AcHelper.PATH_BASED_ORDER,
                AcHelper.ACE_ORDER_NONE,
                Collections.<String>emptyList(), true, session).getAceDump();

        installLog.addMessage(LOG, "Retrieved existing ACLs from repository in " + msHumanReadable(stopWatch.getTime()));

        installAcConfiguration(acConfiguration, installLog, repositoryDumpAceMap, restrictedToPaths, session);

    }

    public boolean isReadyToStart() {
        Session session = null;
        
        boolean isReadyToStart = false;
        for(String configRootPath: configurationRootPaths) {
            try {
                session = repository.loginService(null, null);
                boolean thisConfigIsReadyToStart = !configFilesRetriever.getConfigFileContentFromNode(configRootPath, session).isEmpty();
                LOG.debug("Config {} is ready to start: {}", configRootPath, thisConfigIsReadyToStart);
                isReadyToStart |= thisConfigIsReadyToStart;

            } catch (Exception e) {
                LOG.warn("Could not retrieve config file content for root path " + configRootPath);
                return false;
            } finally {
                if (session != null) {
                    session.logout();
                }
            }
        }
        
        return isReadyToStart;
    }

    @Override
    public String purgeACL(String path) {
        Session session = null;
        String message = "";
        boolean flag = true;
        try {
            session = repository.loginService(null, null);
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
            PersistableInstallationLogger installLog = new PersistableInstallationLogger();
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
            session = repository.loginService(null, null);
            message = PurgeHelper.purgeACLs(session, path);
            PersistableInstallationLogger installLog = new PersistableInstallationLogger();
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
        List<String> resultMessages = new ArrayList<String>();
        for (String configRootPath : configurationRootPaths) {
            LOG.info("Purging authorizables for root path {}", configRootPath);
            resultMessages.add(purgeAuthorizablesFromConfig(configRootPath));
        }
        return StringUtils.join(resultMessages, "\n");
    }

    @Override
    public String purgeAuthorizablesFromConfig(String configRootPath) {
        Session session = null;
        try {
            session = repository.loginService(null, null);

            PersistableInstallationLogger installLog = new PersistableInstallationLogger();
            installLog.addMessage(LOG, "*** Purging AC Tool configuraiton " + configRootPath + "...");

            Map<String, String> newestConfigurations = configFilesRetriever.getConfigFileContentFromNode(configRootPath, session);
            AcConfiguration acConfiguration = configurationMerger.getMergedConfigurations(newestConfigurations, installLog, configReader,
                    session);

            installLog.addMessage(LOG, "Purging ACLs...");
            long startAclPurge = System.currentTimeMillis();
            // removing the ace config section will clear all
            acConfiguration.getAceConfig().clear();

            Map<String, Set<AceBean>> aceDump = dumpservice
                    .createAclDumpMap(AcHelper.PATH_BASED_ORDER, AcHelper.ACE_ORDER_NONE, Collections.<String> emptyList(), true, session)
                    .getAceDump();
            installAces(installLog, acConfiguration, aceDump, null, session);
            installLog.addMessage(LOG, "Purged ACLs for " + acConfiguration.getAuthorizablesConfig().size() + " authorizables in "
                    + msHumanReadable(System.currentTimeMillis() - startAclPurge));

            installLog.addMessage(LOG, "Purging authorizables...");
            Set<String> authorizablesToPurge = new HashSet<>();
            for (AuthorizableConfigBean authorizableConfigBean : acConfiguration.getAuthorizablesConfig()) {
                String authorizableId = authorizableConfigBean.getAuthorizableId();
                if (StringUtils.isNotBlank(authorizableConfigBean.getUnmanagedAcePathsRegex())) {
                    installLog.addMessage(LOG, "Not purging " + authorizableId + " since property unmanagedAcePathsRegex is configured");
                    continue;
                }
                if (PRINCIPAL_EVERYONE.equals(authorizableId)) {
                    continue;
                }
                authorizablesToPurge.add(authorizableId);
            }

            purgeAuthorizables(authorizablesToPurge, session, installLog, false);

            acHistoryService.persistAcePurgeHistory(installLog);

            return installLog.getMessageHistory();
        } catch (Exception e) {
            LOG.error("Exception while purging all authorizable from config: {}", e, e);
            return "Could not purge authorizables from config " + configRootPath + ": " + e;
        } finally {
            if (session != null) {
                session.logout();
            }
        }
    }

    @Override
    public String purgeAuthorizables(String[] authorizableIds) {
        PersistableInstallationLogger installLog = new PersistableInstallationLogger();

        Session session = null;
        try {
            session = repository.loginService(null, null);
            Set<String> authorizablesSet = new HashSet<>(Arrays.asList(authorizableIds));
            purgeAuthorizables(authorizablesSet, session, installLog, true);

            acHistoryService.persistAcePurgeHistory(installLog);
        } catch (RepositoryException e) {
            installLog.addError(LOG, "Could not purge authorizables " + Arrays.asList(authorizableIds) + ": " + e, e);
        } finally {
            if (session != null) {
                session.logout();
            }
        }
        return installLog.getMessageHistory();
    }

    private void purgeAuthorizables(Set<String> authorizableIds, final Session session, InstallationLogger installLog, boolean deleteAces) {

        StopWatch sw = new StopWatch();
        sw.start();

        try {
            if (deleteAces) {
                // first the ACE entries have to be deleted
                Set<String> principalIds = new HashSet<>();
                Set<AclBean> aclBeans = QueryHelper.getAuthorizablesAcls(session, authorizableIds, principalIds);
                deleteAcesForPrincipalIds(session, installLog, principalIds, aclBeans);
            }

            // then the authorizables can be deleted
            UserManager userManager = AccessControlUtils.getUserManagerAutoSaveDisabled(session);
            List<Authorizable> authorizablesToDelete = new ArrayList<>();
            for (String authorizableId : authorizableIds) {
                Authorizable authorizable = userManager.getAuthorizable(authorizableId);
                if (authorizable != null) {
                    authorizablesToDelete.add(authorizable);
                } else {
                    installLog.addMessage(LOG, "Could not delete authorizable '" + authorizableId + "' because it does not exist");
                }
            }

            sortAuthorizablesForDeletion(authorizablesToDelete);

            for (Authorizable authorizableToDelete : authorizablesToDelete) {
                deleteAuthorizable(installLog, authorizableToDelete);
            }

            session.save();

            sw.stop();
            String executionTime = PersistableInstallationLogger.msHumanReadable(sw.getTime());
            installLog.addMessage(LOG, "Purged " + authorizablesToDelete.size() + " authorizables in " + executionTime);

        } catch (Exception e) {
            installLog.addError(LOG, "Purging of authorizables failed: " + e, e);
        }

    }

    public void deleteAcesForPrincipalIds(final Session session, InstallationLogger installLog, final Set<String> principalIds,
            final Set<AclBean> aclBeans) throws RepositoryException {

        StopWatch sw = new StopWatch();
        sw.start();
        AccessControlManager aMgr = session.getAccessControlManager();
        long aceCounter = 0;

        for (AclBean aclBean : aclBeans) {
            if (aclBean == null) {
                continue;
            }

            JackrabbitAccessControlList acl = aclBean.getAcl();
            for (AccessControlEntry ace : acl.getAccessControlEntries()) {
                String principalId = ace.getPrincipal().getName();
                if (principalIds.contains(principalId)) {
                    String parentNodePath = aclBean.getParentPath();
                    acl.removeAccessControlEntry(ace);
                    boolean aclEmpty = acl.isEmpty();
                    if (!aclEmpty) {
                        aMgr.setPolicy(aclBean.getParentPath(), acl);
                    } else {
                        aMgr.removePolicy(aclBean.getParentPath(), acl);
                    }

                    installLog.addVerboseMessage(LOG, "Path " + parentNodePath + ": Removed entry for '" + principalId + "' from ACL "
                            + (aclEmpty ? " (and the now emtpy ACL itself)" : ""));

                    aceCounter++;
                }
            }
        }
        sw.stop();
        String executionTime = msHumanReadable(sw.getTime());
        String resultMsg = (aceCounter > 0)
                ? "Deleted " + aceCounter + " ACEs for " + principalIds.size() + " principals in " + executionTime
                : "Did not delete any ACEs";
        installLog.addMessage(LOG, resultMsg);
    }

    void sortAuthorizablesForDeletion(List<Authorizable> authorizablesToDelete) throws RepositoryException {

        outer: for (int i = 0; i < authorizablesToDelete.size();) {
            Authorizable currentAuthorizable = authorizablesToDelete.get(i);
            Iterator<Group> declaredMemberOfIt = currentAuthorizable.declaredMemberOf();
            LOG.trace("At index {}: {}", i, currentAuthorizable.getID());
            while (declaredMemberOfIt != null && declaredMemberOfIt.hasNext()) {
                Group groupOfAuthorizable = declaredMemberOfIt.next();
                int groupOfAuthorizableIndex = authorizablesToDelete.indexOf(groupOfAuthorizable);
                LOG.trace("  Is member of at index {}: {}", groupOfAuthorizableIndex, groupOfAuthorizable.getID());
                if (groupOfAuthorizableIndex > -1 && groupOfAuthorizableIndex < i) {
                    LOG.trace("    Swap at index {} (groupOfAuthorizableIndex {}):", i, groupOfAuthorizableIndex);
                    // swap and set i
                    authorizablesToDelete.set(groupOfAuthorizableIndex, currentAuthorizable);
                    authorizablesToDelete.set(i, groupOfAuthorizable);
                    i = groupOfAuthorizableIndex;
                    continue outer;
                }
            }
            i++;
        }
    }

    private void deleteAuthorizable(InstallationLogger installLog, Authorizable authorizable) throws RepositoryException {
        try {
            List<String> removedFromGroups = new ArrayList<>();
            Iterator<Group> declaredMemberOf = authorizable.declaredMemberOf();
            while (declaredMemberOf.hasNext()) {
                Group groupTheAuthorizableIsMemberOf = declaredMemberOf.next();
                if (groupTheAuthorizableIsMemberOf.removeMember(authorizable)) {
                    removedFromGroups.add(groupTheAuthorizableIsMemberOf.getID());
                }
            }

            authorizable.remove();
            installLog.addVerboseMessage(LOG, "Deleted authorizable '" + authorizable.getID() + "'"
                    + (!removedFromGroups.isEmpty() ? " and removed it from groups: " + StringUtils.join(removedFromGroups, ", ")
                            : ""));

        } catch (RepositoryException e) {
            installLog.addError(LOG, "Error while deleting authorizable '" + authorizable.getID() + "': e=" + e, e);
        }
    }

    public List<String> getConfigurationRootPaths() {
        return configurationRootPaths;
    }

    @Override
    public Set<String> getCurrentConfigurationPaths() {

        Session session = null;
        Set<String> paths = new LinkedHashSet<String>();
        try {
            session = repository.loginService(null, null);
            
            for(String configRootPath: configurationRootPaths) {
                paths.addAll(configFilesRetriever.getConfigFileContentFromNode(configRootPath, session).keySet());
            }
            
        } catch (Exception e) {
            LOG.warn("Could not retrieve config file content for root paths {}: e={}", configurationRootPaths, e, e);
        } finally {
            if (session != null) {
                session.logout();
            }
        }
        return paths;
    }

    public String getVersion() {
        String bundleVersion = FrameworkUtil.getBundle(AcInstallationServiceImpl.class).getVersion().toString();
        return bundleVersion;
    }

}
