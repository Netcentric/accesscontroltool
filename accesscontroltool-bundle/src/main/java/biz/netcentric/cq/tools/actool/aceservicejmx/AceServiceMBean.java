/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.aceservicejmx;

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

    boolean isReadyToStart();

    @Description("executes the installation of the ACE configuration(s)")
    String execute();

    @Description("executes the installation of the ACE configuration(s), but restricted to given paths")
    String execute(
            @Name("paths") @Description("comma separated list of paths to apply the configuration to, other paths will be skipped") String restrictedToPaths);

    @Description("purges the AccessControlList of the given path, if existing")
    String purgeACL(@Name("path") final String path);

    @Description("purges all AccessControlLists under the given path and its subpaths, if existing")
    String purgeACLs(@Name("path") final String path);

    @Description("purges all authorizables contained in configuration files and all their ACEs from the system")
    public String purgeAllAuthorizablesFromConfigurations();

    @Description("provides status and links to the saved history logs")
    String[] getSavedLogs() throws RepositoryException;

    @Description("shows execution status of the AC Tool")
    public boolean isExecuting();

    @Description("returns a configuration dump containing all groups and all ACLs ordered by path")
    public String pathBasedDump();

    @Description("returns a configuration dump containing all groups and all ACEs ordered by groups")
    public String groupBasedDump();

    @Description("returns links to the existing configuration files in CRX")
    public String[] getConfigurationFiles();

    @Description("returns history log which matches the provided number")
    public String showHistoryLog(
            @Name("historyLogNumber") @Description("number of history log") final String historyLogNumber);

    @Description("purges authorizable(s) and respective ACEs from the system. Several authorizable ids have to be comma separated.")
    public String purgeAuthorizables(
            @Name("authorizableIds") String authorizableIds);
}
