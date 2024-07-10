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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;

public class TestUserConfigsCreatorTest {

    private static final String TEST_GROUP_ID = "test-group-id";
    private static final String TEST_GROUP_NAME = "Test Group Name";
    private static final String TEST_GROUP_PATH = "/home/groups/testfolder/subfolder";
    
    TestUserConfigsCreator testUserConfigsCreator = new TestUserConfigsCreator();
    
    @Test
    public void testBasicInterpolation() {

        Map<String, Object> testVars = getTestVars();
        assertEquals(TEST_GROUP_ID, testUserConfigsCreator.processValue("%{group.id}", testVars));
        assertEquals("Prefix "+ TEST_GROUP_NAME, testUserConfigsCreator.processValue("Prefix %{group.name}", testVars));
        assertEquals(TEST_GROUP_PATH, testUserConfigsCreator.processValue("%{group.path}", testVars));
    }
    
    @Test
    public void testValuesRemainUnchanged() {
        Map<String, Object> testVars = getTestVars();
        assertEquals("Simple Text", testUserConfigsCreator.processValue("Simple Text", testVars));
        assertEquals("{dd2a1c0550fa4750b353a61be823ccd3f8e525c3cdf8c7c359a717cc1785ebaf}", testUserConfigsCreator.processValue("{dd2a1c0550fa4750b353a61be823ccd3f8e525c3cdf8c7c359a717cc1785ebaf}", testVars));
    }

    @Test
    public void testGroupWithoutNameFallsBackToId() {
        assertEquals(TEST_GROUP_ID, testUserConfigsCreator.processValue("%{group.name}", getTestVars(null)));
    }

    @Test
    public void testELFunctions() {
        Map<String, Object> testVars = getTestVars();
        assertEquals("testfolder/subfolder", testUserConfigsCreator.processValue("%{split(group.path,'/')[2]}/%{split(group.path,'/')[3]}", testVars));
        assertEquals("group-id", testUserConfigsCreator.processValue("%{substringAfter(group.id,'-')}", testVars));
    }

    @Test
    public void testCapturingGroups() {
        Matcher matcher = Pattern.compile("prefix-(.*)").matcher("prefix-group");
        assertTrue(matcher.matches());
        assertEquals("group", testUserConfigsCreator.processValue("%{cg1}", getTestVars(TEST_GROUP_NAME, matcher)));
        assertEquals("prefix-group", testUserConfigsCreator.processValue("%{cg0}", getTestVars(TEST_GROUP_NAME, matcher)));
    }

    @Test
    public void testMultipleReplacements() {
        assertEquals("Name "+TEST_GROUP_NAME+" Path "+TEST_GROUP_PATH, testUserConfigsCreator.processValue("Name %{group.name} Path %{group.path}", getTestVars()));
    }

    private Map<String, Object> getTestVars() {
        return getTestVars(TEST_GROUP_NAME);
    }

    private Map<String, Object> getTestVars(String name) {
        Matcher matcher = Pattern.compile("").matcher("");
        matcher.matches();
        return getTestVars(name, matcher);
    }

    private Map<String, Object> getTestVars(String name, Matcher matcher) {
        AuthorizableConfigBean groupAuthConfigBean = new AuthorizableConfigBean();
        groupAuthConfigBean.setAuthorizableId(TEST_GROUP_ID);
        groupAuthConfigBean.setName(name);
        groupAuthConfigBean.setPath(TEST_GROUP_PATH);
        Map<String, Object> vars = new HashMap<>(testUserConfigsCreator.getVarsForAuthConfigBean(groupAuthConfigBean));
        vars.putAll(testUserConfigsCreator.getVarsForCapturedGroups(matcher));
        return vars;
    }

}
