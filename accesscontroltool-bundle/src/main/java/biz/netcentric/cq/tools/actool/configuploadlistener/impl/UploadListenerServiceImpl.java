/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configuploadlistener.impl;

import java.util.Date;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.PropertyOption;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingConstants;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.apache.sling.event.dea.DEAConstants;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.api.AcInstallationService;
import biz.netcentric.cq.tools.actool.configuploadlistener.UploadListenerService;
import biz.netcentric.cq.tools.actool.impl.AcInstallationServiceImpl;

@Component(metatype = true, label = "AC Configuration Upload Listener Service", immediate = true, description = "Listens for ACL configuration uploads and triggers ACL Service.")
@Properties({

@Property(label = "Service status", name = UploadListenerServiceImpl.ACE_UPLOAD_LISTENER_SET_STATUS_SERVICE, options = {
        @PropertyOption(name = "disabled", value = "disabled"),
        @PropertyOption(name = "enabled", value = "enabled") }) })
@Property(label = "Execution delay", name = UploadListenerServiceImpl.ACE_UPLOAD_LISTENER_TRIGGER_DELAY_IN_MS, 
description = "Defer execution of the ACTool after detecting the first changed YAML file by this amount of milliseconds", 
intValue=UploadListenerServiceImpl.ACE_UPLOAD_LISTENER_TRIGGER_DELAY_IN_MS_DEFAULT_VALUE)
@Service(value = UploadListenerService.class)
public class UploadListenerServiceImpl implements UploadListenerService, EventHandler {
    private static final Logger LOG = LoggerFactory.getLogger(UploadListenerServiceImpl.class);

    static final String ACE_UPLOAD_LISTENER_SET_STATUS_SERVICE = "AceUploadListener.setStatusService";
    static final String ACE_UPLOAD_LISTENER_TRIGGER_DELAY_IN_MS = "AceUploadListener.triggerDelayMs";

    static final int ACE_UPLOAD_LISTENER_TRIGGER_DELAY_IN_MS_DEFAULT_VALUE = 5000;
    
    private String configurationPath;
    private boolean enabled;
    private int executionDelayInMs;

    @Reference
    AcInstallationService acInstallationService;
    
    @Reference
    Scheduler scheduler;

    private ServiceRegistration eventHandlerServiceRegistration;

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
            if (resourceType != null && resourceType.equals("nt:file")) 
            {
                // most probably an updated yaml file
                LOG.info("There are new or changed YAML files. Triggering execution of actool with a delay of {} milliseconds.", executionDelayInMs);
                // we need to trigger the installation in a new thread, otherwise it may be blacklisted (http://felix.apache.org/documentation/subprojects/apache-felix-event-admin.html)
                // also delay processing until all YAML files have been placed there
                final ScheduleOptions options = scheduler.AT(new Date(System.currentTimeMillis() + executionDelayInMs));
                options.name("UploadListenerServiceImpl: trigger installation service");
                scheduler.schedule(new Runnable() {
                    @Override
                    public void run() {
                        LOG.info("Triggering reload of configuration.");
                        acInstallationService.apply();
                    }
                }, options);
            } else {
                LOG.debug("Observed resource event is not of resource type nt:file but of resource type '{}'", resourceType);
            }
        } else {
            LOG.debug("Observed resource event path is not ending with '.yaml': '{}'", path);
        }
    }

    @SuppressWarnings("unchecked")
    @Activate
    public void activate(BundleContext bundleContext, @SuppressWarnings("rawtypes") final Map properties)
            throws Exception {
        this.configurationPath = ((AcInstallationServiceImpl) acInstallationService).getConfiguredAcConfigurationRootPath();
        String statusService = PropertiesUtil.toString(properties.get(UploadListenerServiceImpl.ACE_UPLOAD_LISTENER_SET_STATUS_SERVICE),
                "");
        if (StringUtils.equals(statusService, "enabled")) {
            this.enabled = true;
        } else {
            this.enabled = false;
        }
        this.executionDelayInMs = PropertiesUtil.toInteger(properties.get(ACE_UPLOAD_LISTENER_TRIGGER_DELAY_IN_MS), ACE_UPLOAD_LISTENER_TRIGGER_DELAY_IN_MS_DEFAULT_VALUE);
        if (!this.enabled) {
            LOG.debug("UploadListenerServiceImpl is not active, not registering listener");
            return;
        } else if (StringUtils.isBlank(this.configurationPath)) {
            LOG.warn("UploadListenerServiceImpl requires PID "
                    + "biz.netcentric.cq.tools.actool.impl.AcInstallationServiceImpl/'AceService.configurationPath' to be configured");
            return;
        } else {
            // subscribe to resource events via OSGi Event Admin
            // http://sling.apache.org/documentation/the-sling-engine/resources.html#osgi-event-admin
            @SuppressWarnings("rawtypes")
            Dictionary eventHandlerProperties = new Hashtable();
            String[] topics = new String[] {SlingConstants.TOPIC_RESOURCE_ADDED, SlingConstants.TOPIC_RESOURCE_CHANGED};
            eventHandlerProperties.put(EventConstants.EVENT_TOPIC, topics);
            // make sure we only listen for a specific root path
            eventHandlerProperties.put(EventConstants.EVENT_FILTER, String.format("(%s=%s/*)", SlingConstants.PROPERTY_PATH, configurationPath));
            eventHandlerServiceRegistration = bundleContext.registerService(EventHandler.class.getName(), this, eventHandlerProperties);
            LOG.info("Registered event handler for AC configuration root path: {}", this.configurationPath);
        }
    }

    @Deactivate
    public void deactivate() {
        if (eventHandlerServiceRegistration != null) {
            try {
                eventHandlerServiceRegistration.unregister();
                LOG.info("Unregistered event handler for AC configuration root path: {}", this.configurationPath);
            } catch (Exception e) {
                LOG.error("Exception while unregistering event handler in UploadListenerService: " + e, e);
            }
        }
    }

}
