/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configReader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.RepositoryException;

import org.apache.commons.io.IOUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.helper.AceBean;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;
import biz.netcentric.cq.tools.actool.validators.impl.AceBeanValidatorImpl;

public class YamlConfigReaderTest {

    @Test
    public void testLoop() throws IOException, AcConfigBeanValidationException, RepositoryException {
        final YamlConfigReader yamlConfigReader = new YamlConfigReader();
        final List<LinkedHashMap> yamlList = getYamlList("test-loop.yaml");
        final Map<String, Set<AuthorizableConfigBean>> groups = yamlConfigReader.getGroupConfigurationBeans(yamlList, null);
        final Map<String, Set<AceBean>> aces = yamlConfigReader.getAceConfigurationBeans(yamlList, groups.keySet(), null);
        assertEquals("Number of ACEs", 5, aces.size());
        final Set<AceBean> group1 = aces.get("content-BRAND-MKT1-reader");
        assertEquals("Number of ACEs for groupA", 1, group1.size());
        final Set<AceBean> group2 = aces.get("content-BRAND-MKT2-writer");
        assertEquals("Number of ACEs for groupB", 2, group2.size());
    }

    /** See {@link https://github.com/Netcentric/accesscontroltool/issues/14} */
    @Test
    public void testLoopWithHyphen() throws IOException, AcConfigBeanValidationException, RepositoryException {
        final YamlConfigReader yamlConfigReader = new YamlConfigReader();
        final List<LinkedHashMap> yamlList = getYamlList("test-loop-with-hyphen.yaml");
        final Map<String, Set<AuthorizableConfigBean>> groups = yamlConfigReader.getGroupConfigurationBeans(yamlList, null);
        final Map<String, Set<AceBean>> aces = yamlConfigReader.getAceConfigurationBeans(yamlList, groups.keySet(), null);
        assertEquals("Number of ACEs", 5, aces.size());
        final Set<AceBean> group1 = aces.get("content-BRAND-MKT-1-reader");
        assertEquals("Number of ACEs for groupA", 1, group1.size());
        final Set<AceBean> group2 = aces.get("content-BRAND-MKT-2-writer");
        assertEquals("Number of ACEs for groupB", 2, group2.size());
    }

    @Test
    public void testNestedLoop() throws IOException, AcConfigBeanValidationException, RepositoryException {
        final YamlConfigReader yamlConfigReader = new YamlConfigReader();
        final List<LinkedHashMap> yamlList = getYamlList("test-nested-loops.yaml");
        final Map<String, Set<AuthorizableConfigBean>> groups = yamlConfigReader.getGroupConfigurationBeans(yamlList, null);
        final Map<String, Set<AceBean>> aces = yamlConfigReader.getAceConfigurationBeans(yamlList, groups.keySet(), null);
        assertEquals("Number of ACEs", 12, aces.size());
        assertTrue(aces.containsKey("content-BRAND1-reader"));
        assertTrue(aces.containsKey("content-BRAND1-writer"));
        assertTrue(aces.containsKey("content-BRAND2-reader"));
        assertTrue(aces.containsKey("content-BRAND2-writer"));
        assertTrue(aces.containsKey("content-BRAND1-MKT1-reader"));
        assertTrue(aces.containsKey("content-BRAND1-MKT1-writer"));
        assertTrue(aces.containsKey("content-BRAND2-MKT1-reader"));
        assertTrue(aces.containsKey("content-BRAND2-MKT1-writer"));
        assertTrue(aces.containsKey("content-BRAND1-MKT2-reader"));
        assertTrue(aces.containsKey("content-BRAND1-MKT2-writer"));
        assertTrue(aces.containsKey("content-BRAND2-MKT2-reader"));
        assertTrue(aces.containsKey("content-BRAND2-MKT2-writer"));
        final AceBean b1m1r = aces.get("content-BRAND1-MKT1-reader").iterator().next();
        assertEquals("JCR path", "/content/BRAND1/MKT1", b1m1r.getJcrPath());
    }

    @Test
    public void testNullActions() throws IOException, AcConfigBeanValidationException, RepositoryException {
        final YamlConfigReader yamlConfigReader = new YamlConfigReader();
        final List<LinkedHashMap> yamlList = getYamlList("test-null-actions.yaml");
        final Map<String, Set<AuthorizableConfigBean>> groups = yamlConfigReader.getGroupConfigurationBeans(yamlList, null);
        final Map<String, Set<AceBean>> acls = yamlConfigReader.getAceConfigurationBeans(yamlList, groups.keySet(), null);
        final Set<AceBean> acl = acls.get("groupA");
        for (final AceBean ace : acl) {
            assertNotNull("Testing null actions", ace.getActions());
        }
    }

