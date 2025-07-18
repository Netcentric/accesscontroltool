package biz.netcentric.cq.tools.actool.ims;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2024 Cognizant Netcentric
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.osgi.services.HttpClientBuilderFactory;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import biz.netcentric.cq.tools.actool.ims.IMSUserManagement.Configuration;
import biz.netcentric.cq.tools.actool.ims.request.ActionCommand;
import biz.netcentric.cq.tools.actool.ims.request.AddGroupMembership;
import biz.netcentric.cq.tools.actool.ims.request.RemoveGroupMembership;
import biz.netcentric.cq.tools.actool.ims.request.UserActionCommand;
import biz.netcentric.cq.tools.actool.ims.response.GroupResponse;
import biz.netcentric.cq.tools.actool.ims.response.IMSGroup;
import biz.netcentric.cq.tools.actool.ims.response.UsersInGroupResponse;

@ExtendWith(MockitoExtension.class)
class IMSUserManagementTest {

    private static final String MOCK_TOKEN = "mockToken";
    @Mock
    private Configuration configuration;
    @Mock
    private HttpClientBuilderFactory httpClientBuilderFactory;
    @Mock
    private HttpClientBuilder httpClientBuilder;

    @BeforeEach
    void setUp() {
        Mockito.when(httpClientBuilderFactory.newBuilder()).thenReturn(httpClientBuilder);
    }

    @Test
    void testGetGroups() throws IOException {
        IMSUserManagement imsUserManagement = new IMSUserManagement(configuration, httpClientBuilderFactory) {
            @Override
            GroupResponse getGroups(String token, int page) throws java.io.IOException {
                try (InputStream inputStream = getClass().getResourceAsStream("groupResponse" + page +".json")) {
                    Objects.requireNonNull(inputStream, "Resource not found: groupResponse" + page + ".json");
                    ObjectMapper objectMapper = new ObjectMapper();
                    return objectMapper.readValue(inputStream, GroupResponse.class);
                }
            }
        };
        Map<String, IMSGroup> groups = imsUserManagement.getGroups(MOCK_TOKEN);
        assertEquals(2, groups.size());
        // check keys only
        Set<String> expectedKeys = new HashSet<>(Arrays.asList("document cloud 1", "document cloud 2"));
        assertEquals(expectedKeys, groups.keySet());
    }

    @Test
    void testUpdateGroupAdminsCommands() throws IOException {
        Mockito.when(configuration.groupAdmins()).thenReturn(new String[]{"admin1", "admin2", "admin3"});
        IMSUserManagement imsUserManagement = new IMSUserManagement(configuration, httpClientBuilderFactory) {
            @Override
            UsersInGroupResponse getUsersInGroup(String token, String name, int page) throws java.io.IOException {
                String resourceName = "usersInGroup" + name + "." + page +".json";
                try (InputStream inputStream = getClass().getResourceAsStream(resourceName)) {
                    Objects.requireNonNull(inputStream, "Resource not found: " + resourceName);
                    ObjectMapper objectMapper = new ObjectMapper();
                    return objectMapper.readValue(inputStream, UsersInGroupResponse.class);
                }
            }
        };
        List<ActionCommand> updateGroupAdminsCommands = imsUserManagement.updateGroupAdminsCommands(MOCK_TOKEN, Arrays.asList("group1", "group2", "group3"), false);
        List<UserActionCommand> expectedUserActionCommands = new LinkedList<>();
        UserActionCommand userActionCommand = new UserActionCommand("admin1");
        userActionCommand.addStep(new AddGroupMembership(Arrays.asList("_admin_group2", "_admin_group3")));
        expectedUserActionCommands.add(userActionCommand);
        
        userActionCommand = new UserActionCommand("admin2");
        userActionCommand.addStep(new AddGroupMembership(Arrays.asList("_admin_group1", "_admin_group3")));
        expectedUserActionCommands.add(userActionCommand);

        userActionCommand = new UserActionCommand("admin3");
        userActionCommand.addStep(new AddGroupMembership(Arrays.asList("_admin_group1", "_admin_group2", "_admin_group3")));
        expectedUserActionCommands.add(userActionCommand);
        
        userActionCommand = new UserActionCommand("john");
        userActionCommand.addStep(new RemoveGroupMembership(Arrays.asList("_admin_group2")));
        expectedUserActionCommands.add(userActionCommand);
        
        userActionCommand = new UserActionCommand("jane");
        userActionCommand.addStep(new RemoveGroupMembership(Arrays.asList("_admin_group1")));
        expectedUserActionCommands.add(userActionCommand);
        
        MatcherAssert.assertThat(updateGroupAdminsCommands, Matchers.containsInAnyOrder(
                expectedUserActionCommands.toArray(new UserActionCommand[0])));
    }
}
