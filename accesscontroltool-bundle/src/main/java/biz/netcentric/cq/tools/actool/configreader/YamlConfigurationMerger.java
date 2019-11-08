/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configreader;

import static biz.netcentric.cq.tools.actool.history.PersistableInstallationLogger.msHumanReadable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import biz.netcentric.cq.tools.actool.configmodel.*;
import org.apache.commons.lang.time.StopWatch;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.ConstructorException;
import org.yaml.snakeyaml.error.YAMLException;

import biz.netcentric.cq.tools.actool.helper.Constants;
import biz.netcentric.cq.tools.actool.history.InstallationLogger;
import biz.netcentric.cq.tools.actool.history.PersistableInstallationLogger;
import biz.netcentric.cq.tools.actool.validators.AuthorizableValidator;
import biz.netcentric.cq.tools.actool.validators.ConfigurationsValidator;
import biz.netcentric.cq.tools.actool.validators.GlobalConfigurationValidator;
import biz.netcentric.cq.tools.actool.validators.ObsoleteAuthorizablesValidator;
import biz.netcentric.cq.tools.actool.validators.UnmangedExternalMemberRelationshipChecker;
import biz.netcentric.cq.tools.actool.validators.YamlConfigurationsValidator;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;
import biz.netcentric.cq.tools.actool.validators.exceptions.NoListOnTopLevelException;
import biz.netcentric.cq.tools.actool.validators.impl.AceBeanValidatorImpl;
import biz.netcentric.cq.tools.actool.validators.impl.AuthorizableValidatorImpl;

@Component
public class YamlConfigurationMerger implements ConfigurationMerger {

    private static final Logger LOG = LoggerFactory.getLogger(YamlConfigurationMerger.class);

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    YamlMacroProcessor yamlMacroProcessor;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    ObsoleteAuthorizablesValidator obsoleteAuthorizablesValidator;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    VirtualGroupProcessor virtualGroupProcessor;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    TestUserConfigsCreator testUserConfigsCreator;

