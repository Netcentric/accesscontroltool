package biz.netcentric.cq.tools.actool.impl;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@RunWith(MockitoJUnitRunner.class)
public class AcInstallationServiceImplTest {

    AcInstallationServiceImpl acInstallationServiceImpl = new AcInstallationServiceImpl();

    @Mock
    Group group1;

    @Mock
    Group group2;

    @Mock
    Group group3;

    @Mock
    Group group4;

    @Mock
    Group group5;

    @Mock
    Group group6;

    @Mock
    Group group7;

    @Mock
    User user1;

    @Mock
    User user2;

    @Test
    public void testSortAuthorizablesForDeletion() throws RepositoryException {

        when(group1.getID()).thenReturn("group1");
        when(group2.getID()).thenReturn("group2");
        when(group3.getID()).thenReturn("group3");
        when(group4.getID()).thenReturn("group4");
        when(group5.getID()).thenReturn("group5");
        when(group6.getID()).thenReturn("group6");
        when(user1.getID()).thenReturn("user1");
        when(user2.getID()).thenReturn("user2");

        when(group4.declaredMemberOf()).thenAnswer(GroupIteratorAnswer.forGroups(group3, group2));

        when(user1.declaredMemberOf()).thenAnswer(GroupIteratorAnswer.forGroups(group1, group5));

        when(group1.declaredMemberOf()).thenAnswer(GroupIteratorAnswer.forGroups(group4));

        when(group5.declaredMemberOf()).thenAnswer(GroupIteratorAnswer.forGroups(group1, group2, group3));

        when(user2.declaredMemberOf()).thenAnswer(GroupIteratorAnswer.forGroups(group2, group3));

        when(group2.declaredMemberOf()).thenAnswer(GroupIteratorAnswer.forGroups(group3));


        List<Authorizable> authsToDelete = Arrays.<Authorizable> asList(group1, group2, group3, group4, group5, group6, user1, user2);
        acInstallationServiceImpl.sortAuthorizablesForDeletion(authsToDelete);

        assertTrue(authsToDelete.indexOf(group4) < authsToDelete.indexOf(group3));
        assertTrue(authsToDelete.indexOf(group4) < authsToDelete.indexOf(group2));

        assertTrue(authsToDelete.indexOf(user1) < authsToDelete.indexOf(group1));
        assertTrue(authsToDelete.indexOf(user1) < authsToDelete.indexOf(group5));

        assertTrue(authsToDelete.indexOf(group1) < authsToDelete.indexOf(group4));

        assertTrue(authsToDelete.indexOf(group5) < authsToDelete.indexOf(group1));
        assertTrue(authsToDelete.indexOf(group5) < authsToDelete.indexOf(group2));
        assertTrue(authsToDelete.indexOf(group5) < authsToDelete.indexOf(group3));

        assertTrue(authsToDelete.indexOf(user2) < authsToDelete.indexOf(group2));
        assertTrue(authsToDelete.indexOf(user2) < authsToDelete.indexOf(group3));

        assertTrue(authsToDelete.indexOf(group2) < authsToDelete.indexOf(group3));

    }

    private static class GroupIteratorAnswer implements Answer<Iterator<Group>> {

        static GroupIteratorAnswer forGroups(Group... groups) {
            return new GroupIteratorAnswer(Arrays.asList(groups));
        }

        List<Group> groups;

        public GroupIteratorAnswer(List<Group> groups) {
            this.groups = groups;
        }

        @Override
        public Iterator<Group> answer(InvocationOnMock invocation) throws Throwable {

            return groups.iterator();
        }
    }
}
