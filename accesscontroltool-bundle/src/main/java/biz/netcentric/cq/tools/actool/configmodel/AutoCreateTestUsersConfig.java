/*
 * (C) Copyright 2018 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configmodel;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

/** Allows to automatically create test users. */
public class AutoCreateTestUsersConfig {

    private static final String KEY_PREFIX = "prefix";
    private static final String KEY_NAME = "name";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_SKIP_FOR_RUNMODES = "skipForRunmodes";
    private static final String KEY_CREATE_FOR_GROUP_NAMES_REG_EX = "createForGroupNamesRegEx";
    private static final String KEY_PATH = "path";

    private static final List<String> DEFAULT_PRODUCTION_RUNMODES = Arrays.asList("prod", "production");

    private final String prefix;
    private final String name;
    private final String description;
    private final String email;
    private final String password;
    private final List<String> skipForRunmodes;
    private final String createForGroupNamesRegEx;
    private final String path;

    public AutoCreateTestUsersConfig(Map map) {
        if (!map.containsKey(KEY_PREFIX)) {
            throw new IllegalArgumentException(
                    "When configuring " + GlobalConfiguration.KEY_AUTOCREATE_TEST_USERS + ", " + KEY_PREFIX + " is required");
        }
        this.prefix = (String) map.get(KEY_PREFIX);

        this.name = StringUtils.defaultIfEmpty((String) map.get(KEY_NAME), null);
        this.description = StringUtils.defaultIfEmpty((String) map.get(KEY_DESCRIPTION), null);
        this.email = StringUtils.defaultIfEmpty((String) map.get(KEY_EMAIL), null);
        this.password = StringUtils.defaultIfEmpty((String) map.get(KEY_PASSWORD), null);

        Object skipForRunmodesObj = map.get(KEY_SKIP_FOR_RUNMODES);
        if (skipForRunmodesObj instanceof List) {
            this.skipForRunmodes = !CollectionUtils.isEmpty((List) skipForRunmodesObj) ? (List) skipForRunmodesObj
                    : DEFAULT_PRODUCTION_RUNMODES;
        } else {
            String[] skipForRunmodesBits = String.valueOf(skipForRunmodesObj).split(" *, *");
            this.skipForRunmodes = !ArrayUtils.isEmpty(skipForRunmodesBits) ? Arrays.asList(skipForRunmodesBits)
                    : DEFAULT_PRODUCTION_RUNMODES;
        }

        if (!map.containsKey(KEY_CREATE_FOR_GROUP_NAMES_REG_EX)) {
            throw new IllegalArgumentException(
                    "When configuring " + GlobalConfiguration.KEY_AUTOCREATE_TEST_USERS + ", " + KEY_CREATE_FOR_GROUP_NAMES_REG_EX
                            + " is required");
        }
        this.createForGroupNamesRegEx = String.valueOf(map.get(KEY_CREATE_FOR_GROUP_NAMES_REG_EX));
        if (!map.containsKey(KEY_PATH)) {
            throw new IllegalArgumentException(
                    "When configuring " + GlobalConfiguration.KEY_AUTOCREATE_TEST_USERS + ", " + KEY_PATH + " is required");
        }

        this.path = String.valueOf(map.get(KEY_PATH));
    }

    public String getPrefix() {
        return prefix;
    }

    public String getPassword() {
        return password;
    }

    public List<String> getSkipForRunmodes() {
        return skipForRunmodes;
    }

    public String getCreateForGroupNamesRegEx() {
        return createForGroupNamesRegEx;
    }

    public String getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getEmail() {
        return email;
    }
}
