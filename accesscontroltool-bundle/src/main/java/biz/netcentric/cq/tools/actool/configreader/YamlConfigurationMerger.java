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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.configmodel.AcConfiguration;
import biz.netcentric.cq.tools.actool.configmodel.GlobalConfiguration;
import biz.netcentric.cq.tools.actool.helper.AceBean;
import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;
import biz.netcentric.cq.tools.actool.validators.AceBeanValidator;
import biz.netcentric.cq.tools.actool.validators.AuthorizableValidator;
import biz.netcentric.cq.tools.actool.validators.ConfigurationsValidator;
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

        final Yaml yaml = new Yaml();

        final ConfigurationsValidator configurationsValidator = new YamlConfigurationsValidator();

        for (final Map.Entry<String, String> entry : configFileContentByFilename.entrySet()) {

            String sourceFile = entry.getKey();
            configurationsValidator.validateMandatorySectionIdentifiersExistence(entry.getValue(), sourceFile);

            final String message = "Found configuration file " + sourceFile;
            LOG.info(message);
            history.addMessage(message);

            List<LinkedHashMap> yamlList = (List<LinkedHashMap>) yaml.load(entry.getValue());

            yamlList = yamlMacroProcessor.processMacros(yamlList, history);
            // set merged config per file to ensure it is there in case of validation errors (for success, the actual merged config is set
            // after this loop)
            history.setMergedAndProcessedConfig("# File " + sourceFile + "\n" + yaml.dump(yamlList));

            final Set<String> sectionIdentifiers = new LinkedHashSet<String>();

            // FIXME: Why doesn't this use YamlConfigReader?
            // put all section identifiers of current configuration into a set
            for (int i = 0; i < yamlList.size(); i++) {
                sectionIdentifiers.addAll(yamlList.get(i).keySet());
            }
            configurationsValidator.validateSectionIdentifiers(
                    sectionIdentifiers, sourceFile);

            configurationsValidator.validateSectionContentExistence(sourceFile, yamlList);

            try {
                globalConfiguration.merge(configReader.getGlobalConfiguration(yamlList));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid global configuration in " + sourceFile + ": " + e, e);
            }

            // build AuthorizableConfigBeans from current configurations
            final AuthorizableValidator authorizableValidator = new AuthorizableValidatorImpl();
            final Map<String, Set<AuthorizableConfigBean>> groupAuthorizablesMapFromConfig = configReader.getGroupConfigurationBeans(
                    yamlList, authorizableValidator);
            // add AuthorizableConfigBeans built from current configuration to set containing AuthorizableConfigBeans from all
            // configurations
            if (groupAuthorizablesMapFromConfig != null) {
                mergedAuthorizablesMapfromConfig.putAll(groupAuthorizablesMapFromConfig);
            }

            final Map<String, Set<AuthorizableConfigBean>> userAuthorizablesMapFromConfig = configReader.getUserConfigurationBeans(
                    yamlList, authorizableValidator);
            if (userAuthorizablesMapFromConfig != null) {
                mergedAuthorizablesMapfromConfig.putAll(userAuthorizablesMapFromConfig);
            }

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

            // build AceBeans from current configuration
            final AceBeanValidator aceBeanValidator = new AceBeanValidatorImpl(authorizableIdsFromAllConfigs);
            final Map<String, Set<AceBean>> aceMapFromConfig = configReader.getAceConfigurationBeans(yamlList,
                    authorizableIdsFromAllConfigs,
                    aceBeanValidator);

            // add AceBeans built from current configuration to set containing
            // AceBeans from all configurations
            if (aceMapFromConfig != null) {
                mergedAceMapFromConfig.putAll(aceMapFromConfig);
            }

            configurationsValidator.validateInitialContentForNoDuplicates(mergedAceMapFromConfig);

        }
        // set member groups
        final AuthorizableMemberGroupsValidator membersValidator = new AuthorizableMemberGroupsValidator();
        membersValidator.validate(mergedAuthorizablesMapfromConfig);
        
        
        AcConfiguration acConfiguration = new AcConfiguration();
        acConfiguration.setGlobalConfiguration(globalConfiguration);
        acConfiguration.setAuthorizablesConfig(mergedAuthorizablesMapfromConfig);
        acConfiguration.setAceConfig(mergedAceMapFromConfig);

        history.setMergedAndProcessedConfig(
                "# Merged configuration of " + configFileContentByFilename.size() + " files \n" + yaml.dump(acConfiguration));

        return acConfiguration;
    }
}