    @Override
    public AcConfiguration getMergedConfigurations(
            final Map<String, String> configFileContentByFilename,
            final PersistableInstallationLogger installLog,
            final ConfigReader configReader, Session session) throws RepositoryException,
            AcConfigBeanValidationException {

        StopWatch sw = new StopWatch();
        sw.start();

        final GlobalConfiguration globalConfiguration = new GlobalConfiguration();
        final AuthorizablesConfig mergedAuthorizablesBeansfromConfig = new AuthorizablesConfig();
        final AcesConfig mergedAceBeansFromConfig = new AcesConfig();
        final Set<String> authorizableIdsFromAllConfigs = new HashSet<String>(); // needed for detection of doubled defined groups in
                                                                                 // configurations
        final Set<String> obsoleteAuthorizables = new HashSet<String>();
        final PrivilegeConfig privilegeConfig = new PrivilegeConfig();

        final Yaml yamlParser = new Yaml();

        final ConfigurationsValidator configurationsValidator = new YamlConfigurationsValidator();

        for (final Map.Entry<String, String> entry : configFileContentByFilename.entrySet()) {

            String sourceFile = entry.getKey();
            installLog.addMessage(LOG, "Using configuration file " + sourceFile);

            List<Map> yamlRootList;
            try {
                yamlRootList = yamlParser.loadAs(entry.getValue(), List.class);
                if (yamlRootList == null || yamlRootList.isEmpty()) {
                    installLog.addMessage(LOG, "   " + sourceFile + " has no instructions");
                    continue;
                }
            } catch (ClassCastException e) {
                throw new NoListOnTopLevelException("Each yaml file must contain a list on the top level but the yaml at " + sourceFile + " does not.", e);
            } catch (YAMLException e) {
                throw new NoListOnTopLevelException("Invalid yaml, please check format of " + sourceFile, e);
            }
            try {
                privilegeConfig.merge(configReader.getPrivilegeConfiguration(yamlRootList));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid privilege configuration in " + sourceFile + ": " + e, e);
            }

            yamlRootList = yamlMacroProcessor.processMacros(yamlRootList, installLog, session);
            // set merged config per file to ensure it is there in case of validation errors (for success, the actual merged config is set
            // after this loop)
            installLog.setMergedAndProcessedConfig("# File " + sourceFile + "\n" + yamlParser.dump(yamlRootList));

            final Set<String> sectionIdentifiers = new LinkedHashSet<String>();

            // put all section identifiers of current configuration into a set
            for (int i = 0; i < yamlRootList.size(); i++) {
                sectionIdentifiers.addAll(yamlRootList.get(i).keySet());
            }
            configurationsValidator.validateSectionIdentifiers(sectionIdentifiers, sourceFile);

            // --- global configuration section
            try {
                globalConfiguration.merge(configReader.getGlobalConfiguration(yamlRootList));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid global configuration in " + sourceFile + ": " + e, e);
            }

            // --- authorizables config section

            final AuthorizableValidator authorizableValidator = new AuthorizableValidatorImpl(Constants.GROUPS_ROOT, Constants.USERS_ROOT);
            final AuthorizablesConfig groupsFromThisConfig = configReader.getGroupConfigurationBeans(
                    yamlRootList, authorizableValidator);
            // add AuthorizableConfigBeans built from current configuration to set containing AuthorizableConfigBeans from all
            // configurations
            if (groupsFromThisConfig != null) {
                mergedAuthorizablesBeansfromConfig.addAll(groupsFromThisConfig);
            }

            final AuthorizablesConfig usersMapFromThisConfig = configReader.getUserConfigurationBeans(
                    yamlRootList, authorizableValidator);
            if (usersMapFromThisConfig != null) {
                mergedAuthorizablesBeansfromConfig.addAll(usersMapFromThisConfig);
            }

            // validate duplicate authorizables
            final Set<String> authorizableIdsFromCurrentConfig = new HashSet<String>();
            if (groupsFromThisConfig != null) {
                authorizableIdsFromCurrentConfig.addAll(groupsFromThisConfig.getAuthorizableIds());
            }
            if (usersMapFromThisConfig != null) {
                authorizableIdsFromCurrentConfig.addAll(usersMapFromThisConfig.getAuthorizableIds());
            }

            if (authorizableIdsFromCurrentConfig != null) {
                configurationsValidator.validateDuplicateAuthorizables(authorizableIdsFromAllConfigs, authorizableIdsFromCurrentConfig,
                        sourceFile);
                // add IDs from authorizables from current configuration to set
                authorizableIdsFromAllConfigs.addAll(authorizableIdsFromCurrentConfig);
            }

            // --- ace_config section
            final Set<AceBean> currentAceBeansFromConfig = configReader.getAceConfigurationBeans(yamlRootList,
                    getAceBeanValidator(authorizableIdsFromAllConfigs), session, sourceFile);

            configurationsValidator.validateKeepOrder(mergedAceBeansFromConfig, currentAceBeansFromConfig, sourceFile);

            // add AceBeans built from current configuration to set containing AceBeans from all configurations
            if (currentAceBeansFromConfig != null) {
                mergedAceBeansFromConfig.addAll(currentAceBeansFromConfig);
            }

            configurationsValidator.validateInitialContentForNoDuplicates(mergedAceBeansFromConfig);

            // --- obsolete authorizables config section
            obsoleteAuthorizables.addAll(configReader.getObsoluteAuthorizables(yamlRootList));
            obsoleteAuthorizablesValidator.validate(obsoleteAuthorizables, authorizableIdsFromAllConfigs, sourceFile);
        }

        ensureIsMemberOfIsUsedWherePossible(mergedAuthorizablesBeansfromConfig, installLog);

        GlobalConfigurationValidator.validate(globalConfiguration);

        AcConfiguration acConfiguration = new AcConfiguration();
        acConfiguration.setGlobalConfiguration(globalConfiguration);
        acConfiguration.setAuthorizablesConfig(mergedAuthorizablesBeansfromConfig);
        acConfiguration.setAceConfig(mergedAceBeansFromConfig);
        acConfiguration.setObsoleteAuthorizables(obsoleteAuthorizables);
        acConfiguration.setPrivilegeConfig(privilegeConfig);

        virtualGroupProcessor.flattenGroupTree(acConfiguration, installLog);

        testUserConfigsCreator.createTestUserConfigs(acConfiguration, installLog);

        if(!Boolean.TRUE.equals(globalConfiguration.getAllowCreateOfUnmanagedRelationships())) {
        	UnmangedExternalMemberRelationshipChecker.validate(acConfiguration);
        }
        
        installLog.setMergedAndProcessedConfig(
                "# Merged configuration of " + configFileContentByFilename.size() + " files \n" + yamlParser.dump(acConfiguration));

        installLog.addMessage(LOG, "Loaded configuration in " + msHumanReadable(sw.getTime()));

        return acConfiguration;
    }

    AceBeanValidatorImpl getAceBeanValidator(final Set<String> authorizableIdsFromAllConfigs) {
        return new AceBeanValidatorImpl(authorizableIdsFromAllConfigs);
    }

    void ensureIsMemberOfIsUsedWherePossible(AuthorizablesConfig mergedAuthorizablesBeansfromConfig,
            InstallationLogger history) {

        for (AuthorizableConfigBean group : mergedAuthorizablesBeansfromConfig) {
            if (!group.isGroup()) {
                continue;
            }

            final String groupName = group.getAuthorizableId();

            String[] origMembersArr = group.getMembers();

            if ((origMembersArr == null) || (origMembersArr.length == 0)) {
                continue;
            }

            final List<String> members = new ArrayList<String>(Arrays.asList(origMembersArr));

            Iterator<String> membersIt = members.iterator();
            while (membersIt.hasNext()) {
                String member = membersIt.next();

                AuthorizableConfigBean groupForIsMemberOf = mergedAuthorizablesBeansfromConfig.getAuthorizableConfig(member);

                boolean memberContainedInConfig = groupForIsMemberOf != null;
                if (memberContainedInConfig) {
                    groupForIsMemberOf.addIsMemberOf(groupName);
                    membersIt.remove();
                    history.addVerboseMessage(LOG, "Group " + group.getAuthorizableId() + " is declaring member " + member
                            + " - moving relationship to isMemberOf of authorizable " + groupForIsMemberOf.getAuthorizableId()
                            + " (always prefer using isMemberOf over members if referenced member is availalbe in configuration)");
                }

            }
            group.setMembers(members.toArray(new String[members.size()]));

        }
    }
}
