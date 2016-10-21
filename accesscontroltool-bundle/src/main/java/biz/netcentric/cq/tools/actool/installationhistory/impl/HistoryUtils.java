/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.installationhistory.impl;

import java.util.LinkedHashSet;
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

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.comparators.TimestampPropertyComparator;
import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;

public class HistoryUtils {
    private static final Logger LOG = LoggerFactory.getLogger(HistoryUtils.class);

    public static final String HISTORY_NODE_NAME_PREFIX = "history_";
    public static final String NODETYPE_NT_UNSTRUCTURED = "nt:unstructured";
    private static final String PROPERTY_SLING_RESOURCE_TYPE = "sling:resourceType";
    public static final String ACHISTORY_ROOT_NODE = "achistory";
    public static final String STATISTICS_ROOT_NODE = "var/statistics";

    public static final String PROPERTY_TIMESTAMP = "timestamp";
    private static final String PROPERTY_MESSAGES = "messages";
    private static final String PROPERTY_EXECUTION_TIME = "executionTime";
    public static final String PROPERTY_SUCCESS = "success";
    private static final String PROPERTY_INSTALLATION_DATE = "installationDate";
    public static final String PROPERTY_INSTALLED_FROM = "installedFrom";


    public static Node getAcHistoryRootNode(final Session session)
            throws RepositoryException {
        final Node rootNode = session.getRootNode();
        Node statisticsRootNode = safeGetNode(rootNode, STATISTICS_ROOT_NODE,
                NODETYPE_NT_UNSTRUCTURED);
        Node acHistoryRootNode = safeGetNode(statisticsRootNode,
                ACHISTORY_ROOT_NODE, "sling:OrderedFolder");
        return acHistoryRootNode;
    }

    /**
     * Method that persists a new history log in CRX under
     * '/var/statistics/achistory'
     * 
     * @param session the jcr session
     * @param history
     *            history to persist
     * @param nrOfHistoriesToSave
     *            number of newest histories which should be kept in CRX. older
     *            histories get automatically deleted
     * @return the node being created
     */
    public static Node persistHistory(final Session session,
            AcInstallationHistoryPojo history, final int nrOfHistoriesToSave)
            throws RepositoryException {

        Node acHistoryRootNode = getAcHistoryRootNode(session);
        String name = HISTORY_NODE_NAME_PREFIX + System.currentTimeMillis();
        if (StringUtils.isNotBlank(history.getCrxPackageName())) {
            name += "_via_" + history.getCrxPackageName();
        } else {
            name += "_via_jmx";
        }

        Node newHistoryNode = safeGetNode(acHistoryRootNode, name, NODETYPE_NT_UNSTRUCTURED);
        String path = newHistoryNode.getPath();
        setHistoryNodeProperties(newHistoryNode, history);
        deleteObsoleteHistoryNodes(acHistoryRootNode, nrOfHistoriesToSave);

        Node previousHistoryNode = (Node) acHistoryRootNode.getNodes().next();
        if (previousHistoryNode != null) {
            acHistoryRootNode.orderBefore(newHistoryNode.getName(),
                    previousHistoryNode.getName());
        }

        String message = "Saved history in node: " + path;
        history.addMessage(message);
        LOG.debug(message);
        return newHistoryNode;
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
            AcInstallationHistoryPojo history) throws ValueFormatException,
            VersionException, LockException, ConstraintViolationException,
            RepositoryException {

        historyNode.setProperty(PROPERTY_INSTALLATION_DATE, history
                .getInstallationDate().toString());
        historyNode.setProperty(PROPERTY_SUCCESS, history.isSuccess());
        historyNode.setProperty(PROPERTY_EXECUTION_TIME,
                history.getExecutionTime());
        historyNode.setProperty(PROPERTY_MESSAGES,
                history.getVerboseMessageHistory());
        historyNode.setProperty(PROPERTY_TIMESTAMP, history
                .getInstallationDate().getTime());
        historyNode.setProperty(PROPERTY_SLING_RESOURCE_TYPE,
                "/apps/netcentric/actool/components/historyRenderer");

        Map<String, String> configFileContentsByName = history.getConfigFileContentsByName();
        if (configFileContentsByName != null) {
            String commonPrefix = StringUtils
                    .getCommonPrefix(configFileContentsByName.keySet().toArray(new String[configFileContentsByName.size()]));
            String crxPackageName = history.getCrxPackageName(); // for install hook case
            historyNode.setProperty(PROPERTY_INSTALLED_FROM, StringUtils.defaultString(crxPackageName) + commonPrefix);
        }

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
    static String[] getHistoryInfos(final Session session)
            throws RepositoryException, PathNotFoundException {
        Node acHistoryRootNode = getAcHistoryRootNode(session);
        Set<String> messages = new LinkedHashSet<String>();
        int cnt = 1;
        for (NodeIterator iterator = acHistoryRootNode.getNodes(); iterator
                .hasNext();) {
            Node node = (Node) iterator.next();
            if (node != null && node.getName().startsWith("history_")) {
                String successStatusString = "failed";
                if (node.getProperty(PROPERTY_SUCCESS).getBoolean()) {
                    successStatusString = "ok";
                }
                String installationDate = node.getProperty(
                        PROPERTY_INSTALLATION_DATE).getString();
                messages.add(cnt + ". " + node.getPath() + " " + "" + "("
                        + installationDate + ")" + "(" + successStatusString
                        + ")");
            }
            cnt++;
        }
        return messages.toArray(new String[messages.size()]);
    }

    public static String getLogTxt(final Session session, final String path) {
        return getLog(session, path, "\n").toString();
    }

    public static String getLogHtml(final Session session, final String path) {
        return getLog(session, path, "<br />").toString();
    }

    /**
     * Method which assembles String containing informations of the properties
     * of the respective history node which is specified by the path parameter
     */
    public static String getLog(final Session session, final String path,
            final String lineFeedSymbol) {

        StringBuilder sb = new StringBuilder();
        try {
            Node acHistoryRootNode = getAcHistoryRootNode(session);
            Node historyNode = acHistoryRootNode.getNode(path);

            if (historyNode != null) {
                sb.append("Installation triggered: "
                        + historyNode.getProperty(PROPERTY_INSTALLATION_DATE)
                                .getString());
                sb.append(lineFeedSymbol
                        + historyNode.getProperty(PROPERTY_MESSAGES)
                                .getString().replace("\n", lineFeedSymbol));
                sb.append(lineFeedSymbol
                        + "Execution time: "
                        + historyNode.getProperty(PROPERTY_EXECUTION_TIME)
                                .getLong() + " ms");
                sb.append(lineFeedSymbol
                        + "Success: "
                        + historyNode.getProperty(PROPERTY_SUCCESS)
                                .getBoolean());
            }
        } catch (RepositoryException e) {
            LOG.error("RepositoryException: {}", e);
        }
        return sb.toString();
    }

}
