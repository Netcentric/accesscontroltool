/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configuploadlistener.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.api.resource.observation.ResourceChangeListener;
import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.metatype.annotations.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.api.AcInstallationService;
import biz.netcentric.cq.tools.actool.configuploadlistener.UploadListenerService;
import biz.netcentric.cq.tools.actool.configuploadlistener.impl.UploadListenerServiceImpl.Configuration;
import biz.netcentric.cq.tools.actool.impl.AcInstallationServiceImpl;

@Component(configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true)
@Designate(ocd = Configuration.class)
public class UploadListenerServiceImpl implements UploadListenerService {
    private static final Logger LOG = LoggerFactory.getLogger(UploadListenerServiceImpl.class);

    private static final String CONFIG_PID = "biz.netcentric.cq.tools.actool.configuploadlistener.impl.UploadListenerServiceImpl";

    private List<String> configurationPaths;
    private boolean enabled;
    private int executionDelayInMs;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    AcInstallationService acInstallationService;

    @Reference(policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.OPTIONAL)
    Scheduler scheduler;

    private List<AcToolConfigUpdateListener> updateListeners = new ArrayList<>();

    @ObjectClassDefinition(name = "AC Tool Configuration Upload Listener Service", description = "Listens for ACL configuration uploads and triggers ACL Service.", id = CONFIG_PID)
    protected static @interface Configuration {
        @AttributeDefinition(name = "Service status", description = "Enable/disable AC Configuration Upload Listener Service", options = {
                @Option(label = "disabled", value = "disabled"),
                @Option(label = "enabled", value = "enabled")
        })
        String AceUploadListener_setStatusService() default "disabled";

        @AttributeDefinition(name = "Execution delay", description = "Defer execution of the ACTool after detecting the first changed YAML file by this amount of milliseconds")
        int AceUploadListener_triggerDelayMs() default 5000;
    }

    @Activate
    public void activate(BundleContext bundleContext, Configuration configuration) throws Exception {

        this.configurationPaths = ((AcInstallationServiceImpl) acInstallationService).getConfigurationRootPaths();

        if (StringUtils.equals(configuration.AceUploadListener_setStatusService(), "enabled")) {
            this.enabled = true;
        } else {
            this.enabled = false;
        }
        this.executionDelayInMs = configuration.AceUploadListener_triggerDelayMs();
        if (!this.enabled) {
            LOG.debug("UploadListenerServiceImpl is not active, not registering listener(s)");
            return;
        } else if (this.configurationPaths.isEmpty()) {
            LOG.warn("UploadListenerServiceImpl requires PID "
                    + "biz.netcentric.cq.tools.actool.impl.AcInstallationServiceImpl/'configurationRootPaths' to be configured");
            return;
        } else {
            for (String configurationRootPath : configurationPaths) {
                updateListeners.add(new AcToolConfigUpdateListener(configurationRootPath, bundleContext));
            }
        }
    }

    @Deactivate
    public void deactivate() {
        Iterator<AcToolConfigUpdateListener> updateListenersIt = updateListeners.iterator();
        while (updateListenersIt.hasNext()) {
            updateListenersIt.next().unregisterListener();
            updateListenersIt.remove();
        }
    }

    public final class AcToolConfigUpdateListener implements Runnable, ResourceChangeListener {

        private final String configurationRootPath;
        private ServiceRegistration<ResourceChangeListener> resourceChangeListenerRegistration;

        private volatile boolean isScheduled = false; // used to aggregate events into one AC Tool execution

        public AcToolConfigUpdateListener(String configurationRootPath, BundleContext bundleContext) {
            this.configurationRootPath = configurationRootPath;
            registerListener(configurationRootPath, bundleContext);
        }

        private void registerListener(String configurationRootPath, BundleContext bundleContext) {

            Dictionary<String, Object> changeListenerProperties = new Hashtable<>();

            changeListenerProperties.put(ResourceChangeListener.CHANGES, new String[] { "ADDED", "CHANGED", "REMOVED" });
            changeListenerProperties.put(ResourceChangeListener.PATHS, configurationRootPath);
            changeListenerProperties.put(AcToolConfigUpdateListener.class.getSimpleName(), Boolean.TRUE);

            resourceChangeListenerRegistration = bundleContext.registerService(ResourceChangeListener.class, this,
                    changeListenerProperties);
            LOG.info("Registered event handler for AC configuration root path: {}", configurationRootPath);
        }

        private void unregisterListener() {
            try {
                resourceChangeListenerRegistration.unregister();
                LOG.info("Unregistered event handler for AC configuration root path: {}", configurationRootPath);
            } catch (Exception e) {
                LOG.error("Exception while unregistering event handler in UploadListenerService: " + e, e);
            }
        }

        @Override
        public void run() {
            isScheduled = false;
            LOG.info("Applying config for AC Tool root path {}", configurationRootPath);
            acInstallationService.apply(configurationRootPath);
        }

        @Override
        public void onChange(List<ResourceChange> changes) {
            LOG.trace("UploadListener ResourceChangeListener triggered with '{}'", changes);

            for (ResourceChange change : changes) {

                String path = change.getPath();
                // if it ends with .yaml and has resource type nt:file
                if (path.endsWith(".yaml")) {

                    if (scheduler != null) {
                        LOG.info("Received change event for yaml file {}", path);
                        // delay processing until all YAML files have been placed there
                        scheduleExecution();
                    } else {
                        LOG.warn(
                                "Received change event for yaml file {}, but service org.apache.sling.commons.scheduler.Scheduler is not available (Skipping execution)",
                                path);
                    }

                } else {
                    LOG.debug("Observed resource event path is not ending with '.yaml': '{}'", path);
                }
            }
        }

        private synchronized void scheduleExecution() {
            if (!isScheduled) {
                final ScheduleOptions options = scheduler.AT(new Date(System.currentTimeMillis() + executionDelayInMs));
                options.name("UploadListener-Process-Change");
                scheduler.schedule(this, options);
                isScheduled = true;
                LOG.debug("Scheduled execution for path {}", configurationRootPath);
            } else {
                LOG.debug("Execution for path {} is already scheduled", configurationRootPath);
            }
        }

    }
}
