package biz.netcentric.cq.tools.actool.configreader;


import static biz.netcentric.cq.tools.actool.configreader.YamlConfigurationMergerTest.getAcConfigurationForFile;
import static biz.netcentric.cq.tools.actool.configreader.YamlConfigurationMergerTest.getConfigurationMerger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jcr.Session;

import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.Test;
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

        assertNull(acConfiguration.getAuthorizablesConfig().getAuthorizableConfig("groupVirtual"), "virtual group must be gone");

        AuthorizableConfigBean group1UsingVirtual = acConfiguration.getAuthorizablesConfig().getAuthorizableConfig("group1UsingVirtual");
        assertNotNull(group1UsingVirtual);
        assertTrue(ArrayUtils.contains(group1UsingVirtual.getIsMemberOf(), "groupBase"),
                "isMemberOf should now contain 'groupBase' instead of 'groupVirtual'");
        assertFalse(ArrayUtils.contains(group1UsingVirtual.getIsMemberOf(), "groupVirtual"),
                "isMemberOf should not contain 'groupVirtual' anymore");
        AuthorizableConfigBean group2UsingVirtual = acConfiguration.getAuthorizablesConfig().getAuthorizableConfig("group2UsingVirtual");
        assertNotNull(group2UsingVirtual);
        assertTrue(ArrayUtils.contains(group2UsingVirtual.getIsMemberOf(), "groupBase"),
                "isMemberOf should now contain 'groupBase' instead of 'groupVirtual'");
        assertFalse(ArrayUtils.contains(group2UsingVirtual.getIsMemberOf(), "groupVirtual"),
                "isMemberOf should not contain 'groupVirtual' anymore");

        assertEquals(3, acConfiguration.getAuthorizablesConfig().size(), "Number of authorizables");

        assertEquals(4, acConfiguration.getAceConfig().size(), "Number of ACEs");

    }

    @Test
    public void testVirtualGroupsInheritFromMultiple() throws Exception {

        AcConfiguration acConfiguration = getAcConfigurationForFile(getConfigurationMerger(), session, "test-virtualgroups-inherit-from-multiple-vgroups.yaml");

        assertNotNull(acConfiguration.getAuthorizablesConfig().getAuthorizableConfig("groupUsingVirtual"));

        assertNull(acConfiguration.getAuthorizablesConfig().getAuthorizableConfig("groupVirtual1"), "virtual group must be gone");
        assertNull(acConfiguration.getAuthorizablesConfig().getAuthorizableConfig("groupVirtual2"), "virtual group must be gone");
        assertNull(acConfiguration.getAuthorizablesConfig().getAuthorizableConfig("groupVirtual3"), "virtual group must be gone");

        assertEquals(1, acConfiguration.getAuthorizablesConfig().size(), "Number of authorizables");

        assertEquals(5, acConfiguration.getAceConfig().size(), "Number of ACEs");
        assertEquals(5, acConfiguration.getAceConfig().filterByAuthorizableId("groupUsingVirtual").size(), "Number of ACEs");

    }


    @Test
    public void testVirtualGroupsMultipleLevels() throws Exception {

        AcConfiguration acConfiguration = getAcConfigurationForFile(getConfigurationMerger(), session,
                "test-virtualgroups-multiple-levels.yaml");

        assertNotNull(acConfiguration.getAuthorizablesConfig().getAuthorizableConfig("groupBase"));

        assertNull(acConfiguration.getAuthorizablesConfig().getAuthorizableConfig("groupVirtual1"), "virtual group must be gone");
        assertNull(acConfiguration.getAuthorizablesConfig().getAuthorizableConfig("groupVirtual3"), "virtual group must be gone");
        assertNull(acConfiguration.getAuthorizablesConfig().getAuthorizableConfig("groupVirtual2"), "virtual group must be gone");

        AuthorizableConfigBean groupUsingVirtual = acConfiguration.getAuthorizablesConfig().getAuthorizableConfig("groupUsingVirtual");
        assertNotNull(groupUsingVirtual);
        assertTrue(ArrayUtils.contains(groupUsingVirtual.getIsMemberOf(), "groupBase"),
                "isMemberOf should now contain 'groupBase' instead of 'groupVirtual3'");
        assertFalse(ArrayUtils.contains(groupUsingVirtual.getIsMemberOf(), "groupVirtual3"),
                "isMemberOf should not contain 'groupVirtual3' anymore");

        assertEquals(5, acConfiguration.getAceConfig().size());
        assertEquals(1, acConfiguration.getAceConfig().filterByAuthorizableId("groupBase").size());
        assertEquals(4, acConfiguration.getAceConfig().filterByAuthorizableId("groupUsingVirtual").size());

    }

    @Test
    public void testVirtualGroupsInvalidMembersAttribute() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> getAcConfigurationForFile(getConfigurationMerger(), session, "test-virtualgroups-invalid-use-of-members.yaml"));
    }

}
