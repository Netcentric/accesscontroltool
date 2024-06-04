/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.helper;

import static biz.netcentric.cq.tools.actool.history.impl.PersistableInstallationLogger.msHumanReadable;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
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
import javax.jcr.query.Row;
import javax.jcr.security.AccessControlList;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.AccessControlPolicy;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class QueryHelper {
    public static final Logger LOG = LoggerFactory.getLogger(QueryHelper.class);

    private static final String ROOT_REP_POLICY_NODE = "/rep:policy";
    private static final String ROOT_REPO_POLICY_NODE = "/" + Constants.REPO_POLICY_NODE;
    private static final String HOME_REP_POLICY = "/home/rep:policy";

    /** every query cost below that threshold means a dedicated index exists, above that threshold means: fallback to traversal */
    private static final double COST_THRESHOLD_FOR_QUERY_INDEX = 100d;

    /** Method that returns a set containing all rep:policy nodes from repository excluding those contained in paths which are excluded from
     * search
     * 
     * @param session the JCR session
     * @param excludePaths paths which are excluded from search
     * @return all rep:policy nodes delivered by query */
    public static Set<String> getRepPolicyNodePaths(final Session session,
            final List<String> excludePaths) {
        NodeIterator nodeIt = null;
        try {
            nodeIt = session.getRootNode().getNodes();
        } catch (RepositoryException e) {
            LOG.error("Exception: {}", e);
        }

        Set<String> rootChildrenPaths = new TreeSet<String>();
        while (nodeIt.hasNext()) {
            String currentPath = null;
            Node currentNode = nodeIt.nextNode();
            try {
                currentPath = currentNode.getPath();
            } catch (RepositoryException e) {
                LOG.error("Exception: {}", e);
            }

            try {
                if (!currentNode.hasProperty("rep:AuthorizableFolder")) {
                    if (!excludePaths.contains(currentPath)) {
                        rootChildrenPaths.add(currentPath);
                    }
                }
            } catch (RepositoryException e) {
                LOG.error("Exception: {}", e);
            }
        }
        Set<String> paths = new HashSet<>();
        try {
            // get the rep:policy node of "/", if existing
            if (session.nodeExists(ROOT_REP_POLICY_NODE)) {
                paths.add(ROOT_REP_POLICY_NODE);
            }
            if (session.nodeExists(ROOT_REPO_POLICY_NODE)) {
                paths.add(ROOT_REPO_POLICY_NODE);
            }

            // get the rep:policy node of "/home", if existing
            if (session.nodeExists(HOME_REP_POLICY)) {
                paths.add(HOME_REP_POLICY);
            }

            boolean indexForRepACLExists = false;
            try {
                indexForRepACLExists = hasQueryIndexForACLs(session);
            } catch(IOException|RepositoryException e) {
                LOG.warn("Cannot figure out if query index for rep:ACL nodes exist", e);
            }
            LOG.debug("Index for repACL exists: {}",indexForRepACLExists);
            String queryForAClNodes = indexForRepACLExists ? 
                    "SELECT * FROM [rep:ACL] WHERE ISDESCENDANTNODE([%s])" : 
                    "SELECT ace.* FROM [rep:ACE] AS ace WHERE ace.[rep:principalName] IS NOT NULL AND ISDESCENDANTNODE(ace, [%s])";
            LOG.debug("Query to obtain all ACLs: {}", queryForAClNodes);
            
            for (String path : rootChildrenPaths) {
                if(StringUtils.equals(path, ROOT_REP_POLICY_NODE) || StringUtils.equals(path, ROOT_REPO_POLICY_NODE)) {
                    continue;
                }
                
                String query = String.format(queryForAClNodes, path);

                long startTime1 = System.currentTimeMillis();
                Set<String> nodesResult = indexForRepACLExists ? 
                        getNodePathsFromQuery(session, query, Query.JCR_SQL2):
                        getDistinctParentNodePathsFromQuery(session, query, Query.JCR_SQL2);
                LOG.debug("Query to find ACLs under {} ran in {}ms (count ACLs: {})", path, System.currentTimeMillis()-startTime1, nodesResult.size());
                paths.addAll(nodesResult);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not query repository for existing ACLs: "+e, e);
        }
        return paths;
    }

    static boolean hasQueryIndexForACLs(final Session session) throws RepositoryException, IOException {
        Query query = session.getWorkspace().getQueryManager().createQuery("EXPLAIN MEASURE SELECT * FROM [rep:ACL] AS s WHERE ISDESCENDANTNODE([/])", Query.JCR_SQL2);
        QueryResult queryResult = query.execute();
        Row row = queryResult.getRows().nextRow();
        // inspired by https://github.com/apache/jackrabbit-oak/blob/cc8adb42d89bc4625138a62ab074e7794a4d39ab/oak-jcr/src/test/java/org/apache/jackrabbit/oak/jcr/query/QueryTest.java#L1092
        String plan = row.getValue("plan").getString();
        String costJson = plan.substring(plan.lastIndexOf('{'));
        
        // use jackson for JSON parsing
        ObjectMapper mapper = new ObjectMapper();

        // read the json strings and convert it into JsonNode
        JsonNode node = mapper.readTree(costJson);
        double cost = node.get("s").asDouble(Double.MAX_VALUE);
        // look at https://jackrabbit.apache.org/oak/docs/query/query-engine.html#cost-calculation for the threshold
        // https://github.com/apache/jackrabbit-oak/blob/cc8adb42d89bc4625138a62ab074e7794a4d39ab/oak-core/src/main/java/org/apache/jackrabbit/oak/query/index/TraversingIndex.java#L75

        // for traversing cost = estimation of node count
        // for property index = between 2 and 100
        LOG.debug("Cost for rep:ACL query is estimated with {}", cost);
        return cost <= COST_THRESHOLD_FOR_QUERY_INDEX;
    }

    /** Get Nodes with XPATH Query. */
    public static Set<String> getNodePathsFromQuery(final Session session,
            final String xpathQuery) throws RepositoryException {
        return getNodePathsFromQuery(session, xpathQuery, Query.XPATH);
    }

    
    public static Set<String> getDistinctParentNodePathsFromQuery(final Session session,final String queryStatement, String queryLanguageType) throws InvalidQueryException,  RepositoryException {
        Set<String> paths = getNodePathsFromQuery(session, queryStatement, queryLanguageType);
        Set<String> parentPaths = new HashSet<>();

        for (String path : paths) {
            parentPaths.add(Text.getRelativeParent(path, 1));
        }

        return parentPaths;
    }

    
    /** @param session the jcr session
     * @param queryStatement - ex. "SELECT * FROM [rep:ACL]"
     * @param queryLanguageType - ex. Query.JCR_SQL2 */
    public static NodeIterator getNodesFromQuery(final Session session,
            final String queryStatement, String queryLanguageType) throws RepositoryException {
        Query query = session.getWorkspace().getQueryManager().createQuery(queryStatement, queryLanguageType);
        QueryResult queryResult = query.execute();
        return queryResult.getNodes();
    }

    /** @param session the jcr session
     * @param queryStatement - ex. "SELECT * FROM [rep:ACL]"
     * @param queryLanguageType - ex. Query.JCR_SQL2 */
    public static Set<String> getNodePathsFromQuery(final Session session,
            final String queryStatement, String queryLanguageType) throws RepositoryException {
        Set<String> paths = new HashSet<>();
        NodeIterator nit = getNodesFromQuery(session, queryStatement, queryLanguageType);
        while (nit.hasNext()) {
            // get the next rep:policy node
            Node node = nit.nextNode();
            paths.add(node.getPath());
        }
        return paths;
    }

    public static Set<AclBean> getAuthorizablesAcls(final Session session,
            final Set<String> authorizableIds, Set<String> principalIdsToBeFilled) throws InvalidQueryException,
            RepositoryException {
        
        LOG.debug("Querying AclBeans for {} authorizables", authorizableIds.size());

        StopWatch sw = new StopWatch();
        sw.start();
        
        Collection<Node> nodes = new LinkedList<>();

        Iterator<String> authorizablesIdIterator = authorizableIds.iterator();


        while (authorizablesIdIterator.hasNext()) {
            StringBuilder queryStringBuilder = new StringBuilder();
            queryStringBuilder.append(
                    "SELECT ace.* FROM [rep:ACE] AS ace INNER JOIN [rep:Authorizable] AS authorizable "
                            + "ON ace.[rep:principalName] = authorizable.[rep:principalName] WHERE ");
            queryStringBuilder.append(getAuthorizablesQueryStringBuilder(authorizablesIdIterator, 100));

            String query = queryStringBuilder.toString();

            NodeIterator nit = getNodesFromQuery(session, query, Query.JCR_SQL2);
            Collection<Node> resultNodes = new LinkedList<>();
            while (nit.hasNext()) {
                resultNodes.add(nit.nextNode());
            }
            LOG.trace("Querying AclBeans with {} returned {} results", query, resultNodes.size());
            nodes.addAll(resultNodes);
        }
        Set<AclBean> resultBeans = buildAclBeansFromNodes(session, nodes, principalIdsToBeFilled);

        sw.stop();
        LOG.debug("Found {} AclBeans in {}", resultBeans.size(), msHumanReadable(sw.getTime()));

        return resultBeans;
    }

    private static Set<AclBean> buildAclBeansFromNodes(final Session session,
            Collection<Node> nodes, Set<String> principalIdsToBeFilled) throws UnsupportedRepositoryOperationException,
            RepositoryException, PathNotFoundException, AccessDeniedException,
            ItemNotFoundException {
        AccessControlManager aMgr = session.getAccessControlManager();
        AccessControlList acl;
        Set<AclBean> aclSet = new TreeSet<AclBean>(); // use natural ordering
        for (Node allowOrDenyNode : nodes) {
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
                jcrPathAcl = ROOT_REPO_POLICY_NODE;
            }

            acl = getValidAccessControlList(aMgr, aclEffectiveOnPath);
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

    private static AccessControlList getValidAccessControlList(AccessControlManager aMgr, String aclEffectiveOnPath) throws RepositoryException {
        AccessControlPolicy[] acps = aMgr.getPolicies(aclEffectiveOnPath);

        for (AccessControlPolicy acp : acps) {
            if (acp instanceof AccessControlList) {
                return (AccessControlList) acp;
            }
        }
        return null;
    }

}
