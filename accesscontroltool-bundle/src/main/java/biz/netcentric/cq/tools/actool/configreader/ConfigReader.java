package biz.netcentric.cq.tools.actool.configreader;

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

import java.util.Collection;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import biz.netcentric.cq.tools.actool.configmodel.AcesConfig;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizablesConfig;
import biz.netcentric.cq.tools.actool.configmodel.GlobalConfiguration;
import biz.netcentric.cq.tools.actool.validators.AceBeanValidator;
import biz.netcentric.cq.tools.actool.validators.AuthorizableValidator;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;

public interface ConfigReader {

    public AcesConfig getAceConfigurationBeans(
            final Collection<?> aceConfigData, AceBeanValidator aceBeanValidator, Session session, String sourceFile) throws RepositoryException,
            AcConfigBeanValidationException;

    public AuthorizablesConfig getGroupConfigurationBeans(
            final Collection<?> groupConfigData,
            AuthorizableValidator authorizableValidator)
            throws AcConfigBeanValidationException;

    public AuthorizablesConfig getUserConfigurationBeans(
            final Collection<?> userConfigData,
            AuthorizableValidator authorizableValidator)
            throws AcConfigBeanValidationException;

    public GlobalConfiguration getGlobalConfiguration(final Collection yamlList);

    public Set<String> getObsoluteAuthorizables(Collection yamlList);

}
