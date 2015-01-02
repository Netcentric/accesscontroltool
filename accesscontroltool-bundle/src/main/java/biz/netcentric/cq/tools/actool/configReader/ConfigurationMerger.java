/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configReader;

import java.util.List;
import java.util.Map;

import javax.jcr.RepositoryException;

import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;

public interface ConfigurationMerger {

    /**
     * Method that merges several textual AccessControlConfigurations written in
     * YAML format, each comprising of a groups and ACE configuration.
     * Validation ensures that no doubled defined groups and only valid section
     * identifiers in configuration files are possible
     * 
     * @param session
     * @param newestConfigurations
     *            map which contains all paths and configuration in YAML format.
     *            key is the node path in CRX under which the respective
     *            configuration is stored, entry is the textual configuration
     * @param history
     *            history object
     * @return List which contains the combined groups configurations (as map
     *         holding sets of AuthorizableConfigBeans) as first element and the
     *         combined ACE configurations (as map holding sets of AceBeans) as
     *         second element
     * @throws RepositoryException
     * @throws AcConfigBeanValidationException
     */
    public abstract List getMergedConfigurations(
            final Map<String, String> newestConfigurations,
            final AcInstallationHistoryPojo history,
            final ConfigReader configReader) throws RepositoryException,
            AcConfigBeanValidationException;

}