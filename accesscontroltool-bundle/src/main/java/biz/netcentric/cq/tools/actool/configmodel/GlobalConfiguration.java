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
    public static final String KEY_INSTALL_ACLS_INCREMENTALLY = "installAclsIncrementally";

    public static final String KEY_ALLOW_EXTERNAL_GROUP_NAMES_REGEX_OBSOLETE = "allowExternalGroupNamesRegEx";

    public static final String KEY_DEFAULT_UNMANAGED_EXTERNAL_ISMEMBEROF_REGEX = "defaultUnmanagedExternalIsMemberOfRegex";
    public static final String KEY_DEFAULT_UNMANAGED_EXTERNAL_MEMBERS_REGEX = "defaultUnmanagedExternalMembersRegex";

    @Deprecated
    public static final String KEY_KEEP_EXISTING_MEMBERSHIPS_FOR_GROUP_NAMES_REGEX = "keepExistingMembershipsForGroupNamesRegEx";

    private String minRequiredVersion;
    private boolean installAclsIncrementally = true;

    private Pattern defaultUnmanagedExternalIsMemberOfRegex;
    private Pattern defaultUnmanagedExternalMembersRegex;

    public GlobalConfiguration() {
    }

    public GlobalConfiguration(Map<String, ?> globalConfigMap) {

        if (globalConfigMap != null) {
            if(globalConfigMap.get(KEY_ALLOW_EXTERNAL_GROUP_NAMES_REGEX_OBSOLETE)!=null) {
                throw new IllegalArgumentException("Configuration property " + KEY_ALLOW_EXTERNAL_GROUP_NAMES_REGEX_OBSOLETE
                        + " was renamed to " + KEY_KEEP_EXISTING_MEMBERSHIPS_FOR_GROUP_NAMES_REGEX
                        + " (since v2.0.0) - please adjust your configuration.");
                
            }
            
            setDefaultUnmanagedExternalIsMemberOfRegex((String) globalConfigMap.get(KEY_DEFAULT_UNMANAGED_EXTERNAL_ISMEMBEROF_REGEX));
            setDefaultUnmanagedExternalMembersRegex((String) globalConfigMap.get(KEY_DEFAULT_UNMANAGED_EXTERNAL_MEMBERS_REGEX));

            String keepExistingMembershipsForGroupNamesRegEx = (String) globalConfigMap
                    .get(KEY_KEEP_EXISTING_MEMBERSHIPS_FOR_GROUP_NAMES_REGEX);
            if (StringUtils.isNotBlank(keepExistingMembershipsForGroupNamesRegEx)) {
                if (defaultUnmanagedExternalIsMemberOfRegex == null && defaultUnmanagedExternalMembersRegex == null) {
                    setDefaultUnmanagedExternalIsMemberOfRegex(keepExistingMembershipsForGroupNamesRegEx);
                    setDefaultUnmanagedExternalMembersRegex(keepExistingMembershipsForGroupNamesRegEx);
                } else {
                    throw new IllegalArgumentException("Deprecated " + KEY_KEEP_EXISTING_MEMBERSHIPS_FOR_GROUP_NAMES_REGEX
                            + " cannot be used together with " + KEY_DEFAULT_UNMANAGED_EXTERNAL_ISMEMBEROF_REGEX + " or "
                            + KEY_DEFAULT_UNMANAGED_EXTERNAL_MEMBERS_REGEX);
                }
            }


            setMinRequiredVersion((String) globalConfigMap.get(KEY_MIN_REQUIRED_VERSION));
            if (globalConfigMap.containsKey(KEY_INSTALL_ACLS_INCREMENTALLY)) {
                setInstallAclsIncrementally(Boolean.valueOf(globalConfigMap.get(KEY_INSTALL_ACLS_INCREMENTALLY).toString()));
            }
        }

    }

    public void merge(GlobalConfiguration otherGlobalConfig) {

        if (otherGlobalConfig.getDefaultUnmanagedExternalIsMemberOfRegex() != null) {
            if (defaultUnmanagedExternalIsMemberOfRegex == null) {
                defaultUnmanagedExternalIsMemberOfRegex = otherGlobalConfig.getDefaultUnmanagedExternalIsMemberOfRegex();
            } else {
                throw new IllegalArgumentException("Duplicate config for " + KEY_DEFAULT_UNMANAGED_EXTERNAL_ISMEMBEROF_REGEX);
            }
        }
        if (otherGlobalConfig.getDefaultUnmanagedExternalMembersRegex() != null) {
            if (defaultUnmanagedExternalMembersRegex == null) {
                defaultUnmanagedExternalMembersRegex = otherGlobalConfig.getDefaultUnmanagedExternalMembersRegex();
            } else {
                throw new IllegalArgumentException("Duplicate config for " + KEY_DEFAULT_UNMANAGED_EXTERNAL_MEMBERS_REGEX);
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

    public Pattern getDefaultUnmanagedExternalIsMemberOfRegex() {
        return defaultUnmanagedExternalIsMemberOfRegex;
    }

    public void setDefaultUnmanagedExternalIsMemberOfRegex(String defaultUnmanagedExternalIsMemberOfRegex) {
        this.defaultUnmanagedExternalIsMemberOfRegex = stringToRegex(defaultUnmanagedExternalIsMemberOfRegex);
    }


    public Pattern getDefaultUnmanagedExternalMembersRegex() {
        return defaultUnmanagedExternalMembersRegex;
    }

    public void setDefaultUnmanagedExternalMembersRegex(String defaultUnmanagedExternalMembersRegex) {
        this.defaultUnmanagedExternalMembersRegex = stringToRegex(defaultUnmanagedExternalMembersRegex);
    }

    private Pattern stringToRegex(String regex) {
        return StringUtils.isNotBlank(regex) ? Pattern.compile(regex) : null;
    }

}
