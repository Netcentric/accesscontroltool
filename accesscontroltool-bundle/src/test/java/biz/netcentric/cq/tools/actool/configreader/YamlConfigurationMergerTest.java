/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configreader;

import static org.junit.Assert.*;
import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.anySet;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.MockitoAnnotations.initMocks;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import biz.netcentric.cq.tools.actool.configmodel.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import biz.netcentric.cq.tools.actool.history.PersistableInstallationLogger;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;
import biz.netcentric.cq.tools.actool.validators.impl.ObsoleteAuthorizablesValidatorImpl;

public class YamlConfigurationMergerTest {

    @Mock
    Session session;

    @Before
    public void setup() {
        initMocks(this);
    }

    @Test
    public void testMemberGroups() throws IOException, RepositoryException, AcConfigBeanValidationException {
        AcConfiguration acConfiguration = getAcConfigurationForFile(getConfigurationMerger(), session, "test-membergroups.yaml");
        final AuthorizablesConfig groups = acConfiguration.getAuthorizablesConfig();
        final AuthorizableConfigBean groupA = groups.getAuthorizableConfig("groupA");
        assertEquals(3, groupA.getIsMemberOf().length);
        final AuthorizableConfigBean groupB = groups.getAuthorizableConfig("groupB");
        assertEquals(2, groupB.getIsMemberOf().length);
        assertNotNull(groups.getAuthorizableConfig("groupC"));
        assertNotNull(groups.getAuthorizableConfig("groupD"));

    }

    @Test
    public void testEnsureIsMemberOfIsUsedWherePossible() throws RepositoryException, IOException, AcConfigBeanValidationException {
        AcConfiguration acConfiguration = getAcConfigurationForFile(getConfigurationMerger(), session, "test-membergroups.yaml");

        AuthorizableConfigBean groupAConfig = acConfiguration.getAuthorizablesConfig().getAuthorizableConfig("groupA");
        assertEquals("groupA", groupAConfig.getAuthorizableId());
        String[] members = groupAConfig.getIsMemberOf();

        assertArrayEquals("check if groups have been copied over into isMemberOf field",
                new String[] { "groupB", "groupC", "groupD" }, members);
        //
        AuthorizableConfigBean groupBConfig = acConfiguration.getAuthorizablesConfig().getAuthorizableConfig("groupB");

        assertArrayEquals("ensure that members that were added to isMemberOf are removed from original members arr", new String[0],
                groupBConfig.getMembers());
    }

    @Test(expected = AcConfigBeanValidationException.class)
    public void testReadInvalidYaml1() throws IOException, RepositoryException, AcConfigBeanValidationException {
        getAcConfigurationForFile(getConfigurationMerger(), session, "test-invalid1.yaml");
    }

    @Test(expected = AcConfigBeanValidationException.class)
    public void testReadInvalidYaml2() throws IOException, RepositoryException, AcConfigBeanValidationException {
        getAcConfigurationForFile(getConfigurationMerger(), session, "test-invalid2.yaml");
    }

    @Test()
    public void testReadEmptyYaml() throws IOException, RepositoryException, AcConfigBeanValidationException {
        getAcConfigurationForFile(getConfigurationMerger(), session, "test-empty.yaml");
    }
   
    @Test
    public void testMergePrivilegeConfiguration() throws RepositoryException, IOException, AcConfigBeanValidationException {
        YamlConfigurationMerger merger = getConfigurationMerger();

        ConfigReader reader = new YamlConfigReader();
        Map<String, String> configs = new HashMap<String, String>();
        configs.put("/etc/config-a", YamlConfigReaderTest.getTestConfigAsString("test-merge-privileges-a.yaml"));
        configs.put("/etc/config-b", YamlConfigReaderTest.getTestConfigAsString("test-merge-privileges-b.yaml"));
        AcConfiguration acConfiguration = merger.getMergedConfigurations(configs, new PersistableInstallationLogger(), reader, session);

        PrivilegeConfig privilegeConfig = acConfiguration.getPrivilegeConfig();
        assertEquals(2, privilegeConfig.size());

        Iterator<PrivilegeBean> it = privilegeConfig.iterator();
        PrivilegeBean bean1 = it.next();
        assertEquals("sling:feature1", bean1.getPrivilegeName());

        PrivilegeBean bean2 = it.next();
        assertEquals("sling:feature2", bean2.getPrivilegeName());

    }

    @Test
    public void testMergePrivilegeThrowExceptionOnDuplicateConfig() throws RepositoryException, IOException, AcConfigBeanValidationException {
        YamlConfigurationMerger merger = getConfigurationMerger();

        ConfigReader reader = new YamlConfigReader();
        Map<String, String> configs = new HashMap<String, String>();
        configs.put("/etc/config-a", YamlConfigReaderTest.getTestConfigAsString("test-merge-privileges-a.yaml"));
        configs.put("/etc/config-b", YamlConfigReaderTest.getTestConfigAsString("test-merge-privileges-a.yaml"));
        try {
            AcConfiguration acConfiguration = merger.getMergedConfigurations(configs, new PersistableInstallationLogger(), reader, session);
            fail("expected exception");
        } catch (IllegalArgumentException e){
            assertTrue(e.getMessage().contains("Duplicate privilege configuration"));
        }

    }

    public static AcConfiguration getAcConfigurationForFile(YamlConfigurationMerger merger, Session session, String testConfigFile)
            throws IOException, RepositoryException, AcConfigBeanValidationException {
        final String config = YamlConfigReaderTest.getTestConfigAsString(testConfigFile);
        final ConfigReader reader = new YamlConfigReader();
        final Map<String, String> configs = new HashMap<String, String>();
        configs.put("/etc/config", config);
        AcConfiguration acConfiguration = merger.getMergedConfigurations(configs, new PersistableInstallationLogger(), reader, session);
        return acConfiguration;
    }

    public static YamlConfigurationMerger getConfigurationMerger() {
        YamlConfigurationMerger merger = spy(new YamlConfigurationMerger());
        doReturn(null).when(merger).getAceBeanValidator(anySet());
        merger.yamlMacroProcessor = new YamlMacroProcessorImpl();
        merger.obsoleteAuthorizablesValidator = new ObsoleteAuthorizablesValidatorImpl();
        merger.virtualGroupProcessor = new VirtualGroupProcessor();
        merger.testUserConfigsCreator = new TestUserConfigsCreator();
        return merger;
    }
}
