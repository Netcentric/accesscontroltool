/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.validators;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.configreader.ConfigReader;
import biz.netcentric.cq.tools.actool.configreader.TestAceBean;
import biz.netcentric.cq.tools.actool.configreader.TestAuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.configreader.TestYamlConfigReader;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;
import biz.netcentric.cq.tools.actool.validators.impl.AceBeanValidatorImpl;
import biz.netcentric.cq.tools.actool.validators.impl.AuthorizableValidatorImpl;

public class BeanValidatorsTest {

    @Mock
    SlingRepository repository;

    @Mock
    Session session;

    @Mock
    AccessControlList accessControlPolicy;

    @Mock
    AccessControlManager accessControlManager;

    @Mock
    AuthorizableValidator authorizableValidator;

    @InjectMocks
    ConfigReader yamlConfigReader = new TestYamlConfigReader();

    List<LinkedHashMap> aclList;
    Set<String> groupsFromConfig;
    List<AceBean> aceBeanList = new ArrayList<AceBean>();
    List<AuthorizableConfigBean> authorizableBeanList = new ArrayList<AuthorizableConfigBean>();

    @BeforeEach
    public void setup() throws IOException, RepositoryException,
            AcConfigBeanValidationException {

        initMocks(this);
        doReturn(session).when(repository).loginService(null, null);

        accessControlPolicy = mock(AccessControlList.class,
                withSettings().extraInterfaces(JackrabbitAccessControlList.class));
        doReturn(new String[]{"rep:glob"}).when((JackrabbitAccessControlList) accessControlPolicy).getRestrictionNames();
        doReturn(accessControlManager).when(session).getAccessControlManager();
        doReturn(new AccessControlPolicy[]{accessControlPolicy}).when(accessControlManager).getPolicies("/");

        doThrow(new RepositoryException("invalid permission")).when(accessControlManager).privilegeFromName("read");
        doThrow(new RepositoryException("invalid permission")).when(accessControlManager).privilegeFromName("jcr_all");

        final List<LinkedHashMap> yamlList = ValidatorTestHelper.getYamlList("testconfig.yaml");
        final AuthorizableValidator authorizableValidator = new AuthorizableValidatorImpl("/home/groups", "/home/users");
        authorizableValidator.disable();
        groupsFromConfig = yamlConfigReader.getGroupConfigurationBeans(yamlList, authorizableValidator).getAuthorizableIds();

        ValidatorTestHelper.createAuthorizableTestBeans(yamlList, yamlConfigReader, authorizableBeanList);
        ValidatorTestHelper.createAceTestBeans(yamlList, yamlConfigReader, groupsFromConfig, aceBeanList, session);
    }

    @Test
    public void testAuthorizableBeans() {
        final AuthorizableValidator authorizableValidator = new AuthorizableValidatorImpl("/home/groups", "/home/users");
        for (final AuthorizableConfigBean authorizableBean : authorizableBeanList) {
            assertEquals(
                    ValidatorTestHelper.getSimpleValidationException(authorizableBean,
                            authorizableValidator),
                    ((TestAuthorizableConfigBean) authorizableBean).getAssertedExceptionString());
        }
    }

    @Test
    public void testAceBeans() {
        final AceBeanValidator aceBeanValidator = new AceBeanValidatorImpl(groupsFromConfig);
        for (final AceBean aceBean : aceBeanList) {
            assertEquals(
                    ((TestAceBean) aceBean).getAssertedExceptionString(),
                    ValidatorTestHelper.getSimpleValidationException(aceBean, aceBeanValidator, accessControlManager),
                    "Problem in bean " + aceBean);
        }
    }

}
