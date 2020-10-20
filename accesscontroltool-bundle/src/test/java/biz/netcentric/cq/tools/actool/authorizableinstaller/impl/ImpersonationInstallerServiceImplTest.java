package biz.netcentric.cq.tools.actool.authorizableinstaller.impl;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.api.security.principal.PrincipalIterator;
import org.apache.jackrabbit.api.security.user.Impersonation;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.oak.spi.security.principal.PrincipalImpl;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizablesConfig;
import biz.netcentric.cq.tools.actool.history.InstallationLogger;

@RunWith(MockitoJUnitRunner.class)
public class ImpersonationInstallerServiceImplTest {

    @InjectMocks
    ImpersonationInstallerServiceImpl impersonationInstallerService = new ImpersonationInstallerServiceImpl();

    @Mock
    User user;

    @Mock
    Impersonation impersonation;

    @Mock
    InstallationLogger installationLog;

    AuthorizablesConfig authorizablesConfig;

    @Before
    public void setup() throws RepositoryException {

        when(user.getImpersonation()).thenReturn(impersonation);
        when(impersonation.grantImpersonation(any(Principal.class))).thenReturn(true);
        when(impersonation.revokeImpersonation(any(Principal.class))).thenReturn(true);

        authorizablesConfig = new AuthorizablesConfig();
        authorizablesConfig.add(setupUserConfigBean("test-principal-1"));
        authorizablesConfig.add(setupUserConfigBean("test-principal-2"));
        authorizablesConfig.add(setupUserConfigBean("test-principal-3"));
        authorizablesConfig.add(setupUserConfigBean("test-principal-4"));
    }

    private AuthorizableConfigBean setupUserConfigBean(String id) {
        AuthorizableConfigBean configBean = new AuthorizableConfigBean();
        configBean.setIsGroup(false);
        configBean.setPrincipalName(id);
        configBean.setAuthorizableId(id);
        return configBean;
    }

    @Test
    public void testSetupImpersonation() throws RepositoryException {

        when(impersonation.getImpersonators())
                .thenReturn(new TestPrincipalIterator("existing-to-be-removed-1", "test-principal-1", "test-principal-2"));
        impersonationInstallerService.setupImpersonation(user, Arrays.asList("test-principal-1", "test-principal-2", "test-principal-3"),
                authorizablesConfig, installationLog);

        verify(impersonation).grantImpersonation(new PrincipalImpl("test-principal-3"));
        verify(impersonation).revokeImpersonation(new PrincipalImpl("existing-to-be-removed-1"));

    }

    @Test
    public void testSetupImpersonationWithRegex() throws RepositoryException {

        when(impersonation.getImpersonators()).thenReturn(new TestPrincipalIterator("existing-to-be-removed-1", "test-principal-2"));
        impersonationInstallerService.setupImpersonation(user, Arrays.asList("test-principal-.*"), authorizablesConfig, installationLog);

        verify(impersonation).grantImpersonation(new PrincipalImpl("test-principal-1"));
        verify(impersonation).grantImpersonation(new PrincipalImpl("test-principal-3"));
        verify(impersonation).grantImpersonation(new PrincipalImpl("test-principal-4"));
        verify(impersonation).revokeImpersonation(new PrincipalImpl("existing-to-be-removed-1"));

    }

    @Test
    public void testNothingToDo() throws RepositoryException {

        when(impersonation.getImpersonators()).thenReturn(new TestPrincipalIterator());
        impersonationInstallerService.setupImpersonation(user, Collections.<String> emptyList(), authorizablesConfig, installationLog);

        verify(impersonation, never()).grantImpersonation(any(Principal.class));
        verify(impersonation, never()).revokeImpersonation(any(Principal.class));

    }

    private class TestPrincipalIterator implements PrincipalIterator {

        private final Iterator<String> principalsIt;

        public TestPrincipalIterator(String... principals) {
            this.principalsIt = Arrays.asList(principals).iterator();
        }

        @Override
        public void skip(long skipNum) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getSize() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getPosition() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasNext() {
            return principalsIt.hasNext();
        }

        @Override
        public Object next() {
            return nextPrincipal();
        }

        @Override
        public Principal nextPrincipal() {
            return new PrincipalImpl(principalsIt.next());
        }

    }
}
