package biz.netcentric.cq.tools.actool.helper.runtime;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2024 Cognizant Netcentric
 * %%
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * #L%
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeHelperTest {

    @Mock
    Session session;

    @Mock
    Bundle bundle;

    @Mock
    BundleContext bundleContext;

    @Test
    void shouldBeReadonlyIfRootWritable() throws RepositoryException {
        when(session.hasPermission("/", Session.ACTION_SET_PROPERTY)).thenReturn(true);
        assertTrue(RuntimeHelper.isAppsReadOnly(session));
    }

    @Test
    void shouldNotBeReadonlyIfRootNotWritable() throws RepositoryException {
        when(session.hasPermission("/", Session.ACTION_SET_PROPERTY)).thenReturn(false);
        assertFalse(RuntimeHelper.isAppsReadOnly(session));
    }

    @Test
    void shouldNotBeCompositeStoreWhenCoreInstallerPresent() {
        try (MockedStatic<FrameworkUtil> mockedFrameworkUtil = mockStatic(FrameworkUtil.class)) {
            setupOsgi(RuntimeHelper.INSTALLER_CORE_BUNDLE_SYMBOLIC_ID, mockedFrameworkUtil);
            assertFalse(RuntimeHelper.isCompositeNodeStore());
        }
    }

    @Test
    void shouldBeCompositeStoreWhenCoreInstallerNotPresent() {
        try (MockedStatic<FrameworkUtil> mockedFrameworkUtil = mockStatic(FrameworkUtil.class)) {
            setupOsgi("unknown.bundle", mockedFrameworkUtil);
            assertTrue(RuntimeHelper.isCompositeNodeStore());
        }
    }

    private void setupOsgi(String t, MockedStatic<FrameworkUtil> mockedFrameworkUtil) {
        when(bundle.getBundleContext()).thenReturn(bundleContext);
        when(bundle.getSymbolicName()).thenReturn(t);
        when(bundleContext.getBundles()).thenReturn(new Bundle[]{bundle});
        mockedFrameworkUtil.when(() -> FrameworkUtil.getBundle(RuntimeHelper.class)).thenReturn(bundle);
    }
}