    @Test
    public void testNoActions() throws IOException, AcConfigBeanValidationException, RepositoryException {
        final YamlConfigReader yamlConfigReader = new YamlConfigReader();
        final List<LinkedHashMap> yamlList = getYamlList("test-no-actions.yaml");
        final Map<String, Set<AuthorizableConfigBean>> groups = yamlConfigReader.getGroupConfigurationBeans(yamlList, null);
        final Map<String, Set<AceBean>> acls = yamlConfigReader.getAceConfigurationBeans(yamlList, groups.keySet(), null);
        final Set<AceBean> acl = acls.get("groupA");
        final AceBean ace = acl.iterator().next();
        assertEquals("Number of actions", 0, ace.getActions().length);
    }

    @Test
    public void testGroupLoop() throws IOException, AcConfigBeanValidationException, RepositoryException {
        final YamlConfigReader yamlConfigReader = new YamlConfigReader();
        final List<LinkedHashMap> yamlList = getYamlList("test-loop.yaml");
        final Map<String, Set<AuthorizableConfigBean>> groups = yamlConfigReader.getGroupConfigurationBeans(yamlList, null);
        assertEquals("Number of groups", 7, groups.size());
        assertEquals("Path of group", "/home/groups/BRAND1", groups.get("content-BRAND1-reader").iterator().next().getPath());
        assertEquals("Path of group", "/home/groups/BRAND1", groups.get("content-BRAND1-writer").iterator().next().getPath());
        assertEquals("Path of group", "/home/groups/BRAND2", groups.get("content-BRAND2-reader").iterator().next().getPath());
        assertEquals("Path of group", "/home/groups/BRAND2", groups.get("content-BRAND2-writer").iterator().next().getPath());
        assertEquals("Path of group", "/home/groups/BRAND3", groups.get("content-BRAND3-reader").iterator().next().getPath());
        assertEquals("Path of group", "/home/groups/BRAND3", groups.get("content-BRAND3-writer").iterator().next().getPath());
    }

    @Test
    public void testNestedGroupLoop() throws IOException, AcConfigBeanValidationException, RepositoryException {
        final YamlConfigReader yamlConfigReader = new YamlConfigReader();
        final List<LinkedHashMap> yamlList = getYamlList("test-nested-loops.yaml");
        final Map<String, Set<AuthorizableConfigBean>> groups = yamlConfigReader.getGroupConfigurationBeans(yamlList, null);
        assertEquals("Number of groups", 13, groups.size());
        assertEquals("Path of group", "/home/groups/BRAND1", groups.get("content-BRAND1-reader").iterator().next().getPath());
        assertEquals("Path of group", "/home/groups/BRAND1/MKT1", groups.get("content-BRAND1-MKT1-writer").iterator().next().getPath());
    }

    @Test
    public void testAceForLoopParsing() {
        final String forStmt = "for brand IN [ BRAND1, BRAND2, BRAND3 ]";
        final YamlConfigReader yamlConfigReader = new YamlConfigReader();
        final List<Map<String, ?>> groups = new LinkedList<Map<String, ?>>();
        final Map<String, List<?>> reader = new HashMap<String, List<?>>();
        final List<Map<String, String>> readerAces = new LinkedList<Map<String, String>>();
        reader.put("content-${brand}-reader", readerAces);
        final Map<String, String> ace1 = new HashMap<String, String>();
        ace1.put("path", "/content/${brand}");
        readerAces.add(ace1);
        final Map<String, String> ace2 = new HashMap<String, String>();
        ace2.put("path", "/content/${brand}/foo");
        readerAces.add(ace2);
        groups.add(reader);
        final List<AceBean> beans = yamlConfigReader.unrollAceForLoop(forStmt, groups, new HashMap<String, String>());
        assertEquals("Number of loop iterations", 6, beans.size());
        assertEquals("/content/BRAND1", beans.get(0).getJcrPath());
        assertEquals("/content/BRAND1/foo", beans.get(1).getJcrPath());
        assertEquals("/content/BRAND2", beans.get(2).getJcrPath());
        assertEquals("/content/BRAND2/foo", beans.get(3).getJcrPath());
        assertEquals("/content/BRAND3", beans.get(4).getJcrPath());
        assertEquals("/content/BRAND3/foo", beans.get(5).getJcrPath());
    }

    @Test
    public void testGroupForLoopParsing() {
        final String forStmt = "for brand IN [ BRAND1, BRAND2, BRAND3 ]";
        final YamlConfigReader yamlConfigReader = new YamlConfigReader();
        final List<Map<String, ?>> groups = new LinkedList<Map<String, ?>>();
        final Map<String, List<?>> group = new HashMap<String, List<?>>();
        final List<Map<String, String>> groupMaps = new LinkedList<Map<String, String>>();
        group.put("content-${brand}-reader", groupMaps);
        final Map<String, String> props = new HashMap<String, String>();
        props.put("path", "/home/groups/${brand}");
        groupMaps.add(props);
        groups.add(group);
        final List<AuthorizableConfigBean> beans = yamlConfigReader
                .unrollGroupForLoop(forStmt, groups, new HashMap<String, String>(), true);
        assertEquals("Number of groups", 3, beans.size());
        assertEquals("/home/groups/BRAND1", beans.get(0).getPath());
        assertEquals("/home/groups/BRAND2", beans.get(1).getPath());
        assertEquals("/home/groups/BRAND3", beans.get(2).getPath());
    }

