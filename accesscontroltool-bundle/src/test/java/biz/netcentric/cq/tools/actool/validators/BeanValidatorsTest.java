/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.validators;

import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.configreader.ConfigReader;
import biz.netcentric.cq.tools.actool.configreader.TestAceBean;
import biz.netcentric.cq.tools.actool.configreader.TestAuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.configreader.TestYamlConfigReader;
import biz.netcentric.cq.tools.actool.session.SessionManager;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;
import biz.netcentric.cq.tools.actool.validators.impl.AceBeanValidatorImpl;
import biz.netcentric.cq.tools.actool.validators.impl.AuthorizableValidatorImpl;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.security.AccessControlList;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.AccessControlPolicy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class BeanValidatorsTest {

    @Mock
    SessionManager sessionManager;

    @Mock
    Session session;

    @Mock
    AccessControlList accessControlPolicy;

    @Mock
    AccessControlManager accessControlManager;

    @InjectMocks
    ConfigReader yamlConfigReader = new TestYamlConfigReader();

    List<LinkedHashMap> aclList;
    Set<String> groupsFromConfig;
    List<AceBean> aceBeanList = new ArrayList<AceBean>();
    List<AuthorizableConfigBean> authorizableBeanList = new ArrayList<AuthorizableConfigBean>();

    @Before
    public void setup() throws IOException, RepositoryException,
            AcConfigBeanValidationException {

        initMocks(this);
        when(sessionManager.getSession()).thenReturn(session);
        accessControlPolicy = mock(AccessControlList.class,
                withSettings().extraInterfaces(JackrabbitAccessControlList.class));
        doReturn(new String[] { "rep:glob" }).when((JackrabbitAccessControlList) accessControlPolicy).getRestrictionNames();
        doReturn(accessControlManager).when(session).getAccessControlManager();
        doReturn(new AccessControlPolicy[] { accessControlPolicy }).when(accessControlManager).getPolicies("/");

        doThrow(new RepositoryException("invalid permission")).when(accessControlManager).privilegeFromName("read");
        doThrow(new RepositoryException("invalid permission")).when(accessControlManager).privilegeFromName("jcr_all");

        final List<LinkedHashMap> yamlList = ValidatorTestHelper.getYamlList("testconfig.yaml");
        final AuthorizableValidator authorizableValidator = new AuthorizableValidatorImpl("/home/groups", "/home/users");
        authorizableValidator.disable();
        groupsFromConfig = yamlConfigReader.getGroupConfigurationBeans(
                yamlList, authorizableValidator).keySet();

        ValidatorTestHelper.createAuthorizableTestBeans(yamlList, yamlConfigReader, authorizableBeanList);
        ValidatorTestHelper.createAceTestBeans(yamlList, yamlConfigReader, groupsFromConfig, aceBeanList);
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
        final AceBeanValidator aceBeanValidator = new AceBeanValidatorImpl(
                groupsFromConfig);
        for (final AceBean aceBean : aceBeanList) {
            assertEquals("Problem in bean " + aceBean, ((TestAceBean) aceBean).getAssertedExceptionString(),
                    ValidatorTestHelper.getSimpleValidationException(aceBean, aceBeanValidator,
                            accessControlManager));
        }
    }

}
