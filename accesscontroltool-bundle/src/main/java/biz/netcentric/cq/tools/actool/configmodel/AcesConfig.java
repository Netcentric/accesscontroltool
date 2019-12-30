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
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;

/** "ace_config" part of the AC Tool configuration (natural ordered set of AceBeans). */
public class AcesConfig extends LinkedHashSet<AceBean> {
    private static final long serialVersionUID = -153685832563296002L;

    public Set<String> getJcrPaths() {
        Set<String> jcrPathsInAceConfig = new TreeSet<String>();
        for (AceBean aceBean : this) {
            String path = aceBean.getJcrPath();
            jcrPathsInAceConfig.add(path);
        }
        return jcrPathsInAceConfig;
    }

    public Set<AceBean> filterByAuthorizableId(String authId) {
        Set<AceBean> aclsFiltered = new LinkedHashSet<AceBean>();
        for (AceBean bean : this) {
            if (StringUtils.equals(authId, bean.getAuthorizableId())) {
                aclsFiltered.add(bean);
            }
        }
        return aclsFiltered;
    }

    public boolean containsPath(String jcrPath) {
        boolean result;
        Set<String> jcrPaths = getJcrPaths();
        if (StringUtils.isNotBlank(jcrPath)) {
            result = jcrPaths.contains(jcrPath);
        } else {
            // interpret null as empty string for repo permissions (the dump returns an empty string for repo perm.)
            result = jcrPaths.contains("");
        }
        return result;
    }
}
