/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configreader;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.MockitoAnnotations.initMocks;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.yaml.snakeyaml.Yaml;

import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.configmodel.AcesConfig;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.configmodel.TestDecryptionService;
import biz.netcentric.cq.tools.actool.configmodel.pkcs.JcaPkcs8EncryptedPrivateKeyDecryptor;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;

public class YamlConfigReaderTest {

    @Mock
    Session session;

    @BeforeEach
    public void setup() {
        initMocks(this);
    }

    @Test
    public void testNullActions() throws IOException, AcConfigBeanValidationException, RepositoryException {

        final AcesConfig acls = readAcesFromTestFile("test-null-actions.yaml", new YamlConfigReader());
        final Set<AceBean> acl = acls.filterByAuthorizableId("groupA");
        for (final AceBean ace : acl) {
            assertNotNull(ace.getActions(), "Testing null actions");
        }
    }

    private AcesConfig readAcesFromTestFile(String testFile, ConfigReader yamlConfigReader)
            throws IOException, RepositoryException, AcConfigBeanValidationException {
        List<Map> yamlList = getYamlList(testFile);
        final AcesConfig acls = yamlConfigReader.getAceConfigurationBeans(yamlList, null, session, testFile);
        return acls;
    }

    @Test
    public void testNoActions() throws IOException, AcConfigBeanValidationException, RepositoryException {

        final AcesConfig acls = readAcesFromTestFile("test-no-actions.yaml", new YamlConfigReader());
        final Set<AceBean> acl = acls.filterByAuthorizableId("groupA");
        final AceBean ace = acl.iterator().next();
        assertEquals(0, ace.getActions().length, "Number of actions");
    }

    @Test
    public void testMultipleAcesSamePath() throws IOException, AcConfigBeanValidationException, RepositoryException {

        final AcesConfig acls = readAcesFromTestFile("test-multiple-aces-same-path.yaml", new YamlConfigReader());

        assertEquals(3, acls.filterByAuthorizableId("groupA").size(), "Number of ACLs");
        assertEquals(2, acls.filterByAuthorizableId("groupB").size(), "Number of ACLs");

        Iterator<AceBean> iterator = acls.iterator();
        while (iterator.hasNext()) {
            AceBean aceBean = iterator.next();
            assertEquals("/content", aceBean.getJcrPath());
        }
    }

    @Test
    public void testEmptyGlobVsNoGlob() throws Exception {
        final AcesConfig acls = readAcesFromTestFile("test-empty-glob.yaml", new YamlConfigReader());
        final Iterator<AceBean> it = acls.filterByAuthorizableId("groupA").iterator();
        final AceBean ace1 = it.next();
        assertNull(ace1.getRepGlob(), "repGlob");
        final AceBean ace2 = it.next();
        assertEquals("", ace2.getRepGlob(), "repGlob");
    }

    @Test
    public void testOptionalSections() throws Exception {
        final ConfigReader yamlConfigReader = new YamlConfigReader();
        List<Map> yamlList = getYamlList("test-no-aces.yaml");
        Set<AuthorizableConfigBean> groups = yamlConfigReader.getGroupConfigurationBeans(yamlList, null);
        AcesConfig acls = yamlConfigReader.getAceConfigurationBeans(yamlList, null, session, "test-no-aces.yaml");
        assertNull(acls, "No ACEs");
        yamlList = getYamlList("test-no-groups.yaml");
        groups = yamlConfigReader.getGroupConfigurationBeans(yamlList, null);
        assertNull(groups, "No groups");
        acls = yamlConfigReader.getAceConfigurationBeans(yamlList, null, session, "test-no-groups.yaml");
        assertNotNull(acls.filterByAuthorizableId("groupA"), "ACL for groupA");
        assertEquals(1, acls.filterByAuthorizableId("groupA").size(), "Number of ACEs");
    }

    @Test
    public void testMemberGroups() throws IOException, AcConfigBeanValidationException, RepositoryException {
        final ConfigReader yamlConfigReader = new YamlConfigReader();
        final List<Map> yamlList = getYamlList("test-membergroups.yaml");
        final Set<AuthorizableConfigBean> groups = yamlConfigReader.getGroupConfigurationBeans(yamlList, null);
        assertEquals(4, groups.size(), "Number of groups");

        Iterator<AuthorizableConfigBean> groupsIt = groups.iterator();
        AuthorizableConfigBean firstGroup = groupsIt.next();
        assertArrayEquals(null, firstGroup.getMembers());
        assertEquals("groupA", firstGroup.getAuthorizableId());

        AuthorizableConfigBean secondGroup = groupsIt.next();
        assertEquals("groupB", secondGroup.getAuthorizableId());
        assertArrayEquals(new String[]{"groupA"}, secondGroup.getMembers());

        AuthorizableConfigBean thirdGroup = groupsIt.next();
        assertEquals("groupC", thirdGroup.getAuthorizableId());
        assertEquals("/home/groups/g", thirdGroup.getPath()); // strip "/" at the end

    }

    @Test
    public void testKeys() throws IOException, AcConfigBeanValidationException {
        final YamlConfigReader yamlConfigReader = new YamlConfigReader();
        yamlConfigReader.decryptionService = new TestDecryptionService();
        yamlConfigReader.privateKeyDecryptor = new JcaPkcs8EncryptedPrivateKeyDecryptor();
        final List<Map> yamlList = getYamlList("test-user-with-keys.yaml");
        yamlConfigReader.getUserConfigurationBeans(yamlList, null);
    }

    @Test
    public void testInvalidKeys() throws IOException, AcConfigBeanValidationException {
        final ConfigReader yamlConfigReader = new YamlConfigReader();
        final List<Map> yamlList = getYamlList("test-user-with-invalid-keys.yaml");
        assertThrows(AcConfigBeanValidationException.class,
                () -> yamlConfigReader.getUserConfigurationBeans(yamlList, null));
    }

    @Test
    public void testCreateXPathQueryForPathWithWildcards() {
        assertEquals("/jcr:root/var/test", YamlConfigReader.createXPathQueryForPathWithWildcards("/var/test"));
        assertEquals("/jcr:root/var/*/_x0031_2node/*", YamlConfigReader.createXPathQueryForPathWithWildcards("/var/*/12node/*"));
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
