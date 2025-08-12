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

import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;

public class AuthorizableBeanIDComparator implements
        Comparator<AuthorizableConfigBean> {

    @Override
    public int compare(AuthorizableConfigBean bean1,
            AuthorizableConfigBean bean2) {
        if (bean1.getAuthorizableId().compareTo(bean2.getAuthorizableId()) > 1) {
            return 1;
        } else if (bean1.getAuthorizableId().compareTo(bean2.getAuthorizableId()) < 1) {
            return -1;
        } else if (bean1.getAuthorizableId().compareTo(bean2.getAuthorizableId()) == 0) {
            return 1;
        }
        return 1;
    }

}
