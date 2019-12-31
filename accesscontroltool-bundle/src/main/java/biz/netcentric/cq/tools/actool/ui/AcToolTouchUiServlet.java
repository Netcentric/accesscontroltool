/*
 * (C) Copyright 2019 Netcentric, A Cognizant Digital Business.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.ui;

import java.io.IOException;
import java.util.Iterator;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Touch UI Servlet */
@Component(service = { Servlet.class })
@SlingServletResourceTypes(resourceTypes = "/apps/netcentric/actool/components/overview", methods = { "GET", "POST" })
@SuppressWarnings("serial")
public class AcToolTouchUiServlet extends SlingAllMethodsServlet {

    private static final Logger LOG = LoggerFactory.getLogger(AcToolTouchUiServlet.class);

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private WebConsoleConfigTracker webConsoleConfigTracker;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private AcToolUiService acToolUiService;

    @Override
    protected void doGet(SlingHttpServletRequest req, SlingHttpServletResponse resp) throws ServletException, IOException {

        if (StringUtils.isBlank(req.getRequestPathInfo().getSuffix())) {
            String targetUrl = req.getResourceResolver().resolve(req.getPathInfo()).getPath() + ".html/" + AcToolUiService.PAGE_NAME;
            resp.getWriter().println("<script type=\"text/javascript\">location.href='" + targetUrl + "'</script>");
            return;
        }

        acToolUiService.doGet(req, resp, req.getRequestPathInfo().getResourcePath(), true);

    }

    @Override
    protected void doPost(SlingHttpServletRequest req, SlingHttpServletResponse resp) throws ServletException, IOException {

        if (!mayApplyConfig(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "You do not have sufficent permissions to apply the configuration");
            return;
        }

        acToolUiService.doPost(req, resp);
        LOG.debug("Applied AC tool config via Touch UI by user {}", req.getUserPrincipal());
    }

    public boolean mayApplyConfig(SlingHttpServletRequest request) {

        try {
            User requestUser = request.getResourceResolver().adaptTo(User.class);

            if (requestUser != null) {
                if (StringUtils.equals(requestUser.getID(), "admin")) {
                    LOG.debug("Admin user is allowed to apply AC Tool");
                    return true;
                }

                if (ArrayUtils.contains(webConsoleConfigTracker.getAllowedUsers(), requestUser.getID())) {
                    LOG.debug("User {} is allowed to apply AC Tool (allowed users: {})", requestUser.getID(), ArrayUtils.toString(webConsoleConfigTracker.getAllowedUsers()));
                    return true;
                }

                Iterator<Group> memberOfIt = requestUser.memberOf();

                while (memberOfIt.hasNext()) {
                    Group memberOfGroup = memberOfIt.next();
                    if (ArrayUtils.contains(webConsoleConfigTracker.getAllowedGroups(), memberOfGroup.getID())) {
                        LOG.debug("Group {} is allowed to apply AC Tool (allowed groups: {})", memberOfGroup.getID(), ArrayUtils.toString(webConsoleConfigTracker.getAllowedGroups()));
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            throw new IllegalStateException("Could not check if user may apply AC Tool configuration: " + e, e);
        }
    }

}
