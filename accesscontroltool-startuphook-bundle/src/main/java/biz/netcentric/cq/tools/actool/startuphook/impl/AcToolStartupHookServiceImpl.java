/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.startuphook.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.oak.spi.security.authorization.accesscontrol.AccessControlConstants;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.settings.SlingSettingsService;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.api.AcInstallationService;
import biz.netcentric.cq.tools.actool.helper.Constants;
import biz.netcentric.cq.tools.actool.helper.runtime.RuntimeHelper;
import biz.netcentric.cq.tools.actool.history.impl.HistoryUtils;
import biz.netcentric.cq.tools.actool.impl.AcConfigChangeTracker;

@Component
public class AcToolStartupHookServiceImpl {
    private static final Logger LOG = LoggerFactory.getLogger(AcToolStartupHookServiceImpl.class);

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private AcInstallationService acInstallationService;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private SlingRepository repository;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private SlingSettingsService settingsService;

    private boolean isCompositeNodeStore;

    @Activate
    public void activate( BundleContext bundleContext) {

        LOG.info("Running AcTool Startup Hook (start level: {} runmodes: {})", RuntimeHelper.getCurrentStartLevel(bundleContext),
                settingsService.getRunModes());

        try {
            List<String> relevantPathsForInstallation = getRelevantPathsForInstallation();
            LOG.info("Relevant paths for installation: " + relevantPathsForInstallation);

            acInstallationService.apply(null, relevantPathsForInstallation.toArray(new String[relevantPathsForInstallation.size()]), true);
            LOG.info("AC Tool done. (start level " + RuntimeHelper.getCurrentStartLevel(bundleContext) + ")");

        } catch (RepositoryException e) {
            LOG.error("Exception while triggering AC Tool on startup: " + e, e);
        }

        copyAcHistoryToApps();
    }

    private List<String> getRelevantPathsForInstallation() throws RepositoryException {
        Session session = null;
        try {
            session = repository.loginService(Constants.USER_AC_SERVICE, null);

            isCompositeNodeStore = RuntimeHelper.isCompositeNodeStore(session);
            LOG.info("Repo is running with Composite NodeStore: " + isCompositeNodeStore);

            NodeIterator nodes = session.getRootNode().getNodes();
            List<String> relevantPathsForInstallation = new ArrayList<>();
            while (nodes.hasNext()) {
                Node node = nodes.nextNode();
                if (Arrays.asList(
                        JcrConstants.JCR_SYSTEM,
                        "oak:index",
                        AccessControlConstants.REP_POLICY,
                        AccessControlConstants.REP_REPO_POLICY).contains(node.getName())) {
                    continue;
                }
                if (isCompositeNodeStore && Arrays.asList("apps", "libs").contains(node.getName())) {
                    continue;
                }
                relevantPathsForInstallation.add(node.getPath());
            }

            return relevantPathsForInstallation;

        } finally {
            if (session != null) {
                session.logout();
            }
        }
    }

    private void copyAcHistoryToApps() {

        boolean isCloudReady = RuntimeHelper.isCloudReadyInstance(settingsService);
        LOG.info("copyAcHistoryToApps(): isCloudReady: {} isCompositeNodeStore: {}, runmodes: {}", isCloudReady, isCompositeNodeStore, settingsService.getRunModes());

        if (isCloudReady) {
            Session session = null;
            String rootPathInApps = "/apps/netcentric";
            try {
                session = repository.loginService(Constants.USER_AC_SERVICE, null);
                Node acHistory = session.getNode(HistoryUtils.ACHISTORY_PATH);

                if(isCompositeNodeStore) {
                    String pathInApps = rootPathInApps + "/" + HistoryUtils.ACHISTORY_ROOT_NODE;
                    if(session.nodeExists(pathInApps)) {
                        LOG.info("Copying history from apps {}", pathInApps);
                        NodeIterator nodesInAppsIt = session.getNode(pathInApps).getNodes();
                        while(nodesInAppsIt.hasNext()) {
                            Node historyNodeInApps = nodesInAppsIt.nextNode();
                            String historyNodeInVarPath = acHistory.getPath() + "/"+ historyNodeInApps.getName();
                            if(!session.nodeExists(historyNodeInVarPath)) {
                                LOG.info(" Node {}", historyNodeInApps.getPath());
                                RuntimeHelper.copyNode(historyNodeInApps, acHistory);
                            }
                        }
                    }
                } else {
                    Node ncNodeInApps = session.getNode(rootPathInApps);
                    LOG.info("Copying history from {} to  {}", acHistory.getPath(), ncNodeInApps.getPath());
                    RuntimeHelper.copyNode(acHistory, ncNodeInApps);
                }

                session.save();
            } catch (RepositoryException e) {
                LOG.warn("Could not copy AC History node from/to /apps: " + e, e);
            } finally {
                if (session != null) {
                    session.logout();
                }
            }
        }
    }

}
