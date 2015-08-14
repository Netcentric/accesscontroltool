/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configReader;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.jcr.RepositoryException;

import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.helper.AceBean;
import biz.netcentric.cq.tools.actool.validators.AceBeanValidator;
import biz.netcentric.cq.tools.actool.validators.AuthorizableValidator;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;

public interface ConfigReader {

    public Map<String, Set<AceBean>> getAceConfigurationBeans(
            final Collection<?> aceConfigData, Set<String> groupsFromConfig,
            AceBeanValidator aceBeanValidator) throws RepositoryException,
            AcConfigBeanValidationException;

    public Map<String, Set<AuthorizableConfigBean>> getGroupConfigurationBeans(
            final Collection<?> groupConfigData,
            AuthorizableValidator authorizableValidator)
                    throws AcConfigBeanValidationException;

    public Map<String, Set<AuthorizableConfigBean>> getUserConfigurationBeans(
            final Collection<?> userConfigData,
            AuthorizableValidator authorizableValidator)
                    throws AcConfigBeanValidationException;
}