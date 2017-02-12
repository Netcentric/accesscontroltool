package biz.netcentric.cq.tools.actool.honor;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.security.AccessControlEntry;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.sling.jcr.api.SlingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.aceservice.impl.AceServiceImpl;
import biz.netcentric.cq.tools.actool.helper.AccessControlUtils;
import biz.netcentric.cq.tools.actool.installationhistory.AcHistoryService;
import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;

@Service
@Component
public class HonorPrivilegeServiceImpl implements HonorPrivilegeService {

	private static final Logger LOG = LoggerFactory.getLogger(AceServiceImpl.class);
	
	@Reference
	AcHistoryService acHistoryService;
	
	@Reference
	private SlingRepository repository;

	public void installHonoredPrivileges(Set<PathACL> honoredACL) 
			throws RepositoryException {
		Session session = null;
		
		 try {
			session = repository.loginAdministrative(null);		
			for (PathACL pathACL : honoredACL) {
				AccessControlUtils.applyAccessControlList(session, pathACL.getPath(), pathACL.getAcl());
			}
			session.save();
			
		} finally {
			if (session != null) {
				session.logout();
			}
		}
	}


	/**
	 * Returns the ACLs for each path and group in honoredPrivileges, including
	 * subpaths.
	 * 
	 * @param pathsByGroup
	 *            the paths to retain permisions, per group
	 * @return the ACLs for each path and group in honoredPrivileges, including
	 *         subpaths
	 * @throws RepositoryException
	 *             in case of error
	 */
	public Set<PathACL> getHonoredACL(Map<String, SortedSet<String>> pathsByGroup, AcInstallationHistoryPojo history)
			throws RepositoryException {

		Set<PathACL> result = new HashSet<>();
		Session session = repository.loginAdministrative(null);

		for (Map.Entry<String, SortedSet<String>> entry : pathsByGroup.entrySet()) {
			Set<PathACL> acls = new HashSet<>();
			for (String path : entry.getValue()) {
				if (("/").equals(path)) { 
					String message = "Honor privilege on root folder ignored.";
					LOG.warn(message);
					history.addMessage(message);
				} else {
					acls.addAll(this.findRecursiveACLs(session, entry.getKey(), path));
				}
			}
			result.addAll(acls);
		}

		return result;
	}

	/**
	 * For the given group and path, a map is returned relating that path and
	 * all subpaths to the ACLs holding the permissions for that group.
	 * 
	 * @param session
	 * @param group
	 * @param path
	 * @return
	 * @throws RepositoryException
	 */
	private Set<PathACL> findRecursiveACLs(Session session, String group, String path)
			throws RepositoryException {
		Set<PathACL> result = new HashSet<>();

		Set<String> paths = this.findAllSubPaths(session.getNode(path));

		for (String absPath : paths) {
			// Note: acl contains the permissions for ALL users. This needs to
			// be restricted to our group.
			JackrabbitAccessControlList acl = AccessControlUtils.getAccessControlList(session, absPath);
			result.add(new PathACL(absPath, this.filterPoliciesByGroup(acl, group)));
			LOG.debug("Path: " + absPath + " ACL: " + acl);
		}

		return result;
	}
	
	private Set<String> findAllSubPaths(Node node) throws RepositoryException {
		Set<String> result = new HashSet<>();
		for (Node subNode : JcrUtils.getChildNodes(node)) {
			result.addAll(this.findAllSubPaths(subNode));
		}
		// Ensure that node is not meta-data, such as rep:policy
		if (node.getPath().indexOf(':') < 0) {
			result.add(node.getPath());
		}
		
		return result;
	}
	

	private JackrabbitAccessControlList filterPoliciesByGroup(JackrabbitAccessControlList acl, String group)
			throws RepositoryException {
		for (AccessControlEntry entry : acl.getAccessControlEntries()) {
			if (!entry.getPrincipal().getName().equals(group)) {
				acl.removeAccessControlEntry(entry);
			}
		}

		return acl;
	}
}
