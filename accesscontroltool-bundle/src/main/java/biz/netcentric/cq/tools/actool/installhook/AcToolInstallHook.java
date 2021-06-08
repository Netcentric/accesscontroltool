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
import org.apache.jackrabbit.vault.packaging.InstallContext;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.osgi.annotation.versioning.ProviderType;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.api.InstallationResult;
import biz.netcentric.cq.tools.actool.helper.runtime.RuntimeHelper;

@ProviderType
public class AcToolInstallHook extends OsgiAwareInstallHook {

    private static final Logger LOG = LoggerFactory.getLogger(AcToolInstallHook.class);
    private static final String PROPERTY_ACTOOL_INSTALL_AT_INSTALLED_PHASE = "actool.atInstalledPhase";

    private boolean alreadyRan = false;

    @Override
    public void execute(InstallContext context) throws PackageException {
        LOG.debug("Executing install hook for phase {}.", context.getPhase());

        switch (context.getPhase()) {
        case PREPARE:
            if (!shouldInstallInPhaseInstalled(context.getPackage())) {

                install(context);
            }
            break;
        case INSTALLED:
            if (shouldInstallInPhaseInstalled(context.getPackage())) {
                install(context);
            }
            break;
        default:
            // nothing to do in all other phases
            break;
        }
    }

    private boolean shouldInstallInPhaseInstalled(PackageProperties properties) {
        return Boolean.parseBoolean(properties.getProperty(PROPERTY_ACTOOL_INSTALL_AT_INSTALLED_PHASE));
    }

    private void install(InstallContext context) throws PackageException {
        final ProgressTrackerListener listener = context.getOptions().getListener();
        /*
         * Workaround for a bug in CQ whereby the package installer goes into a loop if a PackageException is thrown Daycare Ticket:
         * https://daycare.day.com/home/netcentric/netcentric_de/partner_services/67812.html Granite Bug: GRANITE-7945, fixed in
         * com.day.jcr.vault, version 2.5.8 (AEM 6.1)
         */
        if (alreadyRan) {
            log("Evading attempt to run the install hook twice due to a bug in CQ.", listener);
            return;
        }
        alreadyRan = true;

        if (RuntimeHelper.isCloudReadyInstance()) {
            log("InstallHook is skipped by default in cloud (use package property 'actool.forceInstallHookInCloud = true' to force run)",
                    listener);
            return;
        }

        // check if AcTool is installed
        log("Installing ACLs through AcToolInstallHook in phase " + context.getPhase() + "...", listener);
        ServiceReference<AcToolInstallHookService> acToolInstallHookService = getServiceReference(AcToolInstallHookService.class);
        if (acToolInstallHookService == null) {
            throw new PackageException(
                    "Could not get AcToolInstallHookService from OSGI service registry. Make sure the ACTool is installed!");
        }
        AcToolInstallHookService acService = (AcToolInstallHookService) getBundleContext().getService(acToolInstallHookService);
        if (acService == null) {
            throw new PackageException(
                    "Could not instantiate AcToolInstallHookService. Make sure the ACTool is installed and check the log for errors");
        }

        try {
            InstallationResult result;
            try {
                result = acService.installYamlFilesFromPackage(context
                        .getPackage(), context.getSession(), context.getOptions().getListener());

            } catch (Exception e) {
                // needed as root cause of PackageException is not reliably logged in AEM 6.2
                LOG.error("Exception during execution of install hook: " + e, e);
                log("Exception while installing configurations: " + e, listener);
                throw new PackageException(e.getMessage(), e);
            }

            if (!result.isSuccess()) {
                throw new PackageException("AC Tool installation failed with "
                        + result.getErrors().size() + " errors. Check log for detailed error message(s)!");
            } else {
                log("Installed ACLs successfully through AcToolInstallHook!", listener);
            }
        } finally {
            getBundleContext().ungetService(acToolInstallHookService);
        }
    }
}
