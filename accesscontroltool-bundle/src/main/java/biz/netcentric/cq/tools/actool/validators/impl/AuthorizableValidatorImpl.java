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

import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.validators.AuthorizableValidator;
import biz.netcentric.cq.tools.actool.validators.Validators;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;
import biz.netcentric.cq.tools.actool.validators.exceptions.InvalidAuthorizableException;
import biz.netcentric.cq.tools.actool.validators.exceptions.InvalidGroupNameException;
import biz.netcentric.cq.tools.actool.validators.exceptions.InvalidIntermediatePathException;

public class AuthorizableValidatorImpl implements AuthorizableValidator {

    private static final Logger LOG = LoggerFactory
            .getLogger(AuthorizableValidatorImpl.class);
    private boolean enabled = true;
    AuthorizableConfigBean authorizableConfigBean;
    final String groupsPath;
    final String usersPath;

    public AuthorizableValidatorImpl(final String groupsPath, final String usersPath) {
        this.groupsPath = groupsPath;
        this.usersPath = usersPath;
    }

    @Override
    public boolean validate(AuthorizableConfigBean authorizableConfigBean)
            throws AcConfigBeanValidationException {
        boolean success = true;
        if (enabled) {
            success = validateAuthorizableProperties(authorizableConfigBean)
                    && validateMemberOf(authorizableConfigBean)
                    && validateMembers(authorizableConfigBean)
                    && validateAuthorizableId(authorizableConfigBean)
                    && validateIntermediatePath(authorizableConfigBean);
        }
        return success;
    }

    public boolean validateIntermediatePath(
            final AuthorizableConfigBean authorizableConfigBean)
                    throws InvalidAuthorizableException, InvalidIntermediatePathException {
        boolean isGroup = authorizableConfigBean.isGroup();
        String intermediatePath = authorizableConfigBean.getPath();
        String currentAuthorizableId = authorizableConfigBean.getAuthorizableId();
        final String basicErrorMessage = "Validation error while validating intermediate path of authorizable: "
                + currentAuthorizableId;
        // we only care about paths starting with a slash. if there is none, the path is assumed to be relative
        if (intermediatePath.startsWith("/")) {
            if (!intermediatePath.startsWith(groupsPath) && !intermediatePath.startsWith(usersPath)) {
                String message = basicErrorMessage
                        + " - the intermediate path either has to be relative (not starting with '/') or has to start with the authorizable root!";
                LOG.error(message);
                throw new InvalidIntermediatePathException(message);
            }

            if (!isGroup && intermediatePath.startsWith(groupsPath)) {
                String message = basicErrorMessage + " - the intermediate path for the user must not be the groups path: " + groupsPath;
                LOG.error(message);
                throw new InvalidIntermediatePathException(message);
            }
            if (isGroup && intermediatePath.startsWith(usersPath)) {
                String message = basicErrorMessage + " - the intermediate path for the group must not be the users path: " + usersPath;
                LOG.error(message);
                throw new InvalidIntermediatePathException(message);
            }
            if (intermediatePath.equals(groupsPath) || intermediatePath.equals(usersPath) || intermediatePath.equals(groupsPath + "/")
                    || intermediatePath.equals(usersPath + "/")) {
                String message = basicErrorMessage
                        + " - the intermediate path must not be equal to the authorizable root but has to specify a subfolder of it!";
                LOG.error(message);
                throw new InvalidIntermediatePathException(message);
            }
        }
        return true;
    }

    public boolean validateAuthorizableProperties(
            final AuthorizableConfigBean authorizableConfigBean)
                    throws InvalidAuthorizableException {

        if (authorizableConfigBean.isGroup()) {
            if (StringUtils.isNotBlank(authorizableConfigBean.getPassword())) {
                final String message = "Group " + authorizableConfigBean.getAuthorizableId()
                        + " may not be configured with password";
                LOG.error(message);
                throw new InvalidAuthorizableException(message);
            }

            if (StringUtils.isNotBlank(authorizableConfigBean.getDisabled())) {
                final String message = "Groups cannot be disabled - property 'disable' is used on "
                        + authorizableConfigBean.getAuthorizableId();
                LOG.error(message);
                throw new InvalidAuthorizableException(message);
            }

        } else {
            if (authorizableConfigBean.isSystemUser()) {
                if (StringUtils.isNotBlank(authorizableConfigBean.getPassword())) {
                    final String message = "System user " + authorizableConfigBean.getAuthorizableId()
                            + " may not be configured with password";
                    LOG.error(message);
                    throw new InvalidAuthorizableException(message);
                }
            } else {
                if (StringUtils.isBlank(authorizableConfigBean.getPassword())) {
                    final String message = "Password is required for user " + authorizableConfigBean.getAuthorizableId();
                    LOG.error(message);
                    throw new InvalidAuthorizableException(message);
                }
            }

            if (StringUtils.isNotBlank(authorizableConfigBean.getMigrateFrom())) {
                final String message = "migrateFrom can only be used with groups (found in " + authorizableConfigBean.getAuthorizableId()
                        + ")";
                LOG.error(message);
                throw new InvalidAuthorizableException(message);
            }

        }
        return true;
    }

    public boolean validateMemberOf(
            final AuthorizableConfigBean authorizableConfigBean)
                    throws InvalidGroupNameException {
        final String currentAuthorizable = authorizableConfigBean.getAuthorizableId();
        final String currentEntryValue = authorizableConfigBean
                .getIsMemberOfStringFromConfig();
        if (StringUtils.isNotBlank(currentEntryValue)) {
            if (currentEntryValue != null) {

                final String[] groups = currentEntryValue.split(",");

                for (int i = 0; i < groups.length; i++) {

                    // remove leading and trailing blanks from groupname
                    groups[i] = StringUtils.strip(groups[i]);

                    if (!Validators.isValidAuthorizableId(groups[i])) {
                        LOG.error(
                                "Validation error while reading group property of authorizable:{}, invalid authorizable name: {}",
                                currentAuthorizable, groups[i]);
                        throw new InvalidGroupNameException(
                                "Validation error while reading group property of authorizable: "
                                        + currentAuthorizable
                                        + ", invalid group name: " + groups[i]);
                    }
                }

                authorizableConfigBean.setIsMemberOf(groups);

            }
        }
        return true;
    }

    public boolean validateMembers(
            final AuthorizableConfigBean authorizableConfigBean)
                    throws InvalidGroupNameException {
        final String currentAuthorizable = authorizableConfigBean.getAuthorizableId();
        final String currentEntryValue = authorizableConfigBean
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
                                currentAuthorizable, groups[i]);
                        throw new InvalidGroupNameException(
                                "Validation error while reading group property of authorizable: "
                                        + currentAuthorizable
                                        + ", invalid group name: " + groups[i]);
                    }
                }

                authorizableConfigBean.setMembers(groups);

            }
        }
        return true;
    }

    public boolean validateAuthorizableId(
            final AuthorizableConfigBean authorizableConfigBean)
                    throws InvalidGroupNameException {
        final String authorizableId = authorizableConfigBean.getAuthorizableId();

        if (Validators.isValidAuthorizableId(authorizableId)) {
            authorizableConfigBean.setAuthorizableId(authorizableId);
        } else {
            final String message = "Validation error while reading group data: invalid group name: "
                    + authorizableId;
            LOG.error(message);
            throw new InvalidGroupNameException(message);

        }
        return true;
    }


    @Override
    public void disable() {
        enabled = false;

    }

}
