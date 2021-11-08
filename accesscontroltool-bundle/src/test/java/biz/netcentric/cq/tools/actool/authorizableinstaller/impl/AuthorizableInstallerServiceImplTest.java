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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.Value;
import javax.jcr.ValueFactory;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.Query;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.jcr.api.SlingRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import com.adobe.granite.crypto.CryptoException;

import biz.netcentric.cq.tools.actool.authorizableinstaller.AuthorizableCreatorException;
import biz.netcentric.cq.tools.actool.configmodel.AcConfiguration;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.configmodel.GlobalConfiguration;
import biz.netcentric.cq.tools.actool.crypto.DecryptionService;
import biz.netcentric.cq.tools.actool.history.InstallationLogger;
import biz.netcentric.cq.tools.actool.history.impl.PersistableInstallationLogger;

@RunWith(Enclosed.class)
public class AuthorizableInstallerServiceImplTest {

    @RunWith(MockitoJUnitRunner.class)
    public static final class BASE_TESTS {
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
        private InstallationLogger status = new PersistableInstallationLogger();

        @Mock
        private UserManager userManager;

        @Mock
        InstallationLogger installationLogger;
        
        
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

        private AuthInstallerUserManagerPrefetchingImpl prefetchingUserManager;
        
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

            acConfiguration.setGlobalConfiguration(globalConfiguration);

            setupAuthorizable(testGroup, TESTGROUP, true, false);
            setupAuthorizable(group1, GROUP1, true, false);
            setupAuthorizable(group2, GROUP2, true, false);
            setupAuthorizable(group3, GROUP3, true, false);
            setupAuthorizable(externalGroup, EXTERNALGROUP, true, false);

            setupAuthorizable(systemUser1, SYSTEM_USER1, false, true);
            setupAuthorizable(regularUser1, USER1, false, false);
            
            when(userManager.findAuthorizables(any(Query.class))).thenReturn(Collections.<Authorizable>emptyIterator());
            prefetchingUserManager = new AuthInstallerUserManagerPrefetchingImpl(userManager, valueFactory, installationLogger);
        }

        private void setupAuthorizable(Authorizable authorizable, String id, boolean isGroup, boolean isSystemUser) throws RepositoryException {
            doReturn(authorizable).when(userManager).getAuthorizable(id);
            doReturn(id).when(authorizable).getID();
            doReturn(isGroup).when(authorizable).isGroup();
        }

        @Test
        public void testApplyGroupMembershipConfigIsMemberOf() throws Exception {
            HashSet<String> configuredGroups = new HashSet<String>(Arrays.asList(GROUP1, GROUP2));
            HashSet<String> groupsInRepo = new HashSet<String>(Arrays.asList(GROUP2, GROUP3, EXTERNALGROUP));

            globalConfiguration.setDefaultUnmanagedExternalIsMemberOfRegex("external.*");

            // just return the value as passed in as fourth argument
            doAnswer(new Answer<Set<String>>() {
                public Set<String> answer(InvocationOnMock invocation) throws Throwable {
                    return (Set<String>) invocation.getArguments()[4];
                }
            }).when(cut).validateAssignedGroups(prefetchingUserManager, acConfiguration.getAuthorizablesConfig(), null, TESTGROUP, configuredGroups, status);

            Set<String> authorizablesInConfig = new HashSet<String>(asList(GROUP1));

            AuthorizableConfigBean authorizableConfigBean = new AuthorizableConfigBean();
            authorizableConfigBean.setAuthorizableId(TESTGROUP);
            cut.applyGroupMembershipConfigIsMemberOf(authorizableConfigBean, acConfiguration, status, prefetchingUserManager, null, configuredGroups,
                    groupsInRepo,
                    authorizablesInConfig);

            Mockito.verifyNoInteractions(group2); // in configuredGroups and in groupsInRepo
            Mockito.verifyNoInteractions(externalGroup); // matches external.* and hence must not be removed (even though it is not in the
            // configuration)

            verify(group1).addMember(testGroup);
            verifyNoMoreInteractions(group1);

            verify(group3).removeMember(testGroup);
            verifyNoMoreInteractions(group3);

        }

