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

/** Global configuration that applies to the overall access control configuration system. */
public class GlobalConfiguration {

    public static final String KEY_ALLOW_EXTERNAL_GROUP_NAMES_REGEX = "allowExternalGroupNamesRegEx";

    private Pattern allowExternalGroupNamesRegEx;

    public GlobalConfiguration() {
    }

    public GlobalConfiguration(Map<String, ?> globalConfigMap) {

        if (globalConfigMap != null) {
            setAllowExternalGroupNamesRegEx(String.valueOf(globalConfigMap.get(KEY_ALLOW_EXTERNAL_GROUP_NAMES_REGEX)));
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
        
    }

    public Pattern getAllowExternalGroupNamesRegEx() {
        return allowExternalGroupNamesRegEx;
    }

    public void setAllowExternalGroupNamesRegEx(String allowExternalGroupNamesRegEx) {
        this.allowExternalGroupNamesRegEx = StringUtils.isNotBlank(allowExternalGroupNamesRegEx)
                ? Pattern.compile(allowExternalGroupNamesRegEx) : null;
    }


}
