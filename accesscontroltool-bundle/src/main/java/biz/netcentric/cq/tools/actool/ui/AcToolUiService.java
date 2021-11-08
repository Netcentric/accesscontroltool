package biz.netcentric.cq.tools.actool.ui;

import static org.apache.commons.lang3.StringEscapeUtils.escapeHtml4;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.webconsole.WebConsoleConstants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.api.AcInstallationService;
import biz.netcentric.cq.tools.actool.api.InstallationLog;
import biz.netcentric.cq.tools.actool.api.InstallationResult;
import biz.netcentric.cq.tools.actool.dumpservice.ConfigDumpService;
import biz.netcentric.cq.tools.actool.history.AcHistoryService;
import biz.netcentric.cq.tools.actool.history.AcToolExecution;
import biz.netcentric.cq.tools.actool.impl.AcInstallationServiceImpl;
import biz.netcentric.cq.tools.actool.impl.AcInstallationServiceInternal;

@Component(service = { AcToolUiService.class })
public class AcToolUiService {

    private static final Logger LOG = LoggerFactory.getLogger(AcToolUiService.class);

    public static final String PARAM_CONFIGURATION_ROOT_PATH = "configurationRootPath";
    public static final String PARAM_APPLY_ONLY_IF_CHANGED = "applyOnlyIfChanged";
    public static final String PARAM_BASE_PATHS = "basePaths";
    public static final String PARAM_SHOW_LOG_NO = "showLogNo";
    public static final String PARAM_SHOW_LOG_VERBOSE = "showLogVerbose";

    public static final String PAGE_NAME = "actool";

    static final String PATH_SEGMENT_DUMP = "dump.yaml";

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private ConfigDumpService dumpService;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    AcInstallationServiceInternal acInstallationService;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private WebConsoleConfigTracker webConsoleConfig;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private AcHistoryService acHistoryService;

    protected void doGet(HttpServletRequest req, HttpServletResponse resp, String postPath, boolean isTouchUi)
            throws ServletException, IOException {

        if (req.getRequestURI().endsWith(PATH_SEGMENT_DUMP)) {
            streamDumpToResponse(resp);
        } else {
            renderUi(req, resp, postPath, isTouchUi);
        }
    }

    @SuppressWarnings(/* SonarCloud false positive */ {
            "javasecurity:S5131" /* response is sent as text/plain, it's not interpreted */,
            "javasecurity:S5145" /* logging the path is fine */ })
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws IOException, ServletException {

        RequestParameters reqParams = RequestParameters.fromRequest(req, acInstallationService);
        LOG.info("Received POST request to apply AC Tool config with configurationRootPath={} basePaths={}", reqParams.configurationRootPath, reqParams.basePaths);

        InstallationLog log = acInstallationService.apply(reqParams.configurationRootPath, reqParams.getBasePathsArr(),
                reqParams.applyOnlyIfChanged);

        String msg = log.getMessageHistory().trim();
        msg = msg.contains("\n") ? StringUtils.substringAfterLast(msg, "\n") : msg;

        PrintWriter pw = resp.getWriter();
        resp.setContentType("text/plain");
        if (((InstallationResult) log).isSuccess()) {
            resp.setStatus(HttpServletResponse.SC_OK);
            pw.println("Applied AC Tool config from " + reqParams.configurationRootPath + ":\n" + msg);
        } else {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            pw.println("Error while applying AC Tool config from " + reqParams.configurationRootPath);
        }
    }

    public String getWebConsoleRoot(HttpServletRequest req) {
        return (String) req.getAttribute(WebConsoleConstants.ATTR_APP_ROOT);
    }
    
    private void renderUi(HttpServletRequest req, HttpServletResponse resp, String postPath, boolean isTouchUi) throws IOException {
        RequestParameters reqParams = RequestParameters.fromRequest(req, acInstallationService);

        final PrintWriter out = resp.getWriter();

        printForm(out, reqParams, postPath, isTouchUi, getWebConsoleRoot(req));

        printInstallationLogsSection(out, reqParams, isTouchUi);

        if(!isTouchUi) {
            String jmxUrl = getWebConsoleRoot(req) + "/jmx/"
                    + URLEncoder.encode("biz.netcentric.cq.tools:type=ACTool", StandardCharsets.UTF_8.toString());
            out.println("More operations are available at <a href='" + jmxUrl + "' "+forceValidLink(isTouchUi)+">AC Tool JMX Bean</a><br/>\n<br/>\n");
        }
    }

    void streamDumpToResponse(final HttpServletResponse resp) throws IOException {
        resp.setContentType("application/x-yaml");
        resp.setHeader("Content-Disposition", "inline; filename=\"actool-dump.yaml\"");
        String dumpAsString = dumpService.getCompletePrincipalBasedDumpsAsString();
        PrintWriter out = resp.getWriter();
        out.println(dumpAsString);
        out.flush();
    }

