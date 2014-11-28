package biz.netcentric.cq.tools.actool.components;

import javax.jcr.Session;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import biz.netcentric.cq.tools.actool.installationhistory.impl.HistoryUtils;

public class HistoryRenderer {

    private SlingHttpServletRequest request;

    public void setSlingRequest(final SlingHttpServletRequest slingRequest) {
        this.request = slingRequest;
    }

    public String getHistory() {
        ResourceResolver resourceResolver = this.request.getResourceResolver();
        Session session = resourceResolver.adaptTo(Session.class);
        Resource resource = request.getResource();

        return HistoryUtils.getLogHtml(session, resource.getName());

    }

}
