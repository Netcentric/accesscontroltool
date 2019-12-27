/*
 * (C) Copyright 2017 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configmodel;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

/** User and group part of the AC Tool configuration (natural ordered set of AuthorizableConfigBeans). */
public class AuthorizablesConfig extends LinkedHashSet<AuthorizableConfigBean> {
    private static final long serialVersionUID = -253685832563496002L;

    public AuthorizableConfigBean getAuthorizableConfig(String authorizableId) {
        for (AuthorizableConfigBean authorizableConfigBean : this) {
            if (StringUtils.equals(authorizableConfigBean.getAuthorizableId(), authorizableId)) {
                return authorizableConfigBean;
            }
        }
        return null;
    }

    public AuthorizableConfigBean getAuthorizableConfigByPrincipalName(String principalName) {
        for (AuthorizableConfigBean authorizableConfigBean : this) {
            if (StringUtils.equals(authorizableConfigBean.getPrincipalName(), principalName)) {
                return authorizableConfigBean;
            }
        }
        return null;
    }

    public Set<String> getAuthorizableIds() {
        Set<String> authorizableIdsFromConfigurations = new LinkedHashSet<String>();
        for (AuthorizableConfigBean authorizableConfigBean : this) {
            authorizableIdsFromConfigurations.add(authorizableConfigBean.getAuthorizableId());
        }
        return authorizableIdsFromConfigurations;
    }

    public Set<String> getPrincipalNames() {
        Set<String> principals = new HashSet<String>();
        for (AuthorizableConfigBean authorizableConfigBean : this) {
            principals.add(authorizableConfigBean.getPrincipalName());
        }
        return principals;
    }

    public String getPrincipalNameForAuthorizableId(String authorizableId) {
        String principalName = null;
        for (AuthorizableConfigBean authorizableConfigBean : this) {
            if (StringUtils.equals(authorizableConfigBean.getAuthorizableId(), authorizableId)) {
                principalName = authorizableConfigBean.getPrincipalName();
                break;
            }
        }
        return principalName;
    }

    public Set<String> removeUnmanagedPrincipalNamesAtPath(String path, Set<String> principals, String defaultUnmanagedAcePathsRegex) {

        Set<String> filteredPrincipals = new HashSet<String>();
        for (String principal : principals) {
            AuthorizableConfigBean authorizableConfig = getAuthorizableConfigByPrincipalName(principal);
            if (authorizableConfig == null /* happens if migrateFrom is used, #290 */
                    || authorizableConfig.managesPath(path, defaultUnmanagedAcePathsRegex)) {
                filteredPrincipals.add(principal);
            }
        }

        return filteredPrincipals;
    }

}
