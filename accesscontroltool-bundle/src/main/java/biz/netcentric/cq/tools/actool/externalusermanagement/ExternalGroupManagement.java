package biz.netcentric.cq.tools.actool.externalusermanagement;

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

import java.io.IOException;
import java.util.Collection;

import biz.netcentric.cq.tools.actool.api.InstallationOptions;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;

/**
 * Implementations of this service synchronize (i.e. create/update/delete) groups in an external directory (outside AEM).
 */
public interface ExternalGroupManagement {
    /**
     * Updates the groups in the external directory.
     * @param groupConfigs the groups to be updated
     * @param options the installation options
     * @return the effective number of groups updated (may be less than the number of groups in {@code groupConfigs}) if some are considered up to date
     * @throws IOException
     */
    int updateGroups(Collection<AuthorizableConfigBean> groupConfigs, InstallationOptions options) throws IOException;

    /**
     * 
     * @return a label for the external group management tool (e.g. IMS)
     */
    String getLabel();
}
