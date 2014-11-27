package biz.netcentric.cq.tools.actool.validators;

import static org.junit.Assert.assertEquals;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.jcr.RepositoryException;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.configReader.ConfigReader;
import biz.netcentric.cq.tools.actool.configReader.YamlConfigReader;
import biz.netcentric.cq.tools.actool.helper.AceBean;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;

public class BeanValidatorsTest {
    List<LinkedHashMap> aclList;
    Set<String> groupsFromConfig;
    List<AceBean> aceBeanList = new ArrayList<AceBean>();
    List<AuthorizableConfigBean> authorizableBeanList = new ArrayList<AuthorizableConfigBean>();
    ConfigReader yamlConfigReader = null;

    @Before
    public void setup() throws IOException, RepositoryException,
            AcConfigBeanValidationException {
        yamlConfigReader = new YamlConfigReader();
        List<LinkedHashMap> yamlList = getYamlList();
        AuthorizableValidator authorizableValidator = new AuthorizableValidatorImpl();
        authorizableValidator.disable();
        groupsFromConfig = yamlConfigReader.getGroupConfigurationBeans(
                yamlList, authorizableValidator).keySet();
        createAuthorizableTestBeans(yamlList);
        createAceTestBeans(yamlList);
    }

    private List<LinkedHashMap> getYamlList() throws IOException {
        String configString = getTestConfigAsString("testconfig.yaml");

        Yaml yaml = new Yaml();
        List<LinkedHashMap> yamlList = (List<LinkedHashMap>) yaml
                .load(configString);
        return yamlList;
    }

    private String getTestConfigAsString(final String resourceName)
            throws IOException {
        ClassLoader classloader = Thread.currentThread()
                .getContextClassLoader();
        InputStream is = classloader.getResourceAsStream(resourceName);

        StringWriter stringWriter = new StringWriter();
        IOUtils.copy(is, stringWriter, "UTF-8");
        return stringWriter.toString();
    }

    private void createAceTestBeans(final List<LinkedHashMap> yamlList)
            throws RepositoryException, AcConfigBeanValidationException {

        AceBeanValidator aceBeanValidator = new AceBeanValidatorImpl(
                this.groupsFromConfig);

        // disable validator since we want to put all data from test-config
        // (regardless if valid or not) into beans for following tests
        // otherwise reading of test-config would be aborted after the first
        // exception
        aceBeanValidator.disable();

        Map<String, Set<AceBean>> aceMap = yamlConfigReader
                .getAceConfigurationBeans(yamlList, groupsFromConfig,
                        aceBeanValidator);

        for (Entry<String, Set<AceBean>> aceMapEntrySet : aceMap.entrySet()) {
            aceBeanList.addAll(aceMapEntrySet.getValue());
        }
    }

    private void createAuthorizableTestBeans(final List<LinkedHashMap> yamlList)
            throws AcConfigBeanValidationException {
        AuthorizableValidator authorizableValidator = new AuthorizableValidatorImpl();
        authorizableValidator.disable();
        Map<String, LinkedHashSet<AuthorizableConfigBean>> authorizablesMap = yamlConfigReader
                .getGroupConfigurationBeans(yamlList, authorizableValidator);
        for (Entry<String, LinkedHashSet<AuthorizableConfigBean>> authorizableEntrySet : authorizablesMap
                .entrySet()) {
            authorizableBeanList.addAll(authorizableEntrySet.getValue());
        }
    }

    @Test
    public void testAuthorizableBeans() {
        AuthorizableValidator authorizableValidator = new AuthorizableValidatorImpl();
        for (AuthorizableConfigBean authorizableBean : this.authorizableBeanList) {
            assertEquals(
                    getSimpleValidationException(authorizableBean,
                            authorizableValidator),
                    authorizableBean.getAssertedExceptionString());
        }
    }

    @Test
    public void testAceBeans() {
        AceBeanValidator aceBeanValidator = new AceBeanValidatorImpl(
                this.groupsFromConfig);
        for (AceBean aceBean : this.aceBeanList) {
            assertEquals(
                    getSimpleValidationException(aceBean, aceBeanValidator),
                    aceBean.getAssertedExceptionString());
        }
    }

    private String getSimpleValidationException(final AceBean aceBean,
            final AceBeanValidator aceBeanValidator) {
        try {
            aceBeanValidator.validate(aceBean);
        } catch (Exception e) {
            return e.getClass().getSimpleName();
        }
        return "";
    }

    private String getSimpleValidationException(
            final AuthorizableConfigBean authorizableConfigBean,
            final AuthorizableValidator authorizableValidator) {
        try {
            authorizableValidator.validate(authorizableConfigBean);
        } catch (Exception e) {
            return e.getClass().getSimpleName();
        }
        return "";
    }
}
