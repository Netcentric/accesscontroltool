package biz.netcentric.cq.tools.actool.ims;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2024 Cognizant Netcentric
 * %%
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * #L%
 */

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.osgi.services.HttpClientBuilderFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.util.converter.Converters;

import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.ims.IMSUserManagement.Configuration;
import biz.netcentric.cq.tools.actool.ims.response.IMSGroup;
import biz.netcentric.cq.tools.actool.ims.response.IMSUser;

/**
 * Example Adobe Developer Console Project: https://developer.adobe.com/console/projects/25605/4566206088345177434/overview (Organization: Netcentric).
 * This IT requires the following environment variables to be set:
 * <ul>
 * <li>actool_ims_it_organizationid</li>
 * <li>actool_ims_it_clientid</li>
 * <li>actool_ims_it_clientsecret</li>
 * </ul>
 */
class IMSUserManagementIT {

    Map<String, Object> properties;

    @BeforeEach
    void setUp() {
        String orgId = System.getenv("ACTOOL_IMS_IT_ORGANIZATIONID");
        Assumptions.assumeTrue(orgId != null, "Skipping IMS ITs because environment variable 'ACTOOL_IMS_IT_ORGANIZATIONID' is not set!");
        properties = new HashMap<>();
        properties.put("organizationId", getMandatoryEnvironmentVariable("ACTOOL_IMS_IT_ORGANIZATIONID"));
        properties.put("clientId", getMandatoryEnvironmentVariable("ACTOOL_IMS_IT_CLIENTID"));
        properties.put("clientSecret", getMandatoryEnvironmentVariable("ACTOOL_IMS_IT_CLIENTSECRET"));
        properties.put("isTestOnly", Boolean.TRUE);
        properties.put("socketTimeout", "60000");
    }

    @Test
    void testAddSimpleGroup() throws IOException {
        Configuration config = Converters.standardConverter().convert(properties).to(Configuration.class);
        IMSUserManagement imsUserManagement = new IMSUserManagement(config, new HttpClientBuilderFactory() {
            @Override
            public HttpClientBuilder newBuilder() {
                return HttpClientBuilder.create();
            }
        });
        assertEquals("Adobe IMS", imsUserManagement.getLabel());
        AuthorizableConfigBean group = new AuthorizableConfigBean();
        group.setAuthorizableId("testGroup");
        group.setDescription("my description");
        assertEquals(1, imsUserManagement.updateGroups(Collections.singleton(group)));

        // test without description
        AuthorizableConfigBean group2 = new AuthorizableConfigBean();
        group2.setAuthorizableId("testGroup");
        assertEquals(1, imsUserManagement.updateGroups(Collections.singleton(group2)));

        // test with empty description
        AuthorizableConfigBean group3 = new AuthorizableConfigBean();
        group3.setAuthorizableId("testGroup");
        group3.setDescription("");
        assertEquals(1, imsUserManagement.updateGroups(Collections.singleton(group3)));
    }

    @Test
    void testAddAlreadyExistingGroupInDifferentialUpdatesMode() throws IOException {
        properties.put("isDifferentialUpdates", Boolean.TRUE);
        Configuration config = Converters.standardConverter().convert(properties).to(Configuration.class);
        IMSUserManagement imsUserManagement = new IMSUserManagement(config, new HttpClientBuilderFactory() {
            @Override
            public HttpClientBuilder newBuilder() {
                return HttpClientBuilder.create();
            }
        });
        AuthorizableConfigBean group = new AuthorizableConfigBean();
        group.setAuthorizableId("testGroup"); // this group is already there
        assertEquals(0, imsUserManagement.updateGroups(Collections.singleton(group)));
    }

    @Test
    void testAddGroupWithProductProfileMembership() throws IOException  {
        properties.put("productProfiles", getMandatoryEnvironmentVariable("ACTOOL_IMS_IT_PRODUCTPROFILE"));
        Configuration config = Converters.standardConverter().convert(properties).to(Configuration.class);
        IMSUserManagement imsUserManagement = new IMSUserManagement(config, new HttpClientBuilderFactory() {
            @Override
            public HttpClientBuilder newBuilder() {
                return HttpClientBuilder.create();
            }
        });
        AuthorizableConfigBean group = new AuthorizableConfigBean();
        group.setAuthorizableId("testGroup");
        group.setDescription("my description");
        imsUserManagement.updateGroups(Collections.singleton(group));
    }

