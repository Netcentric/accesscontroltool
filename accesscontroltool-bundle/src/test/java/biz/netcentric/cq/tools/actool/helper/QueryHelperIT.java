/*
 * (C) Copyright 2023 Cognizant Netcentric.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.helper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import javax.jcr.Node;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import biz.netcentric.cq.tools.actool.extensions.OakRepository;

class QueryHelperIT {

    @RegisterExtension
    static final OakRepository repository = new OakRepository(false);

    @Test
    void testHasQueryIndexForACLsWithoutIndex(Session session) throws RepositoryException, IOException {
        // adjust nodetype index definition (at /oak:index/nodetype) to reflect what is configured in AEM
        Node ntIndexDefNode = session.getNode("/oak:index/nodetype");
        ntIndexDefNode.setProperty("declaringNodeTypes", new String[] { "oak:QueryIndexDefinition", "rep:User", "rep:Authorizable" }, PropertyType.NAME);
        session.save();
        assertFalse(QueryHelper.hasQueryIndexForACLs(session));
    }

    @Test
    void testHasQueryIndexForACLsWithIndex(Session session) throws RepositoryException, IOException {
        // adjust nodetype index definition (at /oak:index/nodetype) to include rep:ACL
        // this is a different type than shipped with ACTool (Lucene), but lucene based index providers are hard to set up in an IT
        Node ntIndexDefNode = session.getNode("/oak:index/nodetype");
        ntIndexDefNode.setProperty("declaringNodeTypes", new String[] { "oak:QueryIndexDefinition", "rep:User", "rep:Authorizable", "rep:ACL" }, PropertyType.NAME);
        session.save();
        assertTrue(QueryHelper.hasQueryIndexForACLs(session));
    }

}