        @Test
        public void testApplyGroupMembershipConfigMembers() throws Exception {

            InstallationLogger history = new PersistableInstallationLogger();
            acConfiguration.setGlobalConfiguration(new GlobalConfiguration());

            AuthorizableConfigBean authorizableConfigBean = new AuthorizableConfigBean();
            authorizableConfigBean.setAuthorizableId(TESTGROUP);

            Set<String> authorizablesInConfig = new HashSet<String>(asList(GROUP1));

            // test no change
            authorizableConfigBean.setMembers(new String[] { GROUP2, GROUP3, SYSTEM_USER1 });

            AuthInstallerUserManagerPrefetchingImpl spyedPrefetchingUserManager = spy(prefetchingUserManager);

            doReturn(asSet(group2.getID(), group3.getID(), systemUser1.getID())).when(spyedPrefetchingUserManager).getDeclaredMembersWithoutRegularUsers(eq(TESTGROUP));
            cut.applyGroupMembershipConfigMembers(acConfiguration, authorizableConfigBean, history, TESTGROUP, spyedPrefetchingUserManager, authorizablesInConfig);
            verify(testGroup, times(0)).addMember(any(Authorizable.class));
            verify(testGroup, times(0)).removeMember(any(Authorizable.class));
            reset(testGroup);

            // test removed in config
            authorizableConfigBean.setMembers(new String[] {});
            doReturn(asSet(group2.getID(), group3.getID(), systemUser1.getID())).when(spyedPrefetchingUserManager).getDeclaredMembersWithoutRegularUsers(eq(TESTGROUP));
            cut.applyGroupMembershipConfigMembers(acConfiguration, authorizableConfigBean, history, TESTGROUP, spyedPrefetchingUserManager, authorizablesInConfig);
            verify(testGroup, times(0)).addMember(any(Authorizable.class));
            verify(testGroup).removeMember(group2);
            verify(testGroup).removeMember(group3);
            verify(testGroup).removeMember(systemUser1);
            verify(testGroup, times(0)).removeMember(regularUser1);// regular user must not be removed
            reset(testGroup);

            // test to be added as in config but not in repo
            authorizableConfigBean.setMembers(new String[] { GROUP2, GROUP3, SYSTEM_USER1 });
            doReturn(asSet()).when(spyedPrefetchingUserManager).getDeclaredMembersWithoutRegularUsers(eq(TESTGROUP));
            cut.applyGroupMembershipConfigMembers(acConfiguration, authorizableConfigBean, history, TESTGROUP, spyedPrefetchingUserManager, authorizablesInConfig);
            verify(testGroup).addMember(group2);
            verify(testGroup).addMember(group3);
            verify(testGroup).addMember(systemUser1);
            verify(testGroup, times(0)).removeMember(any(Authorizable.class));
            reset(testGroup);

            // test authorizable in config not removed
            authorizableConfigBean.setMembers(new String[] {});
            doReturn(asSet(group1.getID(), group2.getID())).when(spyedPrefetchingUserManager).getDeclaredMembersWithoutRegularUsers(eq(TESTGROUP));
            cut.applyGroupMembershipConfigMembers(acConfiguration, authorizableConfigBean, history, TESTGROUP, spyedPrefetchingUserManager, authorizablesInConfig);
            verify(testGroup, times(0)).addMember(any(Authorizable.class));
            verify(testGroup, times(0)).removeMember(group1); // must not be removed since it's contained in config
            verify(testGroup).removeMember(group2);
            reset(testGroup);

            // test authorizable in config not removed if defaultUnmanagedExternalMembersRegex is configured
            acConfiguration.getGlobalConfiguration().setDefaultUnmanagedExternalMembersRegex("group2.*");
            authorizableConfigBean.setMembers(new String[] {});
            doReturn(asSet(group1.getID(), group2.getID())).when(spyedPrefetchingUserManager).getDeclaredMembersWithoutRegularUsers(eq(TESTGROUP));
            cut.applyGroupMembershipConfigMembers(acConfiguration, authorizableConfigBean, history, TESTGROUP, spyedPrefetchingUserManager, authorizablesInConfig);
            verify(testGroup, times(0)).addMember(any(Authorizable.class));
            verify(testGroup, times(0)).removeMember(group1); // must not be removed since it's contained in config
            verify(testGroup, times(0)).removeMember(group2); // must not be removed since allowExternalGroupNamesRegEx config
            reset(testGroup);

        }


