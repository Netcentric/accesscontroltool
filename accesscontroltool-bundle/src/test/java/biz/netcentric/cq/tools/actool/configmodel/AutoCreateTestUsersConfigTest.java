package biz.netcentric.cq.tools.actool.configmodel;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2024 Cognizant Netcentric
 * %%
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * #L%
 */

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.*;

import static biz.netcentric.cq.tools.actool.configmodel.AutoCreateTestUsersConfig.*;
import static org.junit.jupiter.api.Assertions.*;

class AutoCreateTestUsersConfigTest {

    @Test
    void shouldNotContainImpersonalizationAllowedFor() {
        Map<String, Object> configMap = initializeConfigMap(null);
        assertEquals(new ArrayList<>(), (new AutoCreateTestUsersConfig(configMap)).getImpersonationAllowedFor());
    }

    @Test()
    void shouldNotContainImpersonalizationAllowedFor2() {
        Map<String, Object> configMap = initializeConfigMap("invalidValue");
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new AutoCreateTestUsersConfig(configMap);
        });
        assertEquals("Property \"" + KEY_IMPERSONATION_ALLOWED_FOR + "\" must be a list", exception.getMessage());
    }
    @Test
    void shouldNotImpersonalizationAllowedFor() {
        Map<String, Object> map = initializeConfigMap(Arrays.asList("user1"));
        assertEquals(Arrays.asList("user1"), (new AutoCreateTestUsersConfig(map)).getImpersonationAllowedFor());
    }

    @NotNull
    private static Map<String, Object> initializeConfigMap(Object allowedFor) {
        Map<String, Object> map = new HashMap<>();
        map.put(KEY_PATH, "/");
        map.put(KEY_PREFIX, "prefix");
        map.put(KEY_CREATE_FOR_GROUP_NAMES_REG_EX, "");
        map.put(KEY_IMPERSONATION_ALLOWED_FOR, allowedFor);
        return map;
    }
}
