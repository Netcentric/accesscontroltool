package biz.netcentric.cq.tools.actool.configmodel;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

public class AuthorizablesConfigTest {

    AuthorizablesConfig authorizablesConfig;
    AuthorizableConfigBean beanTestGroupAllManaged;
    AuthorizableConfigBean testgroupPartlyManaged;
    AuthorizableConfigBean beanEveryone;

    @Before
    public void setup() {
        authorizablesConfig = new AuthorizablesConfig();

        beanTestGroupAllManaged = getBean("testgroupAllManaged", null);
        authorizablesConfig.add(beanTestGroupAllManaged);

        testgroupPartlyManaged = getBean("testgroupPartlyManaged", "/content/dam/geometrixx.*");
        authorizablesConfig.add(testgroupPartlyManaged);

        // example for negative look-ahead to only manage certain paths as useful for everyone
        beanEveryone = getBean("everyone", "^(?!/etc/linkchecker|/etc/test).*" /*
                                                                                * only manage /etc/linkchecker and /etc/test
                                                                                */ );
        authorizablesConfig.add(beanEveryone);
    }

    @Test
    public void testRemoveUnmanagedPrincipalNamesAtPath() {

        Set<String> principalSet = principalSet(beanTestGroupAllManaged.getPrincipalName(), testgroupPartlyManaged.getPrincipalName(),
                beanEveryone.getPrincipalName());

        Set<String> onlyManagedPrincipalNames = authorizablesConfig.removeUnmanagedPrincipalNamesAtPath("/etc/linkchecker", principalSet,
                null);
        assertEquals(principalSet("testgroupAllManaged", "testgroupPartlyManaged", "everyone"), onlyManagedPrincipalNames);

        onlyManagedPrincipalNames = authorizablesConfig.removeUnmanagedPrincipalNamesAtPath("/etc", principalSet, null);
        assertEquals(principalSet("testgroupAllManaged", "testgroupPartlyManaged"), onlyManagedPrincipalNames);

        onlyManagedPrincipalNames = authorizablesConfig.removeUnmanagedPrincipalNamesAtPath("/content/geometrixx", principalSet, null);
        assertEquals(principalSet("testgroupAllManaged", "testgroupPartlyManaged"), onlyManagedPrincipalNames);

        onlyManagedPrincipalNames = authorizablesConfig.removeUnmanagedPrincipalNamesAtPath("/content/dam/geometrixx", principalSet, null);
        assertEquals(principalSet("testgroupAllManaged"), onlyManagedPrincipalNames);
    }

    @Test
    public void testRemoveUnmanagedPrincipalNamesAtPathWithNoAutorizableBean() {
        //We add a new non-existant name to remove but that doesn't belong in the authorizables list
        Set<String> principalSet = principalSet(beanTestGroupAllManaged.getPrincipalName(), testgroupPartlyManaged.getPrincipalName(),
                beanEveryone.getPrincipalName(), "nonExistentPrincipal");

        Set<String> removedPrincipals = authorizablesConfig.removeUnmanagedPrincipalNamesAtPath("/content/dam/geometrixx", principalSet, null);
        assertEquals(principalSet("testgroupAllManaged", "nonExistentPrincipal"), removedPrincipals);
    }

    @Test
    public void testRemoveUnmanagedPrincipalNamesAtPathUsingGlobalConfig() {

        Set<String> principalSet = principalSet(beanTestGroupAllManaged.getPrincipalName(), testgroupPartlyManaged.getPrincipalName(),
                beanEveryone.getPrincipalName());

        Set<String> onlyManagedPrincipalNames = authorizablesConfig.removeUnmanagedPrincipalNamesAtPath("/etc/linkchecker", principalSet,
                "/etc/.*");

        // "testgroupPartlyManaged", "everyone" are still in since they define their own unmanagedAcePathsRegex
        assertEquals(principalSet("testgroupPartlyManaged", "everyone"), onlyManagedPrincipalNames);

        // without any individual unmanagedAcePathsRegex set but defaultUnmanagedAcePathsRegex set to a matching regex, there will be never
        // anything removed from this path
        testgroupPartlyManaged.setUnmanagedAcePathsRegex(null);
        beanEveryone.setUnmanagedAcePathsRegex(null);
        onlyManagedPrincipalNames = authorizablesConfig.removeUnmanagedPrincipalNamesAtPath("/etc/linkchecker", principalSet,
                "/etc/.*");
        assertEquals(Collections.emptySet(), onlyManagedPrincipalNames);

        // without default restriction all principals have to be returned
        onlyManagedPrincipalNames = authorizablesConfig.removeUnmanagedPrincipalNamesAtPath("/etc/linkchecker", principalSet,
                null);
        assertEquals(principalSet("testgroupAllManaged", "testgroupPartlyManaged", "everyone"), onlyManagedPrincipalNames);

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
