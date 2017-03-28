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
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.jcr.RepositoryException;

import org.junit.Before;
import org.junit.Test;

import biz.netcentric.cq.tools.actool.configmodel.AcConfiguration;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;
import biz.netcentric.cq.tools.actool.validators.impl.ObsoleteAuthorizablesValidatorImpl;

/** Tests the YamlConfigurationMerger
 *
 * @author Roland Gruber */
public class YamlConfigurationMergerTest {

    private YamlConfigurationMerger merger;

    @Before
    public void setup() {
        merger = new YamlConfigurationMerger();
        merger.yamlMacroProcessor = new YamlMacroProcessorImpl();
        merger.obsoleteAuthorizablesValidator = new ObsoleteAuthorizablesValidatorImpl();
    }

    @Test
    public void testMemberGroups() throws IOException, RepositoryException, AcConfigBeanValidationException {
        final String config = YamlConfigReaderTest.getTestConfigAsString("test-membergroups.yaml");
        final ConfigReader reader = new YamlConfigReader();
        final Map<String, String> configs = new HashMap<String, String>();
        configs.put("/etc/config", config);
        AcConfiguration acConfiguration = merger.getMergedConfigurations(configs, mock(AcInstallationHistoryPojo.class), reader);
        final Map<String, Set<AuthorizableConfigBean>> groups = acConfiguration.getAuthorizablesConfig();
        final AuthorizableConfigBean groupA = groups.get("groupA").iterator().next();
        assertEquals(3, groupA.getMemberOf().length);
        final AuthorizableConfigBean groupB = groups.get("groupB").iterator().next();
        assertEquals(2, groupB.getMemberOf().length);
        groups.get("groupC").iterator().next();
        groups.get("groupD").iterator().next();
    }

    @Test
    public void testEnsureIsMemberOfIsUsedWherePossible() throws RepositoryException, IOException, AcConfigBeanValidationException {
        
        final String config = YamlConfigReaderTest.getTestConfigAsString("test-membergroups.yaml");
        final ConfigReader reader = new YamlConfigReader();
        final Map<String, String> configs = new HashMap<String, String>();
        configs.put("/etc/config", config);

        AcConfiguration acConfiguration = merger.getMergedConfigurations(configs, mock(AcInstallationHistoryPojo.class), reader);
        
        AuthorizableConfigBean groupAConfig = acConfiguration.getAuthorizablesConfig().get("groupA").iterator().next();
        assertEquals("groupA", groupAConfig.getPrincipalID());
        String[] members = groupAConfig.getMemberOf();

        assertArrayEquals("check if groups have been copied over into isMemberOf field",
                new String[] { "groupB", "groupC", "groupD" }, members);
        //
        AuthorizableConfigBean groupBConfig = acConfiguration.getAuthorizablesConfig().get("groupB").iterator().next();

        assertArrayEquals("ensure that members that were added to isMemberOf are removed from original members arr", new String[0],
                groupBConfig.getMembers());
    }

}
