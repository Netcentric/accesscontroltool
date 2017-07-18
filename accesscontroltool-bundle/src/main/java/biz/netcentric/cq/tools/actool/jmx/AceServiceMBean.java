/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.jmx;

import javax.jcr.RepositoryException;

import com.adobe.granite.jmx.annotation.Description;
import com.adobe.granite.jmx.annotation.Name;

/**
 * exposes functionalities of the Netcentric AC-Tool
 * 
 * @author jochenkoschorke
 *
 */
@Description("AC Service")
public interface AceServiceMBean {

    @Description("Version")
    public String getVersion();

    @Description("Returns links to the existing configuration files in CRX")
    public String[] getConfigurationFiles();

    @Description("Provides status and links to the saved history logs")
    String[] getSavedLogs() throws RepositoryException;

    @Description("Applies the ACE configuration")
    String apply();

    @Description("Applies the ACE configuration, but restricted to given paths")
    String applyRestrictedToPaths(
            @Name("paths") @Description("comma separated list of paths to apply the configuration to, other paths will be skipped") String restrictedToPaths);

    @Description("Applies the ACE configuration as located at given path")
    String apply(@Name("configurationRootPath") @Description("The configuration root path") String configurationRootPath);

    @Description("Applies the ACE configuration, but restricted to given paths")
    String applyRestrictedToPaths(@Name("configurationRootPath") @Description("The configuration root path") String configurationRootPath,
            @Name("paths") @Description("comma separated list of paths to apply the configuration to, other paths will be skipped") String restrictedToPaths);

    @Description("Purges the AccessControlList of the given path, if existing")

    String purgeACL(@Name("path") final String path);

    @Description("Purges all AccessControlLists under the given path and its subpaths, if existing")
    String purgeACLs(@Name("path") final String path);

    @Description("Purges all authorizables contained in configuration files and all their ACEs from the system")
    public String purgeAllAuthorizablesFromConfiguration();



    @Description("Returns a configuration dump containing all groups and all ACLs ordered by path")
    public String pathBasedDump();

    @Description("Returns a configuration dump containing all groups and all ACEs ordered by groups (can be used as template for AC Tool configuration file)")
    public String groupBasedDump();

    @Description("Returns installation log for the given ordinal")
    public String showInstallationLog(
            @Name("installationLogNumber") @Description("Ordinal of the installation log to be shown") final String historyLogNumber);

    @Description("Purges authorizable(s) and respective ACEs from the system.")
    public String purgeAuthorizables(
            @Name("authorizableIds") @Description("Authorizable Ids to be purged. Multiple authorizable ids may be given separated by comma.") String authorizableIds);
}