    @Test
    public void testMultipleAcesSamePath() throws IOException, AcConfigBeanValidationException, RepositoryException {
        final YamlConfigReader yamlConfigReader = new YamlConfigReader();
        final List<LinkedHashMap> yamlList = getYamlList("test-multiple-aces-same-path.yaml");
        final Map<String, Set<AuthorizableConfigBean>> groups = yamlConfigReader.getGroupConfigurationBeans(yamlList, null);
        final Map<String, Set<AceBean>> acls = yamlConfigReader.getAceConfigurationBeans(yamlList, groups.keySet(), null);
        assertEquals("Number of ACLs", 3, acls.get("groupA").size());
    }

    @Test
    public void testEmptyGlobVsNoGlob() throws Exception {
        final YamlConfigReader yamlConfigReader = new YamlConfigReader();
        final List<LinkedHashMap> yamlList = getYamlList("test-empty-glob.yaml");
        final Map<String, Set<AuthorizableConfigBean>> groups = yamlConfigReader.getGroupConfigurationBeans(yamlList, null);
        final Map<String, Set<AceBean>> acls = yamlConfigReader.getAceConfigurationBeans(yamlList, groups.keySet(), null);
        final Iterator<AceBean> it = acls.get("groupA").iterator();
        final AceBean ace1 = it.next();
        assertNull("repGlob", ace1.getRepGlob());
        final AceBean ace2 = it.next();
        assertEquals("repGlob", "", ace2.getRepGlob());
    }

    @Test
    public void testOptionalSections() throws Exception {
        final YamlConfigReader yamlConfigReader = new YamlConfigReader();
        List<LinkedHashMap> yamlList = getYamlList("test-no-aces.yaml");
        Map<String, Set<AuthorizableConfigBean>> groups = yamlConfigReader.getGroupConfigurationBeans(yamlList, null);
        Map<String, Set<AceBean>> acls = yamlConfigReader.getAceConfigurationBeans(yamlList, groups.keySet(), null);
        assertNull("No ACEs", acls);
        yamlList = getYamlList("test-no-groups.yaml");
        groups = yamlConfigReader.getGroupConfigurationBeans(yamlList, null);
        assertNull("No groups", groups);
        acls = yamlConfigReader.getAceConfigurationBeans(yamlList, null, null);
        assertNotNull("ACL for groupA", acls.get("groupA"));
        assertEquals("Number of ACEs", 1, acls.get("groupA").size());
    }

    /** Test support for rep:userManagerment privilege name */
    @Test
    @Ignore
    public void testUserManagementPrivilege() throws IOException, AcConfigBeanValidationException, RepositoryException {
        final YamlConfigReader yamlConfigReader = new YamlConfigReader();
        final List<LinkedHashMap> yamlList = getYamlList("test-rep-usermanagement.yaml");
        final Map<String, Set<AuthorizableConfigBean>> groups = yamlConfigReader.getGroupConfigurationBeans(yamlList, null);
        final Map<String, Set<AceBean>> acls = yamlConfigReader.getAceConfigurationBeans(yamlList, groups.keySet(),
                new AceBeanValidatorImpl(groups.keySet()));
        final Set<AceBean> acl = acls.get("groupA");
        for (final AceBean ace : acl) {
            assertNotNull("Testing null actions", ace.getActions());
        }
    }

    @Test
    public void testMemberGroups() throws IOException, AcConfigBeanValidationException, RepositoryException {
        final YamlConfigReader yamlConfigReader = new YamlConfigReader();
        final List<LinkedHashMap> yamlList = getYamlList("test-membergroups.yaml");
        final Map<String, Set<AuthorizableConfigBean>> groups = yamlConfigReader.getGroupConfigurationBeans(yamlList, null);
        assertEquals("Number of groups", 4, groups.size());
        assertEquals("", groups.get("groupA").iterator().next().getMembersStringFromConfig());
        assertEquals("groupA", groups.get("groupB").iterator().next().getMembersStringFromConfig());
    }

    private List<LinkedHashMap> getYamlList(final String filename) throws IOException {
        final String configString = getTestConfigAsString(filename);

        final Yaml yaml = new Yaml();
        final List<LinkedHashMap> yamlList = (List<LinkedHashMap>) yaml
                .load(configString);
        return yamlList;
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

}
