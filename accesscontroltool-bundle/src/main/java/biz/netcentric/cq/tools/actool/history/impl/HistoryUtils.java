/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.history.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.ValueFormatException;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.version.VersionException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.commons.JcrUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.api.InstallationResult;
import biz.netcentric.cq.tools.actool.comparators.TimestampPropertyComparator;
import biz.netcentric.cq.tools.actool.configuploadlistener.impl.UploadListenerServiceImpl.AcToolConfigUpdateListener;
import biz.netcentric.cq.tools.actool.helper.runtime.RuntimeHelper;
import biz.netcentric.cq.tools.actool.history.AcToolExecution;
import biz.netcentric.cq.tools.actool.jmx.AceServiceMBeanImpl;
import biz.netcentric.cq.tools.actool.ui.AcToolTouchUiServlet;
import biz.netcentric.cq.tools.actool.ui.AcToolWebconsolePlugin;

public class HistoryUtils {

    private static final Logger LOG = LoggerFactory.getLogger(HistoryUtils.class);

    public static final String LOG_FILE_NAME = "actool.log";
    public static final String LOG_FILE_NAME_VERBOSE = "actool-verbose.log";

    public static final String HISTORY_NODE_NAME_PREFIX = "history_";
    public static final String NODETYPE_NT_UNSTRUCTURED = "nt:unstructured";
    private static final String PROPERTY_SLING_RESOURCE_TYPE = "sling:resourceType";
    public static final String ACHISTORY_ROOT_NODE = "achistory";
    public static final String STATISTICS_ROOT_NODE = "var/statistics";
    public static final String ACHISTORY_PATH = "/"+ HistoryUtils.STATISTICS_ROOT_NODE + "/" + HistoryUtils.ACHISTORY_ROOT_NODE;

    private static final String AC_ROOT_PATH_IN_APPS = "/apps/netcentric";
    public static final String AC_HISTORY_PATH_IN_APPS = AC_ROOT_PATH_IN_APPS + "/" + ACHISTORY_ROOT_NODE;
    
    public static final String PROPERTY_TIMESTAMP = "timestamp";
    private static final String PROPERTY_MESSAGES = "messages";
    private static final String PROPERTY_EXECUTION_TIME = "executionTime";
    public static final String PROPERTY_SUCCESS = "success";
    private static final String PROPERTY_INSTALLATION_DATE = "installationDate";
    public static final String PROPERTY_INSTALLED_FROM = "installedFrom";
    public static final String PROPERTY_STARTLEVEL = "startlevel";
    public static final String PROPERTY_TRIGGER = "trigger";
    public static final String PROPERTY_CONFIG_ROOT_PATH = "configurationRootPath";
    public static final String PROPERTY_ACL_CHANGES = "aclsChanges";
    public static final String PROPERTY_AUTHORIZABLES_CHANGES = "authorizableChanges";

    private static final String AC_TOOL_STARTUPHOOK_CLASS = "biz.netcentric.cq.tools.actool.startuphook.impl.AcToolStartupHookServiceImpl";
    private static final String BUNDLE_START_TASK_CLASS = "org.apache.sling.installer.core.impl.tasks.BundleStartTask";

    public static Node getAcHistoryRootNode(final Session session)
            throws RepositoryException {
        final Node rootNode = session.getRootNode();
        Node statisticsRootNode = safeGetNode(rootNode, STATISTICS_ROOT_NODE, NODETYPE_NT_UNSTRUCTURED);
        Node acHistoryRootNode = safeGetNode(statisticsRootNode, ACHISTORY_ROOT_NODE, "sling:OrderedFolder");
        return acHistoryRootNode;
    }

