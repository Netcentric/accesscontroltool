package biz.netcentric.cq.tools.actool.honor;

import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import javax.jcr.RepositoryException;

import biz.netcentric.cq.tools.actool.configmodel.AuthorizablesConfig;
import biz.netcentric.cq.tools.actool.history.AcInstallationLog;
import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;


/**
 * Defines the API used to take and restore snapshots, intended initially to honour privileges during the
 * restoring of serialised privileges. This allows users to maintain hand changed permissions on selected paths during
 * restoration.
 *   
 * @author jon.entwistle
 *
 */
public interface HonorPrivilegeService {
	
	/**
	 * Takes a snapshot of the privileges on a series of paths for the association of groups and paths. The snapshot 
	 * takes the form of a set of {@link  biz.netcentric.cq.tools.actool.honor.PathACL} instances which can later be 
	 * restored via restorePrivelegeSnapshot.
	 * 
	 * @param authConf The configuration for the authorizables, containing the honor paths.
	 * @param history the installation history
	 * @return An installable snapshot of the privileges on a series of paths for the association of groups and paths
	 * @throws RepositoryException in case of an error in the repository
	 */
	Set<PathACL> takePrivilegeSnapshot(AuthorizablesConfig authConf, AcInstallationLog history)
			throws RepositoryException; 
	
	/**
	 * Restores a snapshot of the privileges on a series of paths for a given group encapsulated in a
	 * {@link  biz.netcentric.cq.tools.actool.honor.PathACL}.
	 *  
	 * @param snapshotACL A snapshot of privileges to restore.
	 * @param history the installation history
	 * @throws RepositoryException in case of an error from the repository
	 */
	void restorePrivilegeSnapshot(Set<PathACL> snapshotACL,  AcInstallationLog history)
			throws RepositoryException;
}