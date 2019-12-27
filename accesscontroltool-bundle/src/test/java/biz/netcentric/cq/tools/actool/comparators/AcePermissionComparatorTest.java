/*
 * (C) Copyright 2017 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.comparators;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import biz.netcentric.cq.tools.actool.configmodel.AceBean;

public class AcePermissionComparatorTest {

    @Test
    public void testReorderingWithoutKeepOrder() {

        // keep order if it is correct already
        assertEquals("no1-no2-no3-no4", toComparableString(toAutoSortedSet(
                getAceBean("no1", "deny", false),
                getAceBean("no2", "deny", false),
                getAceBean("no3", "allow", false),
                getAceBean("no4", "allow", false))));

        // only allows - order not changed
        assertEquals("no1-no2-no3", toComparableString(toAutoSortedSet(
                getAceBean("no1", "allow", false),
                getAceBean("no2", "allow", false),
                getAceBean("no3", "allow", false))));

        // one allow many denies - order changed
        assertEquals("no2-no3-no4-no1", toComparableString(toAutoSortedSet(
                getAceBean("no1", "allow", false),
                getAceBean("no2", "deny", false),
                getAceBean("no3", "deny", false),
                getAceBean("no4", "deny", false))));

        // one deny reorder
        assertEquals("no4-no1-no2-no3", toComparableString(toAutoSortedSet(
                getAceBean("no1", "allow", false),
                getAceBean("no2", "allow", false),
                getAceBean("no3", "allow", false),
                getAceBean("no4", "deny", false))));

        // many mixed ones
        assertEquals("no2-no4-no6-no8-no1-no3-no5-no7", toComparableString(toAutoSortedSet(
                getAceBean("no1", "allow", false),
                getAceBean("no2", "deny", false),
                getAceBean("no3", "allow", false),
                getAceBean("no4", "deny", false),
                getAceBean("no5", "allow", false),
                getAceBean("no6", "deny", false),
                getAceBean("no7", "allow", false),
                getAceBean("no8", "deny", false))));

    }

    @Test
    public void testReorderingWithKeepOrder() {

        // simple case - one order kept
        assertEquals("no2-no3-no1-no4", toComparableString(toAutoSortedSet(
                getAceBean("no1", "allow", false),
                getAceBean("no2", "deny", false),
                getAceBean("no3", "deny", false),
                getAceBean("no4", "deny", true))));

        // two item keep order
        assertEquals("no3-no1-no2-no4", toComparableString(toAutoSortedSet(
                getAceBean("no1", "allow", false),
                getAceBean("no2", "deny", true),
                getAceBean("no3", "deny", false),
                getAceBean("no4", "deny", true))));

        // all items keep order
        assertEquals("no1-no2-no3-no4", toComparableString(toAutoSortedSet(
                getAceBean("no1", "allow", true),
                getAceBean("no2", "deny", true),
                getAceBean("no3", "allow", true),
                getAceBean("no4", "deny", true))));

    }

    private Set<AceBean> toAutoSortedSet(AceBean... beans) {

        final Set<AceBean> orderedAceBeanSet = new TreeSet<AceBean>(new AcePermissionComparator());
        orderedAceBeanSet.addAll(Arrays.asList(beans));
        return orderedAceBeanSet;

    }

    private String toComparableString(Set<AceBean> beans) {
        List<String> ids = new ArrayList<String>();
        for (AceBean bean : beans) {
            ids.add(bean.getAuthorizableId());
        }
        return StringUtils.join(ids, "-");

    }

    private AceBean getAceBean(String id, String permission, boolean keepOrder) {
        AceBean aceBean = new AceBean();
        aceBean.setAuthorizableId(id);
        aceBean.setPermission(permission);
        aceBean.setKeepOrder(keepOrder);
        return aceBean;
    }

}
