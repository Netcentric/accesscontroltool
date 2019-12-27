/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.validators.impl;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.jcr.AccessDeniedException;
import javax.jcr.RepositoryException;
import javax.jcr.security.AccessControlManager;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.security.util.CqActions;

import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.configmodel.Restriction;
import biz.netcentric.cq.tools.actool.helper.AccessControlUtils;
import biz.netcentric.cq.tools.actool.validators.AceBeanValidator;
import biz.netcentric.cq.tools.actool.validators.Validators;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;
import biz.netcentric.cq.tools.actool.validators.exceptions.DoubledDefinedActionException;
import biz.netcentric.cq.tools.actool.validators.exceptions.DoubledDefinedJcrPrivilegeException;
import biz.netcentric.cq.tools.actool.validators.exceptions.InvalidActionException;
import biz.netcentric.cq.tools.actool.validators.exceptions.InvalidGroupNameException;
import biz.netcentric.cq.tools.actool.validators.exceptions.InvalidJcrPrivilegeException;
import biz.netcentric.cq.tools.actool.validators.exceptions.InvalidPathException;
import biz.netcentric.cq.tools.actool.validators.exceptions.InvalidPermissionException;
import biz.netcentric.cq.tools.actool.validators.exceptions.InvalidRepGlobException;
import biz.netcentric.cq.tools.actool.validators.exceptions.InvalidRestrictionsException;
import biz.netcentric.cq.tools.actool.validators.exceptions.NoActionOrPrivilegeDefinedException;
import biz.netcentric.cq.tools.actool.validators.exceptions.NoGroupDefinedException;
import biz.netcentric.cq.tools.actool.validators.exceptions.TooManyActionsException;

public class AceBeanValidatorImpl implements AceBeanValidator {
    private static final Logger LOG = LoggerFactory.getLogger(AceBeanValidatorImpl.class);

    private long currentBeanCounter = 0;
    private AceBean aceBean;
    private Set<String> authorizableIdsFromCurrentConfig;

    private String previousAuthorizableId;

    public AceBeanValidatorImpl(Set<String> authorizableIdsFromCurrentConfig) {
        this.authorizableIdsFromCurrentConfig = authorizableIdsFromCurrentConfig;
    }

    public AceBeanValidatorImpl() {
    }

    @Override
    public boolean validate(final AceBean aceBean, AccessControlManager aclManager)
            throws AcConfigBeanValidationException {

        this.aceBean = aceBean;
        return validate(aclManager);
    }

    private boolean validate(AccessControlManager aclManager) throws AcConfigBeanValidationException {

        if (this.aceBean.isInitialContentOnlyConfig()) {
            return true;
        }

        maintainBeanCounter();

        validateAuthorizableId();
        validateAcePath();

        // either actions or privileges are required
        boolean isActionDefined = validateActions();
        boolean isPrivilegeDefined = validatePrivileges(aclManager);

        validatePermission(this.aceBean);

        // either action(s) or permission(s) or both have to be defined!
        final boolean isActionOrPrivilegeDefined = isActionDefined || isPrivilegeDefined;

        final boolean hasInitialContent = StringUtils.isNotBlank(this.aceBean.getInitialContent());

        if (!isActionOrPrivilegeDefined && !hasInitialContent) {
            final String errorMessage = getBeanDescription(this.currentBeanCounter,
                    this.aceBean.getAuthorizableId())
                    + ", no actions or privileges defined"
                    + "! Installation aborted!";
            LOG.error(errorMessage);
            throw new NoActionOrPrivilegeDefinedException(errorMessage);
        }

        validateRestrictions(this.aceBean, aclManager);

        return true;
    }

    private void maintainBeanCounter() {
        if (StringUtils.equals(aceBean.getAuthorizableId(), previousAuthorizableId)) {
            this.currentBeanCounter++;
        } else {
            this.currentBeanCounter = 1;
        }
        previousAuthorizableId = aceBean.getAuthorizableId();
    }

    private boolean validateRestrictions(final AceBean tmpAceBean, final AccessControlManager aclManager)
            throws InvalidRepGlobException, InvalidRestrictionsException {
        boolean valid = true;

        final List<Restriction> restrictions = tmpAceBean.getRestrictions();
        if (restrictions.isEmpty()) {
            return true;
        }

        final Set<String> restrictionNamesFromAceBean = new HashSet<String>();
        for (Restriction restriction : restrictions) {
            restrictionNamesFromAceBean.add(restriction.getName());
        }

        final Set<String> allowedRestrictionNames = getSupportedRestrictions(aclManager);

        if (!allowedRestrictionNames.containsAll(restrictionNamesFromAceBean)) {
            restrictionNamesFromAceBean.removeAll(allowedRestrictionNames);
            valid = false;
            final String errorMessage = getBeanDescription(this.currentBeanCounter,
                    tmpAceBean.getAuthorizableId())
                    + ",  this repository doesn't support following restriction(s): "
                    + restrictionNamesFromAceBean;
            throw new InvalidRestrictionsException(errorMessage);
        }

        return valid;
    }

    private Set<String> getSupportedRestrictions(final AccessControlManager aclManager)
            throws InvalidRepGlobException {
        Set<String> allowedRestrictions = new HashSet<>();
        try {
            final JackrabbitAccessControlList jacl = getJackrabbitAccessControlList(aclManager);
            allowedRestrictions = new HashSet<>(Arrays.asList(jacl.getRestrictionNames()));
        } catch (final RepositoryException e) {
            throw new InvalidRepGlobException("Could not get restriction names from ACL of path: " + this.aceBean.getJcrPath());
        }
        return allowedRestrictions;
    }

