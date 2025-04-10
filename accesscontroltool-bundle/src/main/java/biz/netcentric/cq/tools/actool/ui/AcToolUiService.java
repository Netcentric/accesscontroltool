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

import static org.apache.commons.lang3.StringEscapeUtils.escapeHtml4;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.oak.spi.security.principal.EveryonePrincipal;
import org.apache.sling.api.SlingHttpServletRequest;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.api.AcInstallationService;
import biz.netcentric.cq.tools.actool.api.InstallationLogLevel;
import biz.netcentric.cq.tools.actool.api.InstallationOptionsBuilder;
import biz.netcentric.cq.tools.actool.dumpservice.ConfigDumpService;
import biz.netcentric.cq.tools.actool.helper.UncheckedRepositoryException;
import biz.netcentric.cq.tools.actool.history.AcHistoryService;
import biz.netcentric.cq.tools.actool.history.AcToolExecution;
import biz.netcentric.cq.tools.actool.impl.AcInstallationServiceImpl;
import biz.netcentric.cq.tools.actool.impl.AcInstallationServiceInternal;
import biz.netcentric.cq.tools.actool.user.UserProcessor;

/** 
 * Service that allows to apply AC Tool configuration and gather status of users/groups and permissions from a Web UI (either Touch UI or
 * Web Console Plugin).
 * Leverages either Coral UI 3 (for Touch UI) or JQuery UI (for Web Console Plugin) for rendering widgets.
 */
@Component(service = { AcToolUiService.class })
@Designate(ocd=biz.netcentric.cq.tools.actool.ui.AcToolUiService.Configuration.class)
public class AcToolUiService {

    private static final String CONTENT_DISPOSITION = "Content-Disposition";

    private static final Logger LOG = LoggerFactory.getLogger(AcToolUiService.class);

    public static final String PARAM_CONFIGURATION_ROOT_PATH = "configurationRootPath";
    public static final String PARAM_APPLY_ONLY_IF_CHANGED = "applyOnlyIfChanged";
    private static final String PARAM_UPDATE_EXISTING_EXTERNAL_GROUPS = "updateExistingExternalGroups";
    public static final String PARAM_BASE_PATHS = "basePaths";
    public static final String PARAM_SHOW_LOG_ID = "showLogId";
    public static final String PARAM_SHOW_LOG_VERBOSE = "showLogVerbose";

    public static final String PAGE_NAME = "actool";

    static final String SUFFIX_DUMP_YAML = "dump.yaml";
    static final String SUFFIX_USERS_CSV = "users.csv";
    static final String SUFFIX_DOWNLOAD_LOG = "download.log";
    static final String SUFFIX_STREAM_LOG = "streamlog";

    private static final int MAX_LINE_WIDTH = 180; // max line width for log output in characters

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private ConfigDumpService dumpService;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private UserProcessor userProcessor;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    AcInstallationServiceInternal acInstallationService;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private AcHistoryService acHistoryService;

    @ObjectClassDefinition(name = "AC Tool UI Service", 
            description="Service that allows to apply AC Tool configuration and gather status of users/groups and permissions from a Web UI (either Touch UI or Web Console Plugin).")
    protected static @interface Configuration {
        
        @AttributeDefinition(name="Read access", description="Principal names allowed to export all users/groups and permissions in the system. Only leveraged for Touch UI but not for Web Console Plugin.")
        String[] readAccessPrincipalNames() default { "administrators", "admin" };
        
        @AttributeDefinition(name="Write access", description="Principal names allowed to modify users/groups and permissions in the system via ACTool configuration files. Only leveraged for Touch UI but not for Web Console Plugin.")
        String[] writeAccessPrincipalNames() default { "administrators", "admin" };
    }

    private final Map<String, String> countryCodePerName;

    private final Configuration config;

    @Activate
    public AcToolUiService(Configuration config) {
        this.config = config;
        countryCodePerName = new HashMap<>();
        for (String iso : Locale.getISOCountries()) {
            Locale l = new Locale(Locale.ENGLISH.getLanguage(), iso);
            countryCodePerName.put(l.getDisplayCountry(), iso);
        }
    }

