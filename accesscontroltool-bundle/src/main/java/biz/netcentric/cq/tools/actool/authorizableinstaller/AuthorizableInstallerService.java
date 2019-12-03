/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.authorizableinstaller;

import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.sling.api.SlingIOException;
import org.apache.sling.api.resource.LoginException;

import com.adobe.granite.crypto.CryptoException;
import com.adobe.granite.keystore.KeyStoreNotInitialisedException;

import biz.netcentric.cq.tools.actool.configmodel.AcConfiguration;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizablesConfig;
import biz.netcentric.cq.tools.actool.history.InstallationLogger;

public interface AuthorizableInstallerService {

    void installAuthorizables(
            AcConfiguration acConfiguration,
            AuthorizablesConfig authorizablesConfigBeans,
            final Session session, InstallationLogger installLog)
    throws RepositoryException, AuthorizableCreatorException, CryptoException, SlingIOException, SecurityException, KeyStoreNotInitialisedException, LoginException, IOException, GeneralSecurityException;
}
