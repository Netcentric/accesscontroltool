/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.authorizableinstaller;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import com.adobe.granite.crypto.CryptoException;

import biz.netcentric.cq.tools.actool.configmodel.AuthorizablesConfig;
import biz.netcentric.cq.tools.actool.history.AcInstallationLog;

public interface AuthorizableInstallerService {

    void installAuthorizables(
            AuthorizablesConfig principalMapFromConfig,
            final Session session, AcInstallationLog installLog)
            throws RepositoryException, AuthorizableCreatorException, CryptoException;

}