    /**
     * 
     * @param req the request
     * @param resp the response
     * @param basePath the basePath of the underlying servlet. Every URL starting with this prefix is served through the same servlet. This is either the path to the webconsole plugin (without extension) or a content path including extension having the necessary resource type (for Touch UI)
     * @param isTouchUi {@code true} if the request is from a Touch UI, {@code false} if it is from a Web Console Plugin
     * @throws ServletException
     * @throws IOException
     */
    protected void doGet(HttpServletRequest req, HttpServletResponse resp, String basePath, boolean isTouchUi)
            throws ServletException, IOException {

        if (req.getRequestURI().startsWith(basePath)) {
            if (req.getRequestURI().endsWith(SUFFIX_DUMP_YAML)) {
                callWhenReadAccessGranted(req, resp, this::streamDumpToResponse);
                return;
            } else if (req.getRequestURI().endsWith(SUFFIX_USERS_CSV)) {
                callWhenReadAccessGranted(req, resp, this::streamUsersCsvToResponse);
                return;
            } else if (req.getRequestURI().endsWith(SUFFIX_DOWNLOAD_LOG)) {
                downloadLog(req, resp);
                return;
            } else if (req.getRequestURI().endsWith(SUFFIX_STREAM_LOG)) {
                streamLog(req, resp);
                return;
            } else {
                // either spool resource
                String resourcePath = req.getRequestURI().substring(basePath.length());
                if (resourcePath.startsWith("/res/")) {
                    // check for a resource, fail if none
                    if (!spoolResource(req, resourcePath, resp)) {
                        resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                    }
                    return;
                }
            }
        }
        // or render UI
        renderUi(req, resp, basePath, isTouchUi);
    }

    // The following method is copied and slightly adjusted from from https://github.com/apache/felix-dev/blob/5d878f37b89ceef59920644d5e427f493b904030/webconsole/src/main/java/org/apache/felix/webconsole/servlet/AbstractServlet.java#L71

   /**
     * If the request addresses a resource , this method serves it
     * and returns <code>true</code>. Otherwise <code>false</code> is returned.
     * <p>
     * If <code>true</code> is returned, the request is considered complete and
     * request processing terminates. Otherwise request processing continues
     * with normal plugin rendering.
     *
     * @param request The request object
     * @param resourcePath The path to the resource to be served
     * @param response The response object
     *
     * @throws IOException If an error occurs accessing or spooling the resource.
     */
    protected final boolean spoolResource(final HttpServletRequest request, String resourcePath, final HttpServletResponse response) throws IOException {
        // check for a resource, fail if none
        final URL url = getClass().getResource(resourcePath);
        if ( url == null ) {
            return false;
        }

        // open the connection and the stream (we use the stream to be able
        // to at least hint to close the connection because there is no
        // method to explicitly close the conneciton, unfortunately)
        final URLConnection connection = url.openConnection();
        try ( final InputStream ins = connection.getInputStream()) {
            // FELIX-2017 Equinox may return an URL for a non-existing
            // resource but then (instead of throwing) return null on
            // getInputStream. We should account for this situation and
            // just assume a non-existing resource in this case.
            if (ins == null) {
                return false;
            }

            // check whether we may return 304/UNMODIFIED
            long lastModified = connection.getLastModified();
            if ( lastModified > 0 ) {
                long ifModifiedSince = request.getDateHeader( "If-Modified-Since" );
                if ( ifModifiedSince >= ( lastModified / 1000 * 1000 ) ) {
                    // Round down to the nearest second for a proper compare
                    // A ifModifiedSince of -1 will always be less
                    response.setStatus( HttpServletResponse.SC_NOT_MODIFIED );

                    return true;
                }

                // have to send, so set the last modified header now
                response.setDateHeader( "Last-Modified", lastModified );
            }

            // describe the contents
            response.setContentType(request.getServletContext().getMimeType( request.getPathInfo() ) );
            if (connection.getContentLength() != -1) {
                response.setContentLength( connection.getContentLength() );
            }
            response.setStatus( HttpServletResponse.SC_OK);

            // spool the actual contents
            final OutputStream out = response.getOutputStream();
            final byte[] buf = new byte[2048];
            int rd;
            while ( ( rd = ins.read( buf ) ) >= 0 ) {
                out.write( buf, 0, rd );
            }
        }
        return true;
    }
        
