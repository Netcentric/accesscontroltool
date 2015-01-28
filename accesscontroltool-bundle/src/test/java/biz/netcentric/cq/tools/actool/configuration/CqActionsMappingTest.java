/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configuration;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import biz.netcentric.cq.tools.actool.helper.AceBean;

public class CqActionsMappingTest {
    AceBean aceBean1;
    AceBean aceBean2;
    AceBean aceBean3;

    @Before
    public void setup() {
        // ACEs from groups contained in config

        aceBean1 = new AceBean();
        aceBean1.setPrincipal("group-A");
        aceBean1.setActions(null);
        aceBean1.setActionsStringFromConfig("");
        aceBean1.setPermission("deny");
        aceBean1.setJcrPath("/content");
        aceBean1.setPrivilegesString("jcr:read,crx:replicate");
        aceBean1.setRepGlob("");

        aceBean2 = new AceBean();
        aceBean2.setPrincipal("group-A");
        aceBean2.setActions(new String[] { "read", "replicate" });
        aceBean1.setPermission("deny");
        aceBean2.setJcrPath("/content");
        aceBean2.setPrivilegesString("");
        aceBean2.setRepGlob("");

        aceBean3 = new AceBean();

    }

    @Test
    public void getConvertedPrivilegeBeanTest() {
        aceBean2 = CqActionsMapping.getConvertedPrivilegeBean(aceBean2);

        assertArrayEquals(aceBean1.getActions(), aceBean2.getActions());
        assertEquals(aceBean1.getPrivilegesString(),
                aceBean2.getPrivilegesString());
    }

    @Test
    public void getAggregatedPrivilegesBeanTest() {
        aceBean3.setPrivilegesString("jcr:addChildNodes,jcr:removeNode,jcr:modifyProperties,jcr:removeChildNodes");
        CqActionsMapping.getAggregatedPrivilegesBean(aceBean3);
        assertEquals(aceBean3.getPrivilegesString(), "jcr:write");

        aceBean3.setPrivilegesString("jcr:addChildNodes,jcr:removeNode,jcr:modifyProperties,jcr:removeChildNodes,jcr:write");
        CqActionsMapping.getAggregatedPrivilegesBean(aceBean3);
        assertEquals(aceBean3.getPrivilegesString(), "jcr:write");

        aceBean3.setPrivilegesString("jcr:workspaceManagement,jcr:lifecycleManagement,jcr:versionManagement,jcr:lockManagement,crx:replicate,jcr:read,jcr:modifyAccessControl,rep:write,rep:privilegeManagement,rep:userManagement,jcr:nodeTypeManagement,jcr:namespaceManagement,jcr:write,jcr:nodeTypeDefinitionManagement,jcr:retentionManagement,jcr:readAccessControl");
        CqActionsMapping.getAggregatedPrivilegesBean(aceBean3);
        assertEquals(aceBean3.getPrivilegesString(), "jcr:all");

        aceBean3.setPrivilegesString("jcr:modifyProperties,jcr:addChildNodes,jcr:removeNode,jcr:removeChildNodes,jcr:nodeTypeManagement");
        CqActionsMapping.getAggregatedPrivilegesBean(aceBean3);
        assertEquals(aceBean3.getPrivilegesString(), "rep:write");
    }
}