package biz.netcentric.cq.tools.actool.helper;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import javax.jcr.AccessDeniedException;
import javax.jcr.ItemNotFoundException;
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
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

public class PurgeHelper {

	public static String purgeACLs(final Session session, final String path) throws Exception {

		StringBuilder message = new StringBuilder();
		if (StringUtils.isNotBlank(path)) {
			String queryString = "/jcr:root" + path.trim() + "//rep:policy";
			Node startNode = session.getNode(path);
			//				Iterator<Node> results = resourceResolver.findResources(query, Query.XPATH);
			Query query = session.getWorkspace().getQueryManager().createQuery(queryString, Query.XPATH );
			QueryResult result = query.execute();
			NodeIterator nodeIterator = result.getNodes();

			AccessControlManager accessManager = session.getAccessControlManager();

			while (nodeIterator.hasNext()) {
				Node res = nodeIterator.nextNode().getParent();
				if (res != null) {
					AccessControlPolicy[] policies = accessManager.getPolicies(res.getPath());
					for (int j = 0; j<policies.length; j++) {
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

	public static void purgeAcl(final Session session, final String path) throws Exception {

		if (StringUtils.isNotBlank(path)) {
			AccessControlManager accessManager = session.getAccessControlManager();
			Node node = session.getNode(path);

			AccessControlPolicy[] policies = accessManager.getPolicies(node.getPath());
			for (int i = 0; i < policies.length; i++) {
				accessManager.removePolicy(node.getPath(), policies[i]);   
				AcHelper.LOG.info("Removed all policies from node " + node.getPath() + ".\n");                            
			}
		}
	}

	public static void purgeACLs(final ResourceResolver resourceResolver, final String[] paths) throws Exception {
		Session session = resourceResolver.adaptTo(Session.class);

		for (int i = 0; i < paths.length; i++) {
			if (StringUtils.isNotBlank(paths[i])) {
				String query = "/jcr:root" + paths[i].trim() + "//rep:policy";
				Iterator<Resource> results = resourceResolver.findResources(query, Query.XPATH);
				AccessControlManager accessManager = session.getAccessControlManager();

				while (results.hasNext()) {
					Resource res = results.next().getParent();
					if (res != null) {
						AccessControlPolicy[] policies = accessManager.getPolicies(res.getPath());
						for (int j = 0; j<policies.length; j++) {
							accessManager.removePolicy(res.getPath(), policies[j]);   
						}
					}
				}
			}
		}
		session.save();
	}


	public static String deleteAcesFromAuthorizable(final Session session, final Set<AclBean> aclSet, final String authorizableId) throws AccessDeniedException, PathNotFoundException, ItemNotFoundException, RepositoryException{
		return deleteAcesFromAuthorizables(session, new HashSet<String>(Arrays.asList(new String[]{authorizableId})), aclSet);
	}

	public static String deleteAcesFromAuthorizables(final Session session, final Set<String> authorizabeIDs, final Set<AclBean> aclBeans)
			throws UnsupportedRepositoryOperationException,
			RepositoryException, AccessControlException, PathNotFoundException,
			AccessDeniedException, LockException, VersionException {

		StopWatch sw = new StopWatch();
		sw.start();
		StringBuilder message = new StringBuilder();
		AccessControlManager aMgr = session.getAccessControlManager();
		long aceCounter = 0;

		for(AclBean aclBean : aclBeans){
			if(aclBean != null){
				for (AccessControlEntry ace : aclBean.getAcl().getAccessControlEntries()) {
					String authorizableId = ace.getPrincipal().getName();
					if(authorizabeIDs.contains(authorizableId)){
						String parentNodePath = aclBean.getParentPath();
						String acePath = aclBean.getJcrPath();
						aclBean.getAcl().removeAccessControlEntry(ace);
						aMgr.setPolicy(aclBean.getParentPath(), aclBean.getAcl());
						AcHelper.LOG.info("removed ACE {} from ACL of node: {}", acePath, parentNodePath );
						message.append("removed ACE of principal: " + authorizableId + " from ACL of node: " + parentNodePath  + "\n");
						aceCounter++;
					}
				}
			}
		}
		sw.stop();
		long executionTime = sw.getTime();
		message.append("\n\ndeleted: " + aceCounter + " ACEs in repository");
		message.append("\nexecution time: " + executionTime + " ms");
		return message.toString();
	}
}
