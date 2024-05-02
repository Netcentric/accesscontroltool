package biz.netcentric.cq.tools.actool.ui;

import static org.apache.commons.lang3.StringEscapeUtils.escapeHtml4;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.webconsole.WebConsoleConstants;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.oak.spi.security.principal.EveryonePrincipal;
import org.apache.sling.api.SlingHttpServletRequest;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.api.AcInstallationService;
import biz.netcentric.cq.tools.actool.api.InstallationLog;
import biz.netcentric.cq.tools.actool.api.InstallationResult;
import biz.netcentric.cq.tools.actool.dumpservice.ConfigDumpService;
import biz.netcentric.cq.tools.actool.helper.UncheckedRepositoryException;
import biz.netcentric.cq.tools.actool.history.AcHistoryService;
import biz.netcentric.cq.tools.actool.history.AcToolExecution;
import biz.netcentric.cq.tools.actool.impl.AcInstallationServiceImpl;
import biz.netcentric.cq.tools.actool.impl.AcInstallationServiceInternal;
import biz.netcentric.cq.tools.actool.user.UserProcessor;

@Component(service = { AcToolUiService.class })
public class AcToolUiService {

    private static final Logger LOG = LoggerFactory.getLogger(AcToolUiService.class);

    public static final String PARAM_CONFIGURATION_ROOT_PATH = "configurationRootPath";
    public static final String PARAM_APPLY_ONLY_IF_CHANGED = "applyOnlyIfChanged";
    public static final String PARAM_BASE_PATHS = "basePaths";
    public static final String PARAM_SHOW_LOG_NO = "showLogNo";
    public static final String PARAM_SHOW_LOG_VERBOSE = "showLogVerbose";

    public static final String PAGE_NAME = "actool";

    static final String SUFFIX_DUMP_YAML = "dump.yaml";
    static final String SUFFIX_USERS_CSV = "users.csv";

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private ConfigDumpService dumpService;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private UserProcessor userProcessor;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    AcInstallationServiceInternal acInstallationService;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private WebConsoleConfigTracker webConsoleConfig;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private AcHistoryService acHistoryService;

    private final Map<String, String> countryCodePerName;

    public AcToolUiService() {
        countryCodePerName = new HashMap<>();
        for (String iso : Locale.getISOCountries()) {
            Locale l = new Locale(Locale.ENGLISH.getLanguage(), iso);
            countryCodePerName.put(l.getDisplayCountry(), iso);
        }
    }

    protected void doGet(HttpServletRequest req, HttpServletResponse resp, String path, boolean isTouchUi)
            throws ServletException, IOException {

        if (req.getRequestURI().endsWith(SUFFIX_DUMP_YAML)) {
            callWhenAuthorized(req, resp, this::streamDumpToResponse);
        } else if (req.getRequestURI().endsWith(SUFFIX_USERS_CSV)) {
            callWhenAuthorized(req, resp, this::streamUsersCsvToResponse);
        } else {
            renderUi(req, resp, path, isTouchUi);
        }
    }

