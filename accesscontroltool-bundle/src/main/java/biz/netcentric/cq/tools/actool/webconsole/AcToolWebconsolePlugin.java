/*
 * (C) Copyright 2019 Netcentric, A Cognizant Digital Business.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.webconsole;

import static org.apache.commons.lang.StringEscapeUtils.escapeHtml;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.api.AcInstallationService;
import biz.netcentric.cq.tools.actool.api.InstallationLog;
import biz.netcentric.cq.tools.actool.dumpservice.ConfigDumpService;
import biz.netcentric.cq.tools.actool.history.AcHistoryService;
import biz.netcentric.cq.tools.actool.history.PersistableInstallationLogger;
import biz.netcentric.cq.tools.actool.impl.AcInstallationServiceImpl;
import biz.netcentric.cq.tools.actool.impl.AcInstallationServiceInternal;

/** Webconsole plugin to execute health check services */
@Component(service=Servlet.class, property={
        org.osgi.framework.Constants.SERVICE_DESCRIPTION + "=AC Tool Web Console Plugin",
        "felix.webconsole.label=" + AcToolWebconsolePlugin.LABEL,
        "felix.webconsole.title="+ AcToolWebconsolePlugin.TITLE,
        "felix.webconsole.category="+ AcToolWebconsolePlugin.CATEGORY
})
@SuppressWarnings("serial")
public class AcToolWebconsolePlugin extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(AcToolWebconsolePlugin.class);

    
    public static final String TITLE = "AC Tool";
    public static final String LABEL = "actool";
    public static final String CATEGORY = "Main";
    
    public static final String PARAM_CONFIGURATION_ROOT_PATH = "configurationRootPath";
    public static final String PARAM_BASE_PATHS = "basePaths";
    public static final String PARAM_SHOW_LOG_NO = "showLogNo";

    private static final String PATH_SEGMENT_DUMP = "dump.yaml";

    @Reference
    AcInstallationServiceInternal acInstallationService;
    
    @Reference
    AcHistoryService acHistoryService;
    
    @Reference
    ConfigDumpService dumpService;

    
    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {

        if(processGetActions(req, resp)) {
            return;
        }
        
        RequestParameters reqParams = RequestParameters.fromRequest(req, acInstallationService);
        
        final PrintWriter out = resp.getWriter();
        
        printForm(out, reqParams);

        printInstallationLogsSection(out, reqParams);
        
        out.println("More operations are available at <a href='jmx/biz.netcentric.cq.tools:type=ACTool'>AC Tool JMX Bean</a><br/>\n<br/>\n");
        
    }

    private boolean processGetActions(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
        if(req.getRequestURI().endsWith("/"+LABEL+"/"+PATH_SEGMENT_DUMP)) {
            resp.setContentType("application/x-yaml");
            resp.setHeader("Content-Disposition", "inline; filename=\"actool-dump.yaml\"");
            String dumpAsString = dumpService.getCompletePrincipalBasedDumpsAsString();
            PrintWriter out = resp.getWriter();
            out.println(dumpAsString);
            out.flush();
            return true;
        }
        return false;
    }

    private void printInstallationLogsSection(PrintWriter out, RequestParameters reqParams) {
        String[] installationLogPaths = acHistoryService.getInstallationLogPaths();
        

        final HtmlWriter writer = new HtmlWriter(out);
        writer.openTable();
        writer.tableHeader("Previous Logs", 1);

        if(installationLogPaths==null) {
            writer.tr();
            writer.td("No logs found on this instance (yet)");
            writer.closeTr();
            writer.closeTable();
            return;
        }
        
        for (int i=0; i<installationLogPaths.length; i++) {
            String installationLogPath = installationLogPaths[i];
            String logLabel = StringUtils.substringAfterLast(installationLogPath, "/");
            String linkToLog = LABEL+"?showLogNo="+(i+1);
            writer.tr();
            writer.openTd();
            writer.println(markFailureRed(logLabel)+" [<a href='"+linkToLog+"'>show</a>]");
            writer.closeTd();
            writer.closeTr();
        }
        writer.closeTable();
        
        if(reqParams.showLogNo > 0 && reqParams.showLogNo <= installationLogPaths.length) {
            
            String installationLogPath = installationLogPaths[reqParams.showLogNo-1];
            String logLabel = StringUtils.substringAfterLast(installationLogPath, "/");
            String logHtml = acHistoryService.getLogFromHistory(reqParams.showLogNo, true);
            
            writer.openTable();
            writer.tableHeader("Log of "+markFailureRed(logLabel), 1, false);
            writer.tr();
            writer.openTd();
            writer.println(logHtml);
            writer.closeTd();
            writer.closeTr();
            writer.closeTable();
        }

    }


    private String markFailureRed(String logLabel) {
        return logLabel.replace("(failed)", "<span style='color:red'>(failed)</span>");
    }

    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws IOException, ServletException {
        
        RequestParameters reqParams = RequestParameters.fromRequest(req, acInstallationService);
        LOG.info("Received POST request to apply AC Tool config with configurationRootPath={} basePaths={}", reqParams.configurationRootPath, reqParams.basePaths);
        
        InstallationLog log = acInstallationService.apply(reqParams.configurationRootPath, reqParams.getBasePathsArr());
        
        PrintWriter pw = resp.getWriter();
        resp.setContentType("text/plain");
        if(((PersistableInstallationLogger)log).isSuccess()) {
            resp.setStatus(HttpServletResponse.SC_OK);
            pw.println("Applied AC Tool config from "+reqParams.configurationRootPath+".");
        } else {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            pw.println("Error while applying AC Tool config from "+reqParams.configurationRootPath);
        }
    }

    private void printForm(final PrintWriter out, RequestParameters reqParams) throws IOException {
        final HtmlWriter writer = new HtmlWriter(out);
        
        // spinner css
        writer.println("<style>#applyButton{margin:10px 4px 10px 4px}.spinner{display:inline-block;position:relative;width:32px;height:32px}.spinner div{display:inline-block;position:absolute;left:3px;width:7px;background:#777;animation:spinner 1.2s cubic-bezier(0,.5,.5,1) infinite}.spinner div:nth-child(1){left:3px;animation-delay:-.24s}.spinner div:nth-child(2){left:13px;animation-delay:-.12s}.spinner div:nth-child(3){left:23px;animation-delay:0}@keyframes spinner{0%{top:3px;height:26px}100%,50%{top:10px;height:13px}}</style>");

        writer.print("<form id='acForm'>");
        writer.openTable();
        writer.tableHeader("AC Tool v"+acInstallationService.getVersion(), 3);

        writer.tr();
        writer.openTd();
        writer.print("<b>Configuration Root Path</b><br/> (default from <a href='configMgr/biz.netcentric.cq.tools.actool.impl.AcInstallationServiceImpl'>OSGi config</a>)");
        writer.closeTd();
        writer.openTd();
        writer.print("<input type='text' name='" + PARAM_CONFIGURATION_ROOT_PATH + "' value='");
        if ( reqParams.configurationRootPath != null ) {
            writer.print(escapeHtml(reqParams.configurationRootPath));
        }
        writer.println("' class='input' size='70'>");
        writer.closeTd();
        
        writer.openTd();
        writer.println("<button id='downloadDumpButton' onclick=\"window.open('"+LABEL+"/"+PATH_SEGMENT_DUMP+"', '_blank')\"> Download Dump </button>");
        writer.closeTd();
        
        writer.closeTr();
        
        writer.tr();
        writer.openTd();
        writer.println("<b>Base Path(s)</b> to restrict where ACLs are installed<br/>  (comma-separated, leave empty to apply the whole configuration)");
        writer.closeTd();
        writer.openTd();
        writer.print("<input type='text' name='" + PARAM_BASE_PATHS + "' value='");
        if ( reqParams.basePaths != null ) {
            writer.print(escapeHtml(StringUtils.join(reqParams.basePaths, ",")));
        }
        writer.println("' class='input' size='70'>");
        writer.closeTd();
        
        writer.openTd();
        writer.println("");
        writer.closeTd();
                
        writer.closeTr();

        writer.tr();
        writer.openTd();
        String onClick = "$('#applySpinner').show(); var b=$('#applyButton'); b.prop('disabled', true); b.html(' Applying AC Tool Configuration... '); var fd=$('#acForm').serialize();$.post('"+LABEL+"', fd).done(function(){alert('Config successfully applied')}).fail(function(){alert('Config could not be applied - check log for errors')}).always(function() { location.href='"+LABEL+"?"+PARAM_SHOW_LOG_NO+"=1&'+fd; }); return false";
        writer.println("<button id='applyButton' onclick=\""+onClick+"\"> Apply AC Tool Configuration </button>");
        writer.closeTd();
        writer.openTd();
        writer.println("<div id='applySpinner' style='display:none' class='spinner'><div></div><div></div><div></div></div>");

        writer.openTd();
        writer.println("");
        writer.closeTd();
        
        
        writer.closeTr();

        writer.println("</form>");
        writer.closeTable();
    }

    
    private static class RequestParameters {
        
        static RequestParameters fromRequest(HttpServletRequest req, AcInstallationService acInstallationService) {
            String configRootPath = getParam(req, PARAM_CONFIGURATION_ROOT_PATH, ((AcInstallationServiceImpl)acInstallationService).getConfiguredAcConfigurationRootPath());
            String basePathsParam = req.getParameter(PARAM_BASE_PATHS);
            RequestParameters result = new RequestParameters(
                    configRootPath,
                    StringUtils.isNotBlank(basePathsParam) ? Arrays.asList(basePathsParam.split(" *, *")): null,
                    Integer.parseInt(getParam(req, PARAM_SHOW_LOG_NO, "0"))
            );

            return result;
        }
        
        private final String configurationRootPath;
        private final List<String> basePaths;
        private final int showLogNo;

        RequestParameters(String configurationRootPath, List<String> basePaths, int showLogNo) {
            super();
            this.configurationRootPath = configurationRootPath;
            this.basePaths = basePaths;
            this.showLogNo = showLogNo;
        }

        String[] getBasePathsArr() {
            if(basePaths==null) {
                return null;
            } else {
                return basePaths.toArray(new String[basePaths.size()]);
            }
        }
        
        private static String getParam(final HttpServletRequest req, final String name, final String defaultValue) {
            String result = req.getParameter(name);
            if(result == null) {
                result = defaultValue;
            }
            return result;
        }
        
    }
    

    static class HtmlWriter  {
    
        final PrintWriter pw;
    
        HtmlWriter(final PrintWriter pw) {
            this.pw = pw;
        }
        
        public void openTable() {
            pw.println("<table class='content' cellpadding='0' cellspacing='0' width='100%'>");
        }

        public void closeTable() {
            pw.println("</table>");
        }

        void print(String s) {
            pw.print(s);
        }
        void println(String s) {
            pw.println(s);
        }
        void newLine() {
            pw.println("<br/>");
        }
                
        void openTd() {
            openTd(1);
        }
        void openTd(int colspan) {
            pw.print("<td class='content' colspan='"+colspan+"'>");
        }

        void closeTd() {
            pw.print("</td>");
        }

    
        void td(final String label) {
            td(label, 1);
        }

        void td(final String label, int colspan) {
            openTd(colspan);
            pw.print(escapeHtml(label));
            closeTd();
        }
    
        void tr() {
            pw.println("<tr class='content'>");
        }
        
        void closeTr() {
            pw.println("</tr>");
        }
    
        void tableHeader(String title, int colspan) {
            tableHeader(title, colspan, true);
        }
        void tableHeader(String title, int colspan, boolean escape) {
            tr();
            pw.print("<th class='content container' colspan='"+colspan+"'>");
            pw.print(escape ? escapeHtml(title): title);
            pw.println("</th>");
            closeTr();
        }
    }
        
}
