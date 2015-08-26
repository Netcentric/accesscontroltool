/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.aceservice;

import java.util.Collection;

import javax.jcr.Session;

import biz.netcentric.cq.tools.actool.installationhistory.InstallationListener;

public interface AcInstallService {

	/**
	 * Processes all YAML files below the given rootNodePaths and installs the listed authorizables and ACLs/ACEs accordingly.
	 * @param session the session to use to access the YAML files and to install the ACLs (might be {@code null}, in which case the admin session is taken)
	 * @param rootNodePath the path of the root node under which the YAML files are located. Might be {@code null}, in which case the root nodes configured in the according OSGi configuration are taken.
	 * @param listener the listener which is called from the implementation to indicate progress/errors.
	 * @throws IllegalStateException in case the changes cannot be applied to the repository
	 * @throws IllegalArgumentException in case the YAML files are invalid, ....
	 */
	void install(Session session, String rootNodePath, InstallationListener listener) throws IllegalStateException, IllegalArgumentException;

	/**
	 * 
	 * @return {@code true} in case a YAML file is available in the configured root path in the repository, otherwise {@code false}
	 */
	boolean hasConfigurationInRootPath();
	
	/**
	 * Purges all ACLs being set on the given root path (and below)
	 * @param rootNodePath the rootPath for which the ACLs should be removed
	 * @param isRecursive if {@code true} will remove ACLs recursively from the given rootPath and its children, otherwise only for the given rootPath.
	 * @param listener the listener which should receive methods callbacks whenever something should be logged.
	 */
	void purgeAcls(Session session, String rootNodePath, boolean isRecursive, InstallationListener listener);
	
	/**
	 * Purges the given authorizable(s) and all connected Access Control Entries (ACEs) from the
     * system.
	 * @param authorizableIds the list of authorizable IDs to purge
	 * @param listener the listener which should receive methods callbacks whenever something should be logged
	 */
	void purgeAuthorizables(Collection<String> authorizableIds, InstallationListener listener);
}
