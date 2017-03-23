/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.helper;

import static biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo.msHumanReadable;

import java.util.Iterator;
import java.util.Set;

import javax.jcr.AccessDeniedException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.lock.LockException;
import javax.jcr.query.Query;
import javax.jcr.query.QueryResult;
import javax.jcr.security.AccessControlEntry;
import javax.jcr.security.AccessControlException;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.AccessControlPolicy;
import javax.jcr.version.VersionException;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.StopWatch;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PurgeHelper {
    public static final Logger LOG = LoggerFactory.getLogger(PurgeHelper.class);


    public static String purgeACLs(final Session session, final String path)
            throws Exception {

        StringBuilder message = new StringBuilder();
        if (StringUtils.isNotBlank(path)) {
            String queryString = "/jcr:root" + path.trim() + "//rep:policy";
            Query query = session.getWorkspace().getQueryManager()
                    .createQuery(queryString, Query.XPATH);
            QueryResult result = query.execute();
            NodeIterator nodeIterator = result.getNodes();

            AccessControlManager accessManager = session
                    .getAccessControlManager();

            while (nodeIterator.hasNext()) {
                Node res = nodeIterator.nextNode().getParent();
                if (res != null) {
                    AccessControlPolicy[] policies = accessManager
                            .getPolicies(res.getPath());
                    for (int j = 0; j < policies.length; j++) {
                        accessManager.removePolicy(res.getPath(), policies[j]);
                    }
                    message.append("Removed all policies from node "
                            + res.getPath() + ".\n");
                }
            }
            message.append("\n\nCompleted removing ACLs from path: " + path
                    + " and it's subpaths!");
        }

        session.save();

        return message.toString();
    }

    public static void purgeAcl(final Session session, final String path)
            throws Exception {

        if (StringUtils.isNotBlank(path)) {
            AccessControlManager accessManager = session
                    .getAccessControlManager();
            Node node = session.getNode(path);

            AccessControlPolicy[] policies = accessManager.getPolicies(node
                    .getPath());
            for (int i = 0; i < policies.length; i++) {
                accessManager.removePolicy(node.getPath(), policies[i]);
                AcHelper.LOG.info("Removed all policies from node "
                        + node.getPath() + ".\n");
            }
        }
    }

    public static void purgeACLs(final ResourceResolver resourceResolver,
            final String[] paths) throws Exception {
        Session session = resourceResolver.adaptTo(Session.class);

        for (int i = 0; i < paths.length; i++) {
            if (StringUtils.isNotBlank(paths[i])) {
                String query = "/jcr:root" + paths[i].trim() + "//rep:policy";
                Iterator<Resource> results = resourceResolver.findResources(
                        query, Query.XPATH);
                AccessControlManager accessManager = session
                        .getAccessControlManager();

                while (results.hasNext()) {
                    Resource res = results.next().getParent();
                    if (res != null) {
                        AccessControlPolicy[] policies = accessManager
                                .getPolicies(res.getPath());
                        for (int j = 0; j < policies.length; j++) {
                            accessManager.removePolicy(res.getPath(),
                                    policies[j]);
                        }
                    }
                }
            }
        }
        session.save();
    }

    public static String deleteAcesForPrincipalIds(final Session session,
            final Set<String> principalIds, final Set<AclBean> aclBeans)
            throws UnsupportedRepositoryOperationException,
            RepositoryException, AccessControlException, PathNotFoundException,
            AccessDeniedException, LockException, VersionException {

        StopWatch sw = new StopWatch();
        sw.start();
        StringBuilder message = new StringBuilder();
        AccessControlManager aMgr = session.getAccessControlManager();
        long aceCounter = 0;

        for (AclBean aclBean : aclBeans) {
            if (aclBean == null) {
                continue;
            }

            JackrabbitAccessControlList acl = aclBean.getAcl();
            for (AccessControlEntry ace : acl.getAccessControlEntries()) {
                String principalId = ace.getPrincipal().getName();
                if (principalIds.contains(principalId)) {
                    String parentNodePath = aclBean.getParentPath();
                    acl.removeAccessControlEntry(ace);
                    boolean aclEmpty = acl.isEmpty();
                    if (!aclEmpty) {
                        aMgr.setPolicy(aclBean.getParentPath(), acl);
                    } else {
                        aMgr.removePolicy(aclBean.getParentPath(), acl);
                    }

                    String msg = "Removed ACE of principal: " + principalId + " from ACL of node: " + parentNodePath + " " + (aclEmpty ? " (and the now emtpy ACL itself)":"") ;
                    LOG.info(msg);
                    message.append(msg + "\n");
                    aceCounter++;
                }
            }

        }
        sw.stop();
        String executionTime = msHumanReadable(sw.getTime());
        String resultMsg = "Deleted " + aceCounter + " ACEs for " + principalIds.size() + " principals in " + executionTime;
        message.append(resultMsg + "\n");
        LOG.debug(resultMsg);

        return message.toString();
    }
}
