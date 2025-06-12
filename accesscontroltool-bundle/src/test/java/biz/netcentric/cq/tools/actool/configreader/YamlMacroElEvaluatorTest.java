package biz.netcentric.cq.tools.actool.configreader;

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
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.el.ELException;

class YamlMacroElEvaluatorTest {
    private YamlMacroElEvaluator elEvaluator;
    
    @BeforeEach
    public void setUp() {
        elEvaluator = new YamlMacroElEvaluator();
    }

    @Test
    void testFunctions() {
        assertEquals(true, evaluateSimpleExpression("isBlank(\"\")"));
        assertEquals(true, evaluateSimpleExpression("isBlank(\"      \")"));
        assertEquals(true, evaluateSimpleExpression("isEmpty(\"\")"));
        assertEquals(false, evaluateSimpleExpression("isEmpty(\"      \")"));
        assertEquals("bread&amp;butter", evaluateSimpleExpression("escapeXml(\"bread&butter\")"));
        assertEquals("Test", evaluateSimpleExpression("capitalize(\"test\")"));
        assertEquals("item1,item2", evaluateSimpleExpression("join(var1, \",\")", Collections.singletonMap("var1", new Object[] {"item1", "item2"})));
        assertEquals("foo", evaluateSimpleExpression("defaultIfBlank(\"    \",\"foo\")"));
        assertEquals("bar", evaluateSimpleExpression("defaultIfBlank(\"bar\",\"foo\")"));

        Map<String,Object> lists= ImmutableMap.of("list1",Arrays.asList("foo","bar"), "list2",Arrays.asList("fizz","buzz"));
        assertIterableEquals(Arrays.asList("foo","bar","fizz","buzz"), (Iterable)evaluateSimpleExpression("union(list1,list2)",lists));
        assertIterableEquals(Arrays.asList("item1"),(Iterable) evaluateSimpleExpression("keys(list)",Collections.singletonMap("list", ImmutableMap.of("item1","value"))));

    }

    @Test
    void testNonExistingFunction() {
        assertThrows(ELException.class, () -> evaluateSimpleExpression("invalid(\"test\")"));
    }

    @Test
    void testSyntaxErrpr() {
        assertThrows(ELException.class, () -> evaluateSimpleExpression("invalid(\"test\""));
    }

    private Object evaluateSimpleExpression(String expression) {
        return evaluateSimpleExpression(expression, Collections.emptyMap());
    }

    private Object evaluateSimpleExpression(String expression, Map<String, Object> variables) {
        return elEvaluator.evaluateEl("${" + expression + "}", Object.class, variables);
    }
}
