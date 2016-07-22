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

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.RepositoryException;

import org.apache.commons.io.IOUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;
import biz.netcentric.cq.tools.actool.validators.impl.AceBeanValidatorImpl;

public class YamlConfigReaderTest {


    @Test
    public void testNullActions() throws IOException, AcConfigBeanValidationException, RepositoryException {
        final ConfigReader yamlConfigReader = new YamlConfigReader();
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
        final ConfigReader yamlConfigReader = new YamlConfigReader();
        final List<LinkedHashMap> yamlList = getYamlList("test-no-actions.yaml");
        final Map<String, Set<AuthorizableConfigBean>> groups = yamlConfigReader.getGroupConfigurationBeans(yamlList, null);
        final Map<String, Set<AceBean>> acls = yamlConfigReader.getAceConfigurationBeans(yamlList, groups.keySet(), null);
        final Set<AceBean> acl = acls.get("groupA");
        final AceBean ace = acl.iterator().next();
        assertEquals("Number of actions", 0, ace.getActions().length);
    }

    @Test
    public void testMultipleAcesSamePath() throws IOException, AcConfigBeanValidationException, RepositoryException {
        final ConfigReader yamlConfigReader = new YamlConfigReader();
        final List<LinkedHashMap> yamlList = getYamlList("test-multiple-aces-same-path.yaml");
        final Map<String, Set<AuthorizableConfigBean>> groups = yamlConfigReader.getGroupConfigurationBeans(yamlList, null);
        final Map<String, Set<AceBean>> acls = yamlConfigReader.getAceConfigurationBeans(yamlList, groups.keySet(), null);
        assertEquals("Number of ACLs", 3, acls.get("groupA").size());
    }

    @Test
    public void testEmptyGlobVsNoGlob() throws Exception {
        final ConfigReader yamlConfigReader = new YamlConfigReader();
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
        final ConfigReader yamlConfigReader = new YamlConfigReader();
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

    /** Test support for rep:userManagement privilege name */
    @Test
    @Ignore
    public void testUserManagementPrivilege() throws IOException, AcConfigBeanValidationException, RepositoryException {
        final ConfigReader yamlConfigReader = new YamlConfigReader();
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
        final ConfigReader yamlConfigReader = new YamlConfigReader();
        final List<LinkedHashMap> yamlList = getYamlList("test-membergroups.yaml");
        final Map<String, Set<AuthorizableConfigBean>> groups = yamlConfigReader.getGroupConfigurationBeans(yamlList, null);
        assertEquals("Number of groups", 4, groups.size());
        assertEquals("", groups.get("groupA").iterator().next().getMembersStringFromConfig());
        assertEquals("groupA", groups.get("groupB").iterator().next().getMembersStringFromConfig());
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
