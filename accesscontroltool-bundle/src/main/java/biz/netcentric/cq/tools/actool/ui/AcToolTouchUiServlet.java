/*
 * (C) Copyright 2019 Netcentric, A Cognizant Digital Business.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 */
package biz.netcentric.cq.tools.actool.ui;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2024 Cognizant Netcentric
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.io.IOException;

import javax.servlet.Servlet;
import javax.servlet.ServletException;

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
    private AcToolUiService acToolUiService;

    @Override
    protected void doGet(SlingHttpServletRequest req, SlingHttpServletResponse resp) throws ServletException, IOException {
        acToolUiService.doGet(req, resp, req.getRequestPathInfo().getResourcePath() + ".html", true);
    }

    @Override
    protected void doPost(SlingHttpServletRequest req, SlingHttpServletResponse resp) throws ServletException, IOException {
        acToolUiService.doPost(req, resp);
        LOG.debug("Applied AC tool config via Touch UI by user {}", req.getUserPrincipal());
    }


}
