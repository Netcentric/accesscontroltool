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

import org.apache.jackrabbit.vault.fs.io.Archive;

import biz.netcentric.cq.tools.actool.history.AcInstallationLog;

public interface AcToolInstallHookService {

    public AcInstallationLog installYamlFilesFromPackage(Archive archive, Session session)
            throws Exception;

}