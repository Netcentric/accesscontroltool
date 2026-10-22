package biz.netcentric.cq.tools.actool.configmodel;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import biz.netcentric.cq.tools.actool.configreader.YamlConfigReader;

@ExtendWith(MockitoExtension.class)
public class IgnoreMissingPrincipalsTest {

    @Test
    public void testIgnoreMissingPrincipalsConfigurationParsing() throws Exception {
        
        // Test creating GlobalConfiguration from Map
        Map<String, Object> globalConfigMap = new LinkedHashMap<>();
        globalConfigMap.put("ignoreMissingPrincipals", true);
        
        GlobalConfiguration globalConfig = new GlobalConfiguration(globalConfigMap);
        
        // Verify ignoreMissingPrincipals is properly parsed
        assertTrue(globalConfig.getIgnoreMissingPrincipals());
    }
    
    @Test
    public void testIgnoreMissingPrincipalsDefaultValue() {
        GlobalConfiguration globalConfig = new GlobalConfiguration();
        
        // By default, ignoreMissingPrincipals should be null (not set)
        assertEquals(null, globalConfig.getIgnoreMissingPrincipals());
    }
    
    @Test
    public void testIgnoreMissingPrincipalsConfigurationMerging() {
        GlobalConfiguration config1 = new GlobalConfiguration();
        config1.setIgnoreMissingPrincipals(true);
        
        GlobalConfiguration config2 = new GlobalConfiguration();
        
        config2.merge(config1);
        
        assertTrue(config2.getIgnoreMissingPrincipals());
    }
    
    @Test
    public void testIgnoreMissingPrincipalsConfigurationSetterGetter() {
        GlobalConfiguration globalConfig = new GlobalConfiguration();
        
        globalConfig.setIgnoreMissingPrincipals(false);
        assertFalse(globalConfig.getIgnoreMissingPrincipals());
        
        globalConfig.setIgnoreMissingPrincipals(true);
        assertTrue(globalConfig.getIgnoreMissingPrincipals());
    }
}