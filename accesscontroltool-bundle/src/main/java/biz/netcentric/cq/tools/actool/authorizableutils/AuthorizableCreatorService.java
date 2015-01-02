/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.authorizableutils;

import java.util.LinkedHashSet;
import java.util.Map;

import javax.jcr.AccessDeniedException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;

import org.apache.sling.jcr.api.SlingRepository;

import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;

public interface AuthorizableCreatorService {

    public void createNewAuthorizables(
            Map<String, LinkedHashSet<AuthorizableConfigBean>> principalMapFromConfig,
            final Session session, AcInstallationHistoryPojo status,
            AuthorizableInstallationHistory authorizableInstallationHistory)
            throws AccessDeniedException,
            UnsupportedRepositoryOperationException, RepositoryException,
            AuthorizableCreatorException;

    public void performRollback(SlingRepository repository,
            AuthorizableInstallationHistory authorizableInstallationHistory,
            AcInstallationHistoryPojo history) throws RepositoryException;
}
