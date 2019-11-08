/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configmodel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Bean representation of {@link javax.jcr.security.Privilege}
 */
public class PrivilegeBean {

    public static final Logger LOG = LoggerFactory.getLogger(PrivilegeBean.class);

    private String privilegeName;
    private boolean isAbstract;
    private String[] aggregateNames = {};

    /**
     * The name of the privilege.
     * Corresponds to the node name under /jcr:system/rep:privileges,
     * e.g. /jcr:system/rep:privileges/sling:feature
     */
    public String getPrivilegeName() {
        return privilegeName;
    }

    public void setPrivilegeName(String privilegeName) {
        this.privilegeName = privilegeName;
    }

    /**
     * @return <code>true</code> if this privilege is an abstract privilege;
     *         <code>false</code> otherwise.
     */
    public boolean isAbstract() {
        return isAbstract;
    }

    public void setAbstract(boolean anAbstract) {
        isAbstract = anAbstract;
    }

    /**
     * If this privilege is an aggregate privilege, returns the names of the
     * directly contained privileges. Otherwise returns an empty
     * array.
     *
     * @return an array of aggregate names
     */
    public String[] getAggregateNames() {
        return aggregateNames;
    }

    public void setAggregateNames(String[] aggregateNames) {
        this.aggregateNames = aggregateNames;
    }

    @Override
    public PrivilegeBean clone() {

        PrivilegeBean clone = new PrivilegeBean();
        clone.setAbstract(isAbstract);
        clone.setAggregateNames(aggregateNames);
        clone.setPrivilegeName(privilegeName);
        return clone;

    }

    // privileges are equal by name
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PrivilegeBean that = (PrivilegeBean) o;
        return privilegeName.equals(that.privilegeName);
    }

    // privileges are equal by name
    @Override
    public int hashCode() {
        return Objects.hash(privilegeName);
    }
}
