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
import java.util.HashSet;
import java.util.Set;

public class Constants {

    private Constants() {
    }

    public static final String GLOBAL_CONFIGURATION_KEY = "global_config";

    public static final String GROUP_CONFIGURATION_KEY = "group_config";
    public static final String USER_CONFIGURATION_KEY = "user_config";
    public static final String ACE_CONFIGURATION_KEY = "ace_config";
    public static final String PRIVILEGE_CONFIGURATION_KEY = "privilege_config";

    public static final String OBSOLETE_AUTHORIZABLES_KEY = "obsolete_authorizables";

    public static final Set<String> VALID_CONFIG_SECTION_IDENTIFIERS = new HashSet<String>(Arrays.asList(
            GLOBAL_CONFIGURATION_KEY,
            GROUP_CONFIGURATION_KEY,
            USER_CONFIGURATION_KEY,
            ACE_CONFIGURATION_KEY,
            OBSOLETE_AUTHORIZABLES_KEY,
            PRIVILEGE_CONFIGURATION_KEY));

    public static final String USER_ANONYMOUS = "anonymous";
    public static final String USER_AC_SERVICE = "actool";

    public static final String GROUPS_ROOT = "/home/groups";
    public static final String USERS_ROOT = "/home/users";

    public static final String REPO_POLICY_NODE = "rep:repoPolicy";

}
