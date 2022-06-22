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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.MockitoAnnotations.initMocks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.configmodel.AcesConfig;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizablesConfig;
import biz.netcentric.cq.tools.actool.configmodel.GlobalConfiguration;
import biz.netcentric.cq.tools.actool.history.InstallationLogger;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;

public class YamlMacroProcessorTest {

    @Mock
    YamlMacroChildNodeObjectsProviderImpl yamlMacroChildNodeObjectsProvider;

    @Mock
    InstallationLogger installLog;

    @Mock
    Session session;

    @InjectMocks
    YamlMacroProcessorImpl yamlMacroProcessor = new YamlMacroProcessorImpl();

    Map<String, Object> globalVariables = new HashMap<>();

    @BeforeEach
    public void setup() {
        initMocks(this);

        globalVariables.put(YamlConfigurationMerger.GLOBAL_VAR_RUNMODES, Arrays.asList("runmode1", "runmode2", "runmode3"));
    }

    @Test
    public void testGroupLoop() throws IOException, AcConfigBeanValidationException, RepositoryException {

        List<Map> yamlList = getYamlList("test-loop.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, globalVariables, installLog, session);

        AuthorizablesConfig groups = readGroupConfigs(yamlList);
        assertEquals(10, groups.size(), "Number of groups");
        assertEquals( "/home/groups/BRAND1", groups.getAuthorizableConfig("content-BRAND1-reader").getPath(), "Path of group");
        assertEquals( "/home/groups/BRAND1", groups.getAuthorizableConfig("content-BRAND1-writer").getPath(), "Path of group");
        assertEquals( "/home/groups/BRAND2", groups.getAuthorizableConfig("content-BRAND2-reader").getPath(), "Path of group");
        assertEquals( "/home/groups/BRAND2", groups.getAuthorizableConfig("content-BRAND2-writer").getPath(), "Path of group");
        assertEquals( "/home/groups/BRAND3", groups.getAuthorizableConfig("content-BRAND3-reader").getPath(), "Path of group");
        assertEquals( "/home/groups/BRAND3", groups.getAuthorizableConfig("content-BRAND3-writer").getPath(), "Path of group");

        assertEquals( "/home/groups/VAR1", groups.getAuthorizableConfig("content-VAR1").getPath(), "Path of group");
        assertEquals( "/home/groups/VAR2", groups.getAuthorizableConfig("content-VAR2").getPath(), "Path of group");
        assertEquals( "/home/groups/VAR3", groups.getAuthorizableConfig("content-VAR3").getPath(), "Path of group");

    }

    @Test
    public void testNestedGroupLoop() throws IOException, AcConfigBeanValidationException, RepositoryException {
        final ConfigReader yamlConfigReader = new YamlConfigReader();
        List<Map> yamlList = getYamlList("test-nested-loops.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, globalVariables, installLog, session);

        AuthorizablesConfig groups = readGroupConfigs(yamlList);

        assertEquals(13, groups.size(), "Number of groups");
        assertEquals("/home/groups/BRAND1", groups.getAuthorizableConfig("content-BRAND1-reader").getPath(), "Path of group");
        assertEquals("/home/groups/BRAND1/MKT1", groups.getAuthorizableConfig("content-BRAND1-MKT1-writer").getPath(), "Path of group");
    }

    @Test
    public void testAceLoop() throws IOException, AcConfigBeanValidationException, RepositoryException {

        List<Map> yamlList = getYamlList("test-loop.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, globalVariables, installLog, session);

        AcesConfig aces = readAceConfigs(yamlList);
        assertEquals(7, aces.size(), "Number of ACEs");
        final Set<AceBean> group1 = aces.filterByAuthorizableId("content-BRAND-MKT1-reader");
        assertEquals(1, group1.size(), "Number of ACEs for groupA");
        final Set<AceBean> group2 = aces.filterByAuthorizableId("content-BRAND-MKT2-writer");
        assertEquals(2, group2.size(), "Number of ACEs for groupB");
    }

    /**
     * @see <a href=
     * "https://github.com/Netcentric/accesscontroltool/issues/14">https://github.com/Netcentric/accesscontroltool/issues/14</a>
     */
    @Test
    public void testAceLoopWithHyphen() throws IOException, AcConfigBeanValidationException, RepositoryException {

        List<Map> yamlList = getYamlList("test-loop-with-hyphen.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, globalVariables, installLog, session);

        AcesConfig aces = readAceConfigs(yamlList);
        assertEquals(7, aces.size(), "Number of ACEs");
        final Set<AceBean> group1 = aces.filterByAuthorizableId("content-BRAND-MKT-1-reader");
        assertEquals(1, group1.size(), "Number of ACEs for groupA");
        final Set<AceBean> group2 = aces.filterByAuthorizableId("content-BRAND-MKT-2-writer");
        assertEquals(2, group2.size(), "Number of ACEs for groupB");
    }

    /**
     * @see <a href=
     * "https://github.com/Netcentric/accesscontroltool/issues/452">https://github.com/Netcentric/accesscontroltool/issues/452</a>
     */
    @Test
    public void testAceLoopWithColon() throws IOException, AcConfigBeanValidationException, RepositoryException {

        List<Map> yamlList = getYamlList("test-loop-with-colon.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, globalVariables, installLog, session);

        AcesConfig aces = readAceConfigs(yamlList);
        assertEquals(2, aces.size(), "Number of ACEs");
        final Set<AceBean> groups1 = aces.filterByAuthorizableId("content-tags-project1-reader");
        assertEquals(1, groups1.size(), "Number of ACEs for project1");
        assertEquals("/content/cq:tags/project1", groups1.iterator().next().getJcrPath());
        final Set<AceBean> groups2 = aces.filterByAuthorizableId("content-tags-project2-reader");
        assertEquals(1, groups2.size(), "Number of ACEs for project2");
        assertEquals("/content/cq:tags/project2", groups2.iterator().next().getJcrPath());
    }

    /**
     * @see <a href=
     * "https://github.com/Netcentric/accesscontroltool/issues/330">https://github.com/Netcentric/accesscontroltool/issues/330</a>
     */
    @Test
    public void testAceLoopWithSpecialCharacters() throws IOException, AcConfigBeanValidationException, RepositoryException {

        List<Map> yamlList = getYamlList("test-loop-with-special-characters.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, globalVariables, installLog, session);

        AuthorizablesConfig groupConfigs = readGroupConfigs(yamlList);

        assertEquals(4, groupConfigs.size(), "Number of groups in config");

        assertNotNull(groupConfigs.getAuthorizableConfig("brand_1"), "Expected to find brand_1");
        assertNotNull(groupConfigs.getAuthorizableConfig("BRAND-2"), "Expected to find BRAND-2");
        assertNotNull(groupConfigs.getAuthorizableConfig("BRAND.3"), "Expected to find BRAND.3");
        assertNotNull(groupConfigs.getAuthorizableConfig("BRAND 4"), "Expected to find BRAND 4");
    }

    @Test
    public void testNestedLoop() throws IOException, AcConfigBeanValidationException, RepositoryException {

        List<Map> yamlList = getYamlList("test-nested-loops.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, globalVariables, installLog, session);

        AcesConfig aces = readAceConfigs(yamlList);
        assertEquals(12, aces.size(), "Number of ACEs");
        assertFalse(aces.filterByAuthorizableId("content-BRAND1-reader").isEmpty());
        assertFalse(aces.filterByAuthorizableId("content-BRAND1-writer").isEmpty());
        assertFalse(aces.filterByAuthorizableId("content-BRAND2-reader").isEmpty());
        assertFalse(aces.filterByAuthorizableId("content-BRAND2-writer").isEmpty());
        assertFalse(aces.filterByAuthorizableId("content-BRAND1-MKT1-reader").isEmpty());
        assertFalse(aces.filterByAuthorizableId("content-BRAND1-MKT1-writer").isEmpty());
        assertFalse(aces.filterByAuthorizableId("content-BRAND2-MKT1-reader").isEmpty());
        assertFalse(aces.filterByAuthorizableId("content-BRAND2-MKT1-writer").isEmpty());
        assertFalse(aces.filterByAuthorizableId("content-BRAND1-MKT2-reader").isEmpty());
        assertFalse(aces.filterByAuthorizableId("content-BRAND1-MKT2-writer").isEmpty());
        assertFalse(aces.filterByAuthorizableId("content-BRAND2-MKT2-reader").isEmpty());
        assertFalse(aces.filterByAuthorizableId("content-BRAND2-MKT2-writer").isEmpty());
        AceBean b1m1r = aces.filterByAuthorizableId("content-BRAND1-MKT1-reader").iterator().next();
        assertEquals("JCR path", "/content/BRAND1/MKT1", b1m1r.getJcrPath());
    }

    @Test
    public void testIf() throws IOException, AcConfigBeanValidationException, RepositoryException {

        List<Map> yamlList = getYamlList("test-if.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, globalVariables, installLog, session);

        AcesConfig aces = readAceConfigs(yamlList);

        assertEquals(3, aces.size(), "Number of Groups in ACE Section");
        final Set<AceBean> group1 = aces.filterByAuthorizableId("content-reader");
        assertEquals(1, group1.size(), "Number of ACEs for groupA");
        final Set<AceBean> group2 = aces.filterByAuthorizableId("content-writer");
        assertEquals(2, group2.size(), "Number of ACEs for groupB");

        AuthorizablesConfig groups = readGroupConfigs(yamlList);
        assertEquals(1, groups.size(), "Number of groups");
        assertEquals("/home/groups/folder2", groups.getAuthorizableConfig("content-writer").getPath(), "Path of group");

    }

    @Test
    public void testLoopChildrenOf() throws IOException, AcConfigBeanValidationException, RepositoryException {

        List<Map> yamlList = getYamlList("test-loop-children-of.yaml");

        String contentLocationChildrenFromYamlFile = "/content/test";

        doReturn(getExampleValuesForLoopOverChildrenOfPath()).when(yamlMacroChildNodeObjectsProvider)
                .getValuesForPath(contentLocationChildrenFromYamlFile, installLog, session, false);

        yamlList = yamlMacroProcessor.processMacros(yamlList, globalVariables, installLog, session);

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

    @Test
    public void testLoopChildrenWithContentOf() throws IOException, AcConfigBeanValidationException, RepositoryException {

        List<Map> yamlList = getYamlList("test-loop-children-with-content-of.yaml");

        String contentLocationChildrenFromYamlFile = "/content/test";

        doReturn(getExampleValuesForLoopOverChildrenOfPath()).when(yamlMacroChildNodeObjectsProvider)
                .getValuesForPath(contentLocationChildrenFromYamlFile, installLog, session, true);

        yamlList = yamlMacroProcessor.processMacros(yamlList, globalVariables, installLog, session);

        // new Yaml().dump(yamlList, new PrintWriter(System.out));

        AuthorizablesConfig groups = readGroupConfigs(yamlList);
        assertEquals(4, groups.size());

        AuthorizableConfigBean group1 = groups.getAuthorizableConfig("content-node1-reader");
        assertEquals("/home/groups/test", group1.getPath());
        assertEquals("Jcr Content Property with deep content subval1", group1.getName());

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
                        put("subnode", new HashMap<String, Object>() {
                            {
                                put("prop", "subval1");
                            }
                        });
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
                        put("subnode", new HashMap<String, Object>() {
                            {
                                put("prop", "subval2");
                            }
                        });
                    }
                });
            }
        };
        result.add(test2);

        return result;
    }

    @Test
    public void testSystemUser() throws IOException, AcConfigBeanValidationException, RepositoryException {

        List<Map> yamlList = getYamlList("test-system-user.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, globalVariables, installLog, session);

        AuthorizablesConfig users = readUserConfigs(yamlList);
        assertEquals(1, users.size(), "Number of users");
        AuthorizableConfigBean user = users.getAuthorizableConfig("test-system-user");
        assertEquals(user.isSystemUser(), true);
        assertEquals(user.getName(), "Test System User");
    }

    // --- using YamlConfigReader to make assertions easier (the raw yaml structure makes it really hard)

    private AcesConfig readAceConfigs(final List<Map> yamlList)
            throws RepositoryException, AcConfigBeanValidationException {
        AuthorizablesConfig groups = readGroupConfigs(yamlList);
        return new YamlConfigReader().getAceConfigurationBeans(yamlList, null, session, "junit");
    }

    private AuthorizablesConfig readGroupConfigs(List<Map> yamlList) throws AcConfigBeanValidationException {
        return new YamlConfigReader().getGroupConfigurationBeans(yamlList, null);
    }

    private GlobalConfiguration readGlobalConfig(List<Map> yamlList) throws AcConfigBeanValidationException {
        return new YamlConfigReader().getGlobalConfiguration(yamlList);
    }

    private AuthorizablesConfig readUserConfigs(List<Map> yamlList)
            throws AcConfigBeanValidationException {
        return new YamlConfigReader().getUserConfigurationBeans(yamlList, null);
    }

    @Test
    public void testVariables() throws Exception {

        List<Map> yamlList = getYamlList("test-variables.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, globalVariables, installLog, session);

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

        List<Map> yamlList = getYamlList("test-variables-ldap.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, globalVariables, installLog, session);

        AuthorizablesConfig groups = readGroupConfigs(yamlList);
        assertEquals("cn=editor-group,ou=mydepart,ou=Groups,dc=comp,dc=com;IDPNAME",
                groups.getAuthorizableConfig("aem-only-editor-group").getExternalId());

    }

    @Test
    public void testLoopWithArrVariable() throws Exception {

        List<Map> yamlList = getYamlList("test-loop-with-variable-evaluating-to-arr.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, globalVariables, installLog, session);

        AuthorizablesConfig groups = readGroupConfigs(yamlList);
        assertEquals("Name of val1", groups.getAuthorizableConfig("content-reader-loop1-val1").getName());
        assertEquals("Name of val2", groups.getAuthorizableConfig("content-reader-loop1-val2").getName());

        assertEquals("Name of val3", groups.getAuthorizableConfig("content-reader-loop2-val3").getName());
        assertEquals("Name of val4", groups.getAuthorizableConfig("content-reader-loop2-val4").getName());
        assertEquals("Name of val1", groups.getAuthorizableConfig("content-reader-loop2-val1").getName());

    }

    @Test
    public void testDefRegEx() throws Exception {

        Matcher matcher = YamlMacroProcessorImpl.VARIABLE_DEF_PATTERN_ONE_LINE.matcher("DEF test=\"val\"");

        assertTrue(matcher.find());
        assertEquals("test", matcher.group(1));
        assertEquals(null, matcher.group(2));
        assertEquals("\"", matcher.group(3));
        assertEquals("val", matcher.group(4));
        assertEquals("\"", matcher.group(5));

        matcher = YamlMacroProcessorImpl.VARIABLE_DEF_PATTERN_ONE_LINE.matcher("DEF test=val");

        assertTrue(matcher.find());
        assertEquals("test", matcher.group(1));
        assertEquals(null, matcher.group(2));
        assertEquals("", matcher.group(3));
        assertEquals("val", matcher.group(4));
        assertEquals("", matcher.group(5));

        matcher = YamlMacroProcessorImpl.VARIABLE_DEF_PATTERN_ONE_LINE.matcher("DEF test=[val1,val2]");

        assertTrue(matcher.find());
        assertEquals("test", matcher.group(1));
        assertEquals("val1,val2", matcher.group(2));
        assertEquals(null, matcher.group(3));
        assertEquals(null, matcher.group(4));
        assertEquals(null, matcher.group(5));

    }

    @Test
    public void testContainsItemFunction() throws Exception {

        List<Map> yamlList = getYamlList("test-array-containsItem-function.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, globalVariables, installLog, session);

        AcesConfig aces = readAceConfigs(yamlList);
        assertEquals(2, aces.size(), "Number of ACEs expected to be 2 and not 4");

    }

    @Test
    public void testContainsAllItemsFunction() throws Exception {
        List<Map> yamlList = getYamlList("test-array-containsAllItems-function.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, globalVariables, installLog, session);

        AcesConfig aces = readAceConfigs(yamlList);
        assertEquals(9, aces.size(), "Number of ACEs expected to be 9");
    }

    @Test
    public void testContainsAnyItemFunction() throws Exception {
        List<Map> yamlList = getYamlList("test-array-containsAnyItem-function.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, globalVariables, installLog, session);

        AcesConfig aces = readAceConfigs(yamlList);
        assertEquals(8, aces.size(), "Number of ACEs expected to be 8");
    }

    @Test
    public void testLoopOverRunmodes() throws Exception {

        List<Map> yamlList = getYamlList("test-loop-over-runmodes.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, globalVariables, installLog, session);

        AuthorizablesConfig groups = readGroupConfigs(yamlList);
        assertEquals(3, groups.size());

        assertNotNull(groups.getAuthorizableConfig("testgroup-runmode1"));
        assertNotNull(groups.getAuthorizableConfig("testgroup-runmode2"));
        assertNotNull(groups.getAuthorizableConfig("testgroup-runmode3"));

    }

    @Test
    public void testDefWithUnderscore() throws Exception {

        List<Map> yamlList = getYamlList("test-def-with-underscore.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, globalVariables, installLog, session);

        AuthorizablesConfig groups = readGroupConfigs(yamlList);
        assertEquals(3, groups.size());

        assertNotNull(groups.getAuthorizableConfig("content-tags-test1-reader"));
        assertNotNull(groups.getAuthorizableConfig("content-tags-test2-reader"));
        assertNotNull(groups.getAuthorizableConfig("content-tags-test3-reader"));

    }

    @Test
    public void testDefWithComplexStructures() throws Exception {

        List<Map> yamlList = getYamlList("test-def-complex-structure.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, globalVariables, installLog, session);

        AuthorizablesConfig groups = readGroupConfigs(yamlList);
        assertEquals(12, groups.size());

        AuthorizableConfigBean authorizableConfig1 = groups.getAuthorizableConfig("group-type1-val1");
        assertNotNull(authorizableConfig1);
        assertEquals("Type1 val1", authorizableConfig1.getName());
        assertEquals("value-buried-deep-in-structure", authorizableConfig1.getDescription());

        assertNotNull(groups.getAuthorizableConfig("group-type1-val2"));
        assertNotNull(groups.getAuthorizableConfig("group-type1-val3"));

        AuthorizableConfigBean authorizableConfig2 = groups.getAuthorizableConfig("group-type2-key1");
        assertNotNull(authorizableConfig2);
        assertEquals("Type2 mapval1", authorizableConfig2.getName());
        assertNotNull(groups.getAuthorizableConfig("group-type2-key2"));
        assertNotNull(groups.getAuthorizableConfig("group-type2-key3"));

        AuthorizableConfigBean authorizableConfig3 = groups.getAuthorizableConfig("group-type3-obj1val1");
        assertNotNull(authorizableConfig3);
        assertEquals("Type3 obj1val2 obj1val3", authorizableConfig3.getName());
        assertEquals("obj1val3 => mapped-obj1val3", authorizableConfig3.getDescription());
        assertNotNull(groups.getAuthorizableConfig("group-type3-obj2val1"));
        assertNotNull(groups.getAuthorizableConfig("group-type3-obj3val1"));

        AuthorizableConfigBean authorizableConfig4 = groups.getAuthorizableConfig("group-type4-deepListVal1");
        assertNotNull(authorizableConfig4);
        assertEquals("Type4 deepListVal1", authorizableConfig4.getName());
        assertNotNull(groups.getAuthorizableConfig("group-type4-deepListVal2"));
        assertNotNull(groups.getAuthorizableConfig("group-type4-deepListVal3"));

    }

    @Test
    public void testDefWithComplexNestedLoops() throws Exception {


        List<Map> yamlList = getYamlList("test-def-complex-nested-loops.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, globalVariables, installLog, session);

        AcesConfig aceConfigs = readAceConfigs(yamlList);
        assertEquals(8, aceConfigs.size());
        Iterator<AceBean> it = aceConfigs.iterator();

        String[][] expectedVals = new String[][]{
                new String[]{"/content/base/level1name1", "<jcr:root jcr:primaryType=\"sling:Folder\" jcr:title=\"level1title1\"/>"},
                new String[]{"/content/base/level1name1/level2name1", "<jcr:root jcr:primaryType=\"sling:Folder\" jcr:title=\"level2title1\"/>"},
                new String[]{"/content/base/level1name1/level2name1/level3name1", "<jcr:root jcr:primaryType=\"sling:Folder\" jcr:title=\"level3title1\"/>"},
                new String[]{"/content/base/level1name1/level2name2", "<jcr:root jcr:primaryType=\"sling:Folder\" jcr:title=\"level2title2\"/>"},
                new String[]{"/content/base/level1name1/level2name3", "<jcr:root jcr:primaryType=\"sling:Folder\" jcr:title=\"level2title3\"/>"},
                new String[]{"/content/base/level1name1/level2name3/level3name1", "<jcr:root jcr:primaryType=\"sling:Folder\" jcr:title=\"level3title1\"/>"},
                new String[]{"/content/base/level1name1/level2name3/level3name2", "<jcr:root jcr:primaryType=\"sling:Folder\" jcr:title=\"level3title2\"/>"},
                new String[]{"/content/base/level1name1/level2name3/level3name3", "<jcr:root jcr:primaryType=\"sling:Folder\" jcr:title=\"level3title3\"/>"},
        };

        for (int i = 0; i < expectedVals.length && it.hasNext(); i++) {
            AceBean aceBean = it.next();
            assertEquals("Expected path for bean " + i, expectedVals[i][0], aceBean.getJcrPath());
            assertEquals("Expected initial content for bean " + i, expectedVals[i][1], aceBean.getInitialContent());
        }


    }

    @Test
    public void testDefGlobalVars() throws Exception {

        List<Map> yamlList = getYamlList("test-def-global-1-set.yaml");

        yamlList = yamlMacroProcessor.processMacros(yamlList, globalVariables, installLog, session);

        assertEquals("xyz", globalVariables.get("groupPrefix"));
        assertEquals(Arrays.asList("val1", "val2"), globalVariables.get("comlexArr"));
        assertNull(globalVariables.get("varNotGlobal"));

        List<Map> yamlList2 = getYamlList("test-def-global-2-use.yaml");

        yamlList2 = yamlMacroProcessor.processMacros(yamlList2, globalVariables, installLog, session);

        AuthorizablesConfig groups = readGroupConfigs(yamlList2);
        assertEquals(2, groups.size());
        AuthorizableConfigBean authorizableConfig1 = groups.getAuthorizableConfig("xyz-group-reader");
        assertNotNull(authorizableConfig1);
        assertEquals("Name val1", authorizableConfig1.getName());
        assertEquals("varNotGlobal shall be empty: ''", authorizableConfig1.getDescription());

        AuthorizableConfigBean authorizableConfig2 = groups.getAuthorizableConfig("xyz-group-reader2");
        assertNotNull(authorizableConfig2);
        assertEquals("global variable can be overridden with local value: 'localValOverridingGlobal'", authorizableConfig2.getName());
        assertEquals("global variable overridden elsewhere (not in this file) has global val: 'globalVal'", authorizableConfig2.getDescription());

    }

}
