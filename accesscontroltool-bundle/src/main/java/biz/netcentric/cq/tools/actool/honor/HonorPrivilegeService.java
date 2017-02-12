package biz.netcentric.cq.tools.actool.honor;

import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import javax.jcr.RepositoryException;

import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;

public interface HonorPrivilegeService {


	void restorePrivilegeSnapshot(Set<PathACL> snapshotACL) 
			throws RepositoryException;
	
	Set<PathACL> takePrivelegeSnapshot(Map<String, SortedSet<String>> pathsByGroup, AcInstallationHistoryPojo history)
			throws RepositoryException; 
}