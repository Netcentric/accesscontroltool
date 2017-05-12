/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configreader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.MockitoAnnotations.initMocks;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
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
        final List<LinkedHashMap> yamlList = getYamlList("test-null-actions.yaml");
        final AcesConfig acls = yamlConfigReader.getAceConfigurationBeans(yamlList, null, session);
        final Set<AceBean> acl = acls.filterByPrincipalName("groupA");
        for (final AceBean ace : acl) {
            assertNotNull("Testing null actions", ace.getActions());
        }
    }

    @Test
    public void testNoActions() throws IOException, AcConfigBeanValidationException, RepositoryException {
        final ConfigReader yamlConfigReader = new YamlConfigReader();
        final List<LinkedHashMap> yamlList = getYamlList("test-no-actions.yaml");
        final AcesConfig acls = yamlConfigReader.getAceConfigurationBeans(yamlList, null, session);
        final Set<AceBean> acl = acls.filterByPrincipalName("groupA");
        final AceBean ace = acl.iterator().next();
        assertEquals("Number of actions", 0, ace.getActions().length);
    }

    @Test
    public void testMultipleAcesSamePath() throws IOException, AcConfigBeanValidationException, RepositoryException {
        final ConfigReader yamlConfigReader = new YamlConfigReader();
        final List<LinkedHashMap> yamlList = getYamlList("test-multiple-aces-same-path.yaml");
        final AcesConfig acls = yamlConfigReader.getAceConfigurationBeans(yamlList, null, session);
        assertEquals("Number of ACLs", 3, acls.filterByPrincipalName("groupA").size());
    }

    @Test
    public void testEmptyGlobVsNoGlob() throws Exception {
        final ConfigReader yamlConfigReader = new YamlConfigReader();
        final List<LinkedHashMap> yamlList = getYamlList("test-empty-glob.yaml");
        final AcesConfig acls = yamlConfigReader.getAceConfigurationBeans(yamlList, null, session);
        final Iterator<AceBean> it = acls.filterByPrincipalName("groupA").iterator();
        final AceBean ace1 = it.next();
        assertNull("repGlob", ace1.getRepGlob());
        final AceBean ace2 = it.next();
        assertEquals("repGlob", "", ace2.getRepGlob());
    }

    @Test
    public void testOptionalSections() throws Exception {
        final ConfigReader yamlConfigReader = new YamlConfigReader();
        List<LinkedHashMap> yamlList = getYamlList("test-no-aces.yaml");
        Set<AuthorizableConfigBean> groups = yamlConfigReader.getGroupConfigurationBeans(yamlList, null);
        AcesConfig acls = yamlConfigReader.getAceConfigurationBeans(yamlList, null, session);
        assertNull("No ACEs", acls);
        yamlList = getYamlList("test-no-groups.yaml");
        groups = yamlConfigReader.getGroupConfigurationBeans(yamlList, null);
        assertNull("No groups", groups);
        acls = yamlConfigReader.getAceConfigurationBeans(yamlList, null, session);
        assertNotNull("ACL for groupA", acls.filterByPrincipalName("groupA"));
        assertEquals("Number of ACEs", 1, acls.filterByPrincipalName("groupA").size());
    }

    @Test
    public void testMemberGroups() throws IOException, AcConfigBeanValidationException, RepositoryException {
        final ConfigReader yamlConfigReader = new YamlConfigReader();
        final List<LinkedHashMap> yamlList = getYamlList("test-membergroups.yaml");
        final Set<AuthorizableConfigBean> groups = yamlConfigReader.getGroupConfigurationBeans(yamlList, null);
        assertEquals("Number of groups", 4, groups.size());

        Iterator<AuthorizableConfigBean> groupsIt = groups.iterator();
        AuthorizableConfigBean firstGroup = groupsIt.next();
        assertEquals("", firstGroup.getMembersStringFromConfig());
        assertEquals("groupA", firstGroup.getAuthorizableId());

        AuthorizableConfigBean secondGroup = groupsIt.next();
        assertEquals("groupB", secondGroup.getAuthorizableId());
        assertEquals("groupA", secondGroup.getMembersStringFromConfig());

    }

    static List<LinkedHashMap> getYamlList(final String filename) throws IOException {
        final String configString = getTestConfigAsString(filename);

        final Yaml yaml = new Yaml();
        List<LinkedHashMap> yamlList = (List<LinkedHashMap>) yaml.load(configString);

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