    /**
     * Method that persists a new history log in CRX under
     * '/var/statistics/achistory'
     * 
     * @param session the jcr session
     * @param installLog
     *            history to persist
     * @param nrOfHistoriesToSave
     *            number of newest histories which should be kept in CRX. older
     *            histories get automatically deleted
     * @return the node being created
     */
    public static Node persistHistory(final Session session,
            PersistableInstallationLogger installLog, final int nrOfHistoriesToSave)
            throws RepositoryException {

        Node acHistoryRootNode = getAcHistoryRootNode(session);
        String name = HISTORY_NODE_NAME_PREFIX + System.currentTimeMillis();
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        
        String trigger;
        if (StringUtils.isNotBlank(installLog.getCrxPackageName())) {
            trigger = "installhook";
        } else if(isInStrackTracke(stackTrace, AceServiceMBeanImpl.class)) {
            trigger = "jmx";
        } else if(isInStrackTracke(stackTrace, AcToolTouchUiServlet.class)) {
            trigger = "aem_admin_ui";
        } else if(isInStrackTracke(stackTrace, AcToolConfigUpdateListener.class)) {
            trigger = "changelistener";
        } else if(isInStrackTracke(stackTrace, AcToolWebconsolePlugin.class)) {
            trigger = "webconsole";
        } else if(isInStrackTracke(stackTrace, AC_TOOL_STARTUPHOOK_CLASS)) {
            if(isInStrackTracke(stackTrace, BUNDLE_START_TASK_CLASS)) {
                trigger = "startup_hook_pckmgr)";
            } else {
                // if the history is not yet copied to apps, it's the image build
                boolean isImageBuild = RuntimeHelper.isCloudReadyInstance() && !session.itemExists(AC_HISTORY_PATH_IN_APPS);
                if(isImageBuild) {
                    trigger = "startup_hook_image_build";
                } else {
                    trigger = "startup_hook";
                }
            }
        } else {
            name += trigger = "api";
        }
        name += AcToolExecutionImpl.TRIGGER_SEPARATOR_IN_NODE_NAME + trigger;

        Node newHistoryNode = safeGetNode(acHistoryRootNode, name, NODETYPE_NT_UNSTRUCTURED);
        String path = newHistoryNode.getPath();
        setHistoryNodeProperties(newHistoryNode, installLog, trigger);
        saveLogs(newHistoryNode, installLog);

        deleteObsoleteHistoryNodes(acHistoryRootNode, nrOfHistoriesToSave);

        Node previousHistoryNode = (Node) acHistoryRootNode.getNodes().next();
        if (previousHistoryNode != null) {
            acHistoryRootNode.orderBefore(newHistoryNode.getName(),
                    previousHistoryNode.getName());
        }

        // Explicitly not adding this to install log (as it has been saved already, regular log is sufficient)
        LOG.info("Saved history in node: {}", path);
        return newHistoryNode;
    }

    static void saveLogs(Node historyNode, InstallationResult installLog) throws RepositoryException {
        // not ideal to save both variants, but the easiest for now
        JcrUtils.putFile(historyNode, LOG_FILE_NAME_VERBOSE, "text/plain",
                new ByteArrayInputStream(installLog.getVerboseMessageHistory().getBytes()));
        JcrUtils.putFile(historyNode, LOG_FILE_NAME, "text/plain",
                new ByteArrayInputStream(installLog.getMessageHistory().getBytes()));
    }

    private static boolean isInStrackTracke(StackTraceElement[] stackTrace, Class<?> classToSearch) {
        return isInStrackTracke(stackTrace, classToSearch.getName());
    }
    private static boolean isInStrackTracke(StackTraceElement[] stackTrace, String classToSearch) {
        for (StackTraceElement stackTraceElement : stackTrace) {
            if(classToSearch.equals(stackTraceElement.getClassName())) { 
                return true;
            }
        }
        return false;
    }

    private static Node safeGetNode(final Node baseNode, final String name,
            final String typeToCreate) throws RepositoryException {
        if (!baseNode.hasNode(name)) {
            LOG.debug("create node: {}", name);
            return baseNode.addNode(name, typeToCreate);

        } else {
            return baseNode.getNode(name);
        }
    }

