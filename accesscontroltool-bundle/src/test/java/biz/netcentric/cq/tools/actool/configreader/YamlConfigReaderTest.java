/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configreader;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.MockitoAnnotations.initMocks;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.yaml.snakeyaml.Yaml;

import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.configmodel.AcesConfig;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;

public class YamlConfigReaderTest {

    @Mock
    Session session;

    @Before
    public void setup() {
        initMocks(this);
    }

    @Test
    public void testNullActions() throws IOException, AcConfigBeanValidationException, RepositoryException {
        final ConfigReader yamlConfigReader = new YamlConfigReader();
        final List<Map> yamlList = getYamlList("test-null-actions.yaml");
        final AcesConfig acls = yamlConfigReader.getAceConfigurationBeans(yamlList, null, session);
        final Set<AceBean> acl = acls.filterByAuthorizableId("groupA");
        for (final AceBean ace : acl) {
            assertNotNull("Testing null actions", ace.getActions());
        }
    }

    @Test
    public void testNoActions() throws IOException, AcConfigBeanValidationException, RepositoryException {
        final ConfigReader yamlConfigReader = new YamlConfigReader();
        final List<Map> yamlList = getYamlList("test-no-actions.yaml");
        final AcesConfig acls = yamlConfigReader.getAceConfigurationBeans(yamlList, null, session);
        final Set<AceBean> acl = acls.filterByAuthorizableId("groupA");
        final AceBean ace = acl.iterator().next();
        assertEquals("Number of actions", 0, ace.getActions().length);
    }

    @Test
    public void testMultipleAcesSamePath() throws IOException, AcConfigBeanValidationException, RepositoryException {
        final ConfigReader yamlConfigReader = new YamlConfigReader();
        final List<Map> yamlList = getYamlList("test-multiple-aces-same-path.yaml");
        final AcesConfig acls = yamlConfigReader.getAceConfigurationBeans(yamlList, null, session);

        assertEquals("Number of ACLs", 3, acls.filterByAuthorizableId("groupA").size());
        assertEquals("Number of ACLs", 2, acls.filterByAuthorizableId("groupB").size());

        Iterator<AceBean> iterator = acls.iterator();
        while (iterator.hasNext()) {
            AceBean aceBean = iterator.next();
            assertEquals("/content", aceBean.getJcrPath());
        }
    }

    @Test
    public void testEmptyGlobVsNoGlob() throws Exception {
        final ConfigReader yamlConfigReader = new YamlConfigReader();
        final List<Map> yamlList = getYamlList("test-empty-glob.yaml");
        final AcesConfig acls = yamlConfigReader.getAceConfigurationBeans(yamlList, null, session);
        final Iterator<AceBean> it = acls.filterByAuthorizableId("groupA").iterator();
        final AceBean ace1 = it.next();
        assertNull("repGlob", ace1.getRepGlob());
        final AceBean ace2 = it.next();
        assertEquals("repGlob", "", ace2.getRepGlob());
    }

    @Test
    public void testOptionalSections() throws Exception {
        final ConfigReader yamlConfigReader = new YamlConfigReader();
        List<Map> yamlList = getYamlList("test-no-aces.yaml");
        Set<AuthorizableConfigBean> groups = yamlConfigReader.getGroupConfigurationBeans(yamlList, null);
        AcesConfig acls = yamlConfigReader.getAceConfigurationBeans(yamlList, null, session);
        assertNull("No ACEs", acls);
        yamlList = getYamlList("test-no-groups.yaml");
        groups = yamlConfigReader.getGroupConfigurationBeans(yamlList, null);
        assertNull("No groups", groups);
        acls = yamlConfigReader.getAceConfigurationBeans(yamlList, null, session);
        assertNotNull("ACL for groupA", acls.filterByAuthorizableId("groupA"));
        assertEquals("Number of ACEs", 1, acls.filterByAuthorizableId("groupA").size());
    }

    @Test
    public void testMemberGroups() throws IOException, AcConfigBeanValidationException, RepositoryException {
        final ConfigReader yamlConfigReader = new YamlConfigReader();
        final List<Map> yamlList = getYamlList("test-membergroups.yaml");
        final Set<AuthorizableConfigBean> groups = yamlConfigReader.getGroupConfigurationBeans(yamlList, null);
        assertEquals("Number of groups", 4, groups.size());

        Iterator<AuthorizableConfigBean> groupsIt = groups.iterator();
        AuthorizableConfigBean firstGroup = groupsIt.next();
        assertArrayEquals(null, firstGroup.getMembers());
        assertEquals("groupA", firstGroup.getAuthorizableId());

        AuthorizableConfigBean secondGroup = groupsIt.next();
        assertEquals("groupB", secondGroup.getAuthorizableId());
        assertArrayEquals(new String[] {"groupA"}, secondGroup.getMembers());

    }

    static List<Map> getYamlList(final String filename) throws IOException {
        final String configString = getTestConfigAsString(filename);

        final Yaml yaml = new Yaml();
        List<Map> yamlList = yaml.loadAs(configString, List.class);
        return yamlList;
    }

    static String getTestConfigAsString(final String resourceName)
            throws IOException {
        final ClassLoader classloader = YamlConfigReaderTest.class.getClassLoader();
        try (final InputStream is = classloader.getResourceAsStream(resourceName)) {
            return IOUtils.toString(is, StandardCharsets.UTF_8);
        }
    }


}
