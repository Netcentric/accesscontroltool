/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.comparators;

import java.util.Comparator;

import org.apache.commons.lang.StringUtils;

import biz.netcentric.cq.tools.actool.configmodel.AceBean;

public class AcePermissionComparator implements Comparator<AceBean> {

    @Override
    public int compare(final AceBean ace1, final AceBean ace2) {
        if (StringUtils.equals(ace1.getPermission(), "allow")
                && StringUtils.equals(ace2.getPermission(), "deny")) {
            return 1;
        } else if (StringUtils.equals(ace1.getPermission(), "deny")
                && StringUtils.equals(ace2.getPermission(), "allow")) {
            return -1;
        }
        // if default return value was 0 no new entry would get added in case of
        // TreeSet, the result would be a Set containg extactly 2 elements
        // (one deny and one allow), therefore default value here is 1 this
        // ensures a grouping of ACEs in one block containing
        // all denies followed by a block containing all allows
        return 1;
    }

}
