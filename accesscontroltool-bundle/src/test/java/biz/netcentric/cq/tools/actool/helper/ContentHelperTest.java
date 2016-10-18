package biz.netcentric.cq.tools.actool.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collection;

import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.junit.Test;

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
