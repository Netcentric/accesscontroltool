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
import org.apache.sling.api.SlingConstants;
import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.apache.sling.event.dea.DEAConstants;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
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

@Component
@Designate(ocd=Configuration.class)
public class UploadListenerServiceImpl implements UploadListenerService {
    private static final Logger LOG = LoggerFactory.getLogger(UploadListenerServiceImpl.class);

    private static final String CONFIG_PID = "biz.netcentric.cq.tools.actool.configuploadlistener.impl.UploadListenerServiceImpl";
    
    private List<String> configurationPaths;
    private boolean enabled;
    private int executionDelayInMs;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    AcInstallationService acInstallationService;
    
    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    Scheduler scheduler;

    private List<AcToolConfigUpdateListener> updateListeners = new ArrayList<>();

    @ObjectClassDefinition(name = "AC Tool Configuration Upload Listener Service", 
            description="Listens for ACL configuration uploads and triggers ACL Service.",
            id=CONFIG_PID)
    protected static @interface Configuration {
        @AttributeDefinition(name="Service status", description = "Enable/disable AC Configuration Upload Listener Service", 
            options={
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
            for(String configurationRootPath: configurationPaths) {
                updateListeners.add(new AcToolConfigUpdateListener(configurationRootPath, bundleContext));
            }
        }
    }

    @Deactivate
    public void deactivate() {
        Iterator<AcToolConfigUpdateListener> updateListenersIt = updateListeners.iterator();
        while(updateListenersIt.hasNext()) {
            updateListenersIt.next().unregisterEventListener();
            updateListenersIt.remove();
        }
    }

    private final class AcToolConfigUpdateListener implements Runnable, EventHandler {
        
        private final String configurationRootPath;
        private ServiceRegistration<EventHandler> eventListenerRegistration;
        
        private volatile boolean isScheduled = false; // used to aggregate events into one AC Tool execution

        public AcToolConfigUpdateListener(String configurationRootPath, BundleContext bundleContext) {
            this.configurationRootPath = configurationRootPath;
            registerEventListener(configurationRootPath, bundleContext);
        }

        private void registerEventListener(String configurationRootPath, BundleContext bundleContext) {
            // subscribe to resource events via OSGi Event Admin
            // http://sling.apache.org/documentation/the-sling-engine/resources.html#osgi-event-admin
            Dictionary<String,Object> eventHandlerProperties = new Hashtable<>();
            String[] topics = new String[] {SlingConstants.TOPIC_RESOURCE_ADDED, SlingConstants.TOPIC_RESOURCE_CHANGED};
            eventHandlerProperties.put(EventConstants.EVENT_TOPIC, topics);
            // make sure we only listen for a specific root path
            eventHandlerProperties.put(EventConstants.EVENT_FILTER, String.format("(%s=%s/*)", SlingConstants.PROPERTY_PATH, configurationRootPath));
            eventListenerRegistration = bundleContext.registerService(EventHandler.class, this, eventHandlerProperties);
            LOG.info("Registered event handler for AC configuration root path: {}", configurationRootPath);
        }

        private void unregisterEventListener() {
            try {
                eventListenerRegistration.unregister();
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
        public void handleEvent(org.osgi.service.event.Event event) {
            LOG.debug("UploadListener Event Handler triggered for event '{}'", event);
            // check if triggered from local instance (in case of clustered environment)
            // http://sling.apache.org/documentation/the-sling-engine/resources.html#osgi-event-admin
            if (event.getProperty(DEAConstants.PROPERTY_APPLICATION) != null) {
                LOG.debug("Ignore remotely triggered resource event");
                return;
            }
            // extract property path
            String path = (String)event.getProperty(SlingConstants.PROPERTY_PATH);
            // if it ends with .yaml and has resource type nt:file
            if (path != null && path.endsWith(".yaml")) {
                // check resource type
                String resourceType = (String)event.getProperty(SlingConstants.PROPERTY_RESOURCE_TYPE);
                if (resourceType != null && resourceType.equals("nt:file")) {
                    // most probably an updated yaml file
                    LOG.info("There are new or changed YAML files. Triggering execution of actool with a delay of {} milliseconds.", executionDelayInMs);
                    // we need to trigger the installation in a new thread, otherwise it may be blacklisted (http://felix.apache.org/documentation/subprojects/apache-felix-event-admin.html)
                    // also delay processing until all YAML files have been placed there
                    scheduleExecution();
                } else {
                    LOG.debug("Observed resource event is not of resource type nt:file but of resource type '{}'", resourceType);
                }
            } else {
                LOG.debug("Observed resource event path is not ending with '.yaml': '{}'", path);
            }
        }

        private synchronized void scheduleExecution() {
            if(!isScheduled) {
                final ScheduleOptions options = scheduler.AT(new Date(System.currentTimeMillis() + executionDelayInMs));
                options.name("UploadListenerServiceImpl: trigger installation service");
                scheduler.schedule(this, options);
                isScheduled = true;
                LOG.debug("Scheduled execution for path {}", configurationRootPath);
            } else {
                LOG.debug("Execution for path {} is already scheduled", configurationRootPath);
            }
        }
    }
}