        static <T> Set<T> asSet(T... entries) {
            return new HashSet<T>(Arrays.asList(entries));
        }

        @Test
        public void testSetAuthorizableProperties() throws Exception {

            AuthorizableConfigBean authorizableConfig = new AuthorizableConfigBean();
            authorizableConfig.setIsGroup(false);

            authorizableConfig.setName("John Doe");
            authorizableConfig.setDescription("Test Description");

            cut.setAuthorizableProperties(regularUser1, authorizableConfig, null, session, status);

            verify(regularUser1).setProperty(eq("profile/givenName"), ArgumentMatchers.argThat(new ValueMatcher("John")));
            verify(regularUser1).setProperty(eq("profile/familyName"), ArgumentMatchers.argThat(new ValueMatcher("Doe")));
            verify(regularUser1).setProperty(eq("profile/aboutMe"), ArgumentMatchers.argThat(new ValueMatcher("Test Description")));

            authorizableConfig.setName("Van der Broek, Sebastian");
            cut.setAuthorizableProperties(regularUser1, authorizableConfig, null, session, status);
            verify(regularUser1).setProperty(eq("profile/givenName"), ArgumentMatchers.argThat(new ValueMatcher("Sebastian")));
            verify(regularUser1).setProperty(eq("profile/familyName"), ArgumentMatchers.argThat(new ValueMatcher("Van der Broek")));

            authorizableConfig.setName("Johann Sebastian Bach");
            cut.setAuthorizableProperties(regularUser1, authorizableConfig, null, session, status);
            verify(regularUser1).setProperty(eq("profile/givenName"), ArgumentMatchers.argThat(new ValueMatcher("Johann Sebastian")));
            verify(regularUser1).setProperty(eq("profile/familyName"), ArgumentMatchers.argThat(new ValueMatcher("Bach")));

        }

        private final class ValueMatcher implements ArgumentMatcher<Value> {
            private String expectedVal;

            public ValueMatcher(String expectedVal) {
                this.expectedVal = expectedVal;
            }

            @Override
            public boolean matches(Value argument) {
                try {
                        return StringUtils.equals(argument.getString(), expectedVal);
                } catch (IllegalStateException | RepositoryException e) {
                        return false;
                }
            }
        }
    }

    public static final class SetUserPassword {

        private static final String USER_ID = "userid";
        private static final String UNPROTECTED_PASSWORD = "unprotected_pass";

        @Mock
        private User user;

        @Mock
        private DecryptionService decryptionService;

        @Mock
        private InstallationLogger installationLogger;

        @Mock
        private SlingRepository repository;

        @Mock
        private Session session;

        @Spy
        @InjectMocks
        private AuthorizableInstallerServiceImpl service;

        private AuthorizableConfigBean configBean;

        @Before
        public void setUp() throws CryptoException, RepositoryException {
            MockitoAnnotations.openMocks(this);

            doReturn(USER_ID).when(user).getID();

            doReturn(UNPROTECTED_PASSWORD).when(decryptionService).decrypt(anyString());

            configBean = new AuthorizableConfigBean();
            configBean.setPassword("{some_protected_pass1}");
        }

        @Test
        public void testPasswordExists() throws RepositoryException, AuthorizableCreatorException {

            doReturn(session).when(repository).login(any(SimpleCredentials.class));
            service.setUserPassword(configBean, user, installationLogger);
            verify(user, times(0)).changePassword(anyString());
        }

        @Test
        public void testPasswordDifferent() throws RepositoryException, AuthorizableCreatorException {
            final AuthorizableConfigBean bean = new AuthorizableConfigBean();
            bean.setPassword("{some_protected_pass1}");
            doThrow(javax.jcr.LoginException.class).when(repository).login(any(SimpleCredentials.class));
            service.setUserPassword(configBean, user, installationLogger);
            verify(user, times(1)).changePassword(UNPROTECTED_PASSWORD);
        }
    }
}
