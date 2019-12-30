/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.helper;

import static biz.netcentric.cq.tools.actool.helper.AcHelper.ACE_ORDER_ACTOOL_BEST_PRACTICE;
import static biz.netcentric.cq.tools.actool.helper.AcHelper.ACE_ORDER_NONE;
import static org.apache.commons.lang3.StringUtils.rightPad;
import static org.junit.Assert.assertEquals;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.configmodel.AceBean;


public class AcHelperTest {
    public static final Logger LOG = LoggerFactory.getLogger(AcHelperTest.class);


    AceBean aceBeanGroupA_1_content_deny;
    AceBean aceBeanGroupA_2_content_allow;
    AceBean aceBeanGroupA_3_contentisp_deny;
    AceBean aceBeanGroupB_1_content_allow;
    AceBean aceBeanGroupB_2_content_deny;
    AceBean aceBeanGroupB_3_contentisp_allow;
    AceBean aceBeanGroupC_1_content_allow;
    AceBean aceBeanGroupC_2_content_deny_keepOrder;
    AceBean aceBeanGroupC_3_content_deny;

    @Before
    public void setup() {
        // ACEs from groups contained in config

        aceBeanGroupA_1_content_deny = new AceBean();
        aceBeanGroupA_1_content_deny.setPrincipalName("group-A");
        aceBeanGroupA_1_content_deny.setActions(new String[] { "read", "replicate" });
        aceBeanGroupA_1_content_deny.setPermission("deny");
        aceBeanGroupA_1_content_deny.setJcrPath("/content");
        aceBeanGroupA_1_content_deny.setPrivilegesString("");

        aceBeanGroupA_2_content_allow = new AceBean();
        aceBeanGroupA_2_content_allow.setPrincipalName("group-A");
        aceBeanGroupA_2_content_allow.setActions(new String[] { "read,modify" });
        aceBeanGroupA_2_content_allow.setPermission("allow");
        aceBeanGroupA_2_content_allow.setJcrPath("/content");
        aceBeanGroupA_2_content_allow.setPrivilegesString("");

        aceBeanGroupA_3_contentisp_deny = new AceBean();
        aceBeanGroupA_3_contentisp_deny.setPrincipalName("group-A");
        aceBeanGroupA_3_contentisp_deny.setActions(new String[] { "read,modify" });
        aceBeanGroupA_3_contentisp_deny.setPermission("deny");
        aceBeanGroupA_3_contentisp_deny.setJcrPath("/content/isp");
        aceBeanGroupA_3_contentisp_deny.setPrivilegesString("");

        aceBeanGroupB_1_content_allow = new AceBean();
        aceBeanGroupB_1_content_allow.setPrincipalName("group-B");
        aceBeanGroupB_1_content_allow.setActions(new String[] { "read" });
        aceBeanGroupB_1_content_allow.setPermission("allow");
        aceBeanGroupB_1_content_allow.setJcrPath("/content");
        aceBeanGroupB_1_content_allow.setPrivilegesString("");

        aceBeanGroupB_2_content_deny = new AceBean();
        aceBeanGroupB_2_content_deny.setPrincipalName("group-B");
        aceBeanGroupB_2_content_deny.setActions(new String[] { "read,delete" });
        aceBeanGroupB_2_content_deny.setPermission("deny");
        aceBeanGroupB_2_content_deny.setJcrPath("/content");
        aceBeanGroupB_2_content_deny.setPrivilegesString("");

        aceBeanGroupB_3_contentisp_allow = new AceBean();
        aceBeanGroupB_3_contentisp_allow.setPrincipalName("group-B");
        aceBeanGroupB_3_contentisp_allow.setActions(new String[] { "read,delete" });
        aceBeanGroupB_3_contentisp_allow.setPermission("allow");
        aceBeanGroupB_3_contentisp_allow.setJcrPath("/content/isp");
        aceBeanGroupB_3_contentisp_allow.setPrivilegesString("");

        aceBeanGroupC_1_content_allow = new AceBean();
        aceBeanGroupC_1_content_allow.setPrincipalName("group-C");
        aceBeanGroupC_1_content_allow.setActions(new String[] { "read" });
        aceBeanGroupC_1_content_allow.setPermission("allow");
        aceBeanGroupC_1_content_allow.setJcrPath("/content");
        aceBeanGroupC_1_content_allow.setPrivilegesString("");

        aceBeanGroupC_2_content_deny_keepOrder = new AceBean();
        aceBeanGroupC_2_content_deny_keepOrder.setPrincipalName("group-C");
        aceBeanGroupC_2_content_deny_keepOrder.setActions(new String[] { "read" });
        aceBeanGroupC_2_content_deny_keepOrder.setPermission("deny");
        aceBeanGroupC_2_content_deny_keepOrder.setJcrPath("/content");
        aceBeanGroupC_2_content_deny_keepOrder.setPrivilegesString("");
        aceBeanGroupC_2_content_deny_keepOrder.setKeepOrder(true);

        aceBeanGroupC_3_content_deny = new AceBean();
        aceBeanGroupC_3_content_deny.setPrincipalName("group-C");
        aceBeanGroupC_3_content_deny.setActions(new String[] { "modify" });
        aceBeanGroupC_3_content_deny.setPermission("deny");
        aceBeanGroupC_3_content_deny.setJcrPath("/content");
        aceBeanGroupC_3_content_deny.setPrivilegesString("");
        aceBeanGroupC_3_content_deny.setKeepOrder(false);

    }

