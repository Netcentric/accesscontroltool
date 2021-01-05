package biz.netcentric.cq.tools.actool.authorizableinstaller.impl;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.Arrays;
import java.util.Iterator;

import javax.jcr.RepositoryException;

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
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import biz.netcentric.cq.tools.actool.history.InstallationLogger;

@RunWith(MockitoJUnitRunner.class)
public class PrefetchedAuthorizablesUserManagerTest {

    @Mock
    UserManager userManager;

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
    
    PrefetchedAuthorizablesUserManager prefetchedAuthorizablesUserManager;
    
    @Before
    public void before() throws RepositoryException {
        
        when(group1.getID()).thenReturn("group1");
        when(group2.getID()).thenReturn("group2");
        when(group3.getID()).thenReturn("group3");
        when(user1.getID()).thenReturn("user1");
        when(user1.getID()).thenReturn("user2");
        when(userNonCached.getID()).thenReturn("userNonCached");
        
        

        when(userManager.findAuthorizables(PrefetchedAuthorizablesUserManager.ALL_AUTHORIZABLES_QUERY)).thenReturn(Arrays.asList(group1, group2, group3, user1, user2).iterator());

    }
    
    @Test
    public void testPrefetchedAuthorizablesUserManager() throws RepositoryException {
        prefetchedAuthorizablesUserManager = new PrefetchedAuthorizablesUserManager(userManager, installationLogger);
        
        assertEquals(5, prefetchedAuthorizablesUserManager.getCacheSize());
        assertEquals(group1, prefetchedAuthorizablesUserManager.getAuthorizable("group1"));
        assertEquals(null, prefetchedAuthorizablesUserManager.getAuthorizable("userNonCached"));

        when(userManager.getAuthorizable("userNonCached")).thenReturn(userNonCached);
        assertEquals(userNonCached, prefetchedAuthorizablesUserManager.getAuthorizable("userNonCached"));
    }

    @Test
    public void testDelegation() throws RepositoryException {
        prefetchedAuthorizablesUserManager = new PrefetchedAuthorizablesUserManager(userManager, installationLogger);

        String testuser = "test";
        PrincipalImpl testPrincipal = new PrincipalImpl(testuser);
        prefetchedAuthorizablesUserManager.getAuthorizable(testPrincipal);
        verify(userManager, times(1)).getAuthorizable(testPrincipal);



        String userPath = "/home/users/path";
        prefetchedAuthorizablesUserManager.getAuthorizableByPath(userPath);
        verify(userManager, times(1)).getAuthorizableByPath(userPath);
        
        String relPath = "profile/test";
        String testVal = "testval";
        prefetchedAuthorizablesUserManager.findAuthorizables(relPath, testVal);
        verify(userManager, times(1)).findAuthorizables(relPath, testVal);
        prefetchedAuthorizablesUserManager.findAuthorizables(relPath, testVal, UserManager.SEARCH_TYPE_AUTHORIZABLE);
        verify(userManager, times(1)).findAuthorizables(relPath, testVal, UserManager.SEARCH_TYPE_AUTHORIZABLE);
        
        Query testQuery = new Query() {
            public <T> void build(QueryBuilder<T> builder) {
                // fetch all authorizables
            }
        };
        prefetchedAuthorizablesUserManager.findAuthorizables(testQuery);
        verify(userManager, times(1)).findAuthorizables(testQuery);
        
        String testpw = "pw";
        prefetchedAuthorizablesUserManager.createUser(testuser, testpw);
        verify(userManager, times(1)).createUser(testuser, testpw);

        prefetchedAuthorizablesUserManager.createUser(testuser, testpw, testPrincipal, userPath);
        verify(userManager, times(1)).createUser(testuser, testpw, testPrincipal, userPath);
        
        prefetchedAuthorizablesUserManager.createSystemUser(testuser, userPath);
        verify(userManager, times(1)).createSystemUser(testuser, userPath);
        
        prefetchedAuthorizablesUserManager.createGroup(testuser);
        verify(userManager, times(1)).createGroup(testuser);

        prefetchedAuthorizablesUserManager.createGroup(testPrincipal);
        verify(userManager, times(1)).createGroup(testPrincipal);
        
        prefetchedAuthorizablesUserManager.createGroup(testPrincipal, userPath);
        verify(userManager, times(1)).createGroup(testPrincipal, userPath);

        prefetchedAuthorizablesUserManager.createGroup(testuser, testPrincipal, userPath);
        verify(userManager, times(1)).createGroup(testuser, testPrincipal, userPath);
        
        prefetchedAuthorizablesUserManager.autoSave(true);
        verify(userManager, times(1)).autoSave(true);
        
        prefetchedAuthorizablesUserManager.isAutoSave();
        verify(userManager, times(1)).isAutoSave();
    }


    
    
    
    
    
}
