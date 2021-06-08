package biz.netcentric.cq.tools.actool.authorizableinstaller.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.jcr.ValueFactory;

import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.Query;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.oak.spi.security.principal.PrincipalImpl;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import biz.netcentric.cq.tools.actool.history.InstallationLogger;

@RunWith(MockitoJUnitRunner.class)
public class AuthInstallerUserManagerPrefetchingImplTest {

    private static final String GROUP_1 = "group1";
    private static final String GROUP1_PATH = "/home/groups/actool";
    private static final String USER_1 = "user1";

    @Mock
    UserManager userManager;

    @Mock
    ValueFactory valueFactory;

    @Mock
    InstallationLogger installationLogger;

    @Mock
    Group group1;

    @Mock
    Group group2;

    @Mock
    Group group3;

    @Mock
    Group groupCreated;

    @Mock
    User user1;

    @Mock
    User user2;

    @Mock
    User userCreated;

    @Mock
    User userNonCached;

    AuthInstallerUserManagerPrefetchingImpl prefetchingUserManager;

    @Before
    public void before() throws RepositoryException {

        setupAuthorizable(group1, GROUP_1, Collections.<Group>emptyList());
        setupAuthorizable(group2, "group2", Arrays.asList(group1));
        setupAuthorizable(group3, "group3", Arrays.asList(group2));
        setupAuthorizable(user1, USER_1, Arrays.asList(group3));
        setupAuthorizable(user2, "user2", Arrays.asList(group3));
        setupAuthorizable(userNonCached, "userNonCached", Arrays.asList(group3));

        when(userManager.findAuthorizables(any(Query.class))).thenReturn(Arrays.asList(group1, group2, group3, user1, user2).iterator());
    }

    private void setupAuthorizable(Authorizable auth, String id, List<Group> memberOf) throws RepositoryException {
        when(auth.getID()).thenReturn(id);
        when(auth.declaredMemberOf()).thenReturn(memberOf.iterator());
    }

    @Test
    public void testPrefetchedAuthorizablesUserManager() throws RepositoryException {
        prefetchingUserManager = new AuthInstallerUserManagerPrefetchingImpl(userManager, valueFactory, installationLogger);

        assertEquals(5, prefetchingUserManager.getCacheSize());
        assertEquals(group1, prefetchingUserManager.getAuthorizable(GROUP_1));
        assertEquals(null, prefetchingUserManager.getAuthorizable("userNonCached"));

        when(userManager.getAuthorizable("userNonCached")).thenReturn(userNonCached);
        assertEquals(userNonCached, prefetchingUserManager.getAuthorizable("userNonCached"));
    }

    @Test
    public void testDelegation() throws RepositoryException {
        prefetchingUserManager = new AuthInstallerUserManagerPrefetchingImpl(userManager, valueFactory, installationLogger);

        String testuser = "test";
        PrincipalImpl testPrincipal = new PrincipalImpl(testuser);

        String userPath = "/home/users/path";
        String testpw = "pw";
        prefetchingUserManager.createUser(testuser, testpw, testPrincipal, userPath);
        verify(userManager, times(1)).createUser(testuser, testpw, testPrincipal, userPath);

        prefetchingUserManager.createSystemUser(testuser, userPath);
        verify(userManager, times(1)).createSystemUser(testuser, userPath);

        prefetchingUserManager.createGroup(testPrincipal);
        verify(userManager, times(1)).createGroup(testPrincipal);

        prefetchingUserManager.createGroup(testPrincipal, userPath);
        verify(userManager, times(1)).createGroup(testPrincipal, userPath);
    }

    @Test
    public void testCacheShouldRefreshedAfterGroupCreation() throws RepositoryException {
        prefetchingUserManager = new AuthInstallerUserManagerPrefetchingImpl(userManager, valueFactory, installationLogger);

        final PrincipalImpl group = new PrincipalImpl(GROUP_1);
        setupAuthorizable(groupCreated, GROUP_1, Collections.<Group>emptyList());
        when(userManager.createGroup(group)).thenReturn(groupCreated);

        final Authorizable groupBeforeRecreation = prefetchingUserManager.getAuthorizable(GROUP_1);
        final Authorizable groupRecreated = prefetchingUserManager.createGroup(group);
        final Authorizable groupAfterRecreation = prefetchingUserManager.getAuthorizable(GROUP_1);

        assertEquals(groupRecreated, groupAfterRecreation);
        assertEquals(groupCreated, groupAfterRecreation);
        assertNotEquals(groupBeforeRecreation, groupAfterRecreation);
    }

