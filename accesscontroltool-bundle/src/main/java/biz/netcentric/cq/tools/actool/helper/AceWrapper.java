/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.helper;

import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.security.Privilege;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlEntry;

/** Wraps an {@link JackrabbitAccessControlEntry} and stores an additional path information. Created and used during the reading of ACEs
 * from a system, in order to create an ACE Dump.
 *
 * @author jochenkoschorke */
public class AceWrapper {
    private final JackrabbitAccessControlEntry ace;
    private final String jcrPath;

    public AceWrapper(JackrabbitAccessControlEntry ace, String jcrPath) {
        super();
        this.ace = ace;
        this.jcrPath = jcrPath;
    }

    public String getJcrPath() {
        return this.jcrPath;
    }

    public JackrabbitAccessControlEntry getAce() {
        return ace;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((this.ace == null) ? 0 : this.ace.hashCode());
        result = prime * result + ((this.jcrPath == null) ? 0 : this.jcrPath.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final AceWrapper other = (AceWrapper) obj;
        if (this.ace == null) {
            if (other.ace != null) {
                return false;
            }
        } else if (!this.ace.equals(other.ace)) {
            return false;
        }
        if (this.jcrPath == null) {
            if (other.jcrPath != null) {
                return false;
            }
        } else if (!this.jcrPath.equals(other.jcrPath)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "AceWrapper [ace=" + this.ace + ", jcrPath=" + this.jcrPath + "]";
    }

    public String getRestrictionAsString(String name)
            throws RepositoryException {
        final Value val = this.ace.getRestriction(name);
        if (val != null) {
            return this.ace.getRestriction(name)
                    .getString();
        }
        // fix for #11: All ACE from groups not contained in a config get an empty repGlob after installation
        return null;
    }

    public String getPrivilegesString() {
        final Privilege[] privileges = this.ace.getPrivileges();
        String privilegesString = "";
        for (final Privilege privilege : privileges) {
            privilegesString = privilegesString + privilege.getName() + ",";
        }
        privilegesString = StringUtils.chop(privilegesString);
        return privilegesString;
    }

}
