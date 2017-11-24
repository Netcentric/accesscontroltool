package biz.netcentric.cq.tools.actool.configmodel;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.Test;

public class AuthorizablesConfigTest {

    @Test
    public void testRemoveUnmanagedPrincipalNamesAtPath() {
        AuthorizablesConfig authorizablesConfig = new AuthorizablesConfig();

        AuthorizableConfigBean beanTestGroupAllManaged = getBean("testgroupAllManaged", null);
        authorizablesConfig.add(beanTestGroupAllManaged);

        AuthorizableConfigBean testgroupPartlyManaged = getBean("testgroupPartlyManaged", "/content/dam/geometrixx.*");
        authorizablesConfig.add(testgroupPartlyManaged);

        // example for negative look-ahead to only manage certain paths as useful for everyone
        AuthorizableConfigBean beanEveryone = getBean("everyone", "^(?!/etc/linkchecker|/etc/test).*" /*
                                                                                                       * only manage /etc/linkchecker and
                                                                                                       * /etc/test
                                                                                                       */ );
        authorizablesConfig.add(beanEveryone);

        Set<String> principalSet = principalSet(beanTestGroupAllManaged.getPrincipalName(), testgroupPartlyManaged.getPrincipalName(),
                beanEveryone.getPrincipalName());

        Set<String> onlyManagedPrincipalNames = authorizablesConfig.removeUnmanagedPrincipalNamesAtPath("/etc/linkchecker", principalSet);
        assertTrue(onlyManagedPrincipalNames.contains("testgroupAllManaged"));
        assertTrue(onlyManagedPrincipalNames.contains("testgroupPartlyManaged"));
        assertTrue(onlyManagedPrincipalNames.contains("everyone"));

        onlyManagedPrincipalNames = authorizablesConfig.removeUnmanagedPrincipalNamesAtPath("/etc", principalSet);
        assertTrue(onlyManagedPrincipalNames.contains("testgroupAllManaged"));
        assertTrue(onlyManagedPrincipalNames.contains("testgroupPartlyManaged"));
        assertFalse(onlyManagedPrincipalNames.contains("everyone"));

        onlyManagedPrincipalNames = authorizablesConfig.removeUnmanagedPrincipalNamesAtPath("/content/geometrixx", principalSet);
        assertTrue(onlyManagedPrincipalNames.contains("testgroupAllManaged"));
        assertTrue(onlyManagedPrincipalNames.contains("testgroupPartlyManaged"));
        assertFalse(onlyManagedPrincipalNames.contains("everyone"));

        onlyManagedPrincipalNames = authorizablesConfig.removeUnmanagedPrincipalNamesAtPath("/content/dam/geometrixx", principalSet);
        assertTrue(onlyManagedPrincipalNames.contains("testgroupAllManaged"));
        assertFalse(onlyManagedPrincipalNames.contains("testgroupPartlyManaged"));
        assertFalse(onlyManagedPrincipalNames.contains("everyone"));

    }

    private AuthorizableConfigBean getBean(String name, String unmanagedAcePathRegex) {
        AuthorizableConfigBean beanEveryone = new AuthorizableConfigBean();
        beanEveryone.setPrincipalName(name);
        beanEveryone.setAuthorizableId(name);
        beanEveryone.setUnmanagedAcePathsRegex(unmanagedAcePathRegex);
        return beanEveryone;
    }

    private Set<String> principalSet(String... principalNames) {
        return new LinkedHashSet<String>(Arrays.asList(principalNames));
    }

}