    @Test
    void testAddGroupWithInvalidProductProfileMembership() {
        properties.put("productProfiles", "invalid");
        Configuration config = Converters.standardConverter().convert(properties).to(Configuration.class);
        IMSUserManagement imsUserManagement = new IMSUserManagement(config, new HttpClientBuilderFactory() {
            @Override
            public HttpClientBuilder newBuilder() {
                return HttpClientBuilder.create();
            }
        });
        AuthorizableConfigBean group = new AuthorizableConfigBean();
        group.setAuthorizableId("testGroup");
        group.setDescription("my description");
        IOException t = assertThrows(IOException.class, () -> { imsUserManagement.updateGroups(Collections.singleton(group)); });
        assertTrue(t.getMessage().contains("error.plc.not_found"), "Exceptions message is supposed to contain 'error.plc.not_found' but was " + t.getMessage());
    }

    @Test
    void testAddGroupWithAdmin() throws IOException {
        properties.put("groupAdmins", getMandatoryEnvironmentVariable("ACTOOL_IMS_IT_USERID"));
        Configuration config = Converters.standardConverter().convert(properties).to(Configuration.class);
        IMSUserManagement imsUserManagement = new IMSUserManagement(config, new HttpClientBuilderFactory() {
            @Override
            public HttpClientBuilder newBuilder() {
                return HttpClientBuilder.create();
            }
        });
        AuthorizableConfigBean group = new AuthorizableConfigBean();
        group.setAuthorizableId("testGroup");
        group.setDescription("my description");
        assertEquals(1, imsUserManagement.updateGroups(Collections.singleton(group)));
    }

    @Test
    void testAdd25GroupsWithAdmin() throws IOException {
        properties.put("groupAdmins", getMandatoryEnvironmentVariable("ACTOOL_IMS_IT_USERID"));
        Configuration config = Converters.standardConverter().convert(properties).to(Configuration.class);
        IMSUserManagement imsUserManagement = new IMSUserManagement(config, new HttpClientBuilderFactory() {
            @Override
            public HttpClientBuilder newBuilder() {
                return HttpClientBuilder.create();
            }
        });
        List<AuthorizableConfigBean> groups = new LinkedList<>();
        for (int n=0; n<25; n++) {
            AuthorizableConfigBean group = new AuthorizableConfigBean();
            group.setAuthorizableId("testGroup" + n);
            group.setDescription("my description" + n);
            groups.add(group);
        }
        assertEquals(25, imsUserManagement.updateGroups(groups));
    }

    @Test
    void testGetGroups() throws IOException {
        Configuration config = Converters.standardConverter().convert(properties).to(Configuration.class);
        IMSUserManagement imsUserManagement = new IMSUserManagement(config, new HttpClientBuilderFactory() {
            @Override
            public HttpClientBuilder newBuilder() {
                return HttpClientBuilder.create();
            }
        });
        String token = imsUserManagement.getOAuthServer2ServerToken();
        Map<String, IMSGroup> groups = imsUserManagement.getGroups(token);
        assertFalse(groups.isEmpty());
    }

    @Test
    void testGetUserInGroup() throws IOException {
        Configuration config = Converters.standardConverter().convert(properties).to(Configuration.class);
        IMSUserManagement imsUserManagement = new IMSUserManagement(config, new HttpClientBuilderFactory() {
            @Override
            public HttpClientBuilder newBuilder() {
                return HttpClientBuilder.create();
            }
        });
        String token = imsUserManagement.getOAuthServer2ServerToken();
        Map<String, IMSUser> users = imsUserManagement.getUsersInGroup(token, "AEM Users-3e6e8bd0a05f39bc82d788bb27ac83b4");
        assertFalse(users.isEmpty());
    }

    private static String getMandatoryEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null) {
            throw new IllegalStateException("Missing environment variable \"" + name + "\" which is necessary for the IT in " + IMSUserManagementIT.class);
        }
        return value;
    }
}
