/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.validators.impl;

import java.util.HashSet;
import java.util.Set;

import javax.jcr.security.AccessControlManager;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.security.util.CqActions;

import biz.netcentric.cq.tools.actool.helper.AceBean;
import biz.netcentric.cq.tools.actool.validators.AceBeanValidator;
import biz.netcentric.cq.tools.actool.validators.Validators;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;
import biz.netcentric.cq.tools.actool.validators.exceptions.AlreadyDefinedGroupException;
import biz.netcentric.cq.tools.actool.validators.exceptions.DoubledDefinedActionException;
import biz.netcentric.cq.tools.actool.validators.exceptions.DoubledDefinedJcrPrivilegeException;
import biz.netcentric.cq.tools.actool.validators.exceptions.InvalidActionException;
import biz.netcentric.cq.tools.actool.validators.exceptions.InvalidGroupNameException;
import biz.netcentric.cq.tools.actool.validators.exceptions.InvalidJcrPrivilegeException;
import biz.netcentric.cq.tools.actool.validators.exceptions.InvalidPathException;
import biz.netcentric.cq.tools.actool.validators.exceptions.InvalidPermissionException;
import biz.netcentric.cq.tools.actool.validators.exceptions.InvalidRepGlobException;
import biz.netcentric.cq.tools.actool.validators.exceptions.NoActionOrPrivilegeDefinedException;
import biz.netcentric.cq.tools.actool.validators.exceptions.NoGroupDefinedException;
import biz.netcentric.cq.tools.actool.validators.exceptions.TooManyActionsException;

public class AceBeanValidatorImpl implements AceBeanValidator {

    private static final Logger LOG = LoggerFactory
            .getLogger(AceBeanValidatorImpl.class);
    private long currentBeanCounter = 0;
    private AceBean aceBean;
    private Set<String> groupsFromCurrentConfig;
    private Set<String> alreadyProcessedAuthorizables = new HashSet<String>();
    private boolean enabled = true;

    public AceBeanValidatorImpl(Set<String> groupsFromCurrentConfig) {
        this.groupsFromCurrentConfig = groupsFromCurrentConfig;
    }

    public AceBeanValidatorImpl() {
    }

    public boolean validate(final AceBean aceBean, AccessControlManager aclManager)
            throws AcConfigBeanValidationException {
        if (!enabled) {
            return true;
        }
        this.aceBean = aceBean;
        return validate(aclManager);
    }

    private boolean validate(AccessControlManager aclManager) throws AcConfigBeanValidationException {

        // mandatory properties per AceBean
        boolean isActionDefined = false;
        boolean isPrivilegeDefined = false;

        this.currentBeanCounter++;
        validateAuthorizableId(groupsFromCurrentConfig, aceBean);
        validateAcePath(aceBean);

        isActionDefined = validateActions(aceBean);
        isPrivilegeDefined = validatePrivileges(aceBean, aclManager);

        validatePermission(aceBean);

        // either action(s) or permission(s) or both have to be defined!
        boolean isActionOrPrivilegeDefined = isActionDefined
                || isPrivilegeDefined;

        if (!isActionOrPrivilegeDefined) {
            String errorMessage = getBeanDescription(this.currentBeanCounter,
                    this.aceBean.getPrincipalName())
                    + ", no actions or privileges defined"
                    + "! Installation aborted!";
            LOG.error(errorMessage);
            throw new NoActionOrPrivilegeDefinedException(errorMessage);
        }

        validateGlobbing(aceBean);

        return true;
    }

    public boolean validateGlobbing(final AceBean tmpAclBean)
            throws InvalidRepGlobException {
        boolean valid = true;
        String currentEntryValue = tmpAclBean.getRepGlob();
        String principal = tmpAclBean.getPrincipalName();

        if (Validators.isValidRegex(currentEntryValue)) {
            tmpAclBean.setRepGlob(currentEntryValue);
        } else {
            valid = false;
            String errorMessage = getBeanDescription(this.currentBeanCounter,
                    principal)
                    + ",  invalid globbing expression: "
                    + currentEntryValue;
            LOG.error(errorMessage);
            throw new InvalidRepGlobException(errorMessage);
        }
        return valid;
    }

    public boolean validatePermission(final AceBean tmpAclBean)
            throws InvalidPermissionException {
        boolean valid = true;
        String permission = tmpAclBean.getPermission();
        String principal = tmpAclBean.getPrincipalName();
        if (Validators.isValidPermission(permission)) {
            tmpAclBean.setPermission(permission);
        } else {
            valid = false;
            String errorMessage = getBeanDescription(this.currentBeanCounter,
                    principal) + ", invalid permission: " + permission;
            LOG.error(errorMessage);
            throw new InvalidPermissionException(errorMessage);
        }
        return valid;
    }

