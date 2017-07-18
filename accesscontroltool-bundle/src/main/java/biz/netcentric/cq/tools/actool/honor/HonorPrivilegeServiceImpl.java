package biz.netcentric.cq.tools.actool.honor;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
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

import biz.netcentric.cq.tools.actool.helper.AccessControlUtils;
import biz.netcentric.cq.tools.actool.history.AcInstallationLog;


/**
 * Implementation of HonorPrivilegeService.
 * 
 * @author jon.entwistle
 *
 */
@Service
@Component
public class HonorPrivilegeServiceImpl implements HonorPrivilegeService {

	private static final Logger LOG = LoggerFactory.getLogger(HonorPrivilegeServiceImpl.class);

	@Reference
	private SlingRepository repository;

	@Override
	public Set<PathACL> takePrivilegeSnapshot(Map<String, SortedSet<String>> pathsByGroup, AcInstallationLog history) throws RepositoryException {

		Set<PathACL> result = new HashSet<>();
		Session session = repository.loginAdministrative(null);

		for (Map.Entry<String, SortedSet<String>> entry : pathsByGroup.entrySet()) {
			Set<PathACL> acls = new HashSet<>();
			for (String path : entry.getValue()) {
				// Attempting to serialise the root node permissions provokes the following error:
				// OakVersion0001: Cannot change property jcr:mixinTypes on checked in node
				if (("/").equals(path)) { 
					String message = "Honor privilege on root folder ignored.";
					LOG.warn(message);
					history.addMessage(LOG, message);
				} else {
					String group = entry.getKey();
					acls.addAll(this.findRecursiveACLs(session, group, path, history));
				}
			}
            if (acls.isEmpty()) {
                history.addWarning(LOG, "No custom ACLs found for group " + entry.getKey());
            }

            result.addAll(acls);
		}
		return result;
	}	
	
	
	/**
	 * Note that this implementation DOES NOT SAVE CHANGES. Clients must call session.save() to persist the 
	 * snapshot.
	 */
	@Override
	public void restorePrivilegeSnapshot(Set<PathACL> snapshotACL,  AcInstallationLog history)
			throws RepositoryException {
		Session session = null;
		
		 try {
			session = repository.loginAdministrative(null);		
			for (PathACL pathACL : snapshotACL) {
				AccessControlUtils.applyAccessControlList(session, pathACL.getPath(), pathACL.getAcl());
			}
			if (!snapshotACL.isEmpty()) {
				session.save();
                history.addMessage(LOG,"Honour privileges successfully restored.");
            }
		} finally {
			if (session != null) {
				session.logout();
			}
		}
	}

	private Set<PathACL> findRecursiveACLs(Session session, String group, String path, AcInstallationLog history) throws RepositoryException {
		Set<PathACL> result = new HashSet<>();


		try {
			Set<String> paths = this.findSubPaths(session.getNode(path));

			for (String absPath : paths) {
				// Restricted to given group.
				JackrabbitAccessControlList acl = AccessControlUtils.getAccessControlList(session, absPath);

				acl = this.filterPoliciesByGroup(acl, group);
				// Ignore ACLs with access rules
				if (acl.getAccessControlEntries().length > 0) {
					result.add(new PathACL(absPath, this.filterPoliciesByGroup(acl, group)));
					LOG.debug("Found ACL for group " + group + " on path " + absPath + ": " + acl);
				}
			}
		} catch (PathNotFoundException e) {
			String msg = "Honour path " + path + " not found: ignoring.";
			LOG.warn(msg);
			history.addWarning(LOG, msg);
		}
		
		return result;
	}
	
	private Set<String> findSubPaths(Node node) throws RepositoryException {
		Set<String> result = new HashSet<>();
		for (Node subNode : JcrUtils.getChildNodes(node)) {
			result.addAll(this.findSubPaths(subNode));
		}
		// Ensure that node is not meta-data
		if (node.getName().indexOf(':') < 0 && node.getPath().indexOf(':') < 0) {
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
