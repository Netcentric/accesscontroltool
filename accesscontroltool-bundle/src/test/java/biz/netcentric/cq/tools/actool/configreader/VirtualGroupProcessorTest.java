package biz.netcentric.cq.tools.actool.configreader;


import static biz.netcentric.cq.tools.actool.configreader.YamlConfigurationMergerTest.getAcConfigurationForFile;
import static biz.netcentric.cq.tools.actool.configreader.YamlConfigurationMergerTest.getConfigurationMerger;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import javax.jcr.Session;

import org.apache.commons.lang3.ArrayUtils;
import org.junit.Test;
import org.mockito.Mock;

import biz.netcentric.cq.tools.actool.configmodel.AcConfiguration;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;


public class VirtualGroupProcessorTest {

    @Mock
    Session session;

    @Test
    public void testVirtualGroups() throws Exception {

        AcConfiguration acConfiguration = getAcConfigurationForFile(getConfigurationMerger(), session, "test-virtualgroups.yaml");

        assertNotNull(acConfiguration.getAuthorizablesConfig().getAuthorizableConfig("groupBase"));

        assertNull("virtual group must be gone", acConfiguration.getAuthorizablesConfig().getAuthorizableConfig("groupVirtual"));

        AuthorizableConfigBean group1UsingVirtual = acConfiguration.getAuthorizablesConfig().getAuthorizableConfig("group1UsingVirtual");
        assertNotNull(group1UsingVirtual);
        assertTrue("isMemberOf should now contain 'groupBase' instead of 'groupVirtual'",
                ArrayUtils.contains(group1UsingVirtual.getIsMemberOf(), "groupBase"));
        assertFalse("isMemberOf should not contain 'groupVirtual' anymore",
                ArrayUtils.contains(group1UsingVirtual.getIsMemberOf(), "groupVirtual"));
        AuthorizableConfigBean group2UsingVirtual = acConfiguration.getAuthorizablesConfig().getAuthorizableConfig("group2UsingVirtual");
        assertNotNull(group2UsingVirtual);
        assertTrue("isMemberOf should now contain 'groupBase' instead of 'groupVirtual'",
                ArrayUtils.contains(group2UsingVirtual.getIsMemberOf(), "groupBase"));
        assertFalse("isMemberOf should not contain 'groupVirtual' anymore",
                ArrayUtils.contains(group2UsingVirtual.getIsMemberOf(), "groupVirtual"));

        assertEquals("Number of authorizables", 3, acConfiguration.getAuthorizablesConfig().size());

        assertEquals("Number of ACEs", 4, acConfiguration.getAceConfig().size());

    }
    
    @Test
    public void testVirtualGroupsInheritFromMultiple() throws Exception {

        AcConfiguration acConfiguration = getAcConfigurationForFile(getConfigurationMerger(), session, "test-virtualgroups-inherit-from-multiple-vgroups.yaml");

        assertNotNull(acConfiguration.getAuthorizablesConfig().getAuthorizableConfig("groupUsingVirtual"));

        assertNull("virtual group must be gone", acConfiguration.getAuthorizablesConfig().getAuthorizableConfig("groupVirtual1"));
        assertNull("virtual group must be gone", acConfiguration.getAuthorizablesConfig().getAuthorizableConfig("groupVirtual2"));
        assertNull("virtual group must be gone", acConfiguration.getAuthorizablesConfig().getAuthorizableConfig("groupVirtual3"));

        assertEquals("Number of authorizables", 1, acConfiguration.getAuthorizablesConfig().size());

        assertEquals("Number of ACEs", 5, acConfiguration.getAceConfig().size());
        assertEquals("Number of ACEs", 5, acConfiguration.getAceConfig().filterByAuthorizableId("groupUsingVirtual").size());
        
    }    
    

    @Test
    public void testVirtualGroupsMultipleLevels() throws Exception {

        AcConfiguration acConfiguration = getAcConfigurationForFile(getConfigurationMerger(), session,
                "test-virtualgroups-multiple-levels.yaml");

        assertNotNull(acConfiguration.getAuthorizablesConfig().getAuthorizableConfig("groupBase"));

        assertNull("virtual group must be gone", acConfiguration.getAuthorizablesConfig().getAuthorizableConfig("groupVirtual1"));
        assertNull("virtual group must be gone", acConfiguration.getAuthorizablesConfig().getAuthorizableConfig("groupVirtual2"));
        assertNull("virtual group must be gone", acConfiguration.getAuthorizablesConfig().getAuthorizableConfig("groupVirtual3"));

        AuthorizableConfigBean groupUsingVirtual = acConfiguration.getAuthorizablesConfig().getAuthorizableConfig("groupUsingVirtual");
        assertNotNull(groupUsingVirtual);
        assertTrue("isMemberOf should now contain 'groupBase' instead of 'groupVirtual3'",
                ArrayUtils.contains(groupUsingVirtual.getIsMemberOf(), "groupBase"));
        assertFalse("isMemberOf should not contain 'groupVirtual3' anymore",
                ArrayUtils.contains(groupUsingVirtual.getIsMemberOf(), "groupVirtual3"));
        
        assertEquals(5, acConfiguration.getAceConfig().size());
        assertEquals(1, acConfiguration.getAceConfig().filterByAuthorizableId("groupBase").size());
        assertEquals(4, acConfiguration.getAceConfig().filterByAuthorizableId("groupUsingVirtual").size());

    }

    @Test(expected = IllegalArgumentException.class)
    public void testVirtualGroupsInvalidMembersAttribute() throws Exception {

        getAcConfigurationForFile(getConfigurationMerger(), session, "test-virtualgroups-invalid-use-of-members.yaml");

    }

}
