package biz.netcentric.cq.tools.actool.aceservice.impl;

import java.util.Map;
import java.util.SortedSet;

import javax.jcr.RepositoryException;

import biz.netcentric.cq.tools.actool.aceservice.impl.model.PathACL;
import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;

public interface PrivilegeFacade {

	void installHonoredPrivileges(Map<String, SortedSet<PathACL>> honoredACL) 
			throws RepositoryException;
	
	Map<String, SortedSet<PathACL>> getHonoredACL(Map<String, SortedSet<String>> pathsByGroup, AcInstallationHistoryPojo history)
			throws RepositoryException; 
}