    private void printInstallationLogsSection(PrintWriter out, RequestParameters reqParams, boolean isTouchUi) {

        List<AcToolExecution> acToolExecutions = acHistoryService.getAcToolExecutions();

        final HtmlWriter writer = new HtmlWriter(out, isTouchUi);
        writer.openTable("previousLogs");
        writer.tableHeader("Previous Logs", 5);

        if (acToolExecutions.isEmpty()) {
            writer.tr();
            writer.td("No logs found on this instance (yet)");
            writer.closeTr();
            writer.closeTable();
            return;
        }

        for (int i = 1; i <= acToolExecutions.size(); i++) {
            AcToolExecution acToolExecution = acToolExecutions.get(i - 1);
            String linkToLog = PAGE_NAME + "?showLogNo=" + i;
            writer.tr();
            writer.openTd();
            writer.println(getExecutionDateStr(acToolExecution));
            writer.closeTd();
            writer.openTd();
            writer.println(StringUtils.defaultString(acToolExecution.getConfigurationRootPath(), ""));
            writer.closeTd();
            writer.openTd();
            writer.println("via " + StringUtils.defaultString(acToolExecution.getTrigger(), "<unknown>"));
            writer.closeTd();
            writer.openTd();
            writer.println(getExecutionStatusStr(acToolExecution));
            writer.closeTd();
            writer.openTd();
            writer.println("[<a href='" + linkToLog + "'>short</a>] [<a href='" + linkToLog + "&showLogVerbose=true'>verbose</a>]");
            writer.closeTd();
            writer.closeTr();
        }
        writer.closeTable();

        if (reqParams.showLogNo > 0 && reqParams.showLogNo <= acToolExecutions.size()) {

            AcToolExecution acToolExecution = acToolExecutions.get(reqParams.showLogNo - 1);
            String logLabel = "Previous Log " + reqParams.showLogNo + ": " + getExecutionLabel(acToolExecution);
            String logHtml = acHistoryService.getLogFromHistory(reqParams.showLogNo, true, reqParams.showLogVerbose);

            writer.openTable("logTable");
            writer.tableHeader(logLabel, 1, false);
            writer.tr();
            writer.openTd();
            writer.println(logHtml);
            writer.closeTd();
            writer.closeTr();
            writer.closeTable();
        }

    }

    private String getExecutionLabel(AcToolExecution acToolExecution) {
        String statusString = getExecutionStatusStr(acToolExecution);
        String configRootPath = acToolExecution.getConfigurationRootPath();
        return getExecutionDateStr(acToolExecution) 
                + (configRootPath != null ? " " + configRootPath : "")
                + " via " + acToolExecution.getTrigger() + ": "
                + statusString;
    }

    private String getExecutionDateStr(AcToolExecution acToolExecution) {
        return getDateFormat().format(acToolExecution.getInstallationDate());
    }

    private String getExecutionStatusStr(AcToolExecution acToolExecution) {
        int authorizableChanges = acToolExecution.getAuthorizableChanges();
        int aclChanges = acToolExecution.getAclChanges();
        String changedStr = (authorizableChanges > -1 && aclChanges > -1) ? " ("+authorizableChanges+" authorizables/"+aclChanges+" ACLs changed)":"";
        String statusString = getExecutionStatusHtml(acToolExecution) + (acToolExecution.isSuccess() ? changedStr : "");
        return statusString;
    }

