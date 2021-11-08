/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.installhook;

import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.packaging.InstallHook;
import org.osgi.annotation.versioning.ProviderType;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A <a href="http://jackrabbit.apache.org/filevault">Jackrabbit FileVault</a> install hook 
 * which supports retrieving OSGi services and the bundle context as well as some logging capabilities.
 * 
 * <p>The new {@link org.apache.jackrabbit.vault.packaging.InstallHook} interface is only supported since bundle {@code com.day.jcr.vault} 
 * in version 2.5.10 (AEM 6.0 SP3 and AEM 6.1).
 * Previous versions of that bundle (and more specifically the {@code com.day.jcr.vault.packaging.impl.JrVltInstallHookProcessor$Hook#loadMainClass(...)} 
 * only support the old interface at {@code com.day.jcr.vault.packaging.InstallHook}</p>
 */
@ProviderType
public abstract class OsgiAwareInstallHook implements InstallHook {

    private final BundleContext bundleContext;
    private static final Logger LOG = LoggerFactory.getLogger(OsgiAwareInstallHook.class);

    public OsgiAwareInstallHook() throws ClassCastException {
        // since this class was loaded through a bundle class loader as well, just take the bundle context
        Bundle currentBundle = FrameworkUtil.getBundle(this.getClass());
        if (currentBundle == null) {
            throw new IllegalStateException("The class " + this.getClass() + " was not loaded through a bundle classloader");
        }

        bundleContext = currentBundle.getBundleContext();
        if (bundleContext == null) {
            throw new IllegalStateException("Could not get bundle context for bundle " + currentBundle);
        }
    }

    public BundleContext getBundleContext() {
        return bundleContext;
    }

	protected <T> ServiceReference<T> getServiceReference(Class<T> clazz) {
        return bundleContext.getServiceReference(clazz);
    }

    protected void log(String message, ProgressTrackerListener listener) {
        if (listener != null) {
            listener.onMessage(ProgressTrackerListener.Mode.TEXT, message, "");
        } else {
            LOG.info(message);
        }
    }
}
