package biz.netcentric.cq.tools.actool.configReader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.RepositoryException;

import org.yaml.snakeyaml.Yaml;

import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.helper.AceBean;
import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;
import biz.netcentric.cq.tools.actool.validators.AceBeanValidator;
import biz.netcentric.cq.tools.actool.validators.AuthorizableValidator;
import biz.netcentric.cq.tools.actool.validators.ConfigurationsValidator;
import biz.netcentric.cq.tools.actool.validators.YamlConfigurationsValidator;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;
import biz.netcentric.cq.tools.actool.validators.impl.AceBeanValidatorImpl;
import biz.netcentric.cq.tools.actool.validators.impl.AuthorizableValidatorImpl;

public class YamlConfigurationMerger implements ConfigurationMerger {
    /*
     * (non-Javadoc)
     * 
     * @see biz.netcentric.cq.tools.actool.configReader.ConfigurationMeger#
     * getMergedConfigurations(java.util.Map,
     * biz.netcentric.cq.tools.actool.installationhistory
     * .AcInstallationHistoryPojo,
     * biz.netcentric.cq.tools.actool.configReader.ConfigReader)
     */
    @Override
    public List getMergedConfigurations(
            final Map<String, String> newestConfigurations,
            final AcInstallationHistoryPojo history,
            final ConfigReader configReader) throws RepositoryException,
            AcConfigBeanValidationException {
        List c = new ArrayList<Map>();
        Map<String, LinkedHashSet<AuthorizableConfigBean>> mergedAuthorizablesMapfromConfig = new LinkedHashMap<String, LinkedHashSet<AuthorizableConfigBean>>();
        Map<String, Set<AceBean>> mergedAceMapFromConfig = new LinkedHashMap<String, Set<AceBean>>();
        Set<String> groupIdsFromAllConfig = new HashSet<String>(); // needed for
                                                                   // detection
                                                                   // of doubled
                                                                   // defined
                                                                   // groups in
                                                                   // configurations
        Map<String, String> mergedTemplateConfigs = new HashMap<String, String>();
        ConfigurationsValidator configurationsValidator = new YamlConfigurationsValidator();

        for (Map.Entry<String, String> entry : newestConfigurations.entrySet()) {
            String message = "start merging configuration data from: "
                    + entry.getKey();

            configurationsValidator
                    .validateMandatorySectionIdentifiersExistence(
                            entry.getValue(), entry.getKey());

            history.addMessage(message);
            Yaml yaml = new Yaml();
            List<LinkedHashMap> yamlList = (List<LinkedHashMap>) yaml
                    .load(entry.getValue());

            Set<String> sectionIdentifiers = new LinkedHashSet<String>();

            // FIXME: Why doesn't this use YamlConfigReader?
            // put all section identifiers of current configuration into a set
            for (int i = 0; i < yamlList.size(); i++) {
                sectionIdentifiers.addAll(yamlList.get(i).keySet());
            }
            configurationsValidator.validateSectionIdentifiers(
                    sectionIdentifiers, entry.getKey());

            configurationsValidator.validateSectionContentExistence(
                    entry.getKey(), yamlList);

            // build AuthorizableConfigBeans from current configurations
            AuthorizableValidator authorizableValidator = new AuthorizableValidatorImpl();
            Map<String, LinkedHashSet<AuthorizableConfigBean>> authorizablesMapfromConfig = configReader
                    .getGroupConfigurationBeans(yamlList, authorizableValidator);
            Set<String> groupIdsFromCurrentConfig = authorizablesMapfromConfig
                    .keySet();

            configurationsValidator.validateDoubleGroups(groupIdsFromAllConfig,
                    groupIdsFromCurrentConfig, entry.getKey());

            // add IDs from authorizables from current configuration to set
            groupIdsFromAllConfig.addAll(groupIdsFromCurrentConfig);

            // build AceBeans from current configuration
            AceBeanValidator aceBeanValidator = new AceBeanValidatorImpl(
                    groupIdsFromCurrentConfig);
            Map<String, Set<AceBean>> aceMapFromConfig = configReader
                    .getAceConfigurationBeans(yamlList,
                            groupIdsFromCurrentConfig, aceBeanValidator);

            // add AuthorizableConfigBeans built from current configuration to
            // set containing AuthorizableConfigBeans from all configurations
            mergedAuthorizablesMapfromConfig.putAll(authorizablesMapfromConfig);

            // add AceBeans built from current configuration to set containing
            // AceBeans from all configurations
            mergedAceMapFromConfig.putAll(aceMapFromConfig);
            
            // Add page creation template configs
            YamlConfigReader yamlConfigReader = new YamlConfigReader();
            Map<String, String> mappings = yamlConfigReader.getTemplateConfiguration(yamlList);
            if (mappings != null) {
                mergedTemplateConfigs.putAll(mappings);
            }
        }
        c.add(mergedAuthorizablesMapfromConfig);
        c.add(mergedAceMapFromConfig);
        c.add(mergedTemplateConfigs);
        return c;
    }
}
