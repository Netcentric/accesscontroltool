/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.impl;

import java.util.Map;
import java.util.Set;

import javax.jcr.Session;

import biz.netcentric.cq.tools.actool.api.AcInstallationService;
import biz.netcentric.cq.tools.actool.history.impl.PersistableInstallationLogger;

public interface AcInstallationServiceInternal extends AcInstallationService {

    /** Used by install hook only and not public.
     * 
     * @param history
     * @param configurationFileContentsByFilename
     * @param restrictedToPaths only apply ACLs to root paths as given
     * @param a jcr session
     * @throws Exception */
    public void installConfigurationFiles(PersistableInstallationLogger history,
            Map<String, String> configurationFileContentsByFilename, String[] restrictedToPaths, Session session)
            throws Exception;

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

    /** return a set containing the paths to the newest configurations under the configuration root path
     *
     * @return set containing paths */
    public Set<String> getCurrentConfigurationPaths();

    /** Purges all authorizables from configuration (all configured config roots) with their ACEs (effectively purges everything contained in configuration)
     * 
     * @return result message */
    public String purgeAuthorizablesFromConfig();

    /** Purges all authorizables from configuration for given root path with their ACEs
     * 
     * @param configurationRootPath the config root path to collect the authorizables from
     * @return result message */
    public String purgeAuthorizablesFromConfig(String configurationRootPath);
    
    /** Returns the version of the AC Tool. */
    public String getVersion();
}
