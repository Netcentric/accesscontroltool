/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.authorizableutils;

import java.util.HashSet;
import java.util.Set;

public class AuthorizableInstallationHistory {

    private Set<String> newCreatedAuthorizables = new HashSet<String>();
    private Set<AuthorizableBean> authorizables = new HashSet<AuthorizableBean>();

    public void addAuthorizable(String name, String id, String path,
            Set<String> members) {
        authorizables.add(new AuthorizableBean(members, name, id, path));
    }

    public void addNewCreatedAuthorizable(String id) {
        this.newCreatedAuthorizables.add(id);
    }

    public Set<String> getNewCreatedAuthorizables() {
        return this.newCreatedAuthorizables;
    }

    public Set<AuthorizableBean> getAuthorizableBeans() {
        return this.authorizables;

    }
}
