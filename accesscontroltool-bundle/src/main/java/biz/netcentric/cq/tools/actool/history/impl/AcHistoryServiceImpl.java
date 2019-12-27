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
import java.io.File;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.commons.jcr.JcrConstants;

import biz.netcentric.cq.tools.actool.comparators.TimestampPropertyComparator;
import biz.netcentric.cq.tools.actool.helper.Constants;
import biz.netcentric.cq.tools.actool.history.AcHistoryService;
import biz.netcentric.cq.tools.actool.history.AcToolExecution;
import biz.netcentric.cq.tools.actool.history.PersistableInstallationLogger;
import biz.netcentric.cq.tools.actool.history.impl.AcHistoryServiceImpl.Configuration;

@Component
@Designate(ocd=Configuration.class)
public class AcHistoryServiceImpl implements AcHistoryService {
    private static final Logger LOG = LoggerFactory.getLogger(AcHistoryServiceImpl.class);

    public static final String INSTALLED_CONFIGS_NODE_NAME = "installedConfigs";

    private int nrOfSavedHistories;

    // to be used by health check to be able to warn if the history could not be persisted
    private boolean wasLastPersistHistoryCallSuccessful = true;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private SlingRepository repository;

    @ObjectClassDefinition(name = "AC Tool History Service", 
            description="Service that writes & fetches Ac installation histories.",
            id="biz.netcentric.cq.tools.actool.history.impl.AcHistoryServiceImpl")
    protected static @interface Configuration {
        @AttributeDefinition(name="ACL number of histories to save")
        int AceService_nrOfSavedHistories() default 7;
    }

    @Activate
    public void activate(Configuration configuration)
            throws Exception {
        nrOfSavedHistories = configuration.AceService_nrOfSavedHistories();
    }

    @Override
    public void persistHistory(PersistableInstallationLogger installLog) {

        if (nrOfSavedHistories == 0) {
            installLog.addVerboseMessage(LOG, "History hasn't been persisted, configured number of histories is " + nrOfSavedHistories);
            return;
        }

        Session session = null;
        try {

            session = repository.loginService(Constants.USER_AC_SERVICE, null);
            Node historyNode = HistoryUtils.persistHistory(session, installLog, nrOfSavedHistories);

            String mergedAndProcessedConfig = installLog.getMergedAndProcessedConfig();
            if (StringUtils.isNotBlank(mergedAndProcessedConfig)) {
                JcrUtils.putFile(historyNode, "mergedConfig.yaml", "text/yaml",
                        new ByteArrayInputStream(mergedAndProcessedConfig.getBytes()));
            }

            if (installLog.isSuccess()) {
                persistInstalledConfigurations(session, historyNode, installLog);
            }
            session.save();

            wasLastPersistHistoryCallSuccessful = true;
        } catch (Exception e) {
            wasLastPersistHistoryCallSuccessful = false;
            LOG.error("Could not persist history: " + e, e);
        } finally {
            if (session != null) {
                session.logout();
            }
        }
    }

    @Override
    public String[] getInstallationLogPaths() {
        
        List<AcToolExecution> historyItems = getAcToolExecutions();
        
        String[] result = new String[historyItems.size()];
        int count = 0;
        Iterator<AcToolExecution> historyItemsIt = historyItems.iterator();
        while(historyItemsIt.hasNext()) {
            count++;
            result[count-1] = count + ". "+historyItemsIt.next().toString();
        }
        return result;

    }

    @Override
    public List<AcToolExecution> getAcToolExecutions() {
        
        Session session = null;
        try {
            session = repository.loginService(Constants.USER_AC_SERVICE, null);
            List<AcToolExecution> historyItems = HistoryUtils.getAcToolExecutions(session);
            return historyItems;
            
        } catch (RepositoryException e) {
            LOG.error("Could not get history items: "+e, e);
            return Collections.<AcToolExecution>emptyList();
        } finally {
            if (session != null) {
                session.logout();
            }
        }
    }

    private String getLogHtml(Session session, String path, boolean includeVerbose) {
        return HistoryUtils.getLogHtml(session, path, includeVerbose);
    }

    private String getLogTxt(Session session, String path, boolean includeVerbose) {
        return HistoryUtils.getLogTxt(session, path, includeVerbose);
    }

    @Override
    public String getLastInstallationHistory() {
        Session session = null;
        String history = "";
        try {
            session = repository.loginService(Constants.USER_AC_SERVICE, null);

            Node statisticsRootNode = HistoryUtils.getAcHistoryRootNode(session);
            NodeIterator it = statisticsRootNode.getNodes();

            if (it.hasNext()) {
                Node lastHistoryNode = it.nextNode();

                if (lastHistoryNode != null) {
                    history = getLogHtml(session, lastHistoryNode.getName(), true);
                }
            } else {
                history = "no history found!";
            }
        } catch (RepositoryException e) {
            LOG.error("RepositoryException: ", e);
        } finally {
            if (session != null) {
                session.logout();
            }
        }
        return history;
    }

