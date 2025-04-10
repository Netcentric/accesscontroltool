package biz.netcentric.cq.tools.actool.api;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2025 Cognizant Netcentric
 * %%
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * #L%
 */

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class InstallationOptionsBuilderTest {

    @Test
    void testDefaultBuilder() {
        InstallationOptionsBuilder builder = new InstallationOptionsBuilder();
        InstallationOptions options = builder.build();
        assertFalse(options.getConfigurationRootPath().isPresent());
        assertTrue(options.getRestrictedToPaths().isEmpty());
        assertFalse(options.shouldSkipIfConfigUnchanged());
        assertFalse(options.shouldUpdateExistingExternalGroups());
    }

    @Test
    void testBuilderWithAllOptions() {
        String configRootPath = "/apps/actool/config";
        InstallationOptionsBuilder builder = new InstallationOptionsBuilder();
        builder.withConfigurationRootPath(configRootPath);
        Collection<String> restrictedToPaths = Arrays.asList("/content/site1", "/content/site2");
        builder.withRestrictedToPaths(restrictedToPaths);
        builder.skipIfConfigUnchanged();
        builder.updateExistingExternalGroups();
        InstallationOptions options = builder.build();
        assertEquals(Optional.of(configRootPath), options.getConfigurationRootPath());
        assertEquals(restrictedToPaths, options.getRestrictedToPaths());
        assertTrue(options.shouldSkipIfConfigUnchanged());
        assertTrue(options.shouldUpdateExistingExternalGroups());
        // test builder on top of existing options
        InstallationOptionsBuilder builder2 = new InstallationOptionsBuilder(options);
        InstallationOptions options2 = builder2.build();
        assertEquals(Optional.of(configRootPath), options2.getConfigurationRootPath());
        assertEquals(restrictedToPaths, options2.getRestrictedToPaths());
        assertTrue(options2.shouldSkipIfConfigUnchanged());
        assertTrue(options2.shouldUpdateExistingExternalGroups());
    }
    
    @Test
    void testConstructWithProperties() {
        InstallationOptionsBuilder builder = new InstallationOptionsBuilder();
        builder.withConfigurationRootPath("/apps/actool/config");
        builder.skipIfConfigUnchanged();
        InstallationOptions options = builder.build();
        Map<String, Object> properties = options.getPersistableProperties();
        InstallationOptionsBuilder builder2 = new InstallationOptionsBuilder(properties);
        assertEquals(options, builder2.build());
    }
}
