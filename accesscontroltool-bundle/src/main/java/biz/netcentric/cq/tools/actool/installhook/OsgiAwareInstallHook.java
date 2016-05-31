/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.installhook;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.jcr.vault.fs.api.ProgressTrackerListener;
import com.day.jcr.vault.fs.io.ImportOptions;
import com.day.jcr.vault.packaging.InstallHook;

public abstract class OsgiAwareInstallHook implements InstallHook {

	private final BundleContext bundleContext;
	private static final Logger LOG = LoggerFactory.getLogger(OsgiAwareInstallHook.class);

	public OsgiAwareInstallHook() throws ClassCastException {
		// since this class was loaded through a bundle class loader as well, just take the bundle context
		Bundle currentBundle = FrameworkUtil.getBundle(this.getClass());
		if (currentBundle == null) {
			throw new IllegalStateException("The class " + this.getClass() + " was not loaded througha a bundle classloader");
		}
		
		bundleContext = currentBundle.getBundleContext();
		if (bundleContext == null) {
			throw new IllegalStateException("Could not get bundle context for bundle " + currentBundle);
		}
	}

	public BundleContext getBundleContext() {
		return bundleContext;
	}

	public ServiceReference getServiceReference(String clazz) {
		ServiceReference serviceReference = bundleContext
				.getServiceReference(clazz);
		return serviceReference;
	}

    public void log(String message, ImportOptions options) {
		ProgressTrackerListener listener = options.getListener();
		if (listener != null) {
			listener.onMessage(ProgressTrackerListener.Mode.TEXT, message, "");
		} else {
			LOG.info(message);
		}
	}
}
