package biz.netcentric.cq.tools.actool.helper;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2024 Cognizant Netcentric
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

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

    public static final String OBSOLETE_AUTHORIZABLES_KEY = "obsolete_authorizables";

    public static final Set<String> VALID_CONFIG_SECTION_IDENTIFIERS = new HashSet<String>(Arrays.asList(
            GLOBAL_CONFIGURATION_KEY,
            GROUP_CONFIGURATION_KEY,
            USER_CONFIGURATION_KEY,
            ACE_CONFIGURATION_KEY,
            OBSOLETE_AUTHORIZABLES_KEY));

    /**
     * Default user ID for the anonymous user in Oak/JR2.
     * Defined for Oak in {@code org.apache.jackrabbit.oak.spi.security.user.UserConstants.DEFAULT_ANONYMOUS_ID}.
     */
    public static final String USER_ANONYMOUS = "anonymous";
    /**
     * Principal name for the everyone principal in Oak/JR2.
     */
    public static final String PRINCIPAL_EVERYONE = "everyone";
    
    public static final String GROUPS_ROOT = "/home/groups";
    public static final String USERS_ROOT = "/home/users";

    public static final String REPO_POLICY_NODE = "rep:repoPolicy";

}
