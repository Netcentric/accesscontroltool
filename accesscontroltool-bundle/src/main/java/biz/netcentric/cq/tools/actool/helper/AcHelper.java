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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.jcr.AccessDeniedException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.Value;
import javax.jcr.ValueFormatException;
import javax.jcr.query.InvalidQueryException;
import javax.jcr.query.Query;
import javax.jcr.query.QueryResult;

import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.configmodel.Restriction;
import biz.netcentric.cq.tools.actool.dumpservice.Dumpservice;

public class AcHelper {
    public static final Logger LOG = LoggerFactory.getLogger(AcHelper.class);

    private AcHelper() {
    }


    public static int ACE_ORDER_DENY_ALLOW = 1;
    public static int ACE_ORDER_NONE = 2;
    public static int ACE_ORDER_ALPHABETICAL = 3;

    public static int PRINCIPAL_BASED_ORDER = 1;
    public static int PATH_BASED_ORDER = 2;

    public static AceBean getAceBean(final AceWrapper ace)
            throws ValueFormatException, IllegalStateException,
            RepositoryException {
        final AceBean aceBean = new AceBean();

        aceBean.setPermission(ace.isAllow() ? "allow" : "deny");
        aceBean.setJcrPath(ace.getJcrPath());
        aceBean.setPrincipal(ace.getPrincipal().getName());
        aceBean.setPrivilegesString(ace.getPrivilegesString());

        List<Restriction> restrictions = buildRestrictionsMap(ace);
        aceBean.setRestrictions(restrictions);
        return aceBean;
    }

    private static List<Restriction> buildRestrictionsMap(final AceWrapper ace) throws RepositoryException, ValueFormatException {
        final String[] restrictionNames = ace.getRestrictionNames();
        final List<Restriction> restrictionsList = new ArrayList<Restriction>();
        for(final String restrictionName : restrictionNames){
            final Value[] values = ace.getRestrictions(restrictionName);
            String[] strValues = new String[values.length];
            for(int i=0;i<values.length;i++) {
                strValues[i] = values[i].getString();
            }
            restrictionsList.add(new Restriction(restrictionName, strValues));
        }
        return restrictionsList;
    }

    public static String getBlankString(final int nrOfBlanks) {
        return StringUtils.repeat(" ", nrOfBlanks);
    }



    /** Method that searches a group by nodename or by ldap attribute 'cn' inside the rep:principal property of a group node. Serves as a
     * fallback, in case a group can't be resolved by principal manager by its name provided in config file after ldap import
     *
     * @param session
     * @param aceBean
     * @return found Principal or null
     * @throws InvalidQueryException
     * @throws RepositoryException */
    public static Principal getPrincipal(final Session session,
            final AceBean aceBean) throws InvalidQueryException,
    RepositoryException {
        Principal principal = null;
        final String principalName = aceBean.getPrincipalName();

        principal = getPrincipalForName(session, principalName);

        if (principal == null) {
            final String query = "/jcr:root/home/groups//*[(@jcr:primaryType = 'rep:Group') and jcr:like(@rep:principalName, 'cn="
                    + principalName + "%')]";
            LOG.debug("Fallback query did not return results for principalName={}, using second fallback query (ldap name): {}",
                    principalName, query);
            principal = getPrincipalByQuery(query, session);
        }

        LOG.debug("Returning {} for principal {}", principal, principalName);
        return principal;
    }

    private static Principal getPrincipalForName(final Session session, String principalName) throws AccessDeniedException,
    UnsupportedRepositoryOperationException, RepositoryException {
        Principal principal = null;
        // AEM 6.1 has potentially a delayed visibility of just created groups when using PrincipalManager, therefore using UserManager
        // Also see https://issues.apache.org/jira/browse/OAK-3228
        final JackrabbitSession js = (JackrabbitSession) session;
        final UserManager userManager = js.getUserManager();
        final Authorizable authorizable = userManager.getAuthorizable(principalName);
        principal = authorizable != null ? authorizable.getPrincipal() : null;
        return principal;
    }

    private static Principal getPrincipalByQuery(final String queryStringGroups, final Session session) throws InvalidQueryException,
    RepositoryException {

        final Query queryGroups = session.getWorkspace().getQueryManager().createQuery(queryStringGroups, Query.XPATH);
        final QueryResult queryResultGroups = queryGroups.execute();
        final NodeIterator nitGroups = queryResultGroups.getNodes();
        String principalName;
        if (!nitGroups.hasNext()) {
            LOG.debug("Executing query '{}' did not have any results", queryStringGroups);
            return null;
        }
        final Node node = nitGroups.nextNode();

        if (node.hasProperty("rep:principalName")) {
            principalName = node.getProperty("rep:principalName").getString();
            final Principal principal = getPrincipalForName(session, principalName);
            return principal;
        }
        LOG.debug("Group '{}' did not have a rep:principalName property", node.getPath());

        return null;
    }

    public static Map<String, Set<AceBean>> createAceMap(
            final SlingHttpServletRequest request, final int keyOrdering,
            final int aclOrdering, final String[] excludePaths,
            Dumpservice dumpservice) throws ValueFormatException,
    IllegalStateException, RepositoryException {
        final Session session = request.getResourceResolver().adaptTo(Session.class);
        return dumpservice.createAclDumpMap(session, keyOrdering,
                aclOrdering, excludePaths).getAceDump();
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
                bean.setPrincipal(principal);

                // if there isn't already a path key in pathBasedAceMap create a
                // new one and add new Set
                // with current ACE as first entry
                if (pathBasedAceMap.get(bean.getJcrPath()) == null) {

                    Set<AceBean> aceSet = null;
                    if (sorting == AcHelper.ACE_ORDER_NONE) {
                        aceSet = new LinkedHashSet<AceBean>();
                    } else if (sorting == AcHelper.ACE_ORDER_DENY_ALLOW) {
                        aceSet = new TreeSet<AceBean>(
                                new biz.netcentric.cq.tools.actool.comparators.AcePermissionComparator());
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


}
