/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.validators.impl;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.validators.AuthorizableValidator;
import biz.netcentric.cq.tools.actool.validators.Validators;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;
import biz.netcentric.cq.tools.actool.validators.exceptions.InvalidGroupNameException;

public class AuthorizableValidatorImpl implements AuthorizableValidator {

    private static final Logger LOG = LoggerFactory
            .getLogger(AuthorizableValidatorImpl.class);
    private boolean enabled = true;
    AuthorizableConfigBean authorizableConfigBean;

    public AuthorizableValidatorImpl() {
    }

    public void validate(final AuthorizableConfigBean authorizableConfigBean)
            throws AcConfigBeanValidationException {
        this.authorizableConfigBean = authorizableConfigBean;
        validate();
    }

    private boolean validate() throws AcConfigBeanValidationException {
        if (enabled) {
            return validateMemberOf(this.authorizableConfigBean)
                    && validateMembers(this.authorizableConfigBean)
                    && validateAuthorizableId(this.authorizableConfigBean);
        }
        return true;
    }

    public boolean validateMemberOf(
            final AuthorizableConfigBean tmpPrincipalConfigBean)
            throws InvalidGroupNameException {
        final String currentPrincipal = tmpPrincipalConfigBean.getPrincipalID();
        final String currentEntryValue = tmpPrincipalConfigBean
                .getMemberOfStringFromConfig();
        if (StringUtils.isNotBlank(currentEntryValue)) {
            if (currentEntryValue != null) {

                final String[] groups = currentEntryValue.split(",");

                for (int i = 0; i < groups.length; i++) {

                    // remove leading and trailing blanks from groupname
                    groups[i] = StringUtils.strip(groups[i]);

                    if (!Validators.isValidAuthorizableId(groups[i])) {
                        LOG.error(
                                "Validation error while reading group property of authorizable:{}, invalid authorizable name: {}",
                                currentPrincipal, groups[i]);
                        throw new InvalidGroupNameException(
                                "Validation error while reading group property of authorizable: "
                                        + currentPrincipal
                                        + ", invalid group name: " + groups[i]);
                    }
                }

                tmpPrincipalConfigBean.setMemberOf(groups);

            }
        }
        return true;
    }

    public boolean validateMembers(
            final AuthorizableConfigBean tmpPrincipalConfigBean)
            throws InvalidGroupNameException {
        final String currentPrincipal = tmpPrincipalConfigBean.getPrincipalID();
        final String currentEntryValue = tmpPrincipalConfigBean
                .getMembersStringFromConfig();
        if (StringUtils.isNotBlank(currentEntryValue)) {
            if (currentEntryValue != null) {

                final String[] groups = currentEntryValue.split(",");

                for (int i = 0; i < groups.length; i++) {

                    // remove leading and trailing blanks from groupname
                    groups[i] = StringUtils.strip(groups[i]);

                    if (!Validators.isValidAuthorizableId(groups[i])) {
                        LOG.error(
                                "Validation error while reading group property of authorizable:{}, invalid authorizable name: {}",
                                currentPrincipal, groups[i]);
                        throw new InvalidGroupNameException(
                                "Validation error while reading group property of authorizable: "
                                        + currentPrincipal
                                        + ", invalid group name: " + groups[i]);
                    }
                }

                tmpPrincipalConfigBean.setMembers(groups);

            }
        }
        return true;
    }

    public boolean validateAuthorizableId(
            final AuthorizableConfigBean tmpPrincipalConfigBean)
            throws InvalidGroupNameException {
        final String currentPrincipal = tmpPrincipalConfigBean.getPrincipalID();

        if (Validators.isValidAuthorizableId((String) currentPrincipal)) {
            tmpPrincipalConfigBean.setPrincipalID((String) currentPrincipal);
        } else {
            final String message = "Validation error while reading group data: invalid group name: "
                    + (String) currentPrincipal;
            LOG.error(message);
            throw new InvalidGroupNameException(message);

        }
        return true;
    }

    @Override
    public void setBean(final AuthorizableConfigBean authorizableConfigBean) {
        this.authorizableConfigBean = authorizableConfigBean;
    }

    @Override
    public void disable() {
        this.enabled = false;

    }

}
