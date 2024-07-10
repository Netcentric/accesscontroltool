package biz.netcentric.cq.tools.actool.authorizableinstaller;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2024 Cognizant Netcentric
 * %%
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * #L%
 */

import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.sling.api.resource.LoginException;

import biz.netcentric.cq.tools.actool.configmodel.AcConfiguration;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizablesConfig;
import biz.netcentric.cq.tools.actool.history.InstallationLogger;

public interface AuthorizableInstallerService {

    void installAuthorizables(
            AcConfiguration acConfiguration,
            AuthorizablesConfig authorizablesConfigBeans,
            final Session session, InstallationLogger installLog)
    throws RepositoryException, AuthorizableCreatorException, LoginException, IOException, GeneralSecurityException;
}
