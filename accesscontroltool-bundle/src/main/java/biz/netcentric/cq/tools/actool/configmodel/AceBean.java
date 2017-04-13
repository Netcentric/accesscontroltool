/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configmodel;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.security.util.CqActions;

import biz.netcentric.cq.tools.actool.dumpservice.AcDumpElement;
import biz.netcentric.cq.tools.actool.dumpservice.AcDumpElementVisitor;

/** @author jochenkoschorke This class is used to store data of an AcessControlEntry. Objects of this class get created during the reading
 *         of the configuration file in order to set the corresponding ACEs in the system on the one hand and to store data during the
 *         reading of existing ACEs before writing the data back to a dump or configuration file again on the other hand. */
public class AceBean implements AcDumpElement {

    public static final Logger LOG = LoggerFactory.getLogger(AceBean.class);

    private String jcrPath;
    private String actionsStringFromConfig;
    private String privilegesString;
    private String principal;
    private String permission;
    private String[] actions;
    private List<Restriction> restrictions = new ArrayList<Restriction>();

    private boolean keepOrder = false; // default is to reorder denies before allows

    private String initialContent;

    public static final String RESTRICTION_NAME_GLOB = "rep:glob";

    @Override
    public AceBean clone() {

        AceBean clone = new AceBean();
        clone.setJcrPath(jcrPath);
        clone.setActionsStringFromConfig(actionsStringFromConfig);
        clone.setPrivilegesString(privilegesString);
        clone.setPrincipal(principal);
        clone.setPermission(permission);
        clone.setActions(actions);
        clone.setRestrictions(new ArrayList<Restriction>(restrictions));
        clone.setInitialContent(initialContent);
        clone.setKeepOrder(keepOrder);

        return clone;

    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permissionString) {
        permission = permissionString;
    }