    private SimpleDateFormat getDateFormat() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    }

    private String getExecutionStatusHtml(AcToolExecution acToolExecution) {
        return acToolExecution.isSuccess() ? "SUCCESS" : "<span style='color:red;font-weight: bold;'>FAILED</span>";
    }

    private void printForm(final PrintWriter out, RequestParameters reqParams, String postPath, boolean isTouchUI, String webConsoleRoot) throws IOException {
        final HtmlWriter writer = new HtmlWriter(out, isTouchUI);

        printCss(isTouchUI, writer);

        writer.print("<form id='acForm' action='" + postPath + "'>");
        writer.openTable("acFormTable");
        writer.tableHeader("AC Tool v" + acInstallationService.getVersion(), 3);

        writer.tr();
        writer.openTd();
        writer.print("<b>Configuration Root Path</b>");
        
        if(!isTouchUI) {
            writer.print("<br/> (default from <a href='" + webConsoleRoot
            + "/configMgr/biz.netcentric.cq.tools.actool.impl.AcInstallationServiceImpl' "+forceValidLink(isTouchUI)+">OSGi config</a>)");
        }
        
        writer.closeTd();
        writer.openTd();
        writer.print("<input type='text' name='" + PARAM_CONFIGURATION_ROOT_PATH + "' value='");
        if (reqParams.configurationRootPath != null) {
            writer.print(escapeHtml4(reqParams.configurationRootPath));
        }
        writer.println("' class='input' size='70'>");
        writer.print("<input type='checkbox' name='" + PARAM_APPLY_ONLY_IF_CHANGED + "' value='true'"
                + (reqParams.applyOnlyIfChanged ? " checked='checked'" : "") + " /> apply only if config changed");
        writer.closeTd();

        writer.openTd();
        writer.println("<button " + getCoralButtonAtts(isTouchUI) + " id='downloadDumpButton' onclick=\"window.open('" + postPath + ".html/"
                + PATH_SEGMENT_DUMP + "', '_blank');return false;\"> Download Dump </button>");
        writer.closeTd();

        writer.closeTr();

        writer.tr();
        writer.openTd();
        writer.println(
                "<b>Base Path(s)</b> to restrict where ACLs are installed<br/>  (comma-separated, leave empty to apply the whole configuration)");
        writer.closeTd();
        writer.openTd();
        writer.print("<input type='text' name='" + PARAM_BASE_PATHS + "' value='");
        if (reqParams.basePaths != null) {
            writer.print(escapeHtml4(StringUtils.join(reqParams.basePaths, ",")));
        }
        writer.println("' class='input' size='70'>");
        writer.closeTd();

        writer.openTd();
        writer.println("");
        writer.closeTd();

        writer.closeTr();

        writer.tr();
        writer.openTd();
        String onClick = "var as=$('#applySpinner');as.show(); var b=$('#applyButton');b.prop('disabled', true); oldL = b.text();b.text(' Applying AC Tool Configuration... ');var f=$('#acForm');var fd=f.serialize();$.post(f.attr('action'), fd).done(function(text){alert(text)}).fail(function(xhr){alert(xhr.status===403?'Permission Denied':'Config could not be applied - check log for errors')}).always(function(text) { var ll=text&amp;&amp;text.indexOf&amp;&amp;text.indexOf('identical to last execution')===-1?'"
                + PARAM_SHOW_LOG_NO + "=1&':'';as.hide();b.text(oldL);b.prop('disabled', false);location.href='" + PAGE_NAME + "?'+ll+fd; });return false";
        writer.println("<button " + getCoralButtonAtts(isTouchUI) + " id='applyButton' onclick=\"" + onClick + "\"> Apply AC Tool Configuration </button>");
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


    private void printCss(boolean isTouchUI, final HtmlWriter writer) {
        StringBuilder css = new StringBuilder();
        // spinner css
        css.append(".spinner{display:inline-block;position:relative;width:32px;height:32px}.spinner div{display:inline-block;position:absolute;left:3px;width:7px;background:#777;animation:spinner 1.2s cubic-bezier(0,.5,.5,1) infinite}.spinner div:nth-child(1){left:3px;animation-delay:-.24s}.spinner div:nth-child(2){left:13px;animation-delay:-.12s}.spinner div:nth-child(3){left:23px;animation-delay:0}@keyframes spinner{0%{top:3px;height:26px}100%,50%{top:10px;height:13px}}");
        if(!isTouchUI) {
            css.append("#applyButton {margin:10px 4px 10px 4px}");
        }
        writer.println("<style>"+css+"</style>");
    }

    private String getCoralButtonAtts(boolean isTouchUI) {
        return isTouchUI ? " is='coral-button' variant='primary' iconsize='S'" : "";
    }

    private String forceValidLink(boolean isTouchUI) {
        return isTouchUI ? "x-cq-linkchecker='valid'": "";
    }

    static class RequestParameters {

        static RequestParameters fromRequest(HttpServletRequest req, AcInstallationService acInstallationService) {
            List<String> allConfigRootPaths = ((AcInstallationServiceImpl) acInstallationService).getConfigurationRootPaths();
            // take the first configured root path as default
            String defaultConfigRootPath = allConfigRootPaths.size() > 0 ? allConfigRootPaths.get(allConfigRootPaths.size()-1) : "";
            String configRootPath = 
                    getParam(req, AcToolUiService.PARAM_CONFIGURATION_ROOT_PATH, defaultConfigRootPath);
            String basePathsParam = req.getParameter(AcToolUiService.PARAM_BASE_PATHS);
            return new RequestParameters(
                    configRootPath,
                    StringUtils.isNotBlank(basePathsParam) ? Arrays.asList(basePathsParam.split(" *, *")) : null,
                    Integer.parseInt(getParam(req, AcToolUiService.PARAM_SHOW_LOG_NO, "0")),
                    Boolean.valueOf(req.getParameter(AcToolUiService.PARAM_SHOW_LOG_VERBOSE)),
                    Boolean.valueOf(req.getParameter(AcToolUiService.PARAM_APPLY_ONLY_IF_CHANGED)));
        }
        
        final String configurationRootPath;
        final List<String> basePaths;
        final int showLogNo;
        final boolean showLogVerbose;
        final boolean applyOnlyIfChanged;

        public RequestParameters(String configurationRootPath, List<String> basePaths, int showLogNo, boolean showLogVerbose,
                boolean applyOnlyIfChanged) {
            super();
            this.configurationRootPath = configurationRootPath;
            this.basePaths = basePaths;
            this.showLogNo = showLogNo;
            this.showLogVerbose = showLogVerbose;
            this.applyOnlyIfChanged = applyOnlyIfChanged;
        }

        public String[] getBasePathsArr() {
            if (basePaths == null) {
                return null;
            } else {
                return basePaths.toArray(new String[basePaths.size()]);
            }
        }

        static String getParam(final HttpServletRequest req, final String name, final String defaultValue) {
            String result = req.getParameter(name);
            if (result == null) {
                result = defaultValue;
            }
            return StringUtils.trim(result);
        }

    }
}
