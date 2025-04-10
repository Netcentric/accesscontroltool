package biz.netcentric.cq.tools.actool.api;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2025 Cognizant Netcentric
 * %%
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * #L%
 */

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.osgi.annotation.versioning.ProviderType;

import biz.netcentric.cq.tools.actool.impl.AcInstallationServiceImpl;

/** 
 * Options to be used when applying the configuration.
 * Create an instance using {@link InstallationOptionsBuilder}.
 * @since 3.6.0 
 */
@ProviderType
public interface InstallationOptions {

    /**
     * 
     * @return the root path of the configuration files. If not set the one configured in {@link AcInstallationServiceImpl} is used.
     */
    public Optional<String> getConfigurationRootPath();

    /**
     * 
     * @return the list of root path entry below which ACLs should be modified. Note this does not restrict the installation of authorizables (users/groups) only the ACEs.
     */
    public List<String> getRestrictedToPaths();

    /**
     * 
     * @return {@code true} if the installation should be skipped if the configuration is unchanged compared to last execution with same parameters
     */
    public boolean shouldSkipIfConfigUnchanged();

    /**
     * 
     * @return {@code true} if the installation should also update existing external groups. By default only new ones are created but existing ones not touched.
     */
    public boolean shouldUpdateExistingExternalGroups();

    /** 
     * For asynchronous installations the options need to be persisted in the repository.
     * As regular Java serialization cannot be used with Sling Jobs (<a href="https://issues.apache.org/jira/browse/SLING-12745">SLING-12745</a>)
     * one has to rely on types compliant with default JCR.
     * 
     * @return a new map with properties of types which are natively supported by the JCR resource provider
     * @see InstallationOptionsBuilder
     */
    public Map<String, Object> getPersistableProperties();

}
