/*
 * (C) Copyright 2023 Cognizant Netcentric.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.helper;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

public class QueryHelperTest {

    @Test
    public void testGetCostFromJsonScalar() throws JsonProcessingException, IOException {

        assertEquals(189540, QueryHelper.getCostFromJsonStr("{ \"s\": 189540.0 }"));
        assertEquals(1, QueryHelper.getCostFromJsonStr("{ \"s\": 1.0 }"));
    }

    @Test
    public void testGetCostFromJsonObject() throws JsonProcessingException, IOException {

        // the keys in sub object happen to be unquoted, the impl needs to use JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES
        assertEquals(1, QueryHelper.getCostFromJsonStr("{ \"s\": { perEntry: 1.0, perExecution: 1.0, count: 47814 } }"));
        assertEquals(189540, QueryHelper.getCostFromJsonStr("{ \"s\": { perEntry: 1.0, perExecution: 189540.0, count: 47814 } }"));

    }

    @Test
    public void testInvalidCostJson() throws JsonProcessingException, IOException {

        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> { 
                    QueryHelper.getCostFromJsonStr("{ \"s\": { someChangedFormat: 123 } }");
                },
                "Expected getCostFromJsonStr() to throw an IllegalArgumentException for invalid JSON"
         );
         assertEquals("Unexpected json structure for query cost: { \"s\": { someChangedFormat: 123 } }", thrown.getMessage());

    }
    
}
