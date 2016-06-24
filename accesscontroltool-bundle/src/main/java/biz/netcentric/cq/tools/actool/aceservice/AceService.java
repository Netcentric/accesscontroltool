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

import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableInstallationHistory;
import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;

public interface AceService {

    public AcInstallationHistoryPojo execute();

    /** method that indicates whether the service is ready for installation (if at least one configurations was found in repository)
     *
     * @return {@code true} if ready, otherwise {@code false} */
    public boolean isReadyToStart();

    /** purges all acls of the node specified by path (no deletion of acls of subnodes)
     *
     * @param path the path from which to purge the ACL
     * @return status message */
    public String purgeACL(final String path);

    /** purges all acls of the node specified by path and all acls of all subnodes
     *
     * @param path the path from which to purge the ACL (including those of all subnodes)
     * @return status message */
    public String purgeACLs(final String path);

    /** method that purges authorizable(s) and all respective aces from the system
     *
     * @param authorizableIds comma-separated list of authorizable ids
     * @return status message */
    public String purgeAuthorizables(String authorizableIds);

    /** returns current execution status
     *
     * @return true if the service is executing, false if not */
    public boolean isExecuting();

    /** return the path in repository under witch the ac confiuration are stored
     *
     * @return node path in repository */
    public String getConfigurationRootPath();

    /** return a set containing the paths to the newest configurations under the configuration root path
     *
     * @return set containing paths */
    public Set<String> getCurrentConfigurationPaths();

    public String purgeAuthorizablesFromConfig();

    /** Common entry point for JMX and install hook.
     * 
     * @param session
     * @param history
     * @param configurationFileContentsByFilename
     * @param authorizableInstallationHistorySet
     * @throws Exception */
    public void installConfigurationFiles(Session session, AcInstallationHistoryPojo history,
            Map<String, String> configurationFileContentsByFilename,
            Set<AuthorizableInstallationHistory> authorizableInstallationHistorySet) throws Exception;

}
