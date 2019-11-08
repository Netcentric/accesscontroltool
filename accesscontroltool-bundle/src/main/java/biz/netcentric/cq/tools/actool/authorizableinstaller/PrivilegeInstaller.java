/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.authorizableinstaller;

import biz.netcentric.cq.tools.actool.configmodel.PrivilegeConfig;
import biz.netcentric.cq.tools.actool.history.InstallationLogger;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

public interface PrivilegeInstaller {

    void installPrivileges(PrivilegeConfig config, Session session, InstallationLogger installLog)
            throws RepositoryException;
}
