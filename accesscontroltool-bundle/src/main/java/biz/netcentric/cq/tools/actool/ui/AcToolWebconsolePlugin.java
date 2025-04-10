/*
 * (C) Copyright 2019 Netcentric, A Cognizant Digital Business.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.ui;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2024 Cognizant Netcentric
 * %%
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * #L%
 */

import java.io.IOException;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;

/** Webconsole plugin to execute health check services */
@Component(service = Servlet.class, property = {
        org.osgi.framework.Constants.SERVICE_DESCRIPTION + "=AC Tool Web Console Plugin",
        "felix.webconsole.label=" + AcToolWebconsolePlugin.LABEL,
        "felix.webconsole.title=" + AcToolWebconsolePlugin.TITLE,
        "felix.webconsole.category=" + AcToolWebconsolePlugin.CATEGORY
})
@SuppressWarnings("serial")
public class AcToolWebconsolePlugin extends HttpServlet {

    public static final String TITLE = "AC Tool";
    public static final String LABEL = "actool";
    public static final String CATEGORY = "Main";

    private static final String WEBCONSOLE_ATTR_APP_ROOT = "felix.webconsole.appRoot"; // from https://github.com/apache/felix-dev/blob/e9dbc04d1ffbd1cdcc40759b63046e6808c5571d/webconsole/src/main/java/org/apache/felix/webconsole/WebConsoleConstants.java#L149

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private AcToolUiService acToolUiService;

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        acToolUiService.doGet(req, resp, getWebConsoleRoot(req) + "/" + LABEL, false);
    }

    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws IOException, ServletException {
        acToolUiService.doPost(req, resp);
    }

    public String getWebConsoleRoot(HttpServletRequest req) {
        return (String) req.getAttribute(WEBCONSOLE_ATTR_APP_ROOT);
    }
}
