/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configreader;

import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import biz.netcentric.cq.tools.actool.configmodel.AcConfiguration;
import biz.netcentric.cq.tools.actool.history.impl.PersistableInstallationLogger;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;

public interface ConfigurationMerger {

    /** Method that merges several textual AccessControlConfigurations written in YAML format, each comprising of a groups and ACE
     * configuration. Validation ensures that no doubled defined groups and only valid section identifiers in configuration files are
     * possible
     * 
     * @param newestConfigurations map which contains all paths and configuration in YAML format. key is the node path in CRX under which
     *            the respective configuration is stored, entry is the textual configuration
     * @param installationLog
     * @return The AcConfiguration
     * @throws RepositoryException in case some repository error has occurred
     * @throws AcConfigBeanValidationException in case the given configuration is invalid */
    public abstract AcConfiguration getMergedConfigurations(
            final Map<String, String> newestConfigurations,
            final PersistableInstallationLogger installationLog,
            final ConfigReader configReader, Session session) throws RepositoryException,
            AcConfigBeanValidationException;

}