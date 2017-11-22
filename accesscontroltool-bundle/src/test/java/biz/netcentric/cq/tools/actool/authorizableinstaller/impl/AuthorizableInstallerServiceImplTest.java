/*
 * (C) Copyright 2017 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.authorizableinstaller.impl;

import static java.util.Arrays.asList;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFactory;

import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import biz.netcentric.cq.tools.actool.configmodel.AcConfiguration;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.configmodel.GlobalConfiguration;
import biz.netcentric.cq.tools.actool.history.AcInstallationLog;

@RunWith(MockitoJUnitRunner.class)
public class AuthorizableInstallerServiceImplTest {

    public static final String TESTGROUP = "testGroup";

    public static final String GROUP1 = "group1";
    public static final String GROUP2 = "group2";
    public static final String GROUP3 = "group3";
    public static final String EXTERNALGROUP = "externalGroup";

    public static final String USER1 = "user1";

    public static final String SYSTEM_USER1 = "systemuser1";

    // class under test
    @Spy
    private AuthorizableInstallerServiceImpl cut = new AuthorizableInstallerServiceImpl();
    
    private AcConfiguration acConfiguration = new AcConfiguration();
    private GlobalConfiguration globalConfiguration = new GlobalConfiguration();
    private AcInstallationLog status = new AcInstallationLog();

    @Mock
    private UserManager userManager;

    @Mock
    private Session session;
    @Mock
    private ValueFactory valueFactory;

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
    
    @Mock
    private User systemUser1;

    @Mock
    private User regularUser1;

    @Before
    public void setup() throws RepositoryException {

        doReturn(valueFactory).when(session).getValueFactory();
        Mockito.when(valueFactory.createValue(anyString())).thenAnswer(new Answer<Value>() {
            @Override
            public Value answer(InvocationOnMock invocation) throws Throwable {
                Value mock = mock(Value.class);
                Object val = invocation.getArguments()[0];
                doReturn(val).when(mock).getString();
                return mock;
            }
        });

        status.setAcConfiguration(acConfiguration);
        acConfiguration.setGlobalConfiguration(globalConfiguration);

        setupAuthorizable(testGroup, TESTGROUP, true, false);
        setupAuthorizable(group1, GROUP1, true, false);
        setupAuthorizable(group2, GROUP2, true, false);
        setupAuthorizable(group3, GROUP3, true, false);
        setupAuthorizable(externalGroup, EXTERNALGROUP, true, false);

        setupAuthorizable(systemUser1, SYSTEM_USER1, false, true);
        setupAuthorizable(regularUser1, USER1, false, false);
    }

    private void setupAuthorizable(Authorizable authorizable, String id, boolean isGroup, boolean isSystemUser) throws RepositoryException {
        doReturn(authorizable).when(userManager).getAuthorizable(id);
        doReturn(id).when(authorizable).getID();
        doReturn(isGroup).when(authorizable).isGroup();
        doReturn("/home/" + (isGroup ? "groups" : "users") + (isSystemUser ? "/system" : "") + "/test").when(authorizable).getPath();
    }
    
    @Test
    public void testApplyGroupMembershipConfigMemberOf() throws Exception {
        HashSet<String> configuredGroups = new HashSet<String>(Arrays.asList(GROUP1, GROUP2));
        HashSet<String> groupsInRepo = new HashSet<String>(Arrays.asList(GROUP2, GROUP3, EXTERNALGROUP));

        globalConfiguration.setKeepExistingMembershipsForGroupNamesRegEx("external.*");

        // just return the value as passed in as fourth argument
        doAnswer(new Answer<Set<String>>() {
            public Set<String> answer(InvocationOnMock invocation) throws Throwable {
                return (Set<String>) invocation.getArguments()[3];
            }
        }).when(cut).validateAssignedGroups(userManager, null, TESTGROUP, configuredGroups, status);

        cut.applyGroupMembershipConfigIsMemberOf(TESTGROUP, status, userManager, null, configuredGroups, groupsInRepo);
        
        verifyZeroInteractions(group2); // in configuredGroups and in groupsInRepo
        verifyZeroInteractions(externalGroup); // matches external.* and hence must not be removed (even though it is not in the
                                               // configuration)
        
        verify(group1).addMember(testGroup);
        verifyNoMoreInteractions(group1);

        verify(group3).removeMember(testGroup);
        verifyNoMoreInteractions(group3);
        
    }
    
    @Test
    public void testApplyGroupMembershipConfigMembers() throws Exception {

        AcInstallationLog history = new AcInstallationLog();
        history.setAcConfiguration(new AcConfiguration());
        history.getAcConfiguration().setGlobalConfiguration(new GlobalConfiguration());

        AuthorizableConfigBean authorizableConfigBean = new AuthorizableConfigBean();
        authorizableConfigBean.setAuthorizableId(TESTGROUP);

        Set<String> authorizablesInConfig = new HashSet<String>(asList(GROUP1));

        // test no change
        authorizableConfigBean.setMembers(new String[] { GROUP2, GROUP3, SYSTEM_USER1 });
        doReturn(asList(group2, group3, regularUser1, systemUser1).iterator()).when(testGroup).getDeclaredMembers();
        cut.applyGroupMembershipConfigMembers(authorizableConfigBean, history, TESTGROUP, userManager, authorizablesInConfig);
        verify(testGroup, times(0)).addMember(any(Authorizable.class));
        verify(testGroup, times(0)).removeMember(any(Authorizable.class));
        reset(testGroup);

        // test removed in config
        authorizableConfigBean.setMembers(new String[] {});
        doReturn(asList(group2, group3, regularUser1, systemUser1).iterator()).when(testGroup).getDeclaredMembers();
        cut.applyGroupMembershipConfigMembers(authorizableConfigBean, history, TESTGROUP, userManager, authorizablesInConfig);
        verify(testGroup, times(0)).addMember(any(Authorizable.class));
        verify(testGroup).removeMember(group2);
        verify(testGroup).removeMember(group3);
        verify(testGroup).removeMember(systemUser1);
        verify(testGroup, times(0)).removeMember(regularUser1);// regular user must not be removed
        reset(testGroup);

        // test to be added as in config but not in repo
        authorizableConfigBean.setMembers(new String[] { GROUP2, GROUP3, SYSTEM_USER1 });
        doReturn(asList().iterator()).when(testGroup).getDeclaredMembers();
        cut.applyGroupMembershipConfigMembers(authorizableConfigBean, history, TESTGROUP, userManager, authorizablesInConfig);
        verify(testGroup).addMember(group2);
        verify(testGroup).addMember(group3);
        verify(testGroup).addMember(systemUser1);
        verify(testGroup, times(0)).removeMember(any(Authorizable.class));
        reset(testGroup);

        // test authorizable in config not removed
        authorizableConfigBean.setMembers(new String[] {});
        doReturn(asList(group1, group2).iterator()).when(testGroup).getDeclaredMembers();
        cut.applyGroupMembershipConfigMembers(authorizableConfigBean, history, TESTGROUP, userManager, authorizablesInConfig);
        verify(testGroup, times(0)).addMember(any(Authorizable.class));
        verify(testGroup, times(0)).removeMember(group1); // must not be removed since it's contained in config
        verify(testGroup).removeMember(group2);
        reset(testGroup);

        // test authorizable in config not removed if allowExternalGroupNamesRegEx is configured
        history.getAcConfiguration().getGlobalConfiguration().setKeepExistingMembershipsForGroupNamesRegEx("group2.*");
        authorizableConfigBean.setMembers(new String[] {});
        doReturn(asList(group1, group2).iterator()).when(testGroup).getDeclaredMembers();
        cut.applyGroupMembershipConfigMembers(authorizableConfigBean, history, TESTGROUP, userManager, authorizablesInConfig);
        verify(testGroup, times(0)).addMember(any(Authorizable.class));
        verify(testGroup, times(0)).removeMember(group1); // must not be removed since it's contained in config
        verify(testGroup, times(0)).removeMember(group2); // must not be removed since allowExternalGroupNamesRegEx config
        reset(testGroup);

    }
    
    
    @Test
    public void testSetAuthorizableProperties() throws Exception {
        
        AuthorizableConfigBean authorizableConfig = new AuthorizableConfigBean();
        authorizableConfig.setIsGroup(false);

        authorizableConfig.setName("John Doe");
        authorizableConfig.setDescription("Test Description");

        cut.setAuthorizableProperties(regularUser1, authorizableConfig, session, status);

        verify(regularUser1).setProperty(eq("profile/givenName"), Matchers.argThat(new ValueMatcher("John")));
        verify(regularUser1).setProperty(eq("profile/familyName"), Matchers.argThat(new ValueMatcher("Doe")));
        verify(regularUser1).setProperty(eq("profile/aboutMe"), Matchers.argThat(new ValueMatcher("Test Description")));

        authorizableConfig.setName("Van der Broek, Sebastian");
        cut.setAuthorizableProperties(regularUser1, authorizableConfig, session, status);
        verify(regularUser1).setProperty(eq("profile/givenName"), Matchers.argThat(new ValueMatcher("Sebastian")));
        verify(regularUser1).setProperty(eq("profile/familyName"), Matchers.argThat(new ValueMatcher("Van der Broek")));

        authorizableConfig.setName("Johann Sebastian Bach");
        cut.setAuthorizableProperties(regularUser1, authorizableConfig, session, status);
        verify(regularUser1).setProperty(eq("profile/givenName"), Matchers.argThat(new ValueMatcher("Johann Sebastian")));
        verify(regularUser1).setProperty(eq("profile/familyName"), Matchers.argThat(new ValueMatcher("Bach")));

    }

    private final class ValueMatcher extends BaseMatcher<Value> {
        private String expectedVal;

        public ValueMatcher(String expectedVal) {
            this.expectedVal = expectedVal;
        }

        @Override
        public boolean matches(Object actualVal) {
            if (!(actualVal instanceof Value)) {
                return false;
            } else {
                try {
                    return StringUtils.equals(((Value) actualVal).getString(), expectedVal);
                } catch (IllegalStateException | RepositoryException e) {
                    return false;
                }
            }

        }

        @Override
        public void describeTo(Description desc) {
            desc.appendText(" is " + expectedVal);
        }
    }
}
