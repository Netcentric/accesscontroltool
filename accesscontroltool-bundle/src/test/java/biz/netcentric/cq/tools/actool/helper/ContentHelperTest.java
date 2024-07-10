
package biz.netcentric.cq.tools.actool.helper;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;

import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.junit.jupiter.api.Test;

import biz.netcentric.cq.tools.actool.helper.ContentHelper.SingleContentFileArchive.SingleContentFileArchiveEntry;

public class ContentHelperTest {

    @Test
    public void testSingleContentFileArchiveEntry() {

        Entry root = new SingleContentFileArchiveEntry("/jcr_root/pathToFile/.content.xml");

        Collection<? extends Entry> children = root.getChildren();
        assertEquals(1, children.size());
        Entry jcrRootEntry = children.iterator().next();
        assertEquals("jcr_root", jcrRootEntry.getName());
        assertTrue(jcrRootEntry.isDirectory());

        Collection<? extends Entry> childrenOfJcrRoot = jcrRootEntry.getChildren();
        assertEquals(1, childrenOfJcrRoot.size());
        Entry pathEntry = childrenOfJcrRoot.iterator().next();
        assertEquals("pathToFile", pathEntry.getName());
        assertTrue(pathEntry.isDirectory());

        Collection<? extends Entry> childrenOfPathEntry = pathEntry.getChildren();
        assertEquals(1, childrenOfPathEntry.size());
        Entry contenEntry = childrenOfPathEntry.iterator().next();
        assertEquals(".content.xml", contenEntry.getName());
        assertFalse(contenEntry.isDirectory());

        Collection<? extends Entry> childrenOfContentEntry = contenEntry.getChildren();
        assertNull(childrenOfContentEntry);

    }

}