    @Test
    public void getPathBasedAceMapUsingNaturalOrderedSetTest() {

        // case 1: natural ordered Set

        Map<String, Set<AceBean>> groupBasedAceMap = new LinkedHashMap<String, Set<AceBean>>();
        Map<String, Set<AceBean>> expectedPathBasedAceMap = new LinkedHashMap<String, Set<AceBean>>();

        // group based map
        groupBasedAceMap.put("group-A", new LinkedHashSet<AceBean>());
        groupBasedAceMap.get("group-A").add(aceBeanGroupA_1_content_deny);
        groupBasedAceMap.get("group-A").add(aceBeanGroupA_2_content_allow);
        groupBasedAceMap.get("group-A").add(aceBeanGroupA_3_contentisp_deny);

        groupBasedAceMap.put("group-B", new LinkedHashSet<AceBean>());
        groupBasedAceMap.get("group-B").add(aceBeanGroupB_1_content_allow);
        groupBasedAceMap.get("group-B").add(aceBeanGroupB_2_content_deny);
        groupBasedAceMap.get("group-B").add(aceBeanGroupB_3_contentisp_allow);

        groupBasedAceMap.put("group-C", new LinkedHashSet<AceBean>());
        groupBasedAceMap.get("group-C").add(aceBeanGroupC_1_content_allow);

        // expected result map (unordered)
        expectedPathBasedAceMap.put("/content", new LinkedHashSet<AceBean>());

        expectedPathBasedAceMap.get("/content").add(aceBeanGroupA_1_content_deny);
        expectedPathBasedAceMap.get("/content").add(aceBeanGroupA_2_content_allow);
        expectedPathBasedAceMap.get("/content").add(aceBeanGroupB_1_content_allow);
        expectedPathBasedAceMap.get("/content").add(aceBeanGroupB_2_content_deny);
        expectedPathBasedAceMap.get("/content").add(aceBeanGroupC_1_content_allow);

        expectedPathBasedAceMap.put("/content/isp", new LinkedHashSet<AceBean>());
        expectedPathBasedAceMap.get("/content/isp").add(aceBeanGroupA_3_contentisp_deny);
        expectedPathBasedAceMap.get("/content/isp").add(aceBeanGroupB_3_contentisp_allow);

        Map<String, Set<AceBean>> actualResult = AcHelper.getPathBasedAceMap(groupBasedAceMap, ACE_ORDER_NONE);

        assertEqualStructures(expectedPathBasedAceMap, actualResult);

    }

    @Test
    public void getPathBasedAceMapUsingOrderedSetTest() {

        // case 2: ordered Set (deny-ACEs before allow ACEs)

        Map<String, Set<AceBean>> groupBasedAceMap = new LinkedHashMap<String, Set<AceBean>>();
        Map<String, Set<AceBean>> expectedPathBasedAceMap = new LinkedHashMap<String, Set<AceBean>>();

        groupBasedAceMap.put("group-A", new LinkedHashSet<AceBean>());
        groupBasedAceMap.get("group-A").add(aceBeanGroupA_1_content_deny);
        groupBasedAceMap.get("group-A").add(aceBeanGroupA_2_content_allow);
        groupBasedAceMap.get("group-A").add(aceBeanGroupA_3_contentisp_deny);

        groupBasedAceMap.put("group-B", new LinkedHashSet<AceBean>());
        groupBasedAceMap.get("group-B").add(aceBeanGroupB_1_content_allow);
        groupBasedAceMap.get("group-B").add(aceBeanGroupB_3_contentisp_allow);
        groupBasedAceMap.get("group-B").add(aceBeanGroupB_2_content_deny);

        groupBasedAceMap.put("group-C", new LinkedHashSet<AceBean>());
        groupBasedAceMap.get("group-C").add(aceBeanGroupC_1_content_allow);

        expectedPathBasedAceMap.put("/content", new LinkedHashSet<AceBean>());
        expectedPathBasedAceMap.get("/content").add(aceBeanGroupA_1_content_deny);
        expectedPathBasedAceMap.get("/content").add(aceBeanGroupB_2_content_deny);
        expectedPathBasedAceMap.get("/content").add(aceBeanGroupA_2_content_allow);
        expectedPathBasedAceMap.get("/content").add(aceBeanGroupB_1_content_allow);
        expectedPathBasedAceMap.get("/content").add(aceBeanGroupC_1_content_allow);

        expectedPathBasedAceMap.put("/content/isp", new LinkedHashSet<AceBean>());
        expectedPathBasedAceMap.get("/content/isp").add(aceBeanGroupA_3_contentisp_deny);
        expectedPathBasedAceMap.get("/content/isp").add(aceBeanGroupB_3_contentisp_allow);

        Map<String, Set<AceBean>> actualResult = AcHelper.getPathBasedAceMap(
                groupBasedAceMap, ACE_ORDER_ACTOOL_BEST_PRACTICE);

        assertEqualStructures(expectedPathBasedAceMap, actualResult);

    }

