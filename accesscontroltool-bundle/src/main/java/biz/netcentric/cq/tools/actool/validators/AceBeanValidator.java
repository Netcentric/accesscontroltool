/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.validators;

import java.util.Set;

import javax.jcr.security.AccessControlManager;

import biz.netcentric.cq.tools.actool.configmodel.AceBean;
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

public interface AceBeanValidator {

    boolean validate(final AceBean aceBean, AccessControlManager accessControlManager)
            throws AcConfigBeanValidationException;

    boolean validateRestrictions(final AceBean tmpAclBean, final AccessControlManager aclManager)
            throws InvalidRepGlobException, InvalidRestrictionsException;

    boolean validatePermission(AceBean tmpAclBean)
            throws InvalidPermissionException;

    boolean validatePrivileges(AceBean tmpAclBean, AccessControlManager accessControlManager)
            throws NoActionOrPrivilegeDefinedException,
            InvalidJcrPrivilegeException, DoubledDefinedJcrPrivilegeException;

    boolean validateActions(AceBean tmpAclBean) throws InvalidActionException,
    TooManyActionsException, DoubledDefinedActionException;

    boolean validateAcePath(AceBean tmpAclBean) throws InvalidPathException;

    boolean validateAuthorizableId(Set<String> groupsFromCurrentConfig,
            AceBean tmpAclBean) throws NoGroupDefinedException,
    InvalidGroupNameException;

    public void setBean(AceBean aceBean);

    public void setGroupsFromCurrentConfig(Set<String> groupsFromCurrentConfig);

    public void setBeanCounter(long value);

    public void setCurrentAuthorizableName(final String name);

    public void enable();

    public void disable();

}
