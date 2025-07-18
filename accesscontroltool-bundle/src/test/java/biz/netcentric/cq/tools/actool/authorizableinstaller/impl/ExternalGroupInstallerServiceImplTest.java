package biz.netcentric.cq.tools.actool.authorizableinstaller.impl;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2025 Cognizant Netcentric
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;

class ExternalGroupInstallerServiceImplTest {

    @Test
    void testGetExternalId() {
        AuthorizableConfigBean groupAuthConfigBean = new AuthorizableConfigBean();
        groupAuthConfigBean.setAuthorizableId("test-group-id");
        groupAuthConfigBean.setName("my-name");
        groupAuthConfigBean.setPath("/path/to/group");
        groupAuthConfigBean.setExternalId("%{group.id};ims");
        assertEquals("test-group-id;ims", ExternalGroupInstallerServiceImpl.getExternalId(groupAuthConfigBean));
    }

}
