package biz.netcentric.cq.tools.actool.externalusermanagement;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2024 Cognizant Netcentric
 * %%
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * #L%
 */

import java.io.IOException;
import java.util.Collection;

import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;

/**
 * Implementations of this service synchronize (i.e. create/update/delete) groups in an external directory (outside AEM).
 */
public interface ExternalGroupManagement {
    /**
     * Updates the groups in the external directory.
     * @param groupConfigs the groups to be updated
     * @return the effective number of groups updated (may be less than the number of groups in {@code groupConfigs}) if some are considered up to date
     * @throws IOException
     */
    int updateGroups(Collection<AuthorizableConfigBean> groupConfigs) throws IOException;

    /**
     * 
     * @return a label for the external group management tool (e.g. IMS)
     */
    String getLabel();
}
