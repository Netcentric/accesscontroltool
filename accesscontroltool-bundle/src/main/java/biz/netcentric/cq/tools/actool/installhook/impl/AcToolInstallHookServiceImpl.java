/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.installhook.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.jcr.Session;

import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;

import biz.netcentric.cq.tools.actool.configreader.ConfigFilesRetriever;
import biz.netcentric.cq.tools.actool.history.InstallationLogger;
import biz.netcentric.cq.tools.actool.history.impl.PersistableInstallationLogger;
import biz.netcentric.cq.tools.actool.history.impl.ProgressTrackerListenerInstallationLogger;
import biz.netcentric.cq.tools.actool.impl.AcInstallationServiceInternal;
import biz.netcentric.cq.tools.actool.installhook.AcToolInstallHookService;

@Component
public class AcToolInstallHookServiceImpl implements AcToolInstallHookService {

    public static final String ACL_HOOK_PATHS = "actool.installhook.configFilesPattern(.*)";
    private static final String JCR_ROOT_PREFIX="/jcr_root";
    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private AcInstallationServiceInternal acInstallationService;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private ConfigFilesRetriever configFilesRetriever;

    @Override
    public InstallationLogger installYamlFilesFromPackage(VaultPackage vaultPackage, Session session,
            ProgressTrackerListener progressTrackerListener)
            throws Exception {
        Archive archive = vaultPackage.getArchive();
        Properties packageProperties = archive.getMetaInf().getProperties();
        List<String> configPathPatterns = new ArrayList<>();
        if (packageProperties != null) {
            for (Object property : packageProperties.keySet()) {
                if (property.toString().matches(ACL_HOOK_PATHS)) {
                    configPathPatterns.add(JCR_ROOT_PREFIX + packageProperties.getProperty(property.toString()));
                }
            }
        }
        PersistableInstallationLogger history = progressTrackerListener != null
                ? new ProgressTrackerListenerInstallationLogger(progressTrackerListener)
                : new PersistableInstallationLogger();
        Map<String, String> configs = configFilesRetriever.getConfigFileContentFromPackage(archive, configPathPatterns);
        history.setCrxPackageName(getArchiveName(archive));
        String[] restrictedToPaths = null; // never use path restriction for hook usage for now
        acInstallationService.installConfigurationFiles(history, configs, restrictedToPaths, session);

        return history;
    }

    private String getArchiveName(Archive archive) {
        Properties properties = archive.getMetaInf().getProperties();
        String archiveName = properties != null ? (properties.getProperty("name") + "-" + properties.getProperty("version")) : null;
        return archiveName;
    }
}
