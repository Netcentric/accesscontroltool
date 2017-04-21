/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configreader;

import static biz.netcentric.cq.tools.actool.configreader.YamlConfigReaderTest.getYamlList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.doReturn;
import static org.mockito.MockitoAnnotations.initMocks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.configmodel.AcesConfig;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizablesConfig;
import biz.netcentric.cq.tools.actool.configmodel.GlobalConfiguration;
import biz.netcentric.cq.tools.actool.history.AcInstallationLog;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;

public class YamlMacroProcessorTest {

    @Mock
    YamlMacroChildNodeObjectsProviderImpl yamlMacroChildNodeObjectsProvider;

    @Mock
    AcInstallationLog installLog;

    @Mock
    Session session;

    @InjectMocks
    YamlMacroProcessorImpl yamlMacroProcessor = new YamlMacroProcessorImpl();

    @Before
    public void setup() {
        initMocks(this);
    }

    @Test
    public void testGroupLoop() throws IOException, AcConfigBeanValidationException, RepositoryException {

        List<LinkedHashMap> yamlList = getYamlList("test-loop.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, installLog, session);

        AuthorizablesConfig groups = readGroupConfigs(yamlList);
        assertEquals("Number of groups", 10, groups.size());
        assertEquals("Path of group", "/home/groups/BRAND1", groups.getAuthorizableConfig("content-BRAND1-reader").getPath());
        assertEquals("Path of group", "/home/groups/BRAND1", groups.getAuthorizableConfig("content-BRAND1-writer").getPath());
        assertEquals("Path of group", "/home/groups/BRAND2", groups.getAuthorizableConfig("content-BRAND2-reader").getPath());
        assertEquals("Path of group", "/home/groups/BRAND2", groups.getAuthorizableConfig("content-BRAND2-writer").getPath());
        assertEquals("Path of group", "/home/groups/BRAND3", groups.getAuthorizableConfig("content-BRAND3-reader").getPath());
        assertEquals("Path of group", "/home/groups/BRAND3", groups.getAuthorizableConfig("content-BRAND3-writer").getPath());

        assertEquals("Path of group", "/home/groups/VAR1", groups.getAuthorizableConfig("content-VAR1").getPath());
        assertEquals("Path of group", "/home/groups/VAR2", groups.getAuthorizableConfig("content-VAR2").getPath());
        assertEquals("Path of group", "/home/groups/VAR3", groups.getAuthorizableConfig("content-VAR3").getPath());

    }

    @Test
    public void testNestedGroupLoop() throws IOException, AcConfigBeanValidationException, RepositoryException {
        final ConfigReader yamlConfigReader = new YamlConfigReader();
        List<LinkedHashMap> yamlList = getYamlList("test-nested-loops.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, installLog, session);

        AuthorizablesConfig groups = readGroupConfigs(yamlList);

        assertEquals("Number of groups", 13, groups.size());
        assertEquals("Path of group", "/home/groups/BRAND1", groups.getAuthorizableConfig("content-BRAND1-reader").getPath());
        assertEquals("Path of group", "/home/groups/BRAND1/MKT1", groups.getAuthorizableConfig("content-BRAND1-MKT1-writer").getPath());
    }

    @Test
    public void testAceLoop() throws IOException, AcConfigBeanValidationException, RepositoryException {

        List<LinkedHashMap> yamlList = getYamlList("test-loop.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, installLog, session);

        AcesConfig aces = readAceConfigs(yamlList);
        assertEquals("Number of ACEs", 7, aces.size());
        final Set<AceBean> group1 = aces.filterByPrincipalName("content-BRAND-MKT1-reader");
        assertEquals("Number of ACEs for groupA", 1, group1.size());
        final Set<AceBean> group2 = aces.filterByPrincipalName("content-BRAND-MKT2-writer");
        assertEquals("Number of ACEs for groupB", 2, group2.size());
    }

    /** @see <a href=
     *      "https://github.com/Netcentric/accesscontroltool/issues/14">https://github.com/Netcentric/accesscontroltool/issues/14</a> */
    @Test
    public void testAceLoopWithHyphen() throws IOException, AcConfigBeanValidationException, RepositoryException {

        List<LinkedHashMap> yamlList = getYamlList("test-loop-with-hyphen.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, installLog, session);

        AcesConfig aces = readAceConfigs(yamlList);
        assertEquals("Number of ACEs", 7, aces.size());
        final Set<AceBean> group1 = aces.filterByPrincipalName("content-BRAND-MKT-1-reader");
        assertEquals("Number of ACEs for groupA", 1, group1.size());
        final Set<AceBean> group2 = aces.filterByPrincipalName("content-BRAND-MKT-2-writer");
        assertEquals("Number of ACEs for groupB", 2, group2.size());
    }

    @Test
    public void testNestedLoop() throws IOException, AcConfigBeanValidationException, RepositoryException {

        List<LinkedHashMap> yamlList = getYamlList("test-nested-loops.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, installLog, session);

        AcesConfig aces = readAceConfigs(yamlList);
        assertEquals("Number of ACEs", 12, aces.size());
        assertFalse(aces.filterByPrincipalName("content-BRAND1-reader").isEmpty());
        assertFalse(aces.filterByPrincipalName("content-BRAND1-writer").isEmpty());
        assertFalse(aces.filterByPrincipalName("content-BRAND2-reader").isEmpty());
        assertFalse(aces.filterByPrincipalName("content-BRAND2-writer").isEmpty());
        assertFalse(aces.filterByPrincipalName("content-BRAND1-MKT1-reader").isEmpty());
        assertFalse(aces.filterByPrincipalName("content-BRAND1-MKT1-writer").isEmpty());
        assertFalse(aces.filterByPrincipalName("content-BRAND2-MKT1-reader").isEmpty());
        assertFalse(aces.filterByPrincipalName("content-BRAND2-MKT1-writer").isEmpty());
        assertFalse(aces.filterByPrincipalName("content-BRAND1-MKT2-reader").isEmpty());
        assertFalse(aces.filterByPrincipalName("content-BRAND1-MKT2-writer").isEmpty());
        assertFalse(aces.filterByPrincipalName("content-BRAND2-MKT2-reader").isEmpty());
        assertFalse(aces.filterByPrincipalName("content-BRAND2-MKT2-writer").isEmpty());
        AceBean b1m1r = aces.filterByPrincipalName("content-BRAND1-MKT1-reader").iterator().next();
        assertEquals("JCR path", "/content/BRAND1/MKT1", b1m1r.getJcrPath());
    }

    @Test
    public void testIf() throws IOException, AcConfigBeanValidationException, RepositoryException {

        List<LinkedHashMap> yamlList = getYamlList("test-if.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, installLog, session);

        AcesConfig aces = readAceConfigs(yamlList);

        assertEquals("Number of Groups in ACE Section", 3, aces.size());
        final Set<AceBean> group1 = aces.filterByPrincipalName("content-reader");
        assertEquals("Number of ACEs for groupA", 1, group1.size());
        final Set<AceBean> group2 = aces.filterByPrincipalName("content-writer");
        assertEquals("Number of ACEs for groupB", 2, group2.size());

        AuthorizablesConfig groups = readGroupConfigs(yamlList);
        assertEquals("Number of groups", 1, groups.size());
        assertEquals("Path of group", "/home/groups/folder2", groups.getAuthorizableConfig("content-writer").getPath());

    }

    @Test
    public void testLoopChildrenOf() throws IOException, AcConfigBeanValidationException, RepositoryException {

        List<LinkedHashMap> yamlList = getYamlList("test-loop-children-of.yaml");

        String contentLocationChildrenFromYamlFile = "/content/test";

        doReturn(getExampleValuesForLoopOverChildrenOfPath()).when(yamlMacroChildNodeObjectsProvider)
                .getValuesForPath(contentLocationChildrenFromYamlFile, installLog, session);

        yamlList = yamlMacroProcessor.processMacros(yamlList, installLog, session);

        // new Yaml().dump(yamlList, new PrintWriter(System.out));

        AuthorizablesConfig groups = readGroupConfigs(yamlList);
        assertEquals(4, groups.size());

        AuthorizableConfigBean group1 = groups.getAuthorizableConfig("content-node1-reader");
        assertEquals("/home/groups/test", group1.getPath());
        assertEquals("Jcr Content Property in Name val1", group1.getName());

        AuthorizableConfigBean group2 = groups.getAuthorizableConfig("content-node1-writer");
        assertEquals("/home/groups/test", group2.getPath());
        assertEquals("Writer of Node 1", group2.getName());

    }

    private List<Object> getExampleValuesForLoopOverChildrenOfPath() {

        List<Object> result = new ArrayList<Object>();
        Map<String, Object> test1 = new HashMap<String, Object>() {
            {
                put("path", "/content/test/node1");
                put("name", "node1");
                put("title", "Node 1");
                put("jcr:content", new HashMap<String, Object>() {
                    {
                        put("jcr:title", "testJcrTitle");
                        put("prop", "val1");
                    }
                });
            }
        };
        result.add(test1);

        Map<String, Object> test2 = new HashMap<String, Object>() {
            {
                put("path", "/content/test/node2");
                put("name", "node2");
                put("title", "Node 2");
                put("jcr:content", new HashMap<String, Object>() {
                    {
                        put("jcr:title", "testJcrTitle");
                        put("prop", "val2");
                    }
                });
            }
        };
        result.add(test2);

        return result;
    }

    @Test
    public void testSystemUser() throws IOException, AcConfigBeanValidationException, RepositoryException {

        List<LinkedHashMap> yamlList = getYamlList("test-system-user.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, installLog, session);

        AuthorizablesConfig users = readUserConfigs(yamlList);
        assertEquals("Number of users", 1, users.size());
        AuthorizableConfigBean user = users.getAuthorizableConfig("test-system-user");
        assertEquals(user.isSystemUser(), true);
        assertEquals(user.getName(), "Test System User");
    }

    // --- using YamlConfigReader to make assertions easier (the raw yaml structure makes it really hard)

    private AcesConfig readAceConfigs(final List<LinkedHashMap> yamlList)
            throws RepositoryException, AcConfigBeanValidationException {
        AuthorizablesConfig groups = readGroupConfigs(yamlList);
        return new YamlConfigReader().getAceConfigurationBeans(yamlList, null, session);
    }

    private AuthorizablesConfig readGroupConfigs(List<LinkedHashMap> yamlList) throws AcConfigBeanValidationException {
        return new YamlConfigReader().getGroupConfigurationBeans(yamlList, null);
    }

    private GlobalConfiguration readGlobalConfig(List<LinkedHashMap> yamlList) throws AcConfigBeanValidationException {
        return new YamlConfigReader().getGlobalConfiguration(yamlList);
    }

    private AuthorizablesConfig readUserConfigs(List<LinkedHashMap> yamlList)
            throws AcConfigBeanValidationException {
        return new YamlConfigReader().getUserConfigurationBeans(yamlList, null);
    }

    @Test
    public void testVariables() throws Exception {

        List<LinkedHashMap> yamlList = getYamlList("test-variables.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, installLog, session);

        GlobalConfiguration globalConfiguration = readGlobalConfig(yamlList);
        AuthorizablesConfig groups = readGroupConfigs(yamlList);
        assertEquals("var2 and var1", groups.getAuthorizableConfig("testGroup").getName());
        assertEquals("var3 and var1", groups.getAuthorizableConfig("testGroup").getDescription());

        assertEquals("var1-BRAND1", groups.getAuthorizableConfig("testGroupNested-BRAND1").getName());
        assertEquals("var1-BRAND2", groups.getAuthorizableConfig("testGroupNested-BRAND2").getName());
        assertEquals("var1-BRAND3", groups.getAuthorizableConfig("testGroupNested-BRAND3").getName());

    }

    @Test
    public void testVariableForLdap() throws Exception {

        List<LinkedHashMap> yamlList = getYamlList("test-variables-ldap.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, installLog, session);

        GlobalConfiguration globalConfiguration = readGlobalConfig(yamlList);
        AuthorizablesConfig groups = readGroupConfigs(yamlList);
        assertEquals("cn=editor-group,ou=mydepart,ou=Groups,dc=comp,dc=com;IDPNAME",
                groups.getAuthorizableConfig("aem-only-editor-group").getExternalId());

    }

}
