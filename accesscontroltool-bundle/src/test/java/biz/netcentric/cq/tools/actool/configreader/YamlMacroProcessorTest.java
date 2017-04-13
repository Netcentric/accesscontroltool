/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configreader;

import static biz.netcentric.cq.tools.actool.configreader.YamlConfigReaderTest.filterList;
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
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.configmodel.GlobalConfiguration;
import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;

public class YamlMacroProcessorTest {

    @Mock
    YamlMacroChildNodeObjectsProviderImpl yamlMacroChildNodeObjectsProvider;

    @Mock
    AcInstallationHistoryPojo acInstallationHistoryPojo;

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

        yamlList = yamlMacroProcessor.processMacros(yamlList, acInstallationHistoryPojo, session);

        Map<String, Set<AuthorizableConfigBean>> groups = readGroupConfigs(yamlList);
        assertEquals("Number of groups", 10, groups.size());
        assertEquals("Path of group", "/home/groups/BRAND1", groups.get("content-BRAND1-reader").iterator().next().getPath());
        assertEquals("Path of group", "/home/groups/BRAND1", groups.get("content-BRAND1-writer").iterator().next().getPath());
        assertEquals("Path of group", "/home/groups/BRAND2", groups.get("content-BRAND2-reader").iterator().next().getPath());
        assertEquals("Path of group", "/home/groups/BRAND2", groups.get("content-BRAND2-writer").iterator().next().getPath());
        assertEquals("Path of group", "/home/groups/BRAND3", groups.get("content-BRAND3-reader").iterator().next().getPath());
        assertEquals("Path of group", "/home/groups/BRAND3", groups.get("content-BRAND3-writer").iterator().next().getPath());