    private JackrabbitAccessControlList getJackrabbitAccessControlList(final AccessControlManager aclManager) throws RepositoryException, AccessDeniedException {
        JackrabbitAccessControlList jacl = null;
        // don't check paths containing wildcards
        if(!this.aceBean.getJcrPath().contains("*")){
            jacl = AccessControlUtils.getModifiableAcl(aclManager, this.aceBean.getJcrPath());
        }
        if(jacl == null){
            // root as fallback
            jacl = AccessControlUtils.getModifiableAcl(aclManager, "/");
        }
        return jacl;
    }

    private boolean validatePermission(final AceBean tmpAclBean) throws InvalidPermissionException {

        final String permission = tmpAclBean.getPermission();
        if (StringUtils.isNotBlank(this.aceBean.getInitialContent()) && StringUtils.isBlank(permission)) {
            return true;
        }

        if (Validators.isValidPermission(permission)) {
            tmpAclBean.setPermission(permission);
        } else {
            final String errorMessage = getBeanDescription(this.currentBeanCounter,
                    tmpAclBean.getAuthorizableId()) + ", invalid permission: '" + permission + "'";
            LOG.error(errorMessage);
            throw new InvalidPermissionException(errorMessage);
        }
        return true;
    }

    private boolean validateActions() throws InvalidActionException, TooManyActionsException, DoubledDefinedActionException {
        final String principal = aceBean.getAuthorizableId();

        final String[] actions = aceBean.getActions();

        if (actions == null || actions.length == 0) {
            return false;
        }

        if (actions.length > CqActions.ACTIONS.length /* package com.day.cq.security.util is optional but constant is guaranteed to be inlined at compile time */) {
            final String errorMessage = getBeanDescription(this.currentBeanCounter,
                    principal) + " too many actions defined!";
            LOG.error(errorMessage);
            throw new TooManyActionsException(errorMessage);
        }
        final Set<String> actionsSet = new HashSet<String>();
        for (int i = 0; i < actions.length; i++) {

            // remove leading and trailing blanks from action name
            actions[i] = StringUtils.strip(actions[i]);

            if (!Validators.isValidAction(actions[i])) {
                final String errorMessage = getBeanDescription(
                        this.currentBeanCounter, principal)
                        + ", invalid action: " + actions[i];
                LOG.error(errorMessage);
                throw new InvalidActionException(errorMessage);
            }
            if (!actionsSet.add(actions[i])) {
                final String errorMessage = getBeanDescription(
                        this.currentBeanCounter, principal)
                        + ", doubled defined action: " + actions[i];
                LOG.error(errorMessage);
                throw new DoubledDefinedActionException(errorMessage);
            }
        }
        aceBean.setActions(actions);

        return true;
    }

    public boolean validatePrivileges(AccessControlManager aclManager)
            throws InvalidJcrPrivilegeException, DoubledDefinedJcrPrivilegeException {
        final String currentEntryValue = aceBean.getPrivilegesString();

        if (!StringUtils.isNotBlank(currentEntryValue)) {
            return false;
        }
        final String[] privileges = currentEntryValue.split(",");
        final Set<String> privilegesSet = new HashSet<String>();

        for (int i = 0; i < privileges.length; i++) {

            // remove leading and trailing blanks from privilege name
            privileges[i] = StringUtils.strip(privileges[i]);

            if (!Validators.isValidJcrPrivilege(privileges[i], aclManager)) {
                final String errorMessage = getBeanDescription(
                        this.currentBeanCounter, aceBean.getAuthorizableId())
                        + ",  invalid jcr privilege: " + privileges[i];
                LOG.error(errorMessage);
                throw new InvalidJcrPrivilegeException(errorMessage);
            }
            if (!privilegesSet.add(privileges[i])) {
                final String errorMessage = getBeanDescription(
                        this.currentBeanCounter, aceBean.getAuthorizableId())
                        + ", doubled defined jcr privilege: " + privileges[i];
                LOG.error(errorMessage);
                throw new DoubledDefinedJcrPrivilegeException(errorMessage);
            }
        }
        aceBean.setPrivilegesString(currentEntryValue);

        return true;
    }


    private boolean validateAcePath() throws InvalidPathException {
        boolean isPathDefined = false;
        final String currentEntryValue = aceBean.getJcrPath();
        if (Validators.isValidNodePath(currentEntryValue)) {
            aceBean.setJcrPath(currentEntryValue);
            isPathDefined = true;
        } else {
            final String errorMessage = getBeanDescription(this.currentBeanCounter,
                    aceBean.getAuthorizableId()) + ", invalid path: " + currentEntryValue;
            LOG.error(errorMessage);
            throw new InvalidPathException(errorMessage);
        }
        return isPathDefined;
    }


    private boolean validateAuthorizableId() throws NoGroupDefinedException, InvalidGroupNameException {
        boolean valid = true;
        final String authorizableId = aceBean.getAuthorizableId();
        // validate authorizable name format
        if (Validators.isValidAuthorizableId(authorizableId)) {

            // validate if authorizable is contained in config
            if (!authorizableIdsFromCurrentConfig.contains(authorizableId)) {
                final String message = getBeanDescription(this.currentBeanCounter,
                        authorizableId) + " is not defined in group configuration";
                throw new NoGroupDefinedException(message);
            }
            aceBean.setAuthorizableId(authorizableId);
        } else {
            valid = false;
            final String errorMessage = getBeanDescription(this.currentBeanCounter,
                    authorizableId)
                    + authorizableId
                    + ", invalid authorizable name: "
                    + authorizableId;
            LOG.error(errorMessage);
            throw new InvalidGroupNameException(errorMessage);

        }
        return valid;
    }

    private String getBeanDescription(long beanCounter, String authorizableId) {
        return "Validation error while reading ACE definition nr." + beanCounter + " of authorizable " + authorizableId;
    }


}
