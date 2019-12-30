/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.startuphook.impl;

import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.helper.runtime.RuntimeHelper;

/** Logging bundle startup with start level/slingRepositoryIsAvailable */
public class StartupBundleActivator implements BundleActivator {
    private static final Logger LOG = LoggerFactory.getLogger(StartupBundleActivator.class);

    @Activate
    public void start(BundleContext bundleContext) {
        boolean slingRepositoryIsAvailable = bundleContext.getServiceReference(SlingRepository.class) !=null;
        int currentStartLevel = RuntimeHelper.getCurrentStartLevel(bundleContext);
        LOG.info("AC Tool Bundle accesscontroltool-startuphook-bundle started at start level {}, SlingRepository is available: {}", 
                currentStartLevel, slingRepositoryIsAvailable);

    }

    @Override
    public void stop(BundleContext context) throws Exception {
        LOG.trace("AC Tool Bundle accesscontroltool-startuphook-bundle stopped");
    }
}