    private void callWhenAuthorized(HttpServletRequest req, HttpServletResponse resp, Consumer<HttpServletResponse> responseConsumer) throws IOException {
        if (!hasAccessToFelixWebConsole(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "You do not have sufficent permissions to export users/groups/permissions");
            return;
        }
        try {
            responseConsumer.accept(resp);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
    @SuppressWarnings(/* SonarCloud false positive */ {
            "javasecurity:S5131" /* response is sent as text/plain, it's not interpreted */,
            "javasecurity:S5145" /* logging the path is fine */ })
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws IOException, ServletException {

        if (!hasAccessToFelixWebConsole(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "You do not have sufficent permissions to apply the configuration");
            return;
        }
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

    /**
     * Replicates the logic of the <a href="Sling Web Console Security Provider">https://sling.apache.org/documentation/bundles/web-console-extensions.html#authentication-handling</a>.
     * @param req the request
     * @return {@code true} if the user bound to the given request may also access the Felix Web Console or if we are outside of Sling, {@code false} otherwise
     */
    private boolean hasAccessToFelixWebConsole(HttpServletRequest req) {

        if (!(req instanceof SlingHttpServletRequest)) {
            // outside Sling this is only called by the Felix Web Console, which has its own security layer
            LOG.debug("Outside Sling no additional security checks are performed!");
            return true;
        }
        try {
            User requestUser = SlingHttpServletRequest.class.cast(req).getResourceResolver().adaptTo(User.class);
            if (requestUser != null) {
                if (StringUtils.equals(requestUser.getID(), "admin")) {
                    LOG.debug("Admin user is allowed to apply AC Tool");
                    return true;
                }

                if (ArrayUtils.contains(webConsoleConfig.getAllowedUsers(), requestUser.getID())) {
                    LOG.debug("User {} is allowed to apply AC Tool (allowed users: {})", requestUser.getID(), ArrayUtils.toString(webConsoleConfig.getAllowedUsers()));
                    return true;
                }

                Iterator<Group> memberOfIt = requestUser.memberOf();

                while (memberOfIt.hasNext()) {
                    Group memberOfGroup = memberOfIt.next();
                    if (ArrayUtils.contains(webConsoleConfig.getAllowedGroups(), memberOfGroup.getID())) {
                        LOG.debug("Group {} is allowed to apply AC Tool (allowed groups: {})", memberOfGroup.getID(), ArrayUtils.toString(webConsoleConfig.getAllowedGroups()));
                        return true;
                    }
                }
            }
            LOG.debug("Could not get associated user for Sling request");
            return false;
        } catch (Exception e) {
            throw new IllegalStateException("Could not check if user may apply AC Tool configuration: " + e, e);
        }
    }

    public String getWebConsoleRoot(HttpServletRequest req) {
        return (String) req.getAttribute(WebConsoleConstants.ATTR_APP_ROOT);
    }

    private void renderUi(HttpServletRequest req, HttpServletResponse resp, String path, boolean isTouchUi) throws IOException {
        RequestParameters reqParams = RequestParameters.fromRequest(req, acInstallationService);

        final PrintWriter out = resp.getWriter();
        final HtmlWriter writer = new HtmlWriter(out, isTouchUi);
        
        printCss(isTouchUi, writer);
        printVersion(writer);
        printImportSection(writer, reqParams, path, isTouchUi, getWebConsoleRoot(req));
        printExportSection(writer, reqParams, path, isTouchUi, getWebConsoleRoot(req));

        printInstallationLogsSection(writer, reqParams, isTouchUi);

        if(!isTouchUi) {
            String jmxUrl = getWebConsoleRoot(req) + "/jmx/"
                    + URLEncoder.encode("biz.netcentric.cq.tools:type=ACTool", StandardCharsets.UTF_8.toString());
            out.println("More operations are available at <a href='" + jmxUrl + "' "+forceValidLink(isTouchUi)+">AC Tool JMX Bean</a><br/>\n<br/>\n");
        }
    }

    void streamDumpToResponse(final HttpServletResponse resp) {
        resp.setContentType("application/x-yaml");
        resp.setHeader("Content-Disposition", "inline; filename=\"actool-dump.yaml\"");
        String dumpAsString = dumpService.getCompletePrincipalBasedDumpsAsString();
        try {
            PrintWriter out;
            out = resp.getWriter();
            out.println(dumpAsString);
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void streamUsersCsvToResponse(HttpServletResponse resp) {
        resp.setContentType("text/csv");
        resp.setHeader("Content-Disposition", "inline; filename=\"users.csv\"");
        try {
            PrintWriter out = resp.getWriter();
            out.println("Identity Type,Username,Domain,Email,First Name,Last Name,Country Code,ID,Product Configurations,Admin Roles,Product Configurations Administered,User Groups,User Groups Administered,Products Administered,Developer Access");
            try {
                userProcessor.forEachNonSystemUser(u -> {
                    try {
                        out.println(String.format(",%s,,%s,%s,%s,%s,,,,,%s", u.getID(), 
                                escapeAsCsvValue(getUserPropertyAsString(u, "profile/email")),
                                escapeAsCsvValue(getUserPropertyAsString(u, "profile/givenName")),
                                escapeAsCsvValue(getUserPropertyAsString(u, "profile/familyName")),
                                escapeAsCsvValue(getCountyCodeFromName(getUserPropertyAsString(u, "profile/country"))),
                                escapeAsCsvValue(getDeclaredMemberOfAsStrings(u))));
                    } catch (RepositoryException e) {
                        throw new UncheckedRepositoryException(e);
                    } 
                });
            } catch (UncheckedRepositoryException|RepositoryException e) {
                throw new IOException("Could not access users or their properties", e);
            }
            out.println();
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String getCountyCodeFromName(String countryName) {
        String countryCode = countryCodePerName.get(countryName);
        return countryCode != null ? countryCode : "";
    }

    private static String escapeAsCsvValue(String text) {
        if (text.contains(",")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        } else {
            return text;
        }
    }

    private static String getDeclaredMemberOfAsStrings(User user) throws RepositoryException {
        List<String> groupNames = new LinkedList<>();
        try {
            user.declaredMemberOf().forEachRemaining(g -> {
                try {
                    if (!EveryonePrincipal.NAME.equals(g.getID())) {
                        groupNames.add(g.getID());
                    }
                } catch (RepositoryException e) {
                    throw new UncheckedRepositoryException(e);
                }
            });
        } catch (UncheckedRepositoryException e) {
            throw e.getCause();
        }
        return String.join(",", groupNames);
    }

    private static String getUserPropertyAsString(User user, String propertyName) throws RepositoryException {
        Value[] values = user.getProperty(propertyName);
        if (values == null) {
            return "";
        }
        try {
            return Arrays.stream(values).map(t -> {
                try {
                    return t.getString();
                } catch (RepositoryException e) {
                    throw new UncheckedRepositoryException(new RepositoryException("Could not convert property \"" + propertyName + "\" of user \"" + user + " to string", e));
                }
            }).collect(Collectors.joining(", "));
        } catch (UncheckedRepositoryException e) {
            throw e.getCause();
        }
    }

    private void printVersion(HtmlWriter writer) {
        writer.openTable("version");
        writer.tableHeader("Version", 1);
        writer.tr();
        writer.td("v" + acInstallationService.getVersion());
        writer.closeTd();
        writer.closeTr();
        writer.closeTable();
    }

    private void printInstallationLogsSection(HtmlWriter writer, RequestParameters reqParams, boolean isTouchUi) {

        List<AcToolExecution> acToolExecutions = acHistoryService.getAcToolExecutions();

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

    private void printImportSection(final HtmlWriter writer, RequestParameters reqParams, String path, boolean isTouchUI, String webConsoleRoot) throws IOException {

        writer.print("<form id='acForm' action='" + path + "'>");
        writer.openTable("acFormTable");
        writer.tableHeader("Import", 2);

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

        writer.closeTr();

        writer.tr();
        writer.openTd();
        String onClick = "var as=$('#applySpinner');as.show(); var b=$('#applyButton');b.prop('disabled', true); oldL = b.text();b.text(' Applying AC Tool Configuration... ');var f=$('#acForm');var fd=f.serialize();$.post(f.attr('action'), fd).done(function(text){alert(text)}).fail(function(xhr){alert(xhr.status===403?'Permission Denied':'Config could not be applied - check log for errors')}).always(function(text) { var ll=text&amp;&amp;text.indexOf&amp;&amp;text.indexOf('identical to last execution')===-1?'"
                + PARAM_SHOW_LOG_NO + "=1&':'';as.hide();b.text(oldL);b.prop('disabled', false);location.href='" + PAGE_NAME + "?'+ll+fd; });return false";
        writer.println("<button " + getCoralButtonAtts(isTouchUI) + " id='applyButton' onclick=\"" + onClick + "\"> Apply AC Tool Configuration </button>");
        writer.closeTd();
        writer.openTd();
        writer.println("<div id='applySpinner' style='display:none' class='spinner'><div></div><div></div><div></div></div>");

        writer.closeTd();

        writer.closeTr();

        writer.println("</form>");
        writer.closeTable();
    }


    private void printExportSection(final HtmlWriter writer, RequestParameters reqParams, String path, boolean isTouchUI, String webConsoleRoot) throws IOException {
        writer.openTable("acExportTable");
        writer.tableHeader("Export", 2);
        writer.tr();
        writer.openTd();
        writer.print("Export in AC Tool YAML format. This includes groups and permissions (in form of ACEs).");
        writer.closeTd();
        writer.openTd();
        writer.println("<button " + getCoralButtonAtts(isTouchUI) + " id='downloadDumpButton' onclick=\"window.open('" + path + ".html/"
                + SUFFIX_DUMP_YAML + "', '_blank');return false;\"> Download YAML </button>");
        writer.closeTd();
        writer.closeTr();
        writer.tr();
        writer.openTd();
        writer.print("Export Users in Admin Console CSV format. This includes non-system users, their profiles and their direct group memberships.");
        writer.closeTd();
        writer.openTd();
        writer.println("<button " + getCoralButtonAtts(isTouchUI) + " id='downloadCsvButton' onclick=\"window.open('" + path + ".html/"
                + SUFFIX_USERS_CSV + "', '_blank');return false;\"> Download CSV </button>");
        writer.closeTd();
        writer.closeTr();

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
