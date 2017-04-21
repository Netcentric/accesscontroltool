/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.validators;

import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;

public interface AuthorizableValidator {

    public boolean validate(AuthorizableConfigBean authorizableConfigBean) throws AcConfigBeanValidationException;

    public void disable();

}