    @Test
    public void testCacheShouldRefreshedAfterGroupWithPathCreation() throws RepositoryException {
        prefetchingUserManager = new AuthInstallerUserManagerPrefetchingImpl(userManager, valueFactory, installationLogger);

        final PrincipalImpl group = new PrincipalImpl(GROUP_1);
        setupAuthorizable(groupCreated, GROUP_1, Collections.<Group>emptyList());
        when(userManager.createGroup(group, GROUP1_PATH)).thenReturn(groupCreated);

        final Authorizable groupBeforeRecreation = prefetchingUserManager.getAuthorizable(GROUP_1);
        final Authorizable groupRecreated = prefetchingUserManager.createGroup(group, GROUP1_PATH);
        final Authorizable groupAfterRecreation = prefetchingUserManager.getAuthorizable(GROUP_1);

        assertEquals(groupRecreated, groupAfterRecreation);
        assertEquals(groupCreated, groupAfterRecreation);
        assertNotEquals(groupBeforeRecreation, groupAfterRecreation);
    }

    @Test
    public void testCacheShouldRefreshedAfterSystemUserCreation() throws RepositoryException {
        prefetchingUserManager = new AuthInstallerUserManagerPrefetchingImpl(userManager, valueFactory, installationLogger);

        setupAuthorizable(userCreated, USER_1, Arrays.asList(group3));
        when(userManager.createSystemUser(USER_1, null)).thenReturn(userCreated);

        final Authorizable userBeforeRecreation = prefetchingUserManager.getAuthorizable(USER_1);
        final Authorizable userRecreated = prefetchingUserManager.createSystemUser(USER_1, null);
        final Authorizable userAfterRecreation = prefetchingUserManager.getAuthorizable(USER_1);

        assertEquals(userRecreated, userAfterRecreation);
        assertEquals(userCreated, userAfterRecreation);
        assertNotEquals(userBeforeRecreation, userAfterRecreation);
    }

    @Test
    public void testCacheShouldRefreshedAfterUserCreation() throws RepositoryException {
        prefetchingUserManager = new AuthInstallerUserManagerPrefetchingImpl(userManager, valueFactory, installationLogger);

        setupAuthorizable(userCreated, USER_1, Arrays.asList(group3));
        final PrincipalImpl user = new PrincipalImpl(GROUP_1);
        when(userManager.createUser(USER_1, "pass", user, null)).thenReturn(userCreated);

        final Authorizable userBeforeRecreation = prefetchingUserManager.getAuthorizable(USER_1);
        final Authorizable userRecreated = prefetchingUserManager.createUser(USER_1, "pass", user, null);
        final Authorizable userAfterRecreation = prefetchingUserManager.getAuthorizable(USER_1);

        assertEquals(userRecreated, userAfterRecreation);
        assertEquals(userCreated, userAfterRecreation);
        assertNotEquals(userBeforeRecreation, userAfterRecreation);
    }

    @Test
    public void testCacheShouldRefreshedAfterAuthorizableDeletion() throws RepositoryException {
        //when(group3.getDeclaredMembers()).thenReturn(Arrays.asList((Authorizable) user1).iterator());

        prefetchingUserManager = new AuthInstallerUserManagerPrefetchingImpl(userManager, valueFactory, installationLogger);

        prefetchingUserManager.removeAuthorizable(user1);

        final Authorizable retrievedUser1 = prefetchingUserManager.getAuthorizable(user1.getID());
        final Set<String> groups = prefetchingUserManager.getDeclaredIsMemberOf(user1.getID());

        assertNull(retrievedUser1);
        assertFalse(groups.isEmpty());
    }

    @Test
    public void testCacheShouldRefreshedAfterGroupDeletion() throws RepositoryException {
        when(group3.getDeclaredMembers()).thenReturn(Arrays.asList((Authorizable) user1).iterator());

        prefetchingUserManager = new AuthInstallerUserManagerPrefetchingImpl(userManager, valueFactory, installationLogger);

        prefetchingUserManager.removeAuthorizable(group3);

        final Authorizable retrievedGroup3 = prefetchingUserManager.getAuthorizable(group3.getID());
        final Set<String> groups = prefetchingUserManager.getDeclaredIsMemberOf(user1.getID());

        assertNull(retrievedGroup3);
        assertFalse(groups.contains(group3.getID()));
    }

    @Test
    public void testCaseSensitiveAuthorizableCacheRetrieval() throws RepositoryException {
        prefetchingUserManager = new AuthInstallerUserManagerPrefetchingImpl(userManager, valueFactory, installationLogger);
        final Authorizable retrievedGroup3 = prefetchingUserManager.getAuthorizable(group1.getID());
        final Authorizable retrievedGroup3Alternative = prefetchingUserManager.getAuthorizable(group1.getID().toUpperCase());

        assertEquals(retrievedGroup3, retrievedGroup3Alternative);
    }
}
