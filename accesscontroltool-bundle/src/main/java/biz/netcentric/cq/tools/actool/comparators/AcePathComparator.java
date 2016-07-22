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

import biz.netcentric.cq.tools.actool.configmodel.AceBean;

public class AcePathComparator implements Comparator<AceBean> {

    @Override
    public int compare(AceBean ace1, AceBean ace2) {
        if (ace1.getJcrPath().compareTo(ace2.getJcrPath()) > 1) {
            return 1;
        } else if (ace1.getJcrPath().compareTo(ace2.getJcrPath()) < 1) {
            return -1;
        } else if (ace1.getJcrPath().compareTo(ace2.getJcrPath()) == 0) {
            return 1;
        }
        return 1;
    }

}
