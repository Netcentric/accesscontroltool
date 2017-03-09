/*
 * (C) Copyright 2017 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.authorizableutils.impl;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import biz.netcentric.cq.tools.actool.configmodel.AcConfiguration;
import biz.netcentric.cq.tools.actool.configmodel.GlobalConfiguration;
import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;

@RunWith(MockitoJUnitRunner.class)
public class AuthorizableCreatorServiceImplTest {

    public static final String TESTGROUP = "testGroup";

    public static final String GROUP1 = "group1";
    public static final String GROUP2 = "group2";
    public static final String GROUP3 = "group3";
    public static final String EXTERNALGROUP = "externalGroup";

    // class under test
    @Spy
    private AuthorizableCreatorServiceImpl cut = new AuthorizableCreatorServiceImpl();
    
    private AcConfiguration acConfiguration = new AcConfiguration();
    private GlobalConfiguration globalConfiguration = new GlobalConfiguration();
    private AcInstallationHistoryPojo status = new AcInstallationHistoryPojo();

    @Mock
    private UserManager userManager;


    @Mock
    private Group testGroup;

    @Mock
    private Group group1;

    @Mock
    private Group group2;

    @Mock
    private Group group3;


    @Mock
    private Group externalGroup;
    
    @Before
    public void setup() throws RepositoryException {

        status.setAcConfiguration(acConfiguration);
        acConfiguration.setGlobalConfiguration(globalConfiguration);

        setupAuthorizable(testGroup, TESTGROUP);
        setupAuthorizable(group1, GROUP1);
        setupAuthorizable(group2, GROUP2);
        setupAuthorizable(group3, GROUP3);
        setupAuthorizable(externalGroup, EXTERNALGROUP);
    }

    private void setupAuthorizable(Authorizable authorizable, String id) throws RepositoryException {
        doReturn(authorizable).when(userManager).getAuthorizable(id);
        doReturn(id).when(authorizable).getID();
    }
    
    @Test
    public void testMergeMemberOfGroups() throws Exception {
        HashSet<String> configuredGroups = new HashSet<String>(Arrays.asList(GROUP1, GROUP2));
        HashSet<String> groupsInRepo = new HashSet<String>(Arrays.asList(GROUP2, GROUP3, EXTERNALGROUP));

        globalConfiguration.setAllowExternalGroupNamesRegEx("external.*");

        // just return the value as passed in as third argument
        doAnswer(new Answer<Set<String>>() {
            public Set<String> answer(InvocationOnMock invocation) throws Throwable {
                return (Set<String>) invocation.getArguments()[2];
            }
        }).when(cut).validateAssignedGroups(userManager, TESTGROUP, configuredGroups);

        cut.mergeMemberOfGroups(TESTGROUP, status, userManager, configuredGroups, groupsInRepo);
        
        verifyZeroInteractions(group2); // in configuredGroups and in groupsInRepo
        verifyZeroInteractions(externalGroup); // matches external.* and hence must not be removed (even though it is not in the
                                               // configuration)
        
        verify(group1).addMember(testGroup);
        verifyNoMoreInteractions(group1);

        verify(group3).removeMember(testGroup);
        verifyNoMoreInteractions(group3);
        
    }
    
}
