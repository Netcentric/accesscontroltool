package biz.netcentric.cq.tools.actool.api;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2024 Cognizant Netcentric
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.osgi.annotation.versioning.ProviderType;

@ProviderType
public interface AcInstallationService {

    /**
     * Applies the configuration asynchronously.
     * Almost immediately returns a string with the ID of the started job.
     * Only one execution at a time is allowed.
     * @param options the installation options which further specify the installation
     * @throws IllegalStateException if another asynchronous installation is currently running
     * @return the job id
     * @since 3.6.0
     * @see #attachLogListener(String, BiConsumer, Consumer)
     */
    public String applyAsynchronously(InstallationOptions options);

    /** Attaches the log listener callback to an installation triggered previously via {@link #applyAsynchronously(InstallationOptions)}.
     * 
     * @param jobId the job id returned by {@link #applyAsynchronously(InstallationOptions)}
     * @param listener the listener to attach, receives the level and the message per each log line
     * @param finishListener the listener to attach, receives a boolean status indicating success or failure once the installation was finished
     * @return {@code true} if the listeners were attached successfully (i.e. an installation with the given executionId was triggered before and is still ongoing), {@code false} otherwise
     * @since 3.6.0 
     * @see #applyAsynchronously(InstallationOptions)
     */
    public boolean attachLogListener(String jobId, BiConsumer<InstallationLogLevel, String> listener, Consumer<Boolean> finishListener);

    /**
     * Checks if the asynchronous installation job with the given ID is running.
     * @param jobId
     * @return {@code true} if the job with the given ID is running, {@code false} otherwise
     * @since 3.6.1
     */
    public boolean isRunning(String jobId);

    
    /** Applies the full configuration as stored at the path configured at PID biz.netcentric.cq.tools.actool.impl.AcInstallationServiceImpl
     * to the repository.
     * 
     * @return the installation log
     * @deprecated use {@link #apply(InstallationOptions)} instead
     */
    @Deprecated
    public InstallationLog apply();

    /** Applies the configuration as stored at the given configurationRootPath to the repository.
     * 
     * @param configurationRootPath the root path for configuration files
     * @return the installation log 
     * @deprecated use {@link #apply(InstallationOptions)} instead
     */
    @Deprecated
    public InstallationLog apply(String configurationRootPath);

    /** Applies parts of the configuration (based on given paths)
     * 
     * @param restrictedToPaths only apply ACLs to root paths as given
     * @return the installation log  
     * @deprecated use {@link #apply(InstallationOptions)} instead
     */
    @Deprecated
    public InstallationLog apply(String[] restrictedToPaths);

    /** Applies the configuration as stored at the given configurationRootPath to the repository, but only apply ACEs to given
     * restrictedToPaths.
     * 
     * @param restrictedToPaths only apply ACLs to root paths as given
     * @param configurationRootPath the root path for configuration files
     * @return the installation log
     * @deprecated use {@link #apply(InstallationOptions)} instead
     */
    @Deprecated
    public InstallationLog apply(String configurationRootPath, String[] restrictedToPaths);


    /** Applies the configuration as stored at the given configurationRootPath to the repository, but only apply ACEs to given
     * restrictedToPaths.
     * 
     * @param restrictedToPaths only apply ACLs to root paths as given
     * @param configurationRootPath the root path for configuration files
     * @param skipIfConfigUnchanged will check if the config is unchanged compared to last execution with same parameters
     * @return the installation log
     * @deprecated use {@link #apply(InstallationOptions)} instead
     */
    @Deprecated
    public InstallationLog apply(String configurationRootPath, String[] restrictedToPaths, boolean skipIfConfigUnchanged);

    /** Applies the configuration.
     * 
     * @param options the installation options which further specify the installation
     * @return the installation log
     * @since 3.6.0
     */
    public InstallationLog apply(InstallationOptions options);

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

}
