/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.helper;

import java.security.Principal;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.security.AccessControlEntry;

import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;

public class AclBean implements Comparable<AclBean> {

    private String jcrPath;
    private String parentPath;
    private JackrabbitAccessControlList acl;

    public AclBean(JackrabbitAccessControlList acl, String jcrPath) {
        this.jcrPath = jcrPath;
        this.acl = acl;
    }

    public AclBean() {

    }

    public String getParentPath() {
        return parentPath;
    }

    public void setParentPath(String parentPath) {
        this.parentPath = parentPath;
    }


    public String getJcrPath() {
        return jcrPath;
    }

    public void setJcrPath(String jcrPath) {
        this.jcrPath = jcrPath;
    }

    public JackrabbitAccessControlList getAcl() {
        return acl;
    }

    public void setAcl(JackrabbitAccessControlList acl) {
        this.acl = acl;
    }

    public String[] getAces(JackrabbitAccessControlList acl)
            throws RepositoryException {
        return this.acl.getRestrictionNames();
    }

    public List<AccessControlEntry> getGroupBasedACEs(Session session,
            Principal principal)
            throws UnsupportedRepositoryOperationException, RepositoryException {
        List<AccessControlEntry> aceList = new ArrayList<AccessControlEntry>();
        if (this.acl != null) {
            AccessControlEntry[] aces = this.acl.getAccessControlEntries();
            for (AccessControlEntry ace : aces) {
                if (ace.getPrincipal().getName().equals(principal.getName())) {
                    aceList.add(ace);
                }
            }
        }
        return aceList;
    }

    @Override
    public String toString() {
        return "[AclBean " + this.jcrPath + " " + this.acl.toString() + "]";
    }

    @Override
    public boolean equals(Object obj) {
        return this.acl.equals(obj);
    }

    @Override
    public int compareTo(AclBean o) {
        if (o == null) {
            return -1;
        }
        String comparePath1 = StringUtils.defaultIfEmpty(getParentPath(), "");
        String comparePath2 = StringUtils.defaultIfEmpty(o.getParentPath(), "");
        return Collator.getInstance().compare(comparePath1, comparePath2);

    }

}