    private void persistInstalledConfigurations(final Session session, final Node historyNode, PersistableInstallationLogger installLog) {
        try {

            Map<String, String> configFileContentsByName = installLog.getConfigFileContentsByName();
            if (configFileContentsByName == null) {
                return;
            }

            String commonPrefix = StringUtils.getCommonPrefix(configFileContentsByName.keySet().toArray(new String[configFileContentsByName.size()]));

            for (String fullConfigFilePath : configFileContentsByName.keySet()) {
                File targetPathFile = new File(
                        INSTALLED_CONFIGS_NODE_NAME + "/" + StringUtils.substringAfter(fullConfigFilePath, commonPrefix));
                File targetPathParentDir = targetPathFile.getParentFile();
                Node configFolder = JcrUtils.getOrCreateByPath(historyNode,
                        targetPathParentDir != null ? targetPathParentDir.getPath() : targetPathFile.getPath(), false,
                                JcrConstants.NT_FOLDER, JcrConstants.NT_FOLDER, false);
                ByteArrayInputStream configFileInputStream = new ByteArrayInputStream( configFileContentsByName.get(fullConfigFilePath).getBytes() );
                JcrUtils.putFile(configFolder, targetPathFile.getName(), "text/yaml", configFileInputStream);
            }

            installLog.addVerboseMessage(LOG,
                    "Saved installed configuration files under : " + historyNode.getPath() + "/" + INSTALLED_CONFIGS_NODE_NAME);
        } catch (RepositoryException e) {
            installLog.addError(LOG, "Exception while saving history node " + historyNode, e);
        }
    }

    @Override
    public String getLogFromHistory(int n, boolean inHtmlFormat, boolean includeVerbose) {
        Session session = null;
        String history = "";
        try {
            session = repository.loginService(Constants.USER_AC_SERVICE, null);

            List<AcToolExecution> acToolExecutions = HistoryUtils.getAcToolExecutions(session);
            if(n <= acToolExecutions.size()) {
                AcToolExecution acToolExecution =  acToolExecutions.get(n-1);
                String path = acToolExecution.getLogsPath();
                history = inHtmlFormat ? getLogHtml(session, path, includeVerbose) : getLogTxt(session, path, includeVerbose);
            }

        } catch (RepositoryException e) {
            LOG.error("RepositoryException: ", e);
        } finally {
            if (session != null) {
                session.logout();
            }
        }
        return history;
    }

    @Override
    public void persistAcePurgeHistory(PersistableInstallationLogger installLog) {
        Session session = null;

        try {
            session = repository.loginService(Constants.USER_AC_SERVICE, null);
            Node acHistoryRootNode = HistoryUtils.getAcHistoryRootNode(session);
            NodeIterator nodeIterator = acHistoryRootNode.getNodes();
            Set<Node> historyNodes = new TreeSet<Node>(
                    new TimestampPropertyComparator());
            Node newestHistoryNode = null;
            while (nodeIterator.hasNext()) {
                historyNodes.add(nodeIterator.nextNode());
            }
            if (!historyNodes.isEmpty()) {
                newestHistoryNode = historyNodes.iterator().next();
                persistPurgeAceHistory(session, installLog, newestHistoryNode);
                session.save();
            }

        } catch (RepositoryException e) {
            LOG.error("Exception: ", e);
        } finally {
            if (session != null) {
                session.logout();
            }
        }
    }

    private static Node persistPurgeAceHistory(final Session session,
            PersistableInstallationLogger installLog, final Node historyNode)
                    throws RepositoryException {

        Node purgeHistoryNode = historyNode.addNode(
                "purge_" + System.currentTimeMillis(),
                HistoryUtils.NODETYPE_NT_UNSTRUCTURED);

        // if there is already a purge history node, order the new one before so
        // the newest one is always on top
        NodeIterator nodeIt = historyNode.getNodes();
        Node previousPurgeNode = null;
        while (nodeIt.hasNext()) {
            Node currNode = nodeIt.nextNode();
            // get previous purgeHistory node
            if (currNode.getName().contains("purge_")) {
                previousPurgeNode = currNode;
                break;
            }
        }

        if (previousPurgeNode != null) {
            historyNode.orderBefore(purgeHistoryNode.getName(),
                    previousPurgeNode.getName());
        }

        installLog.addMessage(LOG, "Saved history in node: " + purgeHistoryNode.getPath());
        HistoryUtils.setHistoryNodeProperties(purgeHistoryNode, installLog);
        return historyNode;
    }

    @Override
    public boolean wasLastPersistHistoryCallSuccessful() {
        return wasLastPersistHistoryCallSuccessful;
    }

}
