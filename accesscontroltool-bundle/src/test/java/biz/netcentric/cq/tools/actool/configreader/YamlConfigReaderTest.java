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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.yaml.snakeyaml.Yaml;

import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;
import biz.netcentric.cq.tools.actool.validators.impl.AceBeanValidatorImpl;

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
        final Map<String, Set<AuthorizableConfigBean>> groups = yamlConfigReader.getGroupConfigurationBeans(yamlList, null);
        final Set<AceBean> acls = yamlConfigReader.getAceConfigurationBeans(yamlList, groups.keySet(), null, session);
        final Set<AceBean> acl = filterList(acls, "groupA");
        for (final AceBean ace : acl) {
            assertNotNull("Testing null actions", ace.getActions());
        }
    }

    @Test
    public void testNoActions() throws IOException, AcConfigBeanValidationException, RepositoryException {
        final ConfigReader yamlConfigReader = new YamlConfigReader();
        final List<LinkedHashMap> yamlList = getYamlList("test-no-actions.yaml");
        final Map<String, Set<AuthorizableConfigBean>> groups = yamlConfigReader.getGroupConfigurationBeans(yamlList, null);
        final Set<AceBean> acls = yamlConfigReader.getAceConfigurationBeans(yamlList, groups.keySet(), null, session);
        final Set<AceBean> acl = filterList(acls, "groupA");
        final AceBean ace = acl.iterator().next();
        assertEquals("Number of actions", 0, ace.getActions().length);
    }

    @Test
    public void testMultipleAcesSamePath() throws IOException, AcConfigBeanValidationException, RepositoryException {
        final ConfigReader yamlConfigReader = new YamlConfigReader();
        final List<LinkedHashMap> yamlList = getYamlList("test-multiple-aces-same-path.yaml");
        final Map<String, Set<AuthorizableConfigBean>> groups = yamlConfigReader.getGroupConfigurationBeans(yamlList, null);
        final Set<AceBean> acls = yamlConfigReader.getAceConfigurationBeans(yamlList, groups.keySet(), null, session);
        assertEquals("Number of ACLs", 3, filterList(acls, "groupA").size());
    }

    @Test
    public void testEmptyGlobVsNoGlob() throws Exception {
        final ConfigReader yamlConfigReader = new YamlConfigReader();
        final List<LinkedHashMap> yamlList = getYamlList("test-empty-glob.yaml");
        final Map<String, Set<AuthorizableConfigBean>> groups = yamlConfigReader.getGroupConfigurationBeans(yamlList, null);
        final Set<AceBean> acls = yamlConfigReader.getAceConfigurationBeans(yamlList, groups.keySet(), null, session);
        final Iterator<AceBean> it = filterList(acls, "groupA").iterator();
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
        Set<AceBean> acls = yamlConfigReader.getAceConfigurationBeans(yamlList, groups.keySet(), null, session);
        assertNull("No ACEs", acls);
        yamlList = getYamlList("test-no-groups.yaml");
        groups = yamlConfigReader.getGroupConfigurationBeans(yamlList, null);
        assertNull("No groups", groups);
        acls = yamlConfigReader.getAceConfigurationBeans(yamlList, null, null, session);
        assertNotNull("ACL for groupA", filterList(acls, "groupA"));
        assertEquals("Number of ACEs", 1, filterList(acls, "groupA").size());
    }

    /** Test support for rep:userManagement privilege name */
    @Test
    @Ignore
    public void testUserManagementPrivilege() throws IOException, AcConfigBeanValidationException, RepositoryException {
        final ConfigReader yamlConfigReader = new YamlConfigReader();
        final List<LinkedHashMap> yamlList = getYamlList("test-rep-usermanagement.yaml");
        final Map<String, Set<AuthorizableConfigBean>> groups = yamlConfigReader.getGroupConfigurationBeans(yamlList, null);
        final Set<AceBean> acls = yamlConfigReader.getAceConfigurationBeans(yamlList, groups.keySet(),
                new AceBeanValidatorImpl(groups.keySet()), session);
        final Set<AceBean> acl = filterList(acls, "groupA");
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

    public static Set<AceBean> filterList(Set<AceBean> acls, String id) {
        Set<AceBean> aclsFiltered = new LinkedHashSet<AceBean>();
        for (AceBean bean : acls) {
            if (StringUtils.equals(id, bean.getPrincipalName())) {
                aclsFiltered.add(bean);
            }
        }
        return aclsFiltered;
    }
}
