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

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.osgi.services.HttpClientBuilderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import biz.netcentric.cq.tools.actool.ims.IMSUserManagement.Configuration;
import biz.netcentric.cq.tools.actool.ims.response.GroupResponse;
import biz.netcentric.cq.tools.actool.ims.response.IMSGroup;

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

}
