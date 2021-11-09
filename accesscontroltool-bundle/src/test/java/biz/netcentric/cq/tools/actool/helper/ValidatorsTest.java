/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.helper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

import biz.netcentric.cq.tools.actool.aem.AcToolCqActions;
import biz.netcentric.cq.tools.actool.validators.Validators;

public class ValidatorsTest {

    @Test
    public void isValidAuthorizableNameTest() {

        assertTrue(Validators.isValidAuthorizableId("group-A"));
        assertTrue(Validators.isValidAuthorizableId("group_A"));
        assertTrue(Validators.isValidAuthorizableId("group.6"));
        assertTrue(Validators.isValidAuthorizableId("Group-1"));
        assertTrue(Validators.isValidAuthorizableId("Group-99"));
        assertTrue(Validators.isValidAuthorizableId("Group..9.9"));
        assertTrue(Validators.isValidAuthorizableId("group A"));
        assertTrue(Validators.isValidAuthorizableId("group -A"));

        assertTrue(Validators.isValidAuthorizableId("group,A"));
        assertTrue(Validators.isValidAuthorizableId("group:A"));
        assertTrue(Validators.isValidAuthorizableId("group;A"));
      
        // even unicode characters are fine
        assertTrue(Validators.isValidAuthorizableId("group-\\u00F8\\u00FCa"));
        // only empty string not allowed
        assertFalse(Validators.isValidAuthorizableId(""));
        assertFalse(Validators.isValidAuthorizableId(null));
    }

    @Test
    public void isValidActionTest() {
        List<String> actionStrings = Stream.of(AcToolCqActions.CqActions.values()).map(Enum::name).collect(Collectors.toList());

        for (String action : actionStrings) {
            assertTrue(Validators.isValidAction(action));
        }

        assertFalse(Validators.isValidAction("write"));
        assertFalse(Validators.isValidAction("Read"));
        assertFalse(Validators.isValidAction("aclEdit"));
        assertFalse(Validators.isValidAction("jcr:all"));
        assertFalse(Validators.isValidAction("jcr:read"));
        assertFalse(Validators.isValidAction(null));
    }

    @Test
    public void isValidPermissionTest() {

        assertTrue(Validators.isValidPermission("allow"));
        assertTrue(Validators.isValidPermission("deny"));

        assertFalse(Validators.isValidPermission("Allow"));
        assertFalse(Validators.isValidPermission("Deny"));
        assertFalse(Validators.isValidPermission("write"));

        assertFalse(Validators.isValidPermission(null));
    }

    @Test
    public void isValidRepGlobTest() {
        assertTrue(Validators.isValidRegex("*/jcr:content*"));
        assertTrue(Validators.isValidRegex("*/content/*"));
        assertFalse(Validators.isValidRegex("["));
    }
}