        assertEquals("Path of group", "/home/groups/VAR1", groups.get("content-VAR1").iterator().next().getPath());
        assertEquals("Path of group", "/home/groups/VAR2", groups.get("content-VAR2").iterator().next().getPath());
        assertEquals("Path of group", "/home/groups/VAR3", groups.get("content-VAR3").iterator().next().getPath());

    }

    @Test
    public void testNestedGroupLoop() throws IOException, AcConfigBeanValidationException, RepositoryException {
        final ConfigReader yamlConfigReader = new YamlConfigReader();
        List<LinkedHashMap> yamlList = getYamlList("test-nested-loops.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, acInstallationHistoryPojo, session);

        final Map<String, Set<AuthorizableConfigBean>> groups = readGroupConfigs(yamlList);

        assertEquals("Number of groups", 13, groups.size());
        assertEquals("Path of group", "/home/groups/BRAND1", groups.get("content-BRAND1-reader").iterator().next().getPath());
        assertEquals("Path of group", "/home/groups/BRAND1/MKT1", groups.get("content-BRAND1-MKT1-writer").iterator().next().getPath());
    }

    @Test
    public void testAceLoop() throws IOException, AcConfigBeanValidationException, RepositoryException {

        List<LinkedHashMap> yamlList = getYamlList("test-loop.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, acInstallationHistoryPojo, session);

        final Set<AceBean> aces = readAceConfigs(yamlList);
        assertEquals("Number of ACEs", 7, aces.size());
        final Set<AceBean> group1 = filterList(aces, "content-BRAND-MKT1-reader");
        assertEquals("Number of ACEs for groupA", 1, group1.size());
        final Set<AceBean> group2 = filterList(aces, "content-BRAND-MKT2-writer");
        assertEquals("Number of ACEs for groupB", 2, group2.size());
    }

    /** @see <a href=
     *      "https://github.com/Netcentric/accesscontroltool/issues/14">https://github.com/Netcentric/accesscontroltool/issues/14</a> */
    @Test
    public void testAceLoopWithHyphen() throws IOException, AcConfigBeanValidationException, RepositoryException {

        List<LinkedHashMap> yamlList = getYamlList("test-loop-with-hyphen.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, acInstallationHistoryPojo, session);

        Set<AceBean> aces = readAceConfigs(yamlList);
        assertEquals("Number of ACEs", 7, aces.size());
        final Set<AceBean> group1 = filterList(aces, "content-BRAND-MKT-1-reader");
        assertEquals("Number of ACEs for groupA", 1, group1.size());
        final Set<AceBean> group2 = filterList(aces, "content-BRAND-MKT-2-writer");
        assertEquals("Number of ACEs for groupB", 2, group2.size());
    }

    @Test
    public void testNestedLoop() throws IOException, AcConfigBeanValidationException, RepositoryException {

        List<LinkedHashMap> yamlList = getYamlList("test-nested-loops.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, acInstallationHistoryPojo, session);

        Set<AceBean> aces = readAceConfigs(yamlList);
        assertEquals("Number of ACEs", 12, aces.size());
        assertFalse(filterList(aces, "content-BRAND1-reader").isEmpty());
        assertFalse(filterList(aces, "content-BRAND1-writer").isEmpty());
        assertFalse(filterList(aces, "content-BRAND2-reader").isEmpty());
        assertFalse(filterList(aces, "content-BRAND2-writer").isEmpty());
        assertFalse(filterList(aces, "content-BRAND1-MKT1-reader").isEmpty());
        assertFalse(filterList(aces, "content-BRAND1-MKT1-writer").isEmpty());
        assertFalse(filterList(aces, "content-BRAND2-MKT1-reader").isEmpty());
        assertFalse(filterList(aces, "content-BRAND2-MKT1-writer").isEmpty());
        assertFalse(filterList(aces, "content-BRAND1-MKT2-reader").isEmpty());
        assertFalse(filterList(aces, "content-BRAND1-MKT2-writer").isEmpty());
        assertFalse(filterList(aces, "content-BRAND2-MKT2-reader").isEmpty());
        assertFalse(filterList(aces, "content-BRAND2-MKT2-writer").isEmpty());
        final AceBean b1m1r = filterList(aces, "content-BRAND1-MKT1-reader").iterator().next();
        assertEquals("JCR path", "/content/BRAND1/MKT1", b1m1r.getJcrPath());
    }

    @Test
    public void testIf() throws IOException, AcConfigBeanValidationException, RepositoryException {

        List<LinkedHashMap> yamlList = getYamlList("test-if.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, acInstallationHistoryPojo, session);

        final Set<AceBean> aces = readAceConfigs(yamlList);

        assertEquals("Number of Groups in ACE Section", 3, aces.size());
        final Set<AceBean> group1 = filterList(aces, "content-reader");
        assertEquals("Number of ACEs for groupA", 1, group1.size());
        final Set<AceBean> group2 = filterList(aces, "content-writer");
        assertEquals("Number of ACEs for groupB", 2, group2.size());

        final Map<String, Set<AuthorizableConfigBean>> groups = readGroupConfigs(yamlList);
        assertEquals("Number of groups", 1, groups.size());
        assertEquals("Path of group", "/home/groups/folder2", groups.get("content-writer").iterator().next().getPath());

    }

    @Test
    public void testLoopChildrenOf() throws IOException, AcConfigBeanValidationException, RepositoryException {

        List<LinkedHashMap> yamlList = getYamlList("test-loop-children-of.yaml");

        String contentLocationChildrenFromYamlFile = "/content/test";

        doReturn(getExampleValuesForLoopOverChildrenOfPath()).when(yamlMacroChildNodeObjectsProvider)
                .getValuesForPath(contentLocationChildrenFromYamlFile, acInstallationHistoryPojo, session);

        yamlList = yamlMacroProcessor.processMacros(yamlList, acInstallationHistoryPojo, session);

        // new Yaml().dump(yamlList, new PrintWriter(System.out));

        final Map<String, Set<AuthorizableConfigBean>> groups = readGroupConfigs(yamlList);
        assertEquals(4, groups.size());

        AuthorizableConfigBean group1 = groups.get("content-node1-reader").iterator().next();
        assertEquals("/home/groups/test", group1.getPath());
        assertEquals("Jcr Content Property in Name val1", group1.getName());

        AuthorizableConfigBean group2 = groups.get("content-node1-writer").iterator().next();
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

        yamlList = yamlMacroProcessor.processMacros(yamlList, acInstallationHistoryPojo, session);

        Map<String, Set<AuthorizableConfigBean>> users = readUserConfigs(yamlList);
        assertEquals("Number of users", 1, users.size());
        AuthorizableConfigBean user = users.get("test-system-user").iterator().next();
        assertEquals(user.isSystemUser(), true);
        assertEquals(user.getName(), "Test System User");
    }

    // --- using YamlConfigReader to make assertions easier (the raw yaml structure makes it really hard)

    private Set<AceBean> readAceConfigs(final List<LinkedHashMap> yamlList)
            throws RepositoryException, AcConfigBeanValidationException {
        Map<String, Set<AuthorizableConfigBean>> groups = readGroupConfigs(yamlList);
        return new YamlConfigReader().getAceConfigurationBeans(yamlList, groups.keySet(), null, session);
    }

    private Map<String, Set<AuthorizableConfigBean>> readGroupConfigs(List<LinkedHashMap> yamlList) throws AcConfigBeanValidationException {
        return new YamlConfigReader().getGroupConfigurationBeans(yamlList, null);
    }

    private GlobalConfiguration readGlobalConfig(List<LinkedHashMap> yamlList) throws AcConfigBeanValidationException {
        return new YamlConfigReader().getGlobalConfiguration(yamlList);
    }

    private Map<String, Set<AuthorizableConfigBean>> readUserConfigs(List<LinkedHashMap> yamlList)
            throws AcConfigBeanValidationException {
        return new YamlConfigReader().getUserConfigurationBeans(yamlList, null);
    }

    @Test
    public void testVariables() throws Exception {

        List<LinkedHashMap> yamlList = getYamlList("test-variables.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, acInstallationHistoryPojo, session);

        GlobalConfiguration globalConfiguration = readGlobalConfig(yamlList);
        Map<String, Set<AuthorizableConfigBean>> groups = readGroupConfigs(yamlList);
        assertEquals("var2 and var1", groups.get("testGroup").iterator().next().getName());
        assertEquals("var3 and var1", groups.get("testGroup").iterator().next().getDescription());

        assertEquals("var1-BRAND1", groups.get("testGroupNested-BRAND1").iterator().next().getName());
        assertEquals("var1-BRAND2", groups.get("testGroupNested-BRAND2").iterator().next().getName());
        assertEquals("var1-BRAND3", groups.get("testGroupNested-BRAND3").iterator().next().getName());

    }

    @Test
    public void testVariableForLdap() throws Exception {

        List<LinkedHashMap> yamlList = getYamlList("test-variables-ldap.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, acInstallationHistoryPojo, session);

        GlobalConfiguration globalConfiguration = readGlobalConfig(yamlList);
        Map<String, Set<AuthorizableConfigBean>> groups = readGroupConfigs(yamlList);
        assertEquals("cn=editor-group,ou=mydepart,ou=Groups,dc=comp,dc=com;IDPNAME",
                groups.get("aem-only-editor-group").iterator().next().getExternalId());

    }

}
