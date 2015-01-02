/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.helper;

import java.util.Arrays;
import org.apache.commons.lang.StringUtils;
import biz.netcentric.cq.tools.actool.dumpservice.AcDumpElement;
import biz.netcentric.cq.tools.actool.dumpservice.AcDumpElementVisitor;

/**
 * 
 * @author jochenkoschorke This class is used to store data of an
 *         AcessControlEntry. Objects of this class get created during the
 *         reading of the configuration file in order to set the corresponding
 *         ACEs in the system on the one hand and to store data during the
 *         reading of existing ACEs before writing the data back to a dump or
 *         configuration file again on the other hand.
 */
public class AceBean implements AcDumpElement {

    private String jcrPath;
    private boolean isAllow;
    private String repGlob;
    private String actionsStringFromConfig;
    private String privilegesString;
    private String principal;
    private String permissionString;
    private boolean isValid = true;
    private boolean isAllowProvided = false;
    private String[] actions;
    private String assertedExceptionString;

    public String getAssertedExceptionString() {
        return assertedExceptionString;
    }

    public void setAssertedExceptionString(final String assertedException) {
        this.assertedExceptionString = assertedException;
    }

    public String getPermissionString() {
        return permissionString;
    }

    public void setPermissionString(String permissionString) {
        this.permissionString = permissionString;
    }

    public void clearActions() {
        this.actions = null;
        this.actionsStringFromConfig = "";
    }

    public String getPrincipalName() {
        return principal;
    }

    public void setPrincipal(String principal) {
        this.principal = principal;
    }

    public String getJcrPath() {
        return jcrPath;
    }

    public void setJcrPath(String jcrPath) {
        this.jcrPath = jcrPath;
    }

    public boolean isAllow() {
        return isAllow;
    }

    public String getPermission() {
        return (this.isAllow) ? "allow" : "deny";
    }

    public boolean getPermissionAsBoolean() {
        return (this.isAllow) ? true : false;
    }

    public void setAllow(boolean isAllow) {
        this.isAllow = isAllow;
    }

    public void setIsAllowProvide(boolean bool) {
        this.isAllowProvided = bool;
    }

    public boolean getIsAllowProvide() {
        return this.isAllowProvided;
    }

    public String getRepGlob() {
        if (this.repGlob == null) {
            return "";
        }
        return repGlob;
    }

    public void setRepGlob(String repGlob) {
        if (repGlob == null) {
            repGlob = "";
        }
        this.repGlob = repGlob;
    }

    public String getActionsString() {
        if (this.actions != null) {
            StringBuilder sb = new StringBuilder();
            for (String action : this.actions) {
                sb.append(action).append(",");
            }
            return StringUtils.chomp(sb.toString(), ",");
        }
        return "";
    }

    public String getActionsStringFromConfig() {
        return this.actionsStringFromConfig;
    }

    public void setActions(String[] actions) {
        this.actions = actions;
    }

    public void setActionsStringFromConfig(String actionsString) {
        this.actionsStringFromConfig = actionsString;
    }

    public String[] getActions() {
        return this.actions;
    }

    public String getPrivilegesString() {
        return privilegesString;
    }

    public String[] getPrivileges() {
        if (StringUtils.isNotBlank(privilegesString)) {
            return privilegesString.split(",");
        }
        return null;
    }

    public void setPrivilegesString(String privilegesString) {
        this.privilegesString = privilegesString;
    }

    @Override
    public String toString() {
        return "AceBean [jcrPath=" + jcrPath + "\n" + ", isAllow=" + isAllow
                + "\n" + ", repGlob=" + repGlob + "\n" + ", actionsString="
                + actionsStringFromConfig + "\n" + ", privilegesString="
                + privilegesString + "\n" + ", principal=" + principal + "\n"
                + ", isAllowProvided=" + isAllowProvided + "\n" + ", actions="
                + Arrays.toString(actions) + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(actions);
        result = prime
                * result
                + ((actionsStringFromConfig == null) ? 0
                        : actionsStringFromConfig.hashCode());
        result = prime * result + (isAllow ? 1231 : 1237);
        result = prime * result + (isAllowProvided ? 1231 : 1237);
        result = prime * result + ((jcrPath == null) ? 0 : jcrPath.hashCode());
        result = prime * result
                + ((principal == null) ? 0 : principal.hashCode());
        result = prime
                * result
                + ((privilegesString == null) ? 0 : privilegesString.hashCode());
        result = prime * result + ((repGlob == null) ? 0 : repGlob.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AceBean other = (AceBean) obj;
        if (!Arrays.equals(actions, other.actions))
            return false;
        if (actionsStringFromConfig == null) {
            if (other.actionsStringFromConfig != null)
                return false;
        } else if (!actionsStringFromConfig
                .equals(other.actionsStringFromConfig))
            return false;
        if (isAllow != other.isAllow)
            return false;
        if (isAllowProvided != other.isAllowProvided)
            return false;
        if (jcrPath == null) {
            if (other.jcrPath != null)
                return false;
        } else if (!jcrPath.equals(other.jcrPath))
            return false;
        if (principal == null) {
            if (other.principal != null)
                return false;
        } else if (!principal.equals(other.principal))
            return false;
        if (privilegesString == null) {
            if (other.privilegesString != null)
                return false;
        } else if (!privilegesString.equals(other.privilegesString))
            return false;
        if (repGlob == null) {
            if (other.repGlob != null)
                return false;
        } else if (!repGlob.equals(other.repGlob))
            return false;
        return true;
    }

    @Override
    public void accept(AcDumpElementVisitor acDumpElementVisitor) {
        acDumpElementVisitor.visit(this);
    }
}
