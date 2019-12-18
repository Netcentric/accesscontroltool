/*
 * (C) Copyright 2017 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.impl;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.Test;

import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizablesConfig;
import biz.netcentric.cq.tools.actool.impl.AcInstallationServiceImpl;

public class AceServiceImplTest {

    AcInstallationServiceImpl aceServiceImpl = new AcInstallationServiceImpl();


    @Test
    public void testIsRelevantPath() {

        String[] restrictedToPaths = new String[] { "/content/site1", "/content/site3" };
        assertTrue(aceServiceImpl.isRelevantPath("/content/site1", restrictedToPaths));
        assertFalse(aceServiceImpl.isRelevantPath("/content/site1ButNotSameRoot", restrictedToPaths));
        assertTrue(aceServiceImpl.isRelevantPath("/content/site1/page", restrictedToPaths));

        assertFalse(aceServiceImpl.isRelevantPath("/content/site2", restrictedToPaths));
        assertFalse(aceServiceImpl.isRelevantPath("/content/site2/page", restrictedToPaths));

        assertTrue(aceServiceImpl.isRelevantPath("/content/site3", restrictedToPaths));
        assertTrue(aceServiceImpl.isRelevantPath("/content/site3/page", restrictedToPaths));

        assertFalse(aceServiceImpl.isRelevantPath("/etc/cloudservices", restrictedToPaths));

    }

    @Test
    public void testIsRelevantPathWithRegEx() {

        String[] restrictedToPaths = new String[] { "/content/site1", "^/$", "^$" };
        
        // regex (for repo level restrition)
        assertTrue(aceServiceImpl.isRelevantPath("", restrictedToPaths));
        // regex (for root only)
        assertTrue(aceServiceImpl.isRelevantPath("/", restrictedToPaths));
        
        // other paths
        assertTrue(aceServiceImpl.isRelevantPath("/content/site1", restrictedToPaths));
        assertFalse(aceServiceImpl.isRelevantPath("/content/site1ButNotSameRoot", restrictedToPaths));
        assertTrue(aceServiceImpl.isRelevantPath("/content/site1/page", restrictedToPaths));

        assertFalse(aceServiceImpl.isRelevantPath("/content/site2", restrictedToPaths));

    }
    
    @Test
    public void testCollectPrincipalsToBeMigrated() {
        AuthorizablesConfig authorizablesConfig = new AuthorizablesConfig();

        AuthorizableConfigBean authorizableConfigBeanSimple = new AuthorizableConfigBean();
        String simpleAuthId = "testSimple";
        String simpleAuthIdOld = "testSimpleOld";
        authorizableConfigBeanSimple.setAuthorizableId(simpleAuthId);
        authorizableConfigBeanSimple.setPrincipalName(simpleAuthId);
        authorizableConfigBeanSimple.setMigrateFrom(simpleAuthIdOld);
        authorizablesConfig.add(authorizableConfigBeanSimple);

        AuthorizableConfigBean authorizableConfigBeanLdap = new AuthorizableConfigBean();
        String ldapAuthId = "testLdap";
        String ldapPrincipalName = "cn=testLdap,dc=name,dc=org";
        String ldapAuthIdOld = "testLdapOld";
        authorizableConfigBeanLdap.setAuthorizableId(ldapAuthId);
        authorizableConfigBeanLdap.setPrincipalName(ldapPrincipalName);
        authorizableConfigBeanLdap.setMigrateFrom(ldapAuthIdOld);
        authorizablesConfig.add(authorizableConfigBeanLdap);

        Set<String> principalsToBeMigrated = aceServiceImpl.collectPrincipalsToBeMigrated(authorizablesConfig);

        assertArrayEquals(new String[] { simpleAuthIdOld, "cn=testLdapOld,dc=name,dc=org" },
                principalsToBeMigrated.toArray(new String[principalsToBeMigrated.size()]));

    }

}