    public boolean validateActions(final AceBean tmpAclBean)
            throws InvalidActionException, TooManyActionsException,
            DoubledDefinedActionException {
        String principal = tmpAclBean.getPrincipalName();

        if (!StringUtils.isNotBlank(tmpAclBean.getActionsStringFromConfig())) {
            return false;
        }

        String[] actions = tmpAclBean.getActionsStringFromConfig().split(",");

        if (actions.length > CqActions.ACTIONS.length) {
            String errorMessage = getBeanDescription(this.currentBeanCounter,
                    principal) + " too many actions defined!";
            LOG.error(errorMessage);
            throw new TooManyActionsException(errorMessage);
        }
        Set<String> actionsSet = new HashSet<String>();
        for (int i = 0; i < actions.length; i++) {

            // remove leading and trailing blanks from action name
            actions[i] = StringUtils.strip(actions[i]);

            if (!Validators.isValidAction(actions[i])) {
                String errorMessage = getBeanDescription(
                        this.currentBeanCounter, principal)
                        + ", invalid action: " + actions[i];
                LOG.error(errorMessage);
                throw new InvalidActionException(errorMessage);
            }
            if (!actionsSet.add(actions[i])) {
                String errorMessage = getBeanDescription(
                        this.currentBeanCounter, principal)
                        + ", doubled defined action: " + actions[i];
                LOG.error(errorMessage);
                throw new DoubledDefinedActionException(errorMessage);
            }
        }
        tmpAclBean.setActions(actions);

        return true;
    }

    public boolean validatePrivileges(final AceBean tmpAclBean, AccessControlManager aclManager)
            throws InvalidJcrPrivilegeException,
            DoubledDefinedJcrPrivilegeException {
        String currentEntryValue = tmpAclBean.getPrivilegesString();
        String principal = tmpAclBean.getPrincipalName();

        if (!StringUtils.isNotBlank(currentEntryValue)) {
            return false;
        }
        String[] privileges = currentEntryValue.split(",");
        Set<String> privilegesSet = new HashSet<String>();

        for (int i = 0; i < privileges.length; i++) {

            // remove leading and trailing blanks from privilege name
            privileges[i] = StringUtils.strip(privileges[i]);

            if (!Validators.isValidJcrPrivilege(privileges[i], aclManager)) {
                String errorMessage = getBeanDescription(
                        this.currentBeanCounter, principal)
                        + ",  invalid jcr privilege: " + privileges[i];
                LOG.error(errorMessage);
                throw new InvalidJcrPrivilegeException(errorMessage);
            }
            if (!privilegesSet.add(privileges[i])) {
                String errorMessage = getBeanDescription(
                        this.currentBeanCounter, principal)
                        + ", doubled defined jcr privilege: " + privileges[i];
                LOG.error(errorMessage);
                throw new DoubledDefinedJcrPrivilegeException(errorMessage);
            }
        }
        tmpAclBean.setPrivilegesString(currentEntryValue);

        return true;
    }

    public boolean validateAcePath(final AceBean tmpAclBean)
            throws InvalidPathException {
        boolean isPathDefined = false;
        String currentEntryValue = tmpAclBean.getJcrPath();
        String principal = tmpAclBean.getPrincipalName();
        if (Validators.isValidNodePath(currentEntryValue)) {
            tmpAclBean.setJcrPath(currentEntryValue);
            isPathDefined = true;
        } else {
            String errorMessage = getBeanDescription(this.currentBeanCounter,
                    principal) + ", invalid path: " + currentEntryValue;
            LOG.error(errorMessage);
            throw new InvalidPathException(errorMessage);
        }
        return isPathDefined;
    }

    public boolean validateAuthorizableId(
            final Set<String> groupsFromCurrentConfig, final AceBean tmpAclBean)
            throws NoGroupDefinedException, InvalidGroupNameException {
        boolean valid = true;
        String principal = tmpAclBean.getPrincipalName();
        // validate authorizable name format
        if (Validators.isValidAuthorizableId(principal)) {

            // validate if authorizable is contained in config
            if (!groupsFromCurrentConfig.contains(principal)) {
                String message = getBeanDescription(this.currentBeanCounter,
                        principal) + " is not defined in group configuration";
                throw new NoGroupDefinedException(message);
            }
            tmpAclBean.setPrincipal(principal);
        } else {
            valid = false;
            String errorMessage = getBeanDescription(this.currentBeanCounter,
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
    public void setCurrentAuthorizableName(final String name)
            throws AlreadyDefinedGroupException {
        if (this.enabled) {
            LOG.info("insert {} into set", name);
            if (!alreadyProcessedAuthorizables.add(name)) {
                String errorMessage = getBeanDescription(1, name)
                        + ", found more than one ACE definition block for this group!";
                LOG.error(errorMessage);
                throw new AlreadyDefinedGroupException(errorMessage);
            }

            LOG.info("start validation of ACEs for authorizable: {}", name);
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
