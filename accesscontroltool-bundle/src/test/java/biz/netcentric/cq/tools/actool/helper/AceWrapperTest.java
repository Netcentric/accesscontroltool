package biz.netcentric.cq.tools.actool.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.jcr.RepositoryException;
import javax.jcr.Value;

import org.apache.jackrabbit.api.security.JackrabbitAccessControlEntry;
import org.apache.jackrabbit.oak.spi.security.privilege.PrivilegeConstants;
import org.junit.Test;

/** Tests the AceWrapper
 * 
 * @author Roland Gruber */
public class AceWrapperTest {

    @Test
    public void testGetRestrictionAsString() throws RepositoryException {
        final JackrabbitAccessControlEntry ace = mock(JackrabbitAccessControlEntry.class);
        final AceWrapper wrapper = new AceWrapper(ace, "/content");
        // no restriction
        assertNull(wrapper.getRestrictionAsString(PrivilegeConstants.JCR_READ));
        // null string restriction
        final Value val = mock(Value.class);
        when(val.getString()).thenReturn(null);
        when(ace.getRestriction(PrivilegeConstants.JCR_READ)).thenReturn(val);
        assertNull(wrapper.getRestrictionAsString(PrivilegeConstants.JCR_READ));
        // empty string restriction
        when(val.getString()).thenReturn("");
        assertEquals("", wrapper.getRestrictionAsString(PrivilegeConstants.JCR_READ));
        // restriction with value
        when(val.getString()).thenReturn("/jcr:*");
        assertEquals("/jcr:*", wrapper.getRestrictionAsString(PrivilegeConstants.JCR_READ));
    }

}
