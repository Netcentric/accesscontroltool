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

	@Override
	public Set<PathACL> takePrivelegeSnapshot(Map<String, SortedSet<String>> pathsByGroup, AcInstallationHistoryPojo history)
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
	
	@Override
	public void restorePrivilegeSnapshot(Set<PathACL> snapshotACL,  AcInstallationHistoryPojo history) 
			throws RepositoryException {
		Session session = null;
		
		 try {
			session = repository.loginAdministrative(null);		
			for (PathACL pathACL : snapshotACL) {
				AccessControlUtils.applyAccessControlList(session, pathACL.getPath(), pathACL.getAcl());
			}
			session.save();
			history.addMessage("Honour privileges successfuly restored on " + snapshotACL.size() + " resources.");
			
		} finally {
			if (session != null) {
				session.logout();
			}
		}
	}

	private Set<PathACL> findRecursiveACLs(Session session, String group, String path)
			throws RepositoryException {
		Set<PathACL> result = new HashSet<>();

		Set<String> paths = this.findSubPaths(session.getNode(path));

		for (String absPath : paths) {
			// Restricted to given group.
			JackrabbitAccessControlList acl = AccessControlUtils.getAccessControlList(session, absPath);
			result.add(new PathACL(absPath, this.filterPoliciesByGroup(acl, group)));
			LOG.debug("Found ACL for group " + group + " on path " + absPath + ": " + acl);
		}

		return result;
	}
	
	private Set<String> findSubPaths(Node node) throws RepositoryException {
		Set<String> result = new HashSet<>();
		for (Node subNode : JcrUtils.getChildNodes(node)) {
			result.addAll(this.findSubPaths(subNode));
		}
		// Ensure that node is not meta-data
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
