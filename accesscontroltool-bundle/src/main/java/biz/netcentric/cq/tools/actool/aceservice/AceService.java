/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.aceservice;

import java.util.Map;
import java.util.Set;

import javax.jcr.Session;

import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;

public interface AceService {

    /** Applies the full configuration as stored at the path configured at PID biz.netcentric.cq.tools.actool.aceservice.impl.AceServiceImpl
     * to the repository.
     * 
     * @return the history */
    public AcInstallationHistoryPojo execute();

    /** Applies the configuration as stored at the given configurationRootPath to the repository.
     * 
     * @param configurationRootPath the root path for configuration files
     * @return the history */
    public AcInstallationHistoryPojo execute(String configurationRootPath);

    /** Applies parts of the configuration (based on given paths)
     * 
     * @param restrictedToPaths only apply ACLs to root paths as given
     * @return the history */
    public AcInstallationHistoryPojo execute(String[] restrictedToPaths);

    /** Applies the configuration as stored at the given configurationRootPath to the repository, but only apply ACEs to given
     * restrictedToPaths.
     * 
     * @param restrictedToPaths only apply ACLs to root paths as given
     * @param configurationRootPath the root path for configuration files
     * @return the history */
    public AcInstallationHistoryPojo execute(String configurationRootPath, String[] restrictedToPaths);

    /** method that indicates whether the service is ready for installation (if at least one configurations was found in repository)
     *
     * @return {@code true} if ready, otherwise {@code false} */
    public boolean isReadyToStart();

    /** purges all acls of the node specified by path (no deletion of acls of subnodes)
     *
     * @param path the path from which to purge the ACL
     * @return status message */
    public String purgeACL(final String path);

    /** Purges all acls of the node specified by path and all acls of all subnodes
     *
     * @param path the path from which to purge the ACL (including those of all subnodes)
     * @return status message */
    public String purgeACLs(final String path);

    /** Purges authorizable(s) and all respective aces from the system
     *
     * @param authorizableIds Array of authorizableIds to purge
     * @return status message */
    public String purgeAuthorizables(String[] authorizableIds);

    /** Returns current execution status
     *
     * @return true if the service is executing, false if not */
    public boolean isExecuting();

    /** return the path in repository under witch the ac confiuration are stored
     *
     * @return node path in repository */
    public String getConfiguredAcConfigurationRootPath();

    /** return a set containing the paths to the newest configurations under the configuration root path
     *
     * @return set containing paths */
    public Set<String> getCurrentConfigurationPaths();

    /** Purges all authorizables form configuration with their ACEs (effectively purges everything contained in configuration)
     * 
     * @return */
    public String purgeAuthorizablesFromConfig();

    /** Common entry point for JMX and install hook.
     * 
     * @param history
     * @param configurationFileContentsByFilename
     * @param restrictedToPaths only apply ACLs to root paths as given
     * @param a jcr session
     * @throws Exception */
    public void installConfigurationFiles(AcInstallationHistoryPojo history,
            Map<String, String> configurationFileContentsByFilename, String[] restrictedToPaths, Session session)
            throws Exception;

}
