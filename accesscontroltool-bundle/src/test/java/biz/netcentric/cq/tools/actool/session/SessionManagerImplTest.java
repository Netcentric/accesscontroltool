package biz.netcentric.cq.tools.actool.session;

import biz.netcentric.cq.tools.actool.session.impl.SessionManagerImpl;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Mockito.*;

/** @author karolis.mackevicius@netcentric.biz
 * @since 24/03/17 */
@RunWith(MockitoJUnitRunner.class)
public class SessionManagerImplTest {

    @InjectMocks
    private SessionManagerImpl sessionManager = new SessionManagerImpl();

    @Mock
    private ResourceResolverFactory resourceResolverFactory;

    @Mock
    private ResourceResolver resourceResolver;

    @Mock
    private Session session;

    @Before
    public void setUp() throws Exception{
        sessionManager.activate();
        when(resourceResolverFactory.getServiceResourceResolver(anyMap())).thenReturn(resourceResolver);
        when(resourceResolver.adaptTo(Session.class)).thenReturn(session);
    }

    @Test(expected = RepositoryException.class)
    public void noUserAvailableExceptionThrown() throws Exception {
        when(resourceResolverFactory.getServiceResourceResolver(anyMap())).thenThrow(LoginException.class);
        sessionManager.getSession();
    }

    @Test
    public void returnsActoolSystemUserSession() throws Exception {
        assertEquals(session, sessionManager.getSession());
    }

    @Test
    public void sessionIsNotLiveNoNeedToClose() throws Exception {
        Session session = sessionManager.getSession();
        when(session.isLive()).thenReturn(false);
        sessionManager.close(session);
        verify(session, times(1)).isLive();
        verify(session, times(0)).logout();
    }

    @Test
    public void currentUserSession() throws Exception{
        when(resourceResolverFactory.getThreadResourceResolver()).thenReturn(resourceResolver);
        assertEquals(session, sessionManager.getSession());
    }

    @Test
    public void currentUserSessionNoNeedToClose() throws Exception{
        when(resourceResolverFactory.getThreadResourceResolver()).thenReturn(resourceResolver);
        sessionManager.close(sessionManager.getSession());
        verify(session, times(0)).isLive();
        verify(session, times(0)).logout();
    }

    @Test
    public void sessionLogout() throws Exception {
        Session session = sessionManager.getSession();
        when(session.isLive()).thenReturn(true);
        sessionManager.close(session);
        verify(session, times(1)).isLive();
        verify(session, times(1)).logout();
    }

}