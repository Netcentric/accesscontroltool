/*
 * (C) Copyright 2016 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configmodel;

import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import biz.netcentric.cq.tools.actool.validators.GlobalConfigurationValidator;

/** Global configuration that applies to the overall access control configuration system. */
public class GlobalConfiguration {

    public static final String KEY_MIN_REQUIRED_VERSION = "minRequiredVersion";
    public static final String KEY_ALLOW_EXTERNAL_GROUP_NAMES_REGEX = "allowExternalGroupNamesRegEx";
    public static final String KEY_INSTALL_ACLS_INCREMENTALLY = "installAclsIncrementally";

    private Pattern allowExternalGroupNamesRegEx;
    private String minRequiredVersion;
    private boolean installAclsIncrementally = true;

    public GlobalConfiguration() {
    }

    public GlobalConfiguration(Map<String, ?> globalConfigMap) {

        if (globalConfigMap != null) {
            setAllowExternalGroupNamesRegEx((String) globalConfigMap.get(KEY_ALLOW_EXTERNAL_GROUP_NAMES_REGEX));
            setMinRequiredVersion((String) globalConfigMap.get(KEY_MIN_REQUIRED_VERSION));
            if (globalConfigMap.containsKey(KEY_INSTALL_ACLS_INCREMENTALLY)) {
                setInstallAclsIncrementally(Boolean.valueOf(globalConfigMap.get(KEY_INSTALL_ACLS_INCREMENTALLY).toString()));
            }
        }

    }

    public void merge(GlobalConfiguration otherGlobalConfig) {

        if (otherGlobalConfig.getAllowExternalGroupNamesRegEx() != null) {
            if (allowExternalGroupNamesRegEx == null) {
                allowExternalGroupNamesRegEx = otherGlobalConfig.getAllowExternalGroupNamesRegEx();
            } else {
                throw new IllegalArgumentException("Duplicate config for " + KEY_ALLOW_EXTERNAL_GROUP_NAMES_REGEX);
            }
        }
        
        if (otherGlobalConfig.getMinRequiredVersion() != null) {
            if (minRequiredVersion == null) {
                minRequiredVersion = otherGlobalConfig.getMinRequiredVersion();
            } else {
                minRequiredVersion = GlobalConfigurationValidator.versionCompare(otherGlobalConfig.getMinRequiredVersion(), minRequiredVersion) > 0
                        ? otherGlobalConfig.getMinRequiredVersion() : minRequiredVersion;
            }
        }

        // default is true, if false is configured anywhere that value is used
        if (!otherGlobalConfig.installAclsIncrementally) {
            installAclsIncrementally = false;
        }

    }

    public Pattern getAllowExternalGroupNamesRegEx() {
        return allowExternalGroupNamesRegEx;
    }

    public void setAllowExternalGroupNamesRegEx(String allowExternalGroupNamesRegEx) {
        this.allowExternalGroupNamesRegEx = StringUtils.isNotBlank(allowExternalGroupNamesRegEx)
                ? Pattern.compile(allowExternalGroupNamesRegEx) : null;
    }

    public String getMinRequiredVersion() {
        return minRequiredVersion;
    }

    public void setMinRequiredVersion(String requiredMinVersion) {
        this.minRequiredVersion = requiredMinVersion;
    }

    public boolean getInstallAclsIncrementally() {
        return installAclsIncrementally;
    }

    public void setInstallAclsIncrementally(boolean installAclsIncrementally) {
        this.installAclsIncrementally = installAclsIncrementally;
    }

}
