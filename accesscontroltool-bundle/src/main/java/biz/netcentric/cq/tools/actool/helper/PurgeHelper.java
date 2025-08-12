package biz.netcentric.cq.tools.actool.helper;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2024 Cognizant Netcentric
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryResult;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.AccessControlPolicy;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PurgeHelper {
    public static final Logger LOG = LoggerFactory.getLogger(PurgeHelper.class);

    public static String purgeACLs(final Session session, final String path) throws RepositoryException {

        StringBuilder message = new StringBuilder();
        if (StringUtils.isNotBlank(path)) {
            String queryString = "/jcr:root" + path.trim() + "//rep:policy";
            Query query = session.getWorkspace().getQueryManager().createQuery(queryString, Query.XPATH);
            QueryResult result = query.execute();
            NodeIterator nodeIterator = result.getNodes();

            AccessControlManager accessManager = session.getAccessControlManager();

            while (nodeIterator.hasNext()) {
                Node res = nodeIterator.nextNode().getParent();
                if (res != null) {
                    AccessControlPolicy[] policies = accessManager.getPolicies(res.getPath());
                    for (int j = 0; j < policies.length; j++) {
                        accessManager.removePolicy(res.getPath(), policies[j]);
                    }
                    message.append("Removed all policies from node " + res.getPath() + ".\n");
                }
            }
            message.append("\n\nCompleted removing ACLs from path: " + path + " and it's subpaths!");
        }

        session.save();

        return message.toString();
    }

    public static void purgeAcl(final Session session, final String path)
            throws RepositoryException {

        if (StringUtils.isNotBlank(path)) {
            AccessControlManager accessManager = session.getAccessControlManager();
            Node node = session.getNode(path);

            AccessControlPolicy[] policies = accessManager.getPolicies(node.getPath());
            for (int i = 0; i < policies.length; i++) {
                accessManager.removePolicy(node.getPath(), policies[i]);
                LOG.info("Removed all policies from node {}", node.getPath());
            }
        }
    }

}
