package biz.netcentric.cq.tools.actool.impl;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2025 Cognizant Netcentric
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.util.Objects;

import org.apache.jackrabbit.api.security.principal.JackrabbitPrincipal;

/**
 * Simple implementation of JackrabbitPrincipal based on a name only.
 */
public class SimpleNamePrincipal implements JackrabbitPrincipal {

    private final String name;
    
    public SimpleNamePrincipal(String name) {
        this.name = name;
    }
    
    @Override
    public String getName() {
        return name;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        SimpleNamePrincipal other = (SimpleNamePrincipal) obj;
        return Objects.equals(name, other.name);
    }

}
