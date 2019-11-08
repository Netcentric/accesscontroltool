/*
 * (C) Copyright 2017 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configmodel;

import java.util.LinkedHashSet;

public class PrivilegeConfig extends LinkedHashSet<PrivilegeBean> {
    private static final long serialVersionUID = -153685832563296002L;


    public void merge(PrivilegeConfig otherConfig) {
        for(PrivilegeBean bean : otherConfig){
            if(contains(bean)){
                throw new IllegalArgumentException("Duplicate privilege configuration for " + bean.getPrivilegeName());
            } else {
                add(bean);
            }
        }
    }
}