    private void callWhenReadAccessGranted(HttpServletRequest req, HttpServletResponse resp, Consumer<HttpServletResponse> responseConsumer) throws IOException, ServletException {
        if (!isOneOfPrincipalNamesBound(req, config.readAccessPrincipalNames())) {
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

        if (!isOneOfPrincipalNamesBound(req, config.writeAccessPrincipalNames())) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "You do not have sufficent permissions to apply the configuration");
            return;
        }
        RequestParameters reqParams = RequestParameters.fromRequest(req, acInstallationService);
        LOG.info("Received POST request to apply AC Tool config with configurationRootPath={} basePaths={}", reqParams.configurationRootPath, reqParams.basePaths);

        InstallationOptionsBuilder builder = new InstallationOptionsBuilder();
        if (StringUtils.isNotBlank(reqParams.configurationRootPath)) {
            builder.withConfigurationRootPath(reqParams.configurationRootPath);
        }
        if (!reqParams.getBasePathsArr().isEmpty()) {
            builder.withRestrictedToPaths(reqParams.getBasePathsArr());
        }
        if (reqParams.applyOnlyIfChanged) {
            builder.skipIfConfigUnchanged();
        }
        if (reqParams.updateExistingExternalGroups) {
            builder.updateExistingExternalGroups();
        }
        try {
            String jobId = acInstallationService.applyAsynchronously(builder.build());
            // return full URL to the server-sent event stream for this installation
            resp.setContentType("text/plain");
            String streamLogUrl = req.getRequestURI() + "/" + SUFFIX_STREAM_LOG + "?jobId=" + URLEncoder.encode(jobId, StandardCharsets.UTF_8.toString()) + "&" + PARAM_SHOW_LOG_VERBOSE + "=" + reqParams.showLogVerbose;
            resp.getWriter().print(streamLogUrl);
        } catch (IllegalStateException e) {
            LOG.warn("Could not apply configuration: {}", e.getMessage());
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        }

    }

    /**
     * Similar to the logic of the <a href="https://sling.apache.org/documentation/bundles/web-console-extensions.html#authentication-handling">Sling Web Console Security Provider</a> but acting on principal names
     * @param req the request
     * @param principalNames the principal names to check against
     * @return {@code true} if the session bound to the given request is bound to any of the given principal names
     * @throws ServletException 
     * @throws RepositoryException 
     */
    private boolean isOneOfPrincipalNamesBound(HttpServletRequest req, String[] principalNames) throws ServletException {
        if (!(req instanceof SlingHttpServletRequest)) {
            // outside Sling this is only called by the Felix Web Console, which has its own security layer
            LOG.debug("Outside Sling no additional security checks are performed!");
            return true;
        }
        Session session = SlingHttpServletRequest.class.cast(req).getResourceResolver().adaptTo(Session.class);
        return isOneOfPrincipalNamesBound(JackrabbitSession.class.cast(session), principalNames);
    }

    private boolean isOneOfPrincipalNamesBound(JackrabbitSession session, String[] principalNames) throws ServletException {
        BoundPrincipals boundPrincipals;
        try {
            boundPrincipals = new BoundPrincipals(JackrabbitSession.class.cast(session));
        } catch (RepositoryException e) {
           throw new ServletException("Could not determine bound principals", e);
        }
        return boundPrincipals.containsOneOf(Arrays.asList(principalNames));
    }

    private void renderUi(HttpServletRequest req, HttpServletResponse resp, String basePath, boolean isTouchUi) throws ServletException, IOException {
        RequestParameters reqParams = RequestParameters.fromRequest(req, acInstallationService);

        final PrintWriter out = resp.getWriter();
        final HtmlWriter writer = new HtmlWriter(out, isTouchUi);
        
        printCss(isTouchUi, writer);
        printJs(basePath, writer);
        printVersion(writer);
        printImportSection(writer, reqParams, basePath, isTouchUi, isOneOfPrincipalNamesBound(req, config.writeAccessPrincipalNames()));
        printExportSection(writer, reqParams, basePath, isTouchUi, isOneOfPrincipalNamesBound(req, config.readAccessPrincipalNames()));

        try {
            printInstallationLogsSection(writer, reqParams, basePath, req.getRequestURI(), isTouchUi);
        } catch (RepositoryException e) {
            throw new ServletException("Could not read log from repository", e);
        }

        if(!isTouchUi) {
            String jmxUrl = basePath + "/../jmx/"
                    + URLEncoder.encode("biz.netcentric.cq.tools:type=ACTool", StandardCharsets.UTF_8.toString());
            out.println("More operations are available at <a href='" + jmxUrl + "' "+forceValidLink(isTouchUi)+">AC Tool JMX Bean</a><br/>\n<br/>\n");
        }
    }

    void downloadLog(HttpServletRequest req, final HttpServletResponse resp) throws IOException {
        RequestParameters reqParams = RequestParameters.fromRequest(req, acInstallationService);
        try {
            if (StringUtils.isBlank(reqParams.showLogId)) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "No log id provided");
                return;
            }
            // generate an ordered map of all executions (key = id, value = execution)
            Map<String, AcToolExecution> acToolExecutions = acHistoryService.getAcToolExecutions().stream().collect(
                    Collectors.toMap(
                            AcToolExecution::getId, 
                            Function.identity(),
                            (u, v) -> {
                                throw new IllegalStateException(String.format("Duplicate key %s", u));
                            },
                            LinkedHashMap::new));
            AcToolExecution acToolExecution = acToolExecutions.get(reqParams.showLogId);
            if (acToolExecution == null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            String filename = "actool-execution-" + reqParams.showLogId + ".log";
            String logPlain = acHistoryService.getLogFromHistory(reqParams.showLogId, false, reqParams.showLogVerbose, -1);
    
            resp.setContentType("text/plain");
            // get id from request
            resp.setHeader(CONTENT_DISPOSITION, "attachment; filename=\""+filename+"\"");
            resp.getWriter().print(logPlain);
        } catch (RepositoryException e) {
            throw new IllegalStateException("Could not read log from repository", e);
        }
    }

    /** Server-sent events emitted for an asynchronous execution log
     * @throws IOException 
     * 
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events">Server-sent events</a>
     */
    private void streamLog(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // set charset explicitly to utf-8, otherwise jetty is appending the default ISO charset which is not accepted by Chrome for event-stream
        resp.setContentType("text/event-stream;charset=utf-8");
        String jobId = req.getParameter("jobId");
        if (StringUtils.isBlank(jobId)) {
            LOG.warn("No jobId provided as request parameter");
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        final boolean isVerbose = Boolean.parseBoolean(req.getParameter(AcToolUiService.PARAM_SHOW_LOG_VERBOSE));
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        boolean isActive = acInstallationService.attachLogListener(jobId, (level, message) -> {
            try {
                if (!isVerbose && level == InstallationLogLevel.TRACE) {
                    return;
                }
                messages.put(level + ": " + message);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, success -> {
            try {
                messages.put("FINISHED: " + success);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        if (!isActive) {
            LOG.debug("Haven't found job with id {}, probably already finished processing it", jobId);
            resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        try {
            while (isActive) {
                String message = messages.poll(30, java.util.concurrent.TimeUnit.SECONDS);
                if (message == null) {
                    // just sent keep-alives
                    message = ".";
                }
                if (message.startsWith("FINISHED: ")) {
                    isActive = false;
                } else {
                    resp.getWriter().println("data: " + message);
                    resp.getWriter().println();
                    resp.getWriter().flush();
                }
            }
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    void streamDumpToResponse(final HttpServletResponse resp) {
        resp.setContentType("application/x-yaml");
        resp.setHeader(CONTENT_DISPOSITION, "inline; filename=\"actool-dump.yaml\"");
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
        resp.setHeader(CONTENT_DISPOSITION, "inline; filename=\"users.csv\"");
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
                    throw new UncheckedRepositoryException(new RepositoryException("Could not convert property \"" + propertyName + "\" of user \"" + user + "\" to string", e));
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

    private void printInstallationLogsSection(HtmlWriter writer, RequestParameters reqParams, String basePath, String currentPath, boolean isTouchUi) throws RepositoryException {

        // generate an ordered map of all executions (key = id, value = execution)
        Map<String, AcToolExecution> acToolExecutions = acHistoryService.getAcToolExecutions().stream().collect(
                Collectors.toMap(
                        AcToolExecution::getId, 
                        Function.identity(),
                        (u, v) -> {
                            throw new IllegalStateException(String.format("Duplicate key %s", u));
                        },
                        LinkedHashMap::new));

        writer.openTable("previousLogs");
        writer.tableHeader("Execution Logs", 5);

        if (acToolExecutions.isEmpty()) {
            writer.tr();
            writer.td("No logs found on this instance (yet)");
            writer.closeTr();
            writer.closeTable();
            return;
        }

        for (AcToolExecution acToolExecution : acToolExecutions.values()) {
            String linkToLog =  currentPath + "?" + PARAM_SHOW_LOG_ID + "=" + acToolExecution.getId();
            String downloadLinkToLog = basePath + "/" + SUFFIX_DOWNLOAD_LOG + "?" + PARAM_SHOW_LOG_ID + "=" + acToolExecution.getId();
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
            writer.println("[<a href='" + linkToLog + "'>short</a>] [<a href='" + linkToLog + "&showLogVerbose=true'>verbose</a>] [<a href='" + downloadLinkToLog + "&showLogVerbose=true'>download verbose</a>]");
            writer.closeTd();
            writer.closeTr();
        }
        writer.closeTable();

        if (StringUtils.isNotBlank(reqParams.showLogId)) {

            AcToolExecution acToolExecution = acToolExecutions.get(reqParams.showLogId);
            if (acToolExecution == null) {
                writer.println("No log found for id " + reqParams.showLogId);
                return;
            } else {
                String logLabel = "Execution Log " + reqParams.showLogId + ": " + getExecutionLabel(acToolExecution);
                String logHtml = acHistoryService.getLogFromHistory(reqParams.showLogId, true, reqParams.showLogVerbose, MAX_LINE_WIDTH);

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

    private void printImportSection(final HtmlWriter writer, RequestParameters reqParams, String basePath, boolean isTouchUI, boolean hasWritePermission) throws IOException {

        writer.print("<form id='acForm' action='" + basePath + "'>");
        writer.openTable("acFormTable");
        writer.tableHeader("Import", 2);

        writer.tr();
        writer.openTd();
        writer.print("<b>Configuration Root Path</b>");
        
        if(!isTouchUI) {
            writer.print("<br/> (default from <a href='" + basePath
            + "/../configMgr/biz.netcentric.cq.tools.actool.impl.AcInstallationServiceImpl' "+forceValidLink(isTouchUI)+">OSGi config</a>)");
        }
        
        writer.closeTd();
        writer.openTd();
        writer.print("<input type='text' name='" + PARAM_CONFIGURATION_ROOT_PATH + "' value='");
        if (reqParams.configurationRootPath != null) {
            writer.print(escapeHtml4(reqParams.configurationRootPath));
        }
        writer.println("' class='input' size='70'>");
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
        writer.println("<b>Advanced Options</b>");
        writer.closeTd();
        writer.openTd();
        writer.print("<input type='checkbox' name='" + PARAM_APPLY_ONLY_IF_CHANGED + "' value='true'"
                + (reqParams.applyOnlyIfChanged ? " checked='checked'" : "") + " /> Apply only if config changed");
        writer.println("<br/>");
        writer.print("<input type='checkbox' name='" + PARAM_UPDATE_EXISTING_EXTERNAL_GROUPS + "' value='true'"
                + (reqParams.updateExistingExternalGroups ? " checked='checked'" : "") + " /> Also update existing external groups");
        writer.println("<br/>");
        writer.print("<input type='checkbox' name='" + PARAM_SHOW_LOG_VERBOSE + "' value='true'"
                + (reqParams.showLogVerbose ? " checked='checked'" : "") + " /> Show verbose log");
        writer.closeTd();
        writer.closeTr();
        
        writer.tr();
        writer.openTd();
        // use Server-sent events to update the UI
        String onClick = "applyAcToolConfig("+!isTouchUI+", $('#acForm')); return false;";
        writer.println("<button " + getCoralButtonAtts(isTouchUI) + (!hasWritePermission ? " disabled" : "") + " onclick=\"" + onClick + "\"> Apply AC Tool Configuration </button>");
        writer.closeTd();
        writer.openTd();
        writer.println("<div id='applySpinner' style='display:none' class='spinner'><div></div><div></div><div></div></div>");

        writer.closeTd();

        writer.closeTr();

        writer.println("</form>");
        writer.closeTable();
    }


    private void printExportSection(final HtmlWriter writer, RequestParameters reqParams, String basePath, boolean isTouchUI, boolean hasReadPermission) throws IOException {
        writer.openTable("acExportTable");
        writer.tableHeader("Export", 2);
        writer.tr();
        writer.openTd();
        writer.print("Export in AC Tool YAML format. This includes groups and permissions (in form of ACEs).");
        writer.closeTd();
        writer.openTd();
        writer.println("<button " + getCoralButtonAtts(isTouchUI) + (!hasReadPermission ? " disabled" : "") + " id='downloadDumpButton' onclick=\"window.open('" + basePath + "/"
                + SUFFIX_DUMP_YAML + "', '_blank');return false;\"> Download YAML </button>");
        writer.closeTd();
        writer.closeTr();
        writer.tr();
        writer.openTd();
        writer.print("Export Users in Admin Console CSV format. This includes non-system users, their profiles and their direct group memberships.");
        writer.closeTd();
        writer.openTd();
        writer.println("<button " + getCoralButtonAtts(isTouchUI) + (!hasReadPermission ? " disabled" : "") + " id='downloadCsvButton' onclick=\"window.open('" + basePath + "/"
                + SUFFIX_USERS_CSV + "', '_blank');return false;\"> Download CSV </button>");
        writer.closeTd();
        writer.closeTr();

        writer.closeTable();
    }

    private void printJs(String resourceUrlPrefix, final HtmlWriter writer) {
        String url = resourceUrlPrefix + "/res/actooluiservice.js";
        // externally referenced JS
        writer.print("<script type=\"text/javascript\" src=\"");
        writer.print(url);
        writer.println("\"></script>");
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
                    getParam(req, AcToolUiService.PARAM_SHOW_LOG_ID, null),
                    Boolean.valueOf(req.getParameter(AcToolUiService.PARAM_SHOW_LOG_VERBOSE)),
                    Boolean.valueOf(req.getParameter(AcToolUiService.PARAM_APPLY_ONLY_IF_CHANGED)),
                    Boolean.valueOf(req.getParameter(AcToolUiService.PARAM_UPDATE_EXISTING_EXTERNAL_GROUPS)));
        }
        
        final String configurationRootPath;
        final List<String> basePaths;
        final String showLogId;
        final boolean showLogVerbose;
        final boolean applyOnlyIfChanged;
        private boolean updateExistingExternalGroups;

        public RequestParameters(String configurationRootPath, List<String> basePaths, String showLogId, boolean showLogVerbose,
                boolean applyOnlyIfChanged, boolean updateExistingExternalGroups) {
            super();
            this.configurationRootPath = configurationRootPath;
            this.basePaths = basePaths;
            this.showLogId = showLogId;
            this.showLogVerbose = showLogVerbose;
            this.applyOnlyIfChanged = applyOnlyIfChanged;
            this.updateExistingExternalGroups = updateExistingExternalGroups;
        }

        public Collection<String> getBasePathsArr() {
            if (basePaths == null) {
                return Collections.emptyList();
            } else {
                return basePaths;
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