    @Test
    public void getPathBasedAceMapUsingOrderedSetWithKeepOrderEntryTest() {

        // case 3: ordered Set (deny-ACEs before allow ACEs, but one deny with keep order)

        Map<String, Set<AceBean>> groupBasedAceMap = new LinkedHashMap<String, Set<AceBean>>();
        Map<String, Set<AceBean>> expectedPathBasedAceMap = new LinkedHashMap<String, Set<AceBean>>();

        groupBasedAceMap.put("group-A", new LinkedHashSet<AceBean>());
        groupBasedAceMap.get("group-A").add(aceBeanGroupA_1_content_deny);
        groupBasedAceMap.get("group-A").add(aceBeanGroupA_2_content_allow);
        groupBasedAceMap.get("group-A").add(aceBeanGroupA_3_contentisp_deny);

        groupBasedAceMap.put("group-B", new LinkedHashSet<AceBean>());
        groupBasedAceMap.get("group-B").add(aceBeanGroupB_1_content_allow);
        groupBasedAceMap.get("group-B").add(aceBeanGroupB_3_contentisp_allow);
        groupBasedAceMap.get("group-B").add(aceBeanGroupB_2_content_deny);

        groupBasedAceMap.put("group-C", new LinkedHashSet<AceBean>());
        groupBasedAceMap.get("group-C").add(aceBeanGroupC_1_content_allow);
        groupBasedAceMap.get("group-C").add(aceBeanGroupC_2_content_deny_keepOrder);
        groupBasedAceMap.get("group-C").add(aceBeanGroupC_3_content_deny);

        expectedPathBasedAceMap.put("/content", new LinkedHashSet<AceBean>());
        expectedPathBasedAceMap.get("/content").add(aceBeanGroupA_1_content_deny);
        expectedPathBasedAceMap.get("/content").add(aceBeanGroupB_2_content_deny);
        expectedPathBasedAceMap.get("/content").add(aceBeanGroupC_3_content_deny);
        expectedPathBasedAceMap.get("/content").add(aceBeanGroupA_2_content_allow);
        expectedPathBasedAceMap.get("/content").add(aceBeanGroupB_1_content_allow);
        expectedPathBasedAceMap.get("/content").add(aceBeanGroupC_1_content_allow);
        // this entry stays at natural position as listed in source file
        expectedPathBasedAceMap.get("/content").add(aceBeanGroupC_2_content_deny_keepOrder);

        expectedPathBasedAceMap.put("/content/isp", new LinkedHashSet<AceBean>());
        expectedPathBasedAceMap.get("/content/isp").add(aceBeanGroupA_3_contentisp_deny);
        expectedPathBasedAceMap.get("/content/isp").add(aceBeanGroupB_3_contentisp_allow);

        Map<String, Set<AceBean>> actualResult = AcHelper.getPathBasedAceMap(
                groupBasedAceMap, ACE_ORDER_ACTOOL_BEST_PRACTICE);

        assertEqualStructures(expectedPathBasedAceMap, actualResult);

    }

    private void assertEqualStructures(Map<String, Set<AceBean>> expectedPathBasedAceMap, Map<String, Set<AceBean>> actualResult) {
        try {
            assertEquals(
                    structureToComparableString(expectedPathBasedAceMap),
                    structureToComparableString(actualResult));
        } catch (AssertionError e) {
            // ensure readable log in case of assertion error
            printStructure("Expected Result", expectedPathBasedAceMap);
            printStructure("Actual Result", actualResult);
            throw e;
        }
    }

    private void printStructure(String description, Map<String, Set<AceBean>> structure) {
        LOG.info(description + "\n" + structureToComparableString(structure));
    }

    private String structureToComparableString(Map<String, Set<AceBean>> structure) {
        StringBuilder sb = new StringBuilder();
        for (String key : structure.keySet()) {
            Set<AceBean> set = structure.get(key);
            for (AceBean aceBean : set) {
                sb.append(rightPad(key + ":", 15) + " ( " + rightPad(aceBean.getJcrPath(), 15)
                        + " " + rightPad(aceBean.getAuthorizableId(), 10)
                        + " " + rightPad(aceBean.getPermission(), 5) +
                        "  " + Integer.toHexString(aceBean.hashCode()) + ")\n");
            }
        }
        return sb.toString();
    }
}
