/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.helper;

import static biz.netcentric.cq.tools.actool.history.AcInstallationLog.msHumanReadable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.jcr.AccessDeniedException;
import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.query.InvalidQueryException;
import javax.jcr.query.Query;
import javax.jcr.query.QueryResult;
import javax.jcr.security.AccessControlList;
import javax.jcr.security.AccessControlManager;

import org.apache.commons.lang.time.StopWatch;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueryHelper {

    public static final Logger LOG = LoggerFactory.getLogger(QueryHelper.class);

    /** Method that returns a set containing all rep:policy nodes from repository excluding those contained in paths which are excluded from
     * search
     * 
     * @param session the jcr session
     * @param excludePaths paths which are excluded from search
     * @return all rep:policy nodes delivered by query */
    public static Set<Node> getRepPolicyNodes(final Session session,
            final List<String> excludePaths) {
        NodeIterator nodeIt = null;
        try {
            nodeIt = session.getRootNode().getNodes();
        } catch (RepositoryException e) {
            AcHelper.LOG.error("Exception: {}", e);
        }

        Set<String> paths = new TreeSet<String>();
        while (nodeIt.hasNext()) {
            String currentPath = null;
            Node currentNode = nodeIt.nextNode();
            try {
                currentPath = currentNode.getPath();
            } catch (RepositoryException e) {
                AcHelper.LOG.error("Exception: {}", e);
            }

            try {
                if (!currentNode.hasProperty("rep:AuthorizableFolder")) {
                    if (!excludePaths.contains(currentPath)) {
                        paths.add(currentPath);
                    }
                }
            } catch (RepositoryException e) {
                AcHelper.LOG.error("Exception: {}", e);
            }
        }
        Set<Node> nodes = new LinkedHashSet<Node>();
        try {
            // get the rep:policy node of "/", if existing
            if (session.nodeExists("/rep:policy")) {
                nodes.add(session.getNode("/rep:policy"));
            }
            if (session.nodeExists("/" + Constants.REPO_POLICY_NODE)) {
                nodes.add(session.getNode("/" + Constants.REPO_POLICY_NODE));
            }

            // get the rep:policy node of "/home", if existing
            if (session.nodeExists("/home/rep:policy")) {
                nodes.add(session.getNode("/home/rep:policy"));
            }

            for (String path : paths) {
                String query = "SELECT * FROM [rep:ACL] WHERE ISDESCENDANTNODE([" + path + "])";
                nodes.addAll(QueryHelper.getNodes(session, query, Query.JCR_SQL2));
            }
        } catch (InvalidQueryException e) {
            AcHelper.LOG.error("InvalidQueryException: {}", e);
        } catch (RepositoryException e) {
            AcHelper.LOG.error("RepositoryException: {}", e);
        }
        return nodes;
    }

    /** Get Nodes with XPATH Query. */
    public static Set<Node> getNodes(final Session session,
            final String xpathQuery) throws InvalidQueryException,
            RepositoryException {
        Set<Node> nodes = getNodes(session, xpathQuery, Query.XPATH);
        return nodes;
    }

    /** @param session the jcr session
     * @param queryStatement - ex. "SELECT * FROM [rep:ACL]"
     * @param queryLanguageType - ex. Query.JCR_SQL2 */
    public static Set<Node> getNodes(final Session session,
            final String queryStatement, String queryLanguageType) throws InvalidQueryException,
            RepositoryException {
        Set<Node> nodes = new HashSet<Node>();

        Query query = session.getWorkspace().getQueryManager()
                .createQuery(queryStatement, queryLanguageType);
        QueryResult queryResult = query.execute();
        NodeIterator nit = queryResult.getNodes();
        List<String> paths = new ArrayList<String>();

        while (nit.hasNext()) {
            // get the next rep:policy node
            Node node = nit.nextNode();
            // AcHelper.LOG.debug("adding node: {} to node set", node.getPath());
            paths.add(node.getPath());
            nodes.add(node);
        }
        return nodes;
    }

    public static Set<AclBean> getAuthorizablesAcls(final Session session,
            final Set<String> authorizableIds, Set<String> principalIdsToBeFilled) throws InvalidQueryException,
            RepositoryException {
        
        LOG.debug("Querying AclBeans for {} authorizables", authorizableIds.size());

        StopWatch sw = new StopWatch();
        sw.start();
        
        Set<Node> nodeSet = new LinkedHashSet<Node>();

        Iterator<String> authorizablesIdIterator = authorizableIds.iterator();


        while (authorizablesIdIterator.hasNext()) {
            StringBuilder queryStringBuilder = new StringBuilder();
            queryStringBuilder.append(
                    "SELECT ace.* FROM [rep:ACE] AS ace INNER JOIN [rep:Authorizable] AS authorizable "
                            + "ON ace.[rep:principalName] = authorizable.[rep:principalName] WHERE ");
            queryStringBuilder.append(getAuthorizablesQueryStringBuilder(authorizablesIdIterator, 100));

            String query = queryStringBuilder.toString();

            Set<Node> resultNodes = getNodes(session, query, Query.JCR_SQL2);
            LOG.trace("Querying AclBeans with {} returned {} results", query, resultNodes.size());
            nodeSet.addAll(resultNodes);
        }
        Set<AclBean> resultBeans = buildAclBeansFromNodeSet(session, nodeSet, principalIdsToBeFilled);

        sw.stop();
        LOG.debug("Found {} AclBeans in {}", resultBeans.size(), msHumanReadable(sw.getTime()));

        return resultBeans;
    }

    private static Set<AclBean> buildAclBeansFromNodeSet(final Session session,
            Set<Node> nodeSet, Set<String> principalIdsToBeFilled) throws UnsupportedRepositoryOperationException,
            RepositoryException, PathNotFoundException, AccessDeniedException,
            ItemNotFoundException {
        AccessControlManager aMgr = session.getAccessControlManager();
        AccessControlList acl;
        Set<AclBean> aclSet = new TreeSet<AclBean>(); // use natural ordering
        for (Node allowOrDenyNode : nodeSet) {
            String principalId = allowOrDenyNode.getProperty("rep:principalName").getValue().getString();
            principalIdsToBeFilled.add(principalId);
            Node aclNode = allowOrDenyNode.getParent();
            String aclEffectiveOnPath;
            String jcrPathAcl;

            if (!Constants.REPO_POLICY_NODE.equals(aclNode.getName())) {
                // default
                aclEffectiveOnPath = aclNode.getParent().getPath();
                jcrPathAcl = aclNode.getPath();
            } else {
                // repo policy
                aclEffectiveOnPath = null;
                jcrPathAcl = "/" + Constants.REPO_POLICY_NODE;
            }

            acl = (AccessControlList) aMgr.getPolicies(aclEffectiveOnPath)[0];
            if (acl == null) {
                LOG.warn("Path " + aclEffectiveOnPath + " unexpectedly does not have a ACL");
                continue;
            }
            AclBean aclBean = new AclBean();
            aclBean.setParentPath(aclEffectiveOnPath);
            aclBean.setAcl((JackrabbitAccessControlList) acl);
            aclBean.setJcrPath(jcrPathAcl);
            aclSet.add(aclBean);
        }
        return aclSet;
    }

    private static StringBuilder getAuthorizablesQueryStringBuilder(final Iterator<String> authorizablesIdIterator,
            final int authorizbalesLimitPerQuery) {
        int authorizableCounter = 0;
        StringBuilder querySb = new StringBuilder();

        if (!authorizablesIdIterator.hasNext()) {
            return querySb;
        }
        while (true) {
            querySb.append("authorizable.[rep:authorizableId] = '" + authorizablesIdIterator.next() + "'");
            authorizableCounter++;
            if (authorizableCounter < authorizbalesLimitPerQuery && authorizablesIdIterator.hasNext()) {
                querySb.append(" or ");
            } else {
                return querySb;
            }
        }
    }

}
