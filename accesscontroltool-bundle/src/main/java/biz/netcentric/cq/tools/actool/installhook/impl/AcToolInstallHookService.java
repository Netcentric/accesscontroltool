/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.installhook.impl;

import javax.jcr.Session;

import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.packaging.VaultPackage;

import biz.netcentric.cq.tools.actool.history.PersistableInstallationLogger;

public interface AcToolInstallHookService {

    public PersistableInstallationLogger installYamlFilesFromPackage(VaultPackage archive, Session session,
            ProgressTrackerListener progressTrackerListener)
            throws Exception;

}