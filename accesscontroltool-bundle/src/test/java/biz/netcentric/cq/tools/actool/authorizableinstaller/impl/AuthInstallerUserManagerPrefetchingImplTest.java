package biz.netcentric.cq.tools.actool.authorizableinstaller.impl;

import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import javax.jcr.RepositoryException;
import javax.jcr.ValueFactory;

import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.Query;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.oak.spi.security.principal.PrincipalImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import biz.netcentric.cq.tools.actool.history.InstallationLogger;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthInstallerUserManagerPrefetchingImplTest {

    private static final String GROUP_1 = "group1";
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
    User user1;

    @Mock
    User user2;

    AuthInstallerUserManagerPrefetchingImpl prefetchingUserManager;

    @BeforeEach
    void before() throws RepositoryException {

        setupAuthorizable(group1, GROUP_1, Collections.<Group>emptyList());
        setupAuthorizable(group2, "group2", Arrays.asList(group1));
        setupAuthorizable(group3, "group3", Arrays.asList(group2));
        setupAuthorizable(user1, USER_1, Arrays.asList(group3));
        setupAuthorizable(user2, "user2", Arrays.asList(group3));

        when(userManager.findAuthorizables(any(Query.class))).thenReturn(Arrays.asList(group1, group2, group3, user1, user2).iterator());
        when(userManager.getAuthorizable("invalidid")).thenThrow(new RepositoryException("Unknown authorizable id 'invalidid'"));
        when(userManager.createUser(anyString(), anyString(), any(Principal.class), anyString())).thenReturn(Mockito.mock(User.class));
        when(userManager.createSystemUser(anyString(), anyString())).thenReturn(Mockito.mock(User.class));
        when(userManager.createGroup(any(Principal.class), anyString())).thenReturn(Mockito.mock(Group.class));
        when(userManager.createGroup(any(Principal.class))).thenReturn(Mockito.mock(Group.class));
    }

    private void setupAuthorizable(Authorizable auth, String id, List<Group> memberOf) throws RepositoryException {
        when(auth.getPath()).thenReturn("/home/userorgroup/"+id);
        when(auth.getID()).thenReturn(id);
        when(auth.declaredMemberOf()).thenReturn(memberOf.iterator());
    }

    @Test
    void testGetDeclaredIsMemberOfAndGetDeclaredMembersWithoutRegularUsers() throws RepositoryException {
        prefetchingUserManager = new AuthInstallerUserManagerPrefetchingImpl(userManager, valueFactory, installationLogger);

        assertEquals(Collections.emptySet(), prefetchingUserManager.getDeclaredIsMemberOf("group1"));
        assertEquals(Collections.singleton("group1"), prefetchingUserManager.getDeclaredIsMemberOf("group2"));
        assertEquals(Collections.singleton("group2"), prefetchingUserManager.getDeclaredIsMemberOf("group3"));
        assertEquals(Collections.singleton("group3"), prefetchingUserManager.getDeclaredIsMemberOf("user1"));
        assertEquals(Collections.singleton("group3"), prefetchingUserManager.getDeclaredIsMemberOf("user2"));
        assertThrows(RepositoryException.class, () -> prefetchingUserManager.getDeclaredIsMemberOf("invalidid"));

        assertEquals(Collections.singleton("group2"), prefetchingUserManager.getDeclaredMembersWithoutRegularUsers("group1"));
        assertEquals(Collections.singleton("group3"), prefetchingUserManager.getDeclaredMembersWithoutRegularUsers("group2"));
        assertEquals(new HashSet<>(Arrays.asList("user1", "user2")), prefetchingUserManager.getDeclaredMembersWithoutRegularUsers("group3"));
        assertEquals(Collections.emptySet(), prefetchingUserManager.getDeclaredMembersWithoutRegularUsers("user1"));
        assertEquals(Collections.emptySet(), prefetchingUserManager.getDeclaredMembersWithoutRegularUsers("user2"));
        assertEquals(Collections.emptySet(), prefetchingUserManager.getDeclaredMembersWithoutRegularUsers("invalidid"));
    }

    @Test
    void testDelegation() throws RepositoryException {
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
    void testCaseSensitiveAuthorizableCacheRetrieval() throws RepositoryException {
        prefetchingUserManager = new AuthInstallerUserManagerPrefetchingImpl(userManager, valueFactory, installationLogger);
        final Authorizable retrievedGroup3 = prefetchingUserManager.getAuthorizable(group1.getID());
        final Authorizable retrievedGroup3Alternative = prefetchingUserManager.getAuthorizable(group1.getID().toUpperCase());

        assertEquals(retrievedGroup3, retrievedGroup3Alternative);
    }
}
