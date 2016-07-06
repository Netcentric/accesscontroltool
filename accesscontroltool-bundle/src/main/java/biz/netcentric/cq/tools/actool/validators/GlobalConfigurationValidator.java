/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.validators;

import org.osgi.framework.FrameworkUtil;

import biz.netcentric.cq.tools.actool.configmodel.GlobalConfiguration;

public class GlobalConfigurationValidator {

    public static void validate(GlobalConfiguration globalConfiguration) {

        if (globalConfiguration != null && globalConfiguration.getMinRequiredVersion() != null) {
            checkForValidVersion(globalConfiguration.getMinRequiredVersion());
        }

    }

    private static void checkForValidVersion(String configuredMinRequiredVersion) {
        String bundleVersion = FrameworkUtil.getBundle(GlobalConfigurationValidator.class).getVersion().toString();
        boolean isVersionValid = versionCompare(bundleVersion, configuredMinRequiredVersion) >= 0;
        if (!isVersionValid) {
            throw new IllegalArgumentException("AC Tool Version " + bundleVersion + " is too old for configuration (MinRequiredVersion="
                    + configuredMinRequiredVersion + ")");
        }
    }

    public static boolean versionIsNewerOrEqualTo(String str1, String str2) {
        return versionCompare(str1, str2) >= 0;
    }

    public static int versionCompare(String str1, String str2) {
        String[] vals1 = str1.split("\\.");
        String[] vals2 = str2.split("\\.");
        int i = 0;
        // set index to first non-equal ordinal or length of shortest version string
        while (i < vals1.length && i < vals2.length && vals1[i].equals(vals2[i])) {
            i++;
        }
        // compare first non-equal ordinal number
        if (i < vals1.length && i < vals2.length) {
            int diff;
            try {
                diff = Integer.valueOf(vals1[i]).compareTo(Integer.valueOf(vals2[i]));
            } catch (NumberFormatException e) {
                diff = vals1[i].compareTo(vals2[i]);
            }
            return Integer.signum(diff);
        }
        // the strings are equal or one string is a substring of the other
        // e.g. "1.2.3" = "1.2.3" or "1.2.3" < "1.2.3.4"
        return Integer.signum(vals1.length - vals2.length);
    }

}
