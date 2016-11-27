package biz.netcentric.cq.tools.actool.validators;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.mockito.MockitoAnnotations.initMocks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.security.AccessControlList;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.AccessControlPolicy;

import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.sling.jcr.api.SlingRepository;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.configreader.ConfigReader;
import biz.netcentric.cq.tools.actool.configreader.YamlConfigReader;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;
import biz.netcentric.cq.tools.actool.validators.impl.AceBeanValidatorImpl;
import biz.netcentric.cq.tools.actool.validators.impl.AuthorizableValidatorImpl;

/** Contains unit tests checking support of different restrictions
 * 
 * @author jochenkoschorkej */
public class RestrictionValidationTest {

    @Mock
    SlingRepository repository;

    @Mock
    Session session;

    @Mock
    AccessControlList accessControlPolicy;

    @Mock
    AccessControlManager accessControlManager;

    @InjectMocks
    ConfigReader yamlConfigReader = new YamlConfigReader();

    List<LinkedHashMap> aclList;
    Set<String> groupsFromConfig;
    List<AceBean> aceBeanList = new ArrayList<AceBean>();
    List<AuthorizableConfigBean> authorizableBeanList = new ArrayList<AuthorizableConfigBean>();

    @Before
    public void setup() throws IOException, RepositoryException,
            AcConfigBeanValidationException {

        initMocks(this);
        doReturn(session).when(repository).loginAdministrative(null);

        accessControlPolicy = mock(AccessControlList.class,
                withSettings().extraInterfaces(JackrabbitAccessControlList.class));

        doReturn(accessControlManager).when(session).getAccessControlManager();
        doReturn(new AccessControlPolicy[] { accessControlPolicy }).when(accessControlManager).getPolicies("/");

        doThrow(new RepositoryException("invalid permission")).when(accessControlManager).privilegeFromName("read");
        doThrow(new RepositoryException("invalid permission")).when(accessControlManager).privilegeFromName("jcr_all");
    }

    private void setupBeansFromTestYaml(final String path) throws IOException, AcConfigBeanValidationException, RepositoryException {
        final List<LinkedHashMap> yamlList = ValidatorTestHelper.getYamlList(path);
        final AuthorizableValidator authorizableValidator = new AuthorizableValidatorImpl("/home/groups", "/home/users");
        authorizableValidator.disable();
        groupsFromConfig = yamlConfigReader.getGroupConfigurationBeans(
                yamlList, authorizableValidator).keySet();
        ValidatorTestHelper.createAuthorizableTestBeans(yamlList, yamlConfigReader, authorizableBeanList);
        ValidatorTestHelper.createAceTestBeans(yamlList, yamlConfigReader, groupsFromConfig, aceBeanList);
    }

    @Test
    public void testAceBeansOnlyRepGlobRestrictionSupported() throws IOException, AcConfigBeanValidationException, RepositoryException {
        doReturn(new String[] { "rep:glob" }).when((JackrabbitAccessControlList) accessControlPolicy).getRestrictionNames();
        setupBeansFromTestYaml("testRestrictionsConfigs/test-restrictions1.yaml");
        testExceptions();
    }

    @Test
    public void testAceBeansOnlyNtNamesRestrictionSupported() throws IOException, AcConfigBeanValidationException, RepositoryException {
        setupBeansFromTestYaml("testRestrictionsConfigs/test-restrictions2.yaml");
        doReturn(new String[] { "rep:ntNames" }).when((JackrabbitAccessControlList) accessControlPolicy).getRestrictionNames();
        testExceptions();
    }

    @Test
    public void testAceBeansAllRestrictionsSupported() throws IOException, AcConfigBeanValidationException, RepositoryException {
        setupBeansFromTestYaml("testRestrictionsConfigs/test-restrictions3.yaml");
        doReturn(new String[] { "rep:ntNames", "rep:glob", "rep:prefixes" }).when((JackrabbitAccessControlList) accessControlPolicy)
                .getRestrictionNames();
        testExceptions();
    }

    private void testExceptions() {
        final AceBeanValidator aceBeanValidator = new AceBeanValidatorImpl(
                groupsFromConfig);
        for (final AceBean aceBean : aceBeanList) {
            assertEquals("Problem in bean " + aceBean, aceBean.getAssertedExceptionString(),
                    ValidatorTestHelper.getSimpleValidationException(aceBean, aceBeanValidator, accessControlManager));
        }
    }

}
