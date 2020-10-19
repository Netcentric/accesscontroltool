package biz.netcentric.cq.tools.actool.authorizableinstaller.impl;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

import java.util.Set;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.api.security.user.Authorizable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NotRegularMembersTest {

    NotRegularMembers notRegularMembers;

    @Mock
    private Authorizable regularMember;

    @Mock
    private Authorizable nonRegularMember;

    @Mock
    private Authorizable errorMember;

    @Before
    public void setUp() throws Exception {
        notRegularMembers = new NotRegularMembers();
    }

    @Test
    public void testEmptySetReturned() throws RepositoryException {
        final Set<String> members = notRegularMembers.getSet();

        assertEquals(0, members.size());
    }

    @Test
    public void testRegularMemberIsDiscarded() throws RepositoryException {
        when(regularMember.isGroup()).thenReturn(false);
        when(regularMember.getPath()).thenReturn("/home/users/a/b/c");
        when(regularMember.getID()).thenReturn("regular-member");

        notRegularMembers.addOrDiscard(regularMember);

        final Set<String> members = notRegularMembers.getSet();
        assertEquals(0, members.size());
    }

    @Test
    public void testNonRegularMemberIsAdded() throws RepositoryException {
        when(nonRegularMember.isGroup()).thenReturn(false);
        when(nonRegularMember.getPath()).thenReturn("/home/users/system/nc");
        when(nonRegularMember.getID()).thenReturn("non-regular-member");

        notRegularMembers.addOrDiscard(nonRegularMember);

        final Set<String> members = notRegularMembers.getSet();
        assertEquals(1, members.size());
        assertEquals("non-regular-member", members.iterator().next());
    }

    @Test
    public void testExceptionIsThrow() throws RepositoryException {
        when(errorMember.getPath()).thenThrow(new RepositoryException());

        notRegularMembers.addOrDiscard(errorMember);
        try {
            final Set<String> members = notRegularMembers.getSet();
            fail("Should throw exception at this point.");
        } catch (final RepositoryException e) {
            //exception is thrown correctly.
        }
    }

    @Test
    public void testAddingMemberAfterGettingTheSet() throws RepositoryException {
        when(nonRegularMember.isGroup()).thenReturn(false);
        when(nonRegularMember.getPath()).thenReturn("/home/users/system/nc");
        when(nonRegularMember.getID()).thenReturn("non-regular-member");

        notRegularMembers.addOrDiscard(nonRegularMember);

        final Set<String> members = notRegularMembers.getSet();
        assertEquals(1, members.size());
        assertEquals("non-regular-member", members.iterator().next());
    }
}