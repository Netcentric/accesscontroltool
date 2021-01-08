package biz.netcentric.cq.tools.actool.authorizableinstaller.impl;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.jcr.RepositoryException;
import javax.jcr.ValueFactory;

import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.Query;
import org.apache.jackrabbit.api.security.user.QueryBuilder;
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

    @Mock
    User userNonCached;

    AuthInstallerUserManagerPrefetchingImpl prefetchingUserManager;

    @Before
    public void before() throws RepositoryException {

        setupAuthorizable(group1, "group1", Collections.<Group> emptyList());
        setupAuthorizable(group2, "group2", Arrays.asList(group1));
        setupAuthorizable(group3, "group3", Arrays.asList(group2));
        setupAuthorizable(user1, "user1", Arrays.asList(group3));
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
        assertEquals(group1, prefetchingUserManager.getAuthorizable("group1"));
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

}
