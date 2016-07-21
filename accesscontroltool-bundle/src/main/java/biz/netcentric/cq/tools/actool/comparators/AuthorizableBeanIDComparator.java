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

import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;

public class AuthorizableBeanIDComparator implements
        Comparator<AuthorizableConfigBean> {

    @Override
    public int compare(AuthorizableConfigBean bean1,
            AuthorizableConfigBean bean2) {
        if (bean1.getPrincipalID().compareTo(bean2.getPrincipalID()) > 1) {
            return 1;
        } else if (bean1.getPrincipalID().compareTo(bean2.getPrincipalID()) < 1) {
            return -1;
        } else if (bean1.getPrincipalID().compareTo(bean2.getPrincipalID()) == 0) {
            return 1;
        }
        return 1;
    }

}