    public static void setHistoryNodeProperties(final Node historyNode,
            PersistableInstallationLogger installLog, String trigger) throws ValueFormatException,
            VersionException, LockException, ConstraintViolationException,
            RepositoryException {

        historyNode.setProperty(PROPERTY_INSTALLATION_DATE, installLog
                .getInstallationDate().toString());
        historyNode.setProperty(PROPERTY_SUCCESS, installLog.isSuccess());
        historyNode.setProperty(PROPERTY_EXECUTION_TIME, installLog.getExecutionTime());
        historyNode.setProperty(PROPERTY_STARTLEVEL, RuntimeHelper.getCurrentStartLevel());
        historyNode.setProperty(PROPERTY_TRIGGER, trigger);
        
        historyNode.setProperty(PROPERTY_ACL_CHANGES, (long) installLog.getCountAclsChanged());
        historyNode.setProperty(PROPERTY_AUTHORIZABLES_CHANGES, (long) installLog.getCountAuthorizablesCreated() + installLog.getCountAuthorizablesMoved());

        historyNode.setProperty(PROPERTY_CONFIG_ROOT_PATH, getEffectiveConfigRootPath(installLog));
        
        historyNode.setProperty(PROPERTY_TIMESTAMP, installLog.getInstallationDate().getTime());
        historyNode.setProperty(PROPERTY_SLING_RESOURCE_TYPE, "/apps/netcentric/actool/components/historyRenderer");

        Map<String, String> configFileContentsByName = installLog.getConfigFileContentsByName();
        if (configFileContentsByName != null) {
            String crxPackageName = installLog.getCrxPackageName(); // for install hook case
            historyNode.setProperty(PROPERTY_INSTALLED_FROM, StringUtils.defaultString(crxPackageName));
        }
    }

    private static String getEffectiveConfigRootPath(PersistableInstallationLogger installLog) {
        Map<String, String> configFileContentsByName = installLog.getConfigFileContentsByName();
        if(configFileContentsByName == null) {
            return null;
        }
        Set<String> configFiles = configFileContentsByName.keySet();
        String effectiveConfigRootPath = StringUtils.getCommonPrefix(configFiles.toArray(new String[configFiles.size()]));
        effectiveConfigRootPath = StringUtils.removeStart(effectiveConfigRootPath, "//jcr_root");
        effectiveConfigRootPath = StringUtils.removeEnd(effectiveConfigRootPath, "/");
        return effectiveConfigRootPath;
    }

    /**
     * Method that ensures that only the number of history logs is persisted in
     * CRX which is configured in nrOfHistoriesToSave
     * 
     * @param acHistoryRootNode
     *            node in CRX under which the history logs are located
     * @param nrOfHistoriesToSave
     *            number of history logs which get stored in CRX (as direct
     *            child nodes of acHistoryRootNode in insertion order - newest
     *            always on top)
     * @throws RepositoryException
     */
    private static void deleteObsoleteHistoryNodes(
            final Node acHistoryRootNode, final int nrOfHistoriesToSave)
            throws RepositoryException {
        NodeIterator childNodeIt = acHistoryRootNode.getNodes();
        Set<Node> historyChildNodes = new TreeSet<Node>(
                new TimestampPropertyComparator());

        while (childNodeIt.hasNext()) {
            Node node = childNodeIt.nextNode();
            if (node.getName().startsWith(HISTORY_NODE_NAME_PREFIX)) {
                historyChildNodes.add(node);
            }
        }
        int index = 1;
        for (Node node : historyChildNodes) {
            if (index > nrOfHistoriesToSave) {
                LOG.debug("delete obsolete history node: ", node.getPath());
                node.remove();
            }
            index++;
        }
    }

