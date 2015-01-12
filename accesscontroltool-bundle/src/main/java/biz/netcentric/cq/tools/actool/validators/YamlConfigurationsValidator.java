/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.validators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;

import biz.netcentric.cq.tools.actool.helper.Constants;

public class YamlConfigurationsValidator implements ConfigurationsValidator {

    /*
     * (non-Javadoc)
     * 
     * @see biz.netcentric.cq.tools.actool.validators.ConfigurationsValidator#
     * validateDoubleGroups(java.util.Set, java.util.Set, java.lang.String)
     */
    @Override
    public void validateDoubleGroups(final Set<String> groupsFromAllConfig,
            final Set<String> groupsFromCurrentConfig, final String configPath)
            throws IllegalArgumentException {

        if (CollectionUtils.containsAny(groupsFromAllConfig,
                groupsFromCurrentConfig)) {
            String errorMessage = "already defined group: ";

            // find the name of the doubled defined group and add it to error
            // message
            for (String group : groupsFromCurrentConfig) {
                if (groupsFromAllConfig.contains(group)) {
                    errorMessage = errorMessage + group
                            + " found in configuration file: " + configPath
                            + "!";
                    errorMessage = errorMessage
                            + " This group was already defined in another configuration file on the system!";
                    break;
                }
            }
            throw new IllegalArgumentException(errorMessage);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see biz.netcentric.cq.tools.actool.validators.ConfigurationsValidator#
     * validateSectionContentExistence(java.lang.String, java.util.Collection)
     */
    @Override
    public void validateSectionContentExistence(final String configPath,
            final Collection configurations) throws IllegalArgumentException {
        List<LinkedHashMap> yamlList = new ArrayList<LinkedHashMap>(
                configurations);
        /*
        if (yamlList.get(0).get(Constants.GROUP_CONFIGURATION_KEY) == null) {
            throw new IllegalArgumentException("Empty "
                    + Constants.GROUP_CONFIGURATION_KEY
                    + " section in configuration file: " + configPath);
        }
        if (yamlList.get(1).get(Constants.ACE_CONFIGURATION_KEY) == null) {
            throw new IllegalArgumentException("Empty "
                    + Constants.ACE_CONFIGURATION_KEY
                    + " section in configuration file: " + configPath);
        }
        */
    }

    /*
     * (non-Javadoc)
     * 
     * @see biz.netcentric.cq.tools.actool.validators.ConfigurationsValidator#
     * validateMandatorySectionIdentifiersExistence(java.lang.String,
     * java.lang.String)
     */
    @Override
    public void validateMandatorySectionIdentifiersExistence(
            final String configuration, final String filePath) {
        // check if mandatory section identifiers are there

        /*
        if (!configuration.contains(Constants.GROUP_CONFIGURATION_KEY)
                && !configuration.contains(Constants.USER_CONFIGURATION_KEY)) {
            throw new IllegalArgumentException(
                    Constants.GROUP_CONFIGURATION_KEY
                            + " section identifier ('"
                            + Constants.GROUP_CONFIGURATION_KEY
                            + "') missing in configuration file: " + filePath);
        }
        if (!configuration.contains(Constants.ACE_CONFIGURATION_KEY)) {
            throw new IllegalArgumentException(Constants.ACE_CONFIGURATION_KEY
                    + "section identifier ('" + Constants.ACE_CONFIGURATION_KEY
                    + "') missing in configuration file: " + filePath);
        }
        */
    }

    /*
     * (non-Javadoc)
     * 
     * @see biz.netcentric.cq.tools.actool.validators.ConfigurationsValidator#
     * validateSectionIdentifiers(java.util.Set, java.lang.String)
     */
    @Override
    public void validateSectionIdentifiers(
            final Set<String> sectionIdentifiers, final String filePath)
            throws IllegalArgumentException {

        // check for invalid section identifiers

        if (!Constants.VALID_CONFIG_SECTION_IDENTIFIERS
                .containsAll(sectionIdentifiers)) {

            for (String identifier : sectionIdentifiers) {
                if (!Constants.VALID_CONFIG_SECTION_IDENTIFIERS
                        .contains(identifier)) {
                    throw new IllegalArgumentException(
                            "invalid section identifier: "
                                    + identifier
                                    + " in configuration file: "
                                    + filePath
                                    + "\n"
                                    + "valid configuration section identifiers are: "
                                    + Constants.VALID_CONFIG_SECTION_IDENTIFIERS);
                }
            }
        }
    }
}
