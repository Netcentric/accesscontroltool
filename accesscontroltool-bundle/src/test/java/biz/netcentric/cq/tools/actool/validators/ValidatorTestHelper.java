package biz.netcentric.cq.tools.actool.validators;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.jcr.security.AccessControlManager;

import org.apache.commons.io.IOUtils;
import org.yaml.snakeyaml.Yaml;

import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.configreader.ConfigReader;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;
import biz.netcentric.cq.tools.actool.validators.impl.AceBeanValidatorImpl;
import biz.netcentric.cq.tools.actool.validators.impl.AuthorizableValidatorImpl;
/**
 * Helper class containing static methods used in validator-related unit tests based on test yaml files
 * @author jochenkoschorkej
 *
 */
public class ValidatorTestHelper {

    private ValidatorTestHelper(){}

    static void createAuthorizableTestBeans(final List<LinkedHashMap> yamlList, ConfigReader yamlConfigReader, List<AuthorizableConfigBean> authorizableBeanList)
            throws AcConfigBeanValidationException {
        final AuthorizableValidator authorizableValidator = new AuthorizableValidatorImpl();
        authorizableValidator.disable();
        final Map<String, Set<AuthorizableConfigBean>> authorizablesMap = yamlConfigReader
                .getGroupConfigurationBeans(yamlList, authorizableValidator);
        for (final Entry<String, Set<AuthorizableConfigBean>> authorizableEntrySet : authorizablesMap
                .entrySet()) {
            authorizableBeanList.addAll(authorizableEntrySet.getValue());
        }
    }
    static String getTestConfigAsString(final String resourceName)
            throws IOException {
        final ClassLoader classloader = Thread.currentThread()
                .getContextClassLoader();
        final InputStream is = classloader.getResourceAsStream(resourceName);

        final StringWriter stringWriter = new StringWriter();
        IOUtils.copy(is, stringWriter, "UTF-8");
        return stringWriter.toString();
    }

    static void createAceTestBeans(final List<LinkedHashMap> yamlList, ConfigReader yamlConfigReader, Set<String> groupsFromConfig, List<AceBean> aceBeanList)
            throws RepositoryException, AcConfigBeanValidationException {

        final AceBeanValidator aceBeanValidator = new AceBeanValidatorImpl(
                groupsFromConfig);

        // disable validator since we want to put all data from test-config
        // (regardless if valid or not) into beans for following tests
        // otherwise reading of test-config would be aborted after the first
        // exception
        aceBeanValidator.disable();

        final Map<String, Set<AceBean>> aceMap = yamlConfigReader
                .getAceConfigurationBeans(yamlList, groupsFromConfig,
                        aceBeanValidator);

        for (final Entry<String, Set<AceBean>> aceMapEntrySet : aceMap.entrySet()) {
            aceBeanList.addAll(aceMapEntrySet.getValue());
        }
    }

    static List<LinkedHashMap> getYamlList(final String path) throws IOException {
        final String configString = ValidatorTestHelper.getTestConfigAsString(path);

        final Yaml yaml = new Yaml();
        final List<LinkedHashMap> yamlList = (List<LinkedHashMap>) yaml
                .load(configString);
        return yamlList;
    }

    static String getSimpleValidationException(final AceBean aceBean,
            final AceBeanValidator aceBeanValidator, final AccessControlManager accessControlManager) {
        try {
            aceBeanValidator.validate(aceBean, accessControlManager);
        } catch (final Exception e) {
            return e.getClass().getSimpleName();
        }
        return "";
    }

    static String getSimpleValidationException(
            final AuthorizableConfigBean authorizableConfigBean,
            final AuthorizableValidator authorizableValidator) {
        try {
            authorizableValidator.validate(authorizableConfigBean);
        } catch (final Exception e) {
            return e.getClass().getSimpleName();
        }
        return "";
    }
}
