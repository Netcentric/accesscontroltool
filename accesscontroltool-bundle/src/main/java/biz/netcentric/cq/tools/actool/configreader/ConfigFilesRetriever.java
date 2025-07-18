package biz.netcentric.cq.tools.actool.configreader;

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

import java.util.Collection;
import java.util.Map;

import javax.jcr.Session;

import org.apache.jackrabbit.vault.fs.io.Archive;

/** Retrieves the contents of a AC tool yaml config file from either a package directly (used by install hook) or from the JCR node structure
 * (used by JMX). Both methods return a map with filename-&gt;yaml-config-content entries.
 *
 * @author ghenzler */
public interface ConfigFilesRetriever {

    /** Returns yaml configurations using a given root node. This will only return configuration entries which apply to the current run
     * mode.
     *
     * @param rootPath the root path in the JCR to start looking for yaml-files
     * @param session
     * @return map of yaml configurations by their path location
     * @throws Exception if things go wrong */
    Map<String, String> getConfigFileContentFromNode(String rootPath, Session session) throws Exception;

    /** Returns yaml configurations from a package.  This will only return configuration entries which apply to the current run mode
     *
     * @param archive the Vault Package
     * @param configFilePatterns collection of regular expression to limit which configuration files are used.
     * @return map of yaml configurations by their path location
     * @throws Exception if things go wrong */
    Map<String, String> getConfigFileContentFromPackage(Archive archive, Collection<String> configFilePatterns) throws Exception;

}
