/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.authorizableutils.impl;

import java.security.Principal;

import org.apache.jackrabbit.api.security.principal.JackrabbitPrincipal;

/**
 * Principal implementation that matches the Apache Jackrabbit one. Required to
 * be able to create a {@link Principal} ourselves, as it is required for
 * creating users with pre-defined intermediate path.
 * 
 * @author thomas.hartmann
 */
public class PrincipalImpl implements Principal, JackrabbitPrincipal {

    private final String name;

    public PrincipalImpl(final String name) {
        if ((name == null) || (name.length() == 0)) {
            throw new IllegalArgumentException(
                    "Principal name can neither be null nor empty String.");
        }
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }

    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Principal) {
            return this.name.equals(((Principal) obj).getName());
        }
        return false;
    }

    public int hashCode() {
        return this.name.hashCode();
    }

    public String toString() {
        return super.getClass().getName() + ":" + this.name;
    }
}