    /**
     * Method that returns a string which contains a number, path of a stored
     * history log in CRX, and the success status of that installation
     * 
     * @param acHistoryRootNode
     *            node in CRX under which the history logs get stored
     * @return String array which holds the single history infos
     * @throws RepositoryException
     * @throws PathNotFoundException
     */
    static List<AcToolExecution> getAcToolExecutions(final Session session)
            throws RepositoryException, PathNotFoundException {
        Node acHistoryRootNode = getAcHistoryRootNode(session);
        
        Set<AcToolExecution> historyInfos = new TreeSet<>();

        for (NodeIterator iterator = acHistoryRootNode.getNodes(); iterator.hasNext();) {
            Node node = (Node) iterator.next();

            if (node != null && node.getName().startsWith(HISTORY_NODE_NAME_PREFIX)) {

                String configRoot = node.hasProperty(PROPERTY_CONFIG_ROOT_PATH)? node.getProperty(PROPERTY_CONFIG_ROOT_PATH).getString() : null;
                int authorizableChanges = node.hasProperty(PROPERTY_AUTHORIZABLES_CHANGES) ? (int) node.getProperty(PROPERTY_AUTHORIZABLES_CHANGES).getLong() : -1;
                int aclChanges = node.hasProperty(PROPERTY_ACL_CHANGES) ? (int) node.getProperty(PROPERTY_ACL_CHANGES).getLong() : -1;

                historyInfos.add(new AcToolExecutionImpl(node.getPath(), 
                        new Date(node.getProperty(PROPERTY_TIMESTAMP).getLong()), 
                        node.getProperty(PROPERTY_SUCCESS).getBoolean(),
                        configRoot, authorizableChanges, aclChanges));
            }

        }
        return new ArrayList<>(historyInfos);
    }

    public static String getLogTxt(final Session session, final String path, boolean includeVerbose) {
        return getLog(session, path, "\n", includeVerbose).toString();
    }

    public static String getLogHtml(final Session session, final String path, boolean includeVerbose) {
        return getLog(session, path, "<br />", includeVerbose).toString();
    }

    /**
     * Method which assembles String containing informations of the properties
     * of the respective history node which is specified by the path parameter
     */
    public static String getLog(final Session session, final String path,
            final String lineFeedSymbol, boolean includeVerbose) {

        StringBuilder sb = new StringBuilder();
        try {

            Node historyNode = path.startsWith("/") ? session.getNode(path) : getAcHistoryRootNode(session).getNode(path);

            if (historyNode != null) {
                sb.append("Installation triggered: "
                        + historyNode.getProperty(PROPERTY_INSTALLATION_DATE)
                                .getString());
                
                if(historyNode.hasProperty(PROPERTY_MESSAGES)) {
                    sb.append(lineFeedSymbol
                            + historyNode.getProperty(PROPERTY_MESSAGES)
                                    .getString().replace("\n", lineFeedSymbol));
                } else {
                    Node logFileNode;
                    if(includeVerbose) {
                        logFileNode = historyNode.getNode(LOG_FILE_NAME_VERBOSE);
                    } else {
                        logFileNode = historyNode.getNode(LOG_FILE_NAME);
                    }
                    sb.append(lineFeedSymbol
                            +  IOUtils.toString(JcrUtils.readFile(logFileNode), StandardCharsets.UTF_8).replace("\n", lineFeedSymbol));
                }

                sb.append(lineFeedSymbol
                        + "Execution time: "
                        + historyNode.getProperty(PROPERTY_EXECUTION_TIME)
                                .getLong() + " ms");
                sb.append(lineFeedSymbol
                        + "Success: "
                        + historyNode.getProperty(PROPERTY_SUCCESS)
                                .getBoolean());
            }
        } catch (IOException|RepositoryException e) {
            sb.append(lineFeedSymbol+"ERROR while retrieving log: "+e);
            LOG.error("ERROR while retrieving log: "+e, e);
        }
        return sb.toString();
    }

}
