package biz.netcentric.cq.tools.actool.ims;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.osgi.services.HttpClientBuilderFactory;
import org.junit.jupiter.api.Test;
import org.osgi.util.converter.Converters;

import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.ims.IMSUserManagement;
import biz.netcentric.cq.tools.actool.ims.IMSUserManagement.Configuration;

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

    @Test
    void test() throws IOException {
        Map<String, Object> properties = new HashMap<>();
        properties.put("organizationId", getMandatoryEnvironmentVariable("ACTOOL_IMS_IT_ORGANIZATIONID"));
        properties.put("clientId", getMandatoryEnvironmentVariable("ACTOOL_IMS_IT_CLIENTID"));
        properties.put("clientSecret", getMandatoryEnvironmentVariable("ACTOOL_IMS_IT_CLIENTSECRET"));
        properties.put("isTestOnly", Boolean.TRUE);
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
        imsUserManagement.updateGroups(Collections.singleton(group));
        
        // test without description
        AuthorizableConfigBean group2 = new AuthorizableConfigBean();
        group2.setAuthorizableId("testGroup");
        imsUserManagement.updateGroups(Collections.singleton(group2));
        
        // test with empty description
        AuthorizableConfigBean group3 = new AuthorizableConfigBean();
        group3.setAuthorizableId("testGroup");
        group3.setDescription("");
        imsUserManagement.updateGroups(Collections.singleton(group3));
    }

    private static String getMandatoryEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null) {
            throw new IllegalStateException("Missing environment variable \"" + name + "\" which is necessary for the IT in " + IMSUserManagementIT.class);
        }
        return value;
    }
}
