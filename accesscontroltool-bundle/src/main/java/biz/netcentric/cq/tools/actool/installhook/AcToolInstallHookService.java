/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.installhook;

import javax.jcr.Session;

import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.osgi.annotation.versioning.ProviderType;

import biz.netcentric.cq.tools.actool.api.InstallationResult;

@ProviderType
public interface AcToolInstallHookService {

    public InstallationResult installYamlFilesFromPackage(VaultPackage archive, Session session,
            ProgressTrackerListener progressTrackerListener)
            throws Exception;

}