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
import java.util.Collections;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.oak.spi.security.authorization.accesscontrol.AccessControlConstants;
import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.framework.BundleContext;
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
import biz.netcentric.cq.tools.actool.helper.runtime.RuntimeHelper;
import biz.netcentric.cq.tools.actool.history.impl.HistoryUtils;

@Component
@Designate(ocd=AcToolStartupHookServiceImpl.Config.class)
public class AcToolStartupHookServiceImpl {
    private static final Logger LOG = LoggerFactory.getLogger(AcToolStartupHookServiceImpl.class);

    private static final String THREAD_NAME_ASYNC = "actool-async";

    @ObjectClassDefinition(name = "AC Tool Startup Hook", description = "Applies AC Tool config automatically upon startup (depending on configuration/runtime)")
    public static @interface Config {
        public enum StartupHookActivation {
            ALWAYS, CLOUD_ONLY, NEVER;
        }

        @AttributeDefinition(name = "Activation Mode", description = "Apply on startup - CLOUD_ONLY autodetects the cloud (by missing OSGi installer bundle) and only runs on startup if deployed in the cloud. ALWAYS can be useful for local testing. NEVER disables AC Tool runs on startup entirely.")
        StartupHookActivation activationMode() default StartupHookActivation.CLOUD_ONLY;

        @AttributeDefinition(name = "Async for Mutable Content", description = "Will execute on the mutable content asynchronously using a Sling Job")
        boolean runAsyncForMutableConent() default false;
    }

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private AcInstallationService acInstallationService;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private SlingRepository repository;

    private boolean isCompositeNodeStore;

    @Activate
    public void activate(BundleContext bundleContext, Config config) {

        boolean isCloudReady = RuntimeHelper.isCloudReadyInstance();
        Config.StartupHookActivation activationMode = config.activationMode();
        boolean runAsyncForMutableConent = config.runAsyncForMutableConent();
        int currentStartLevel = RuntimeHelper.getCurrentStartLevel(bundleContext);
        LOG.info("AcTool Startup Hook (start level: {}  isCloudReady: {}  activationMode: {}  runAsyncForMutableConent: {})",
                currentStartLevel,
                isCloudReady,
                activationMode,
                runAsyncForMutableConent);

        boolean applyOnStartup = (activationMode == Config.StartupHookActivation.ALWAYS)
                || (isCloudReady && activationMode == Config.StartupHookActivation.CLOUD_ONLY);

        if (applyOnStartup) {

            List<String> relevantPathsForInstallation = getRelevantPathsForInstallation();
            LOG.info("Running AcTool with "
                    + (relevantPathsForInstallation.isEmpty() ? "all paths" : "paths " + relevantPathsForInstallation) + "...");
            
            if (runAsyncForMutableConent && isCompositeNodeStore) {
                LOG.info(
                        "Running AcTool asynchronously on mutable content of composite node store (config runAsyncForMutableConent=true)...");
                runAcToolAsync(relevantPathsForInstallation, currentStartLevel, isCloudReady);
            } else {
                runAcTool(relevantPathsForInstallation, currentStartLevel, isCloudReady);
            }

        } else {
            LOG.debug("Skipping AcTool Startup Hook: activationMode: {} isCloudReady: {}", activationMode, isCloudReady);
        }

    }

    private void runAcTool(List<String> relevantPathsForInstallation, int currentStartLevel, boolean isCloudReady) {

        acInstallationService.apply(null, relevantPathsForInstallation.toArray(new String[relevantPathsForInstallation.size()]),
                true);
        LOG.info("AC Tool Startup Hook done. (start level {})", currentStartLevel);

        copyAcHistoryToOrFromApps(isCloudReady);

    }

    private void runAcToolAsync(final List<String> relevantPathsForInstallation, final int currentStartLevel, final boolean isCloudReady) {

        final AcToolStartupHookServiceImpl startupHook = this;
        new Thread(new Runnable() {
            @Override
            public void run() {
                startupHook.runAcTool(relevantPathsForInstallation, currentStartLevel, isCloudReady);
            }
        }, THREAD_NAME_ASYNC).start();
    }

    private List<String> getRelevantPathsForInstallation() {
        Session session = null;
        try {
            session = repository.loginService(null, null);

            isCompositeNodeStore = RuntimeHelper.isCompositeNodeStore(session);
            LOG.info("Repo is running with Composite NodeStore: {}", isCompositeNodeStore);
            
            if(!isCompositeNodeStore) {
                return Collections.emptyList();
            }

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
            
            // also allow 
            //    root path
            relevantPathsForInstallation.add("^/$");
            //    empty path (repo level restrictions)
            relevantPathsForInstallation.add("^$");

            return relevantPathsForInstallation;

        } catch (RepositoryException e) {
            throw new IllegalStateException("Could not retrieve relevant base paths for AC Tool installation: " + e, e);
        } finally {
            if (session != null) {
                session.logout();
            }
        }
    }

    private void copyAcHistoryToOrFromApps(boolean isCloudReady) {

        if(isCloudReady) {
            Session session = null;
            try {
                session = repository.loginService(null, null);

                if(isCompositeNodeStore) {
                    LOG.info("Restoring history from /apps to /var");

                    if(session.nodeExists(HistoryUtils.AC_HISTORY_PATH_IN_APPS)) {
                        NodeIterator nodesInAppsIt = session.getNode(HistoryUtils.AC_HISTORY_PATH_IN_APPS).getNodes();
                        while(nodesInAppsIt.hasNext()) {
                            Node historyNodeInApps = nodesInAppsIt.nextNode();
                            String historyNodeInVarPath = HistoryUtils.ACHISTORY_PATH + "/" + historyNodeInApps.getName();
                            if(!session.nodeExists(historyNodeInVarPath)) {
                                LOG.info("   restoring history node {} to {}", historyNodeInApps.getPath(), historyNodeInVarPath);
                                session.getWorkspace().copy(historyNodeInApps.getPath(), historyNodeInVarPath);
                            }
                        }
                    }
                } else {
                    LOG.info("Saving history in /apps (to make it accessible later when running in composite node store)");
                    LOG.info("   copying node {} to {}", HistoryUtils.ACHISTORY_PATH, HistoryUtils.AC_HISTORY_PATH_IN_APPS);
                    session.getWorkspace().copy(HistoryUtils.ACHISTORY_PATH, HistoryUtils.AC_HISTORY_PATH_IN_APPS);
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