    public void clearActions() {
        actions = null;
        actionsStringFromConfig = "";
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

    public String getJcrPathForPolicyApi() {
        if (StringUtils.isBlank(jcrPath)) {
            return null; // repository level permission
        } else {
            return jcrPath;
        }
    }

    public void setJcrPath(String jcrPath) {
        this.jcrPath = jcrPath;
    }

    public boolean isAllow() {
        return "allow".equalsIgnoreCase(permission);
    }

    public List<Restriction> getRestrictions() {
        return restrictions;
    }

    public void setRestrictions(Object restrictionsRaw, String oldStyleRepGlob) {
        restrictions.clear();
        if (restrictionsRaw != null) {
            if (!(restrictionsRaw instanceof Map)) {
                throw new IllegalArgumentException("If 'restrictions' is provided for an AC entry, it needs to be a map.");
            }
            Map<String, ?> restrictionsMap = (Map<String, ?>) restrictionsRaw;
            for (final String key : restrictionsMap.keySet()) {
                final String value = (String) restrictionsMap.get(key);
                if (value == null) {
                    LOG.debug("Could not get value from restriction map using key: {}", key);
                    continue;
                }
                final String[] values = value.split(",");

                restrictions.add(new Restriction(key, values));
            }
        }

        if (oldStyleRepGlob != null) {
            if (containsRestriction(RESTRICTION_NAME_GLOB)) {
                throw new IllegalArgumentException("Usage of restrictions -> rep:glob and repGlob on top level cannot be mixed.");
            }
            restrictions.add(new Restriction(RESTRICTION_NAME_GLOB, oldStyleRepGlob));
        }


    }

    public boolean containsRestriction(String restrictionName) {
        for (Restriction currentRestriction : restrictions) {
            if (StringUtils.equals(currentRestriction.getName(), restrictionName)) {
                return true;
            }
        }
        return false;
    }

    public void setRestrictions(List<Restriction> restrictions) {
        this.restrictions = restrictions;
    }

    public String getRepGlob() {
        for (Restriction currentRestriction : restrictions) {
            if (StringUtils.equals(currentRestriction.getName(), RESTRICTION_NAME_GLOB)) {
                return currentRestriction.getValue();
            }
        }
        return null;
    }

    public String getActionsString() {
        if (actions != null) {
            final StringBuilder sb = new StringBuilder();
            for (final String action : actions) {
                sb.append(action).append(",");
            }
            return StringUtils.chomp(sb.toString(), ",");
        }
        return "";
    }

    public String getActionsStringFromConfig() {
        return actionsStringFromConfig;
    }

    public void setActions(String[] actions) {
        this.actions = actions;
    }

    public void setActionsStringFromConfig(String actionsString) {
        actionsStringFromConfig = actionsString;
    }

    public String[] getActions() {
        return actions;
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

    public String getInitialContent() {
        return initialContent;
    }

    public void setInitialContent(String initialContent) {
        this.initialContent = initialContent;
    }

    public boolean isKeepOrder() {
        return keepOrder;
    }

    public void setKeepOrder(boolean keepOrder) {
        this.keepOrder = keepOrder;
    }

    @Override
    public String toString() {
        return "AceBean [jcrPath=" + jcrPath + "\n" + ", actionsStringFromConfig=" + actionsStringFromConfig + "\n"
                + ", privilegesString=" + privilegesString + "\n" + ", principal=" + principal + "\n" + ", permission=" + permission
                + "\n, actions=" + Arrays.toString(actions) + "\n" + ", restrictions="
                + restrictions + "\n"
                + ", initialContent=" + initialContent + "]";
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = (prime * result) + Arrays.hashCode(actions);
        result = (prime * result) + ((actionsStringFromConfig == null) ? 0 : actionsStringFromConfig.hashCode());
        result = (prime * result) + ((initialContent == null) ? 0 : initialContent.hashCode());
        result = (prime * result) + ((jcrPath == null) ? 0 : jcrPath.hashCode());
        result = (prime * result) + ((permission == null) ? 0 : permission.hashCode());
        result = (prime * result) + ((principal == null) ? 0 : principal.hashCode());
        result = (prime * result) + ((privilegesString == null) ? 0 : privilegesString.hashCode());
        result = (prime * result) + ((restrictions == null) ? 0 : restrictions.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final AceBean other = (AceBean) obj;
        if (!Arrays.equals(actions, other.actions)) {
            return false;
        }
        if (actionsStringFromConfig == null) {
            if (other.actionsStringFromConfig != null) {
                return false;
            }
        } else if (!actionsStringFromConfig.equals(other.actionsStringFromConfig)) {
            return false;
        }
        if (initialContent == null) {
            if (other.initialContent != null) {
                return false;
            }
        } else if (!initialContent.equals(other.initialContent)) {
            return false;
        }
        if (jcrPath == null) {
            if (other.jcrPath != null) {
                return false;
            }
        } else if (!jcrPath.equals(other.jcrPath)) {
            return false;
        }
        if (permission == null) {
            if (other.permission != null) {
                return false;
            }
        } else if (!permission.equals(other.permission)) {
            return false;
        }
        if (principal == null) {
            if (other.principal != null) {
                return false;
            }
        } else if (!principal.equals(other.principal)) {
            return false;
        }
        if (privilegesString == null) {
            if (other.privilegesString != null) {
                return false;
            }
        } else if (!privilegesString.equals(other.privilegesString)) {
            return false;
        }
        if (restrictions == null) {
            if (other.restrictions != null) {
                return false;
            }
        } else if (!restrictions.equals(other.restrictions)) {
            return false;
        }
        return true;
    }

    @Override
    public void accept(AcDumpElementVisitor acDumpElementVisitor) {
        acDumpElementVisitor.visit(this);
    }

    /** Creates an action map being used in {@link CqActions#installActions(String, Principal, Map, Collection)} out of the set actions on
     * this bean.
     *
     * @return a map containing actions as keys and booleans representing {@code true} for allow and {@code false} for deny. */
    public Map<String, Boolean> getActionMap() {
        if (actions == null) {
            return Collections.emptyMap();
        }
        final Map<String, Boolean> actionMap = new HashMap<String, Boolean>();
        for (final String action : actions) {
            actionMap.put(action, isAllow());
        }
        return actionMap;
    }

    public boolean isInitialContentOnlyConfig() {
        return StringUtils.isNotBlank(initialContent)
                && StringUtils.isBlank(permission)
                && StringUtils.isBlank(privilegesString)
                && StringUtils.isBlank(actionsStringFromConfig);
    }

}
