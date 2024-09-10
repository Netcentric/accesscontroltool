package biz.netcentric.cq.tools.actool.startuphook.impl;

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

import biz.netcentric.cq.tools.actool.api.AcInstallationService;
import biz.netcentric.cq.tools.actool.helper.runtime.RuntimeHelper;
import org.apache.sling.jcr.api.SlingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.startlevel.FrameworkStartLevel;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcToolStartupHookServiceImplTest {

    @Mock
    Bundle bundle;

    @Mock
    BundleContext bundleContext;

    @Mock
    AcToolStartupHookServiceImpl.Config config;

    @Mock
    Session session;

    @Mock
    SlingRepository repository;

    @Mock
    Node rootNode;

    @Mock
    NodeIterator noChildren;

    @Spy
    AcInstallationService installationService;

    @Test
    void testActivationWithModifiableRoot() throws RepositoryException {
        boolean canSetPropertiesOnRootNode = true, runAsync = false, cloudOnly = false;
        setup(canSetPropertiesOnRootNode, runAsync, cloudOnly);
        try (MockedStatic<FrameworkUtil> mockedFrameworkUtil = mockStatic(FrameworkUtil.class)) {
            createAndActivateStartupHookService(mockedFrameworkUtil);
            verify(installationService, times(1)).apply(null, new String[]{"^/$", "^$"}, true);
        }
    }

    @Test
    void testActivationWithUnmodifiableRoot() throws RepositoryException {
        boolean canSetPropertiesOnRootNode = false, runAsync = false, cloudOnly = false;
        setup(canSetPropertiesOnRootNode, runAsync, cloudOnly);
        try (MockedStatic<FrameworkUtil> mockedFrameworkUtil = mockStatic(FrameworkUtil.class)) {
            createAndActivateStartupHookService(mockedFrameworkUtil);
            verify(installationService, times(1)).apply(null, new String[]{}, true);
        }
    }

    @Test
    void testActivationWithCloudOnly() throws RepositoryException {
        boolean canSetPropertiesOnRootNode = false, runAsync = false, cloudOnly = true;
        setup(canSetPropertiesOnRootNode, runAsync, cloudOnly);
        try (MockedStatic<FrameworkUtil> mockedFrameworkUtil = mockStatic(FrameworkUtil.class)) {
            createAndActivateStartupHookService(mockedFrameworkUtil);
            verify(installationService, never()).apply(null, new String[]{"^/$", "^$"}, true);
        }
    }

    @Test
    void testActivationWithAsync() throws RepositoryException, InterruptedException {
        boolean canSetPropertiesOnRootNode = true, runAsync = true, cloudOnly = false;
        setup(canSetPropertiesOnRootNode, runAsync, cloudOnly);
        try (MockedStatic<FrameworkUtil> mockedFrameworkUtil = mockStatic(FrameworkUtil.class)) {
            createAndActivateStartupHookService(mockedFrameworkUtil);
            Thread.sleep(1000L);
            verify(installationService, times(1)).apply(null, new String[]{"^/$", "^$"}, true);
        }
    }

    void setup(boolean canSetPropertiesOnRootNode, boolean runAsyncForMutableContent, boolean cloudOnly) throws RepositoryException {
        FrameworkStartLevel startLevel = Mockito.mock(FrameworkStartLevel.class);
        when(startLevel.getStartLevel()).thenReturn(0);

        if (canSetPropertiesOnRootNode) {
            when(session.hasPermission("/", Session.ACTION_SET_PROPERTY)).thenReturn(true);
            when(noChildren.hasNext()).thenReturn(false);
            when(rootNode.getNodes()).thenReturn(noChildren);
            when(session.getRootNode()).thenReturn(rootNode);
        } else if (!canSetPropertiesOnRootNode && !cloudOnly){
            when(session.hasPermission("/", Session.ACTION_SET_PROPERTY)).thenReturn(false);
        }
        if (!cloudOnly) {
            when(repository.loginService(null, null)).thenReturn(session);
        }

        when(bundle.getBundleContext()).thenReturn(bundleContext);
        when(bundle.getSymbolicName()).thenReturn(RuntimeHelper.INSTALLER_CORE_BUNDLE_SYMBOLIC_ID);
        when(bundle.adapt(FrameworkStartLevel.class)).thenReturn(startLevel);

        when(bundleContext.getBundles()).thenReturn(new Bundle[]{bundle});
        when(bundleContext.getBundle(anyLong())).thenReturn(bundle);

        when(config.runAsyncForMutableConent()).thenReturn(runAsyncForMutableContent);
        when(config.activationMode()).thenReturn(cloudOnly ?
                AcToolStartupHookServiceImpl.Config.StartupHookActivation.CLOUD_ONLY :
                AcToolStartupHookServiceImpl.Config.StartupHookActivation.ALWAYS);
    }

    private void createAndActivateStartupHookService(MockedStatic<FrameworkUtil> mockedFrameworkUtil) {
        mockedFrameworkUtil.when(() -> FrameworkUtil.getBundle(RuntimeHelper.class)).thenReturn(bundle);
        AcToolStartupHookServiceImpl startupHookService = new AcToolStartupHookServiceImpl();
        startupHookService.repository = repository;
        startupHookService.acInstallationService = installationService;
        startupHookService.activate(bundleContext, config);
    }
}