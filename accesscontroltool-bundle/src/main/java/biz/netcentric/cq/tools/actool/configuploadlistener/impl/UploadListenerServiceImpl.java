/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configuploadlistener.impl;

import java.util.Map;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.EventListener;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.PropertyOption;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.jcr.api.SlingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.api.AcInstallationService;
import biz.netcentric.cq.tools.actool.configuploadlistener.UploadListenerService;
import biz.netcentric.cq.tools.actool.helper.Constants;
import biz.netcentric.cq.tools.actool.impl.AcInstallationServiceImpl;

@Component(metatype = true, label = "AC Configuration Upload Listener Service", immediate = true, description = "Listens for ACL configuration uploads and triggers ACL Service.")
@Properties({

@Property(label = "Service status", name = UploadListenerServiceImpl.ACE_UPLOAD_LISTENER_SET_STATUS_SERVICE, options = {
        @PropertyOption(name = "disabled", value = "disabled"),
        @PropertyOption(name = "enabled", value = "enabled") }) })
@Service(value = UploadListenerService.class)
public class UploadListenerServiceImpl implements UploadListenerService, EventListener {
    private static final Logger LOG = LoggerFactory.getLogger(UploadListenerServiceImpl.class);

    static final String ACE_UPLOAD_LISTENER_SET_STATUS_SERVICE = "AceUploadListener.setStatusService";

    private String configurationPath;
    private boolean enabled;

    private Session adminSession;

    @Reference
    SlingRepository repository;

    @Reference
    AcInstallationService acInstallationService;

    @Override
    public void onEvent(EventIterator events) {

        if (this.enabled) {
            int changes = 0; // Number of new or changed files.
            try {
                while (events.hasNext()) {
                    Event event = events.nextEvent();
                    Node node = null;
                    switch (event.getType()) {
                    case Event.NODE_ADDED:
                        node = adminSession.getNode(event.getPath());
                        break;
                    case Event.PROPERTY_CHANGED:
                        if (event.getPath().endsWith("jcr:content/jcr:data")) {
                            node = adminSession.getNode(event.getPath().replace("/jcr:content/jcr:data", ""));
                        }
                        break;
                    default:
                        LOG.warn("Unexpected event: {}", event);    
                    }
                    if (node != null && node.hasProperty("jcr:content/jcr:data")) {
                        LOG.info("Detected new or changed node at {}", node.getPath());
                        changes++;
                    } else {
                        LOG.debug("Node {} associated with event does not have configuration data.", event.getPath());
                    }
                }
            } catch (RepositoryException e) {
                LOG.error("Error while handling events.", e);
            }
            if (changes > 0) {
                LOG.info("There are {} new or changed files. Triggering reload of configuration.", changes);
                acInstallationService.apply();
            }
        }
    }

    @Activate
    public void activate(@SuppressWarnings("rawtypes") final Map properties)
            throws Exception {
        this.configurationPath = ((AcInstallationServiceImpl) acInstallationService).getConfiguredAcConfigurationRootPath();
        String statusService = PropertiesUtil.toString(properties.get(UploadListenerServiceImpl.ACE_UPLOAD_LISTENER_SET_STATUS_SERVICE),
                "");
        if (StringUtils.equals(statusService, "enabled")) {
            this.enabled = true;
        } else {
            this.enabled = false;
        }

        if (!this.enabled) {
            LOG.debug("UploadListenerServiceImpl is not active, not registering listener");
            return;
        } else if (StringUtils.isBlank(this.configurationPath)) {
            LOG.warn("UploadListenerServiceImpl requires PID "
                    + "biz.netcentric.cq.tools.actool.impl.AcInstallationServiceImpl/'AceService.configurationPath' to be configured");
            return;
        } else {
            setupEventListener();
        }
    }

    private void setupEventListener() throws Exception {
        try {
            adminSession = repository.loginService(Constants.USER_AC_SERVICE, null);

            adminSession
                    .getWorkspace()
                    .getObservationManager()
                    .addEventListener(

                            this, // handler

                            // Event.PROPERTY_ADDED|Event.NODE_ADDED,
                            // //binary combination of event types
                            Event.NODE_ADDED | Event.PROPERTY_CHANGED,
                            this.configurationPath, // path

                            true, // is Deep?

                            null, // uuids filter

                            null, // nodetypes filter
                            false);
            LOG.info("Registered event listener for AC configuration root path: {}", this.configurationPath);
        } catch (RepositoryException e) {
            LOG.error("Exception while registering listener in UploadListenerService: " + e, e);
        }
    }

    @Deactivate
    public void deactivate() {
        if (adminSession != null) {
            try {
                adminSession.getWorkspace().getObservationManager().removeEventListener(this);
                LOG.info("Unregistered event listener for AC configuration root path: {}", this.configurationPath);
            } catch (Exception e) {
                LOG.error("Exception while unregistering listener in UploadListenerService: " + e, e);
            }
            adminSession.logout();
        }
    }

}
