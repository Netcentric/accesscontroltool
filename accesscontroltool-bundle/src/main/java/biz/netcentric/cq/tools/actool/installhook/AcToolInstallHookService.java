package biz.netcentric.cq.tools.actool.installhook;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2024 Cognizant Netcentric
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

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
