/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configreader;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.RepositoryException;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.oak.spi.security.user.UserConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import biz.netcentric.cq.tools.actool.configmodel.AcConfiguration;
import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.configmodel.GlobalConfiguration;
import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;
import biz.netcentric.cq.tools.actool.validators.AceBeanValidator;
import biz.netcentric.cq.tools.actool.validators.AuthorizableValidator;
import biz.netcentric.cq.tools.actool.validators.ConfigurationsValidator;
import biz.netcentric.cq.tools.actool.validators.GlobalConfigurationValidator;
import biz.netcentric.cq.tools.actool.validators.ObsoleteAuthorizablesValidator;
import biz.netcentric.cq.tools.actool.validators.YamlConfigurationsValidator;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;
import biz.netcentric.cq.tools.actool.validators.impl.AceBeanValidatorImpl;
import biz.netcentric.cq.tools.actool.validators.impl.AuthorizableMemberGroupsValidator;
import biz.netcentric.cq.tools.actool.validators.impl.AuthorizableValidatorImpl;

@Service
@Component
public class YamlConfigurationMerger implements ConfigurationMerger {

    private static final Logger LOG = LoggerFactory.getLogger(YamlConfigurationMerger.class);

    @Reference
    YamlMacroProcessor yamlMacroProcessor;

    @Reference
    ObsoleteAuthorizablesValidator obsoleteAuthorizablesValidator;

    @Reference
    UserConfiguration userConfiguration;

    @Override
    public AcConfiguration getMergedConfigurations(
            final Map<String, String> configFileContentByFilename,
            final AcInstallationHistoryPojo history,
            final ConfigReader configReader) throws RepositoryException,
                    AcConfigBeanValidationException {

        final GlobalConfiguration globalConfiguration = new GlobalConfiguration();
        final Map<String, Set<AuthorizableConfigBean>> mergedAuthorizablesMapfromConfig = new LinkedHashMap<String, Set<AuthorizableConfigBean>>();
        final Map<String, Set<AceBean>> mergedAceMapFromConfig = new LinkedHashMap<String, Set<AceBean>>();
        final Set<String> authorizableIdsFromAllConfigs = new HashSet<String>(); // needed for detection of doubled defined groups in
                                                                                 // configurations
        final Set<String> obsoleteAuthorizables = new HashSet<String>();

        final Yaml yamlParser = new Yaml();

        final ConfigurationsValidator configurationsValidator = new YamlConfigurationsValidator();

        for (final Map.Entry<String, String> entry : configFileContentByFilename.entrySet()) {

            String sourceFile = entry.getKey();
            final String message = "Found configuration file " + sourceFile;
            LOG.info(message);
            history.addMessage(message);

            List<LinkedHashMap> yamlRootList = (List<LinkedHashMap>) yamlParser.load(entry.getValue());

            yamlRootList = yamlMacroProcessor.processMacros(yamlRootList, history);
            // set merged config per file to ensure it is there in case of validation errors (for success, the actual merged config is set
            // after this loop)
            history.setMergedAndProcessedConfig("# File " + sourceFile + "\n" + yamlParser.dump(yamlRootList));

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

            // build AuthorizableConfigBeans from current configurations
            String groupsPath = (String) userConfiguration.getParameters().get("groupsPath");
            String usersPath = (String) userConfiguration.getParameters().get("usersPath");
            final AuthorizableValidator authorizableValidator = new AuthorizableValidatorImpl(groupsPath, usersPath);
            final Map<String, Set<AuthorizableConfigBean>> groupAuthorizablesMapFromConfig = configReader.getGroupConfigurationBeans(
                    yamlRootList, authorizableValidator);
            // add AuthorizableConfigBeans built from current configuration to set containing AuthorizableConfigBeans from all
            // configurations
            if (groupAuthorizablesMapFromConfig != null) {
                mergedAuthorizablesMapfromConfig.putAll(groupAuthorizablesMapFromConfig);
            }

            final Map<String, Set<AuthorizableConfigBean>> userAuthorizablesMapFromConfig = configReader.getUserConfigurationBeans(
                    yamlRootList, authorizableValidator);
            if (userAuthorizablesMapFromConfig != null) {
                mergedAuthorizablesMapfromConfig.putAll(userAuthorizablesMapFromConfig);
            }

            // validate duplicate authorizables
            final Set<String> authorizableIdsFromCurrentConfig = new HashSet<String>();
            if (groupAuthorizablesMapFromConfig != null) {
                authorizableIdsFromCurrentConfig.addAll(groupAuthorizablesMapFromConfig.keySet());
            }
            if (userAuthorizablesMapFromConfig != null) {
                authorizableIdsFromCurrentConfig.addAll(userAuthorizablesMapFromConfig.keySet());
            }

            if (authorizableIdsFromCurrentConfig != null) {
                configurationsValidator.validateDuplicateAuthorizables(authorizableIdsFromAllConfigs, authorizableIdsFromCurrentConfig,
                        sourceFile);
                // add IDs from authorizables from current configuration to set
                authorizableIdsFromAllConfigs.addAll(authorizableIdsFromCurrentConfig);
            }

            // --- authorizables config section
            final AceBeanValidator aceBeanValidator = new AceBeanValidatorImpl(authorizableIdsFromAllConfigs);
            final Map<String, Set<AceBean>> aceMapFromConfig = configReader.getAceConfigurationBeans(yamlRootList,
                    authorizableIdsFromAllConfigs,
                    aceBeanValidator);

            configurationsValidator.validateKeepOrder(mergedAceMapFromConfig, aceMapFromConfig, sourceFile);

            // add AceBeans built from current configuration to set containing AceBeans from all configurations
            if (aceMapFromConfig != null) {
                mergedAceMapFromConfig.putAll(aceMapFromConfig);
            }

            configurationsValidator.validateInitialContentForNoDuplicates(mergedAceMapFromConfig);

            // --- obsolete authorizables config section
            obsoleteAuthorizables.addAll(configReader.getObsoluteAuthorizables(yamlRootList));
            obsoleteAuthorizablesValidator.validate(obsoleteAuthorizables, authorizableIdsFromAllConfigs, sourceFile);
        }

        // set member groups
        final AuthorizableMemberGroupsValidator membersValidator = new AuthorizableMemberGroupsValidator();
        membersValidator.validate(mergedAuthorizablesMapfromConfig);

        GlobalConfigurationValidator.validate(globalConfiguration);

        AcConfiguration acConfiguration = new AcConfiguration();
        acConfiguration.setGlobalConfiguration(globalConfiguration);
        acConfiguration.setAuthorizablesConfig(mergedAuthorizablesMapfromConfig);
        acConfiguration.setAceConfig(mergedAceMapFromConfig);
        acConfiguration.setObsoleteAuthorizables(obsoleteAuthorizables);

        history.setMergedAndProcessedConfig(
                "# Merged configuration of " + configFileContentByFilename.size() + " files \n" + yamlParser.dump(acConfiguration));

        return acConfiguration;
    }
}
