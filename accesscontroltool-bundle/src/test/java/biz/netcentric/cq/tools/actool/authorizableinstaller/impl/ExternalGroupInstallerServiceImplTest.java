package biz.netcentric.cq.tools.actool.authorizableinstaller.impl;

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
