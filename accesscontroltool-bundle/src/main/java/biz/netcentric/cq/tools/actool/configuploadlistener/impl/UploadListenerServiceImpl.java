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
import org.apache.felix.scr.annotations.PropertyOption;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.jcr.api.SlingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.aceservice.AceService;
import biz.netcentric.cq.tools.actool.configuploadlistener.UploadListenerService;
import biz.netcentric.cq.tools.actool.installationhistory.AcHistoryService;

@Component(metatype = true, label = "AC Configuration Upload Listener Service", immediate = true, description = "Listens for ACL configuration uploads and triggers ACL Service.")
@Properties({

@Property(label = "Service status", name = UploadListenerServiceImpl.ACE_UPLOAD_LISTENER_SET_STATUS_SERVICE, options = {
        @PropertyOption(name = "disabled", value = "disabled"),
        @PropertyOption(name = "enabled", value = "enabled") }) })
@Service(value = UploadListenerService.class)
public class UploadListenerServiceImpl implements UploadListenerService,
        EventListener {

    static final String ACE_UPLOAD_LISTENER_SET_STATUS_SERVICE = "AceUploadListener.setStatusService";

    private String configurationPath;
    private boolean enabled;

    private static final Logger LOG = LoggerFactory
            .getLogger(UploadListenerServiceImpl.class);

    private Session adminSession;

    @Reference
    SlingRepository repository;

    @Reference
    AceService aceService;

    @Reference
    AcHistoryService acHistoryService;

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
                        LOG.info("Detected new or changed node at {}.",  node.getPath());
                        ++changes;
                    } else {
                        LOG.debug("Node {} associated with event does not have configuration data.", event.getPath());
                    }
                }
            } catch (RepositoryException e) {
                LOG.error("Error while handling events.", e);
            }
            if (changes > 0) {
                LOG.info("There are {} new or changed files. Triggering reload of configuration.", changes);
                aceService.execute();
            }
        }
    }

    @Activate
    public void activate(@SuppressWarnings("rawtypes") final Map properties)
            throws Exception {
        this.configurationPath = aceService.getConfigurationRootPath();
        String statusService = PropertiesUtil
                .toString(
                        properties
                                .get(UploadListenerServiceImpl.ACE_UPLOAD_LISTENER_SET_STATUS_SERVICE),
                        "");
        if (StringUtils.equals(statusService, "enabled")) {
            this.enabled = true;
        } else {
            this.enabled = false;
        }

        setEventListener();
    }

    private void setEventListener() throws Exception {
        if (StringUtils.isNotBlank(this.configurationPath)) {
            try {
                adminSession = repository.loginAdministrative(null);

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
                LOG.info(
                        "added EventListener for ACE configuration root path: {}",
                        this.configurationPath);
            } catch (RepositoryException e) {
                LOG.error("RepositoryException in UploadListenerService:{}", e);
            }
        } else {
            LOG.warn("no root ACE configuration path configured in AceService");
        }
    }

    @Deactivate
    public void deactivate() {
        if (adminSession != null) {
            adminSession.logout();
        }
    }

    public void setPath(String path) {
        this.configurationPath = path;
        try {
            setEventListener();
        } catch (Exception e) {
            LOG.error("Exception in UploadListenerService: {}", e);
        }
    }

}
