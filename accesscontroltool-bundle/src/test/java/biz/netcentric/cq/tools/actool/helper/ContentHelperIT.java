/*
 * (C) Copyright 2023 Cognizant Netcentric.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import javax.jcr.AccessDeniedException;
import javax.jcr.InvalidSerializedDataException;
import javax.jcr.ItemExistsException;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.version.VersionException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.extensions.OakRepository;
import biz.netcentric.cq.tools.actool.history.impl.PersistableInstallationLogger;

@ExtendWith(OakRepository.class)
class ContentHelperIT {

    @Test
    void testImportContentInvalid(Session session) throws RepositoryException {
        // dam prefix undefined, still the root node is expected to be created
        String invalidDocViewXml = "<jcr:root jcr:primaryType='nt:unstructured'>\n"
                + "                      <jcr:content\n"
                + "                          jcr:primaryType='nt:unstructured'\n"
                + "                          jcr:title='Digital Bottle Collection'\n"
                + "                          dam:disableImageSmartTag='true'\n"
                + "                          dam:disableVideoSmartTag='true'\n"
                + "                          metadataSchema='System Default'\n"
                + "                          dam:disableImageSmartTag='true'\n"
                + "                          s7-sync-mode='inherit'\n"
                + "                          processingProfile='System Default'\n"
                + "                          cq:conf=''\n"
                + "                          dam:disableColorExtraction='true'\n"
                + "                          postProcessWF='System Default'\n"
                + "                          dam:disableTextSmartTag='true'\n"
                + "                          sourcing='false'/>\n"
                + "                  </jcr:root>";
        assertThrows(Exception.class, () -> {ContentHelper.importContent(session, "/tmp", invalidDocViewXml);});
        assertTrue(session.nodeExists("/tmp"), "Root node is expected to be created in a best effort manner");
    }

    @Test
    void testCreateInitialContent(Session session) throws AccessDeniedException, PathNotFoundException, ItemExistsException, ConstraintViolationException, VersionException, InvalidSerializedDataException, LockException, RepositoryException {
        PersistableInstallationLogger logger = new PersistableInstallationLogger();
        // non existing initial content
        assertFalse(session.nodeExists("/tmp"), "node must not exist prior to creating initial content");
        AceBean aceBean = new AceBean();
        assertFalse(ContentHelper.createInitialContent(session, logger, "/tmp", Collections.singleton(aceBean)));
        assertTrue(logger.getWarnings().isEmpty());
        assertTrue(logger.isSuccess());
        assertFalse(session.nodeExists("/tmp/test"), "Nothing should have been imported");

        // initial content with valid XML for non existing parent
        session.refresh(false);
        logger = new PersistableInstallationLogger();
        aceBean.setInitialContent("<jcr:root jcr:primaryType='nt:unstructured'></jcr:root>");
        aceBean.setJcrPath("/tmp/test");
        assertFalse(ContentHelper.createInitialContent(session, logger, "/tmp/test", Collections.singleton(aceBean)));
        assertTrue(logger.getWarnings().isEmpty());
        assertTrue(logger.isSuccess());
        assertEquals(1, logger.getMissingParentPathsForInitialContent());
        assertFalse(session.nodeExists("/tmp/test"), "Nothing should have been imported");

        // initial content with valid XML for existing parent
        session.refresh(false);
        session.getRootNode().addNode("tmp", "nt:unstructured");
        logger = new PersistableInstallationLogger();
        assertTrue(ContentHelper.createInitialContent(session, logger, "/tmp/test", Collections.singleton(aceBean)));
        assertTrue(logger.getWarnings().isEmpty());
        assertTrue(logger.isSuccess());
        assertTrue(session.nodeExists("/tmp/test"), "Root node is expected to be created");

        // initial content with invalid xml
        session.refresh(false);
        // due to https://issues.apache.org/jira/browse/JCRVLT-690 the session might have been committed nevertheless
        Node testNode = session.getNode("/tmp/test");
        testNode.remove();
        session.save();

        assertFalse(session.nodeExists("/tmp/test"), "node '/tmp' must not exist prior to creating initial content");
        String invalidDocViewXml = "<jcr:root jcr:primaryType='nt:unstructured'>\n"
                + "                      <jcr:content\n"
                + "                          jcr:primaryType='nt:unstructured'\n"
                + "                          jcr:title='Digital Bottle Collection'\n"
                + "                          dam:disableImageSmartTag='true'\n"
                + "                          dam:disableVideoSmartTag='true'\n"
                + "                          metadataSchema='System Default'\n"
                + "                          dam:disableImageSmartTag='true'\n"
                + "                          s7-sync-mode='inherit'\n"
                + "                          processingProfile='System Default'\n"
                + "                          cq:conf=''\n"
                + "                          dam:disableColorExtraction='true'\n"
                + "                          postProcessWF='System Default'\n"
                + "                          dam:disableTextSmartTag='true'\n"
                + "                          sourcing='false'/>\n"
                + "                  </jcr:root>";
        aceBean.setInitialContent(invalidDocViewXml);
        logger = new PersistableInstallationLogger();
        assertFalse(ContentHelper.createInitialContent(session, logger, "/tmp/test", Collections.singleton(aceBean)));
        assertTrue(logger.getWarnings().isEmpty());
        assertFalse(logger.isSuccess());
        assertTrue(session.nodeExists("/tmp/test"), "Root node is expected to be created in a best effort manner");
    }
}
