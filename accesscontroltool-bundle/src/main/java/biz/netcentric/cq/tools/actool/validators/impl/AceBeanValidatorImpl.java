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

import org.apache.commons.lang.StringUtils;
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

    private static final Logger LOG = LoggerFactory
            .getLogger(AceBeanValidatorImpl.class);
    private long currentBeanCounter = 0;
    private AceBean aceBean;
    private Set<String> groupsFromCurrentConfig;
    private boolean enabled = true;

    public AceBeanValidatorImpl(Set<String> groupsFromCurrentConfig) {
        this.groupsFromCurrentConfig = groupsFromCurrentConfig;
    }

    public AceBeanValidatorImpl() {
    }

    @Override
    public boolean validate(final AceBean aceBean, AccessControlManager aclManager)
            throws AcConfigBeanValidationException {
        if (!this.enabled) {
            return true;
        }
        this.aceBean = aceBean;
        return validate(aclManager);
    }

    private boolean validate(AccessControlManager aclManager) throws AcConfigBeanValidationException {

        if (this.aceBean.isInitialContentOnlyConfig()) {
            return true;
        }

        // mandatory properties per AceBean
        boolean isActionDefined = false;
        boolean isPrivilegeDefined = false;

        this.currentBeanCounter++;
        validateAuthorizableId(this.groupsFromCurrentConfig, this.aceBean);
        validateAcePath(this.aceBean);

        isActionDefined = validateActions(this.aceBean);
        isPrivilegeDefined = validatePrivileges(this.aceBean, aclManager);

        validatePermission(this.aceBean);

        // either action(s) or permission(s) or both have to be defined!
        final boolean isActionOrPrivilegeDefined = isActionDefined || isPrivilegeDefined;

        final boolean hasInitialContent = StringUtils.isNotBlank(this.aceBean.getInitialContent());

        if (!isActionOrPrivilegeDefined && !hasInitialContent) {
            final String errorMessage = getBeanDescription(this.currentBeanCounter,
                    this.aceBean.getPrincipalName())
                    + ", no actions or privileges defined"
                    + "! Installation aborted!";
            LOG.error(errorMessage);
            throw new NoActionOrPrivilegeDefinedException(errorMessage);
        }

        validateRestrictions(this.aceBean, aclManager);

        return true;
    }

    @Override
    public boolean validateRestrictions(final AceBean tmpAceBean, final AccessControlManager aclManager)
            throws InvalidRepGlobException, InvalidRestrictionsException {
        boolean valid = true;

        final List<Restriction> restrictions = tmpAceBean.getRestrictions();
        if (restrictions.isEmpty()) {
            return true;
        }

        final String principal = tmpAceBean.getPrincipalName();

        final Set<String> restrictionNamesFromAceBean = new HashSet<String>();
        for (Restriction restriction : restrictions) {
            restrictionNamesFromAceBean.add(restriction.getName());
        }

        final Set<String> allowedRestrictionNames = getSupportedRestrictions(aclManager);

        if (!allowedRestrictionNames.containsAll(restrictionNamesFromAceBean)) {
            restrictionNamesFromAceBean.removeAll(allowedRestrictionNames);
            valid = false;
            final String errorMessage = getBeanDescription(this.currentBeanCounter,
                    principal)
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



    @Override
    public boolean validatePermission(final AceBean tmpAclBean)
            throws InvalidPermissionException {

        final String permission = tmpAclBean.getPermission();
        if (StringUtils.isNotBlank(this.aceBean.getInitialContent()) && StringUtils.isBlank(permission)) {
            return true;
        }

        if (Validators.isValidPermission(permission)) {
            tmpAclBean.setPermission(permission);
        } else {
            final String principal = tmpAclBean.getPrincipalName();
            final String errorMessage = getBeanDescription(this.currentBeanCounter,
                    principal) + ", invalid permission: '" + permission + "'";
            LOG.error(errorMessage);
            throw new InvalidPermissionException(errorMessage);
        }
        return true;
    }

    @Override
    public boolean validateActions(final AceBean tmpAclBean)
            throws InvalidActionException, TooManyActionsException,
            DoubledDefinedActionException {
        final String principal = tmpAclBean.getPrincipalName();

        if (!StringUtils.isNotBlank(tmpAclBean.getActionsStringFromConfig())) {
            return false;
        }

        final String[] actions = tmpAclBean.getActionsStringFromConfig().split(",");

        if (actions.length > CqActions.ACTIONS.length) {
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
        tmpAclBean.setActions(actions);

        return true;
    }

    @Override
    public boolean validatePrivileges(final AceBean tmpAclBean, AccessControlManager aclManager)
            throws InvalidJcrPrivilegeException,
            DoubledDefinedJcrPrivilegeException {
        final String currentEntryValue = tmpAclBean.getPrivilegesString();
        final String principal = tmpAclBean.getPrincipalName();

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
                        this.currentBeanCounter, principal)
                        + ",  invalid jcr privilege: " + privileges[i];
                LOG.error(errorMessage);
                throw new InvalidJcrPrivilegeException(errorMessage);
            }
            if (!privilegesSet.add(privileges[i])) {
                final String errorMessage = getBeanDescription(
                        this.currentBeanCounter, principal)
                        + ", doubled defined jcr privilege: " + privileges[i];
                LOG.error(errorMessage);
                throw new DoubledDefinedJcrPrivilegeException(errorMessage);
            }
        }
        tmpAclBean.setPrivilegesString(currentEntryValue);

        return true;
    }

    @Override
    public boolean validateAcePath(final AceBean tmpAclBean)
            throws InvalidPathException {
        boolean isPathDefined = false;
        final String currentEntryValue = tmpAclBean.getJcrPath();
        final String principal = tmpAclBean.getPrincipalName();
        if (Validators.isValidNodePath(currentEntryValue)) {
            tmpAclBean.setJcrPath(currentEntryValue);
            isPathDefined = true;
        } else {
            final String errorMessage = getBeanDescription(this.currentBeanCounter,
                    principal) + ", invalid path: " + currentEntryValue;
            LOG.error(errorMessage);
            throw new InvalidPathException(errorMessage);
        }
        return isPathDefined;
    }

    @Override
    public boolean validateAuthorizableId(
            final Set<String> groupsFromCurrentConfig, final AceBean tmpAclBean)
                    throws NoGroupDefinedException, InvalidGroupNameException {
        boolean valid = true;
        final String principal = tmpAclBean.getPrincipalName();
        // validate authorizable name format
        if (Validators.isValidAuthorizableId(principal)) {

            // validate if authorizable is contained in config
            if (!groupsFromCurrentConfig.contains(principal)) {
                final String message = getBeanDescription(this.currentBeanCounter,
                        principal) + " is not defined in group configuration";
                throw new NoGroupDefinedException(message);
            }
            tmpAclBean.setPrincipal(principal);
        } else {
            valid = false;
            final String errorMessage = getBeanDescription(this.currentBeanCounter,
                    principal)
                    + principal
                    + ", invalid authorizable name: "
                    + principal;
            LOG.error(errorMessage);
            throw new InvalidGroupNameException(errorMessage);

        }
        return valid;
    }

    @Override
    public void setBean(AceBean aceBean) {
        this.aceBean = aceBean;

    }

    @Override
    public void setGroupsFromCurrentConfig(
            final Set<String> groupsFromCurrentConfig) {
        this.groupsFromCurrentConfig = groupsFromCurrentConfig;
    }

    @Override
    public void setBeanCounter(final long value) {
        this.currentBeanCounter = value;
    }

    private String getBeanDescription(final long beanCounter,
            final String principalName) {
        return "Validation error while reading ACE definition nr."
                + beanCounter + " of authorizable: " + principalName;
    }

    @Override
    public void setCurrentAuthorizableName(final String name) {
        if (this.enabled) {
            LOG.debug("Start validation of ACEs for authorizable: {}", name);
            this.currentBeanCounter = 0;
        }
    }

    @Override
    public void enable() {
        this.enabled = true;

    }

    @Override
    public void disable() {
        this.enabled = false;
    }
}
