/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.helper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFormatException;
import javax.jcr.security.AccessControlEntry;
import javax.jcr.security.AccessControlList;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlEntry;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.comparators.AcePermissionComparator;
import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.configmodel.Restriction;

public class AcHelper {
    public static final Logger LOG = LoggerFactory.getLogger(AcHelper.class);

    private AcHelper() {
    }

    /** By default ACEs with denies are sorted up to the top of the list, this follows the best practice to order denies always before
     * allows - this makes by default allows always take precedence over denies.
     *
     * Denies should be used sparsely: Normally there is exactly one group that includes all deny-ACEs for to-be-secured content and many
     * groups with allow-ACEs, that selectively allow what has been denied by the "global deny" group.
     *
     * For some special cases (e.g. when working with restrictions that limit a preceding allow) it is possible to specify "keepOrder=true",
     * for those cases the natural order from the config file is kept when {@link #ACE_ORDER_ACTOOL_BEST_PRACTICE} is used. */
    public static int ACE_ORDER_ACTOOL_BEST_PRACTICE = 1;

    /** Retains order of ACEs in ACLs. */
    public static int ACE_ORDER_NONE = 2;

    /** Sorts ACEs in ACLs alphabetical. */
    public static int ACE_ORDER_ALPHABETICAL = 3;

    public static int PRINCIPAL_BASED_ORDER = 1;
    public static int PATH_BASED_ORDER = 2;

    public static AceBean getAceBean(AccessControlEntry ace, AccessControlList acl) throws RepositoryException {
        AceWrapper aceWrapper = new AceWrapper((JackrabbitAccessControlEntry) ace, ( (JackrabbitAccessControlList)  acl).getPath());
        AceBean aceBean = AcHelper.getAceBean(aceWrapper);
        return aceBean;
    }

    public static AceBean getAceBean(final AceWrapper aceWrapper)
            throws IllegalStateException, RepositoryException {
        final AceBean aceBean = new AceBean();
        final JackrabbitAccessControlEntry ace = aceWrapper.getAce();

        aceBean.setPermission(ace.isAllow() ? "allow" : "deny");
        aceBean.setJcrPath(aceWrapper.getJcrPath());
        aceBean.setPrincipalName(ace.getPrincipal().getName());
        aceBean.setPrivilegesString(aceWrapper.getPrivilegesString());

        List<Restriction> restrictions = buildRestrictionsMap(ace);
        aceBean.setRestrictions(restrictions);
        return aceBean;
    }

    private static List<Restriction> buildRestrictionsMap(final JackrabbitAccessControlEntry ace) throws RepositoryException {
        final String[] restrictionNames = ace.getRestrictionNames();
        final List<Restriction> restrictionsList = new ArrayList<Restriction>();
        for (final String restrictionName : restrictionNames) {
            final Value[] values = ace.getRestrictions(restrictionName);
            String[] strValues = new String[values.length];
            for (int i = 0; i < strValues.length; i++) {
                strValues[i] = values[i].getString();
            }
            restrictionsList.add(new Restriction(restrictionName, strValues));
        }
        return restrictionsList;
    }

    public static String getBlankString(final int nrOfBlanks) {
        return StringUtils.repeat(" ", nrOfBlanks);
    }

    public static Map<String, Set<AceBean>> getPathBasedAceMap(Set<AceBean> aceBeansFromConfig, int sorting) {
        final Map<String, Set<AceBean>> pathBasedAceMap = new TreeMap<String, Set<AceBean>>();

        for (final AceBean bean : aceBeansFromConfig) {

            // if there isn't already a path key in pathBasedAceMap create a
            // new one and add new Set
            // with current ACE as first entry
            if (pathBasedAceMap.get(bean.getJcrPath()) == null) {

                Set<AceBean> aceSet = null;
                if (sorting == AcHelper.ACE_ORDER_NONE) {
                    aceSet = new LinkedHashSet<AceBean>();
                } else if (sorting == AcHelper.ACE_ORDER_ACTOOL_BEST_PRACTICE) {
                    aceSet = new TreeSet<AceBean>(new AcePermissionComparator());
                }

                aceSet.add(bean);
                pathBasedAceMap.put(bean.getJcrPath(), aceSet);
                // add current ACE to Set
            } else {
                pathBasedAceMap.get(bean.getJcrPath()).add(bean);
            }
        }

        return pathBasedAceMap;
    }

    /** changes a group based ACE map into a path based ACE map
     *
     * @param groupBasedAceMap the group based ace map
     * @param sorting specifies whether ACEs get sorted by permissions (all denies followed by all allows)
     * @return the path based ace map */
    public static Map<String, Set<AceBean>> getPathBasedAceMap(
            final Map<String, Set<AceBean>> groupBasedAceMap, final int sorting) {
        final Map<String, Set<AceBean>> pathBasedAceMap = new TreeMap<String, Set<AceBean>>();

        // loop through all Sets of groupBasedAceMap
        for (final Entry<String, Set<AceBean>> entry : groupBasedAceMap.entrySet()) {
            final String principal = entry.getKey();

            // get current Set of current principal
            final Set<AceBean> tmpSet = entry.getValue();

            for (final AceBean bean : tmpSet) {

                // set current principal
                bean.setPrincipalName(principal);

                // if there isn't already a path key in pathBasedAceMap create a
                // new one and add new Set
                // with current ACE as first entry
                if (pathBasedAceMap.get(bean.getJcrPath()) == null) {

                    Set<AceBean> aceSet = null;
                    if (sorting == AcHelper.ACE_ORDER_NONE) {
                        aceSet = new LinkedHashSet<AceBean>();
                    } else if (sorting == AcHelper.ACE_ORDER_ACTOOL_BEST_PRACTICE) {
                        aceSet = new TreeSet<AceBean>(new AcePermissionComparator());
                    }

                    aceSet.add(bean);
                    pathBasedAceMap.put(bean.getJcrPath(), aceSet);
                    // add current ACE to Set
                } else {
                    pathBasedAceMap.get(bean.getJcrPath()).add(bean);
                }
            }
        }
        return pathBasedAceMap;
    }

    public static String valuesToString(Value[] propertyValues) throws RepositoryException {
        if (propertyValues == null) {
            return null;
        } else if (propertyValues.length == 0) {
            return null;
        } else if (propertyValues.length == 1) {
            return propertyValues[0].getString();
        } else {
            throw new IllegalArgumentException(
                    "Unexpectedly received more than one value for a property that is expected to be non-multiple");
        }
    }


}
