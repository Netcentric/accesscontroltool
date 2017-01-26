/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.validators;

import java.util.Map;
import java.util.Set;

import biz.netcentric.cq.tools.actool.configmodel.AceBean;

public interface ConfigurationsValidator {

    /**
     * Method that checks if a group in the current configuration file was
     * already defined in another configuration file which has been already
     * processed
     * 
     * @param authorizablesFromAllConfig
     *            set holding all names of groups of all config files which have
     *            already been processed
     * @param authorizablesFromCurrentConfig
     *            set holding all names of the groups from the current
     *            configuration
     * @param configPath
     *            repository path of current config
     */
    public abstract void validateDuplicateAuthorizables(Set<String> authorizablesFromAllConfig,
            Set<String> authorizablesFromCurrentConfig, String configPath)
            throws IllegalArgumentException;

    /**
     * Method that checks if only valid configuration section identifiers (group
     * (and optional users) and ACE) exist in the current configuration file
     */
    public abstract void validateSectionIdentifiers(
            Set<String> sectionIdentifiers, String filePath)
            throws IllegalArgumentException;

    /** Checks that no duplicate initialContent property is set
     * 
     * @param history */
    public abstract void validateInitialContentForNoDuplicates(
            Map<String, Set<AceBean>> mergedAceMapFromConfig) throws IllegalArgumentException;

    /** Checks that keepOrder=true must be specified in one file for one given path (to ensure the natural order for an ACL can not span
     * multiple files, include order of multiple files may vary) */
    public abstract void validateKeepOrder(Map<String, Set<AceBean>> aceMapFromAllConfigs,
            Map<String, Set<AceBean>> aceMapFromCurrentConfig,
            String sourceFile);

}