/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.validators;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.helper.AcHelper;
import biz.netcentric.cq.tools.actool.helper.Constants;

public class YamlConfigurationsValidator implements ConfigurationsValidator {

    /*
     * (non-Javadoc)
     * 
     * @see biz.netcentric.cq.tools.actool.validators.ConfigurationsValidator# validateDoubleGroups(java.util.Set, java.util.Set,
     * java.lang.String)
     */
    @Override
    public void validateDuplicateAuthorizables(final Set<String> groupsFromAllConfig,
            final Set<String> groupsFromCurrentConfig, final String configPath)
                    throws IllegalArgumentException {

        if (CollectionUtils.containsAny(groupsFromAllConfig,
                groupsFromCurrentConfig)) {
            String errorMessage = "Already defined authorizable: ";

            // find the name of the doubled defined group and add it to error
            // message
            for (String group : groupsFromCurrentConfig) {
                if (groupsFromAllConfig.contains(group)) {
                    errorMessage = errorMessage + group
                            + " found in configuration file: " + configPath
                            + "!";
                    errorMessage += " This authorizable was already defined in another configuration file on the system!";
                    break;
                }
            }
            throw new IllegalArgumentException(errorMessage);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see biz.netcentric.cq.tools.actool.validators.ConfigurationsValidator# validateSectionIdentifiers(java.util.Set, java.lang.String)
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

    @Override
    public void validateInitialContentForNoDuplicates(Set<AceBean> aceBeansFromConfig)
            throws IllegalArgumentException {

        Map<String, Set<AceBean>> pathBasedAceMapFromConfig = AcHelper
                .getPathBasedAceMap(aceBeansFromConfig, AcHelper.ACE_ORDER_ACTOOL_BEST_PRACTICE);

        for (String path : pathBasedAceMapFromConfig.keySet()) {
            Set<AceBean> aceBeanSet = pathBasedAceMapFromConfig.get(path);
            String initialContent = null;
            for (AceBean aceBean : aceBeanSet) {
                if (StringUtils.isNotBlank(aceBean.getInitialContent())) {
                    if (initialContent == null) {
                        initialContent = aceBean.getInitialContent();
                    } else {
                        throw new IllegalArgumentException("Duplicate 'initialContent' for path " + path);
                    }
                }
            }
        }
    }

    @Override
    public void validateKeepOrder(Set<AceBean> aceBeansFromAllConfigs, Set<AceBean> aceBeansFromCurrentConfig,
            String sourceFile) {

        Set<String> pathsWithKeepOrderSet = new LinkedHashSet<String>();
        for (AceBean aceBean : aceBeansFromAllConfigs) {
            if (aceBean.isKeepOrder()) {
                pathsWithKeepOrderSet.add(aceBean.getJcrPath());
            }
        }

        if (aceBeansFromCurrentConfig != null) {
            for (AceBean aceBean : aceBeansFromCurrentConfig) {
                if (aceBean.isKeepOrder() && pathsWithKeepOrderSet.contains(aceBean.getJcrPath())) {
                    throw new IllegalArgumentException(
                            "If keepOrder=true is used, the ACE definitions for one particular path must only be defined in one source file (ACE for "
                                    + aceBean.getJcrPath() + " and group " + aceBean.getAuthorizableId() + " as defined in " + sourceFile
                                    + " was defined before) ");
                }
            }
        }

    }
}
