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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

import static org.apache.sling.api.resource.runtime.dto.AuthType.no;
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

    @ParameterizedTest
    @CsvSource({
            "false, false, true",
            "false, true, false",
            "true, false, true",
            "true, true, false"
    })
    void testActivationSync(boolean runAsync, boolean canReadApps, boolean pathsForInstallationEmpty) throws RepositoryException, InterruptedException {
        setup(canReadApps);
        try (MockedStatic<FrameworkUtil> mockedFrameworkUtil = mockStatic(FrameworkUtil.class)) {
            createAndActivateStartupHookService(mockedFrameworkUtil, runAsync);
            // wait for the thread in AcToolStartupHookServiceImpl#runAcToolAsync to get started
            if (runAsync && canReadApps) {
                Thread.sleep(1000L);
            }
            verify(installationService, times(1)).apply(null,  pathsForInstallationEmpty ? new String[]{} : new String[]{ "^/$", "^$" }, true);
        }
    }

    void setup(boolean canReadApps) throws RepositoryException {
        FrameworkStartLevel startLevel = Mockito.mock(FrameworkStartLevel.class);
        when(startLevel.getStartLevel()).thenReturn(0);

        when(config.activationMode()).thenReturn(AcToolStartupHookServiceImpl.Config.StartupHookActivation.ALWAYS);

        when(session.hasPermission("/", Session.ACTION_SET_PROPERTY)).thenReturn(canReadApps);
        if (canReadApps) {
            when(noChildren.hasNext()).thenReturn(false);
            when(rootNode.getNodes()).thenReturn(noChildren);
            when(session.getRootNode()).thenReturn(rootNode);
        }
        when(repository.loginService(null, null)).thenReturn(session);

        when(bundle.getBundleContext()).thenReturn(bundleContext);
        when(bundle.getSymbolicName()).thenReturn(RuntimeHelper.INSTALLER_CORE_BUNDLE_SYMBOLIC_ID);
        when(bundle.adapt(FrameworkStartLevel.class)).thenReturn(startLevel);

        when(bundleContext.getBundles()).thenReturn(new Bundle[] { bundle });
        when(bundleContext.getBundle(anyLong())).thenReturn(bundle);
    }

    private void createAndActivateStartupHookService(MockedStatic<FrameworkUtil> mockedFrameworkUtil, boolean runAsyncForMutableContent) {
        mockedFrameworkUtil.when(() -> FrameworkUtil.getBundle(RuntimeHelper.class)).thenReturn(bundle);
        AcToolStartupHookServiceImpl startupHookService = new AcToolStartupHookServiceImpl();
        startupHookService.repository = repository;
        startupHookService.acInstallationService = installationService;
        when(config.runAsyncForMutableConent()).thenReturn(runAsyncForMutableContent);
        startupHookService.activate(bundleContext, config);
    }
}