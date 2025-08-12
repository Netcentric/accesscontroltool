package biz.netcentric.cq.tools.actool.comparators;

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
