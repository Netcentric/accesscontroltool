/*
 * (C) Copyright 2017 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.aceinstaller;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.InputStream;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.jcr.Binary;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFormatException;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.Privilege;

import org.apache.jackrabbit.api.security.JackrabbitAccessControlEntry;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlManager;
import org.apache.jackrabbit.oak.spi.security.principal.PrincipalImpl;
import org.apache.sling.jcr.api.SlingRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import biz.netcentric.cq.tools.actool.aem.AemCqActionsSupportImpl;
import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.configmodel.Restriction;
import biz.netcentric.cq.tools.actool.configreader.YamlConfigReader;
import biz.netcentric.cq.tools.actool.history.InstallationLogger;

@RunWith(MockitoJUnitRunner.class)
public class AceBeanInstallerIncrementalTest {

    private static final String FAKE_PRINCIPAL_ID = "author";

    String testPath = "/content/testpath";
    String testPrincipal1 = "testPrincipal1";
    String testPrincipal2 = "testPrincipal2";
    String testPrincipal3 = "testPrincipal3";

    AceBean bean1 = createTestBean(testPath, testPrincipal1, true, "jcr:read", "");
    AceBean bean2 = createTestBean(testPath, testPrincipal2, true, "jcr:read,jcr:lockManagement,jcr:versionManagement,rep:write", "");
    AceBean bean2Content = createTestBean(testPath, testPrincipal2, true,
            "jcr:addChildNodes,jcr:nodeTypeManagement,jcr:removeChildNodes,jcr:removeNode", "",
            new Restriction(AceBean.RESTRICTION_NAME_GLOB, "*/jcr:content*"));
    AceBean bean3 = createTestBean(testPath, testPrincipal3, true, "rep:write", "");

    AceBean beanWithAction1 = createTestBean(testPath, testPrincipal1, true, "", "read");
    AceBean beanWithAction2 = createTestBean(testPath, testPrincipal2, true, "", "read,create,modify,delete");

    @Spy
    @InjectMocks
    AceBeanInstallerIncremental aceBeanInstallerIncremental;

    @Spy
    InstallationLogger installLog;

    @Mock
    JackrabbitAccessControlList jackrabbitAccessControlList;

    @Mock
    Session session;

    @Mock
    JackrabbitAccessControlManager accessControlManager;

    @Mock
    SlingRepository slingRepository;

    @Spy
    AemCqActionsSupportImpl aemCqActionsSupport  = new AemCqActionsSupportImpl();
    
    @Before
    public void setup() throws RepositoryException {

        doReturn(accessControlManager).when(session).getAccessControlManager();

        // empty by default
        doReturn(new JackrabbitAccessControlEntry[0]).when(jackrabbitAccessControlList).getAccessControlEntries();
        doReturn(testPath).when(jackrabbitAccessControlList).getPath();

        doReturn(jackrabbitAccessControlList).when(aceBeanInstallerIncremental).getAccessControlList(eq(accessControlManager), anyString());

        doReturn(true).when(aceBeanInstallerIncremental).installPrivileges(any(AceBean.class), any(Principal.class),
                eq(jackrabbitAccessControlList), eq(session), eq(accessControlManager));

        // default privilege is a simple privilege with the given string name
        doAnswer(new Answer<Privilege>() {
            public Privilege answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();   
                return new TestPrivilege(args[0].toString());
            }
        }).when(accessControlManager).privilegeFromName(anyString());
        
        // to test aggregates
        doReturn(new TestPrivilege("jcr:read", new String[] { "jcr:readNodes", "jcr:readProperties" })).when(accessControlManager)
                .privilegeFromName("jcr:read");

        // easier to mock than the static SessionUtil.clone()
        doReturn(session).when(slingRepository).loginService(null, null);

        doReturn(true).when(aceBeanInstallerIncremental).definesContent(testPath, session);

        doReturn(new PrincipalImpl(FAKE_PRINCIPAL_ID)).when(aceBeanInstallerIncremental).getTestActionMapperPrincipal();
        doNothing().when(aceBeanInstallerIncremental).applyCqActions(any(AceBean.class), any(Session.class), any(Principal.class));
    }

    @Test
    public void testPrivilegesToComparableSet() throws RepositoryException {

        // ensure mocking is correc
        assertFalse(accessControlManager.privilegeFromName("jcr:removeNode").isAggregate()); // default mocking
        assertFalse(accessControlManager.privilegeFromName("jcr:lockManagement").isAggregate()); // default mocking
        assertTrue(accessControlManager.privilegeFromName("jcr:read").isAggregate()); // aggragate test mocking

        assertEquals("simple non-aggregate must equal",
                "[jcr:lockManagement]",
                createComparablePrivSet("jcr:lockManagement"));

        assertEquals("simple aggregate must be resolved to non-aggregates",
                "[jcr:readNodes, jcr:readProperties]",
                createComparablePrivSet("jcr:read"));

        assertEquals("non-aggregate order is sorted (test order un-changed)",
                "[jcr:lockManagement, jcr:removeNode]",
                createComparablePrivSet("jcr:lockManagement, jcr:removeNode"));

        assertEquals("non-aggregate order is sorted (test order changed)",
                "[jcr:lockManagement, jcr:removeNode]",
                createComparablePrivSet("jcr:removeNode, jcr:lockManagement"));

        assertEquals("privilege order not important even for mix of aggregate and non-aggregate privs (must be still equal)",
                "[jcr:lockManagement, jcr:readNodes, jcr:readProperties, jcr:removeNode]",
                createComparablePrivSet("jcr:removeNode, jcr:read, jcr:lockManagement"));

    }

    @Test
    public void testSimplePrivilegesAcesAdditive() throws Exception {

        aceBeanInstallerIncremental.installAcl(
                asSet(bean1, bean2, bean3), testPath,
                asSet(testPrincipal1, testPrincipal2, testPrincipal3), session, installLog);

        verify(jackrabbitAccessControlList, never()).removeAccessControlEntry(any(JackrabbitAccessControlEntry.class));

        verify(aceBeanInstallerIncremental).installPrivileges(eq(bean1), eq(new PrincipalImpl(testPrincipal1)),
                eq(jackrabbitAccessControlList), eq(session), eq(accessControlManager));
        verify(aceBeanInstallerIncremental).installPrivileges(eq(bean2), eq(new PrincipalImpl(testPrincipal2)),
                eq(jackrabbitAccessControlList), eq(session), eq(accessControlManager));
        verify(aceBeanInstallerIncremental).installPrivileges(eq(bean3), eq(new PrincipalImpl(testPrincipal3)),
                eq(jackrabbitAccessControlList), eq(session), eq(accessControlManager));

    }

    @Test
    public void testSimplePrivilegesAcesUnchanged() throws Exception {

        // make bean1 and bean
        doReturn(new JackrabbitAccessControlEntry[] {
                aceBeanToAce(bean1), aceBeanToAce(bean2), aceBeanToAce(bean3)
        }).when(jackrabbitAccessControlList).getAccessControlEntries();

        aceBeanInstallerIncremental.installAcl(
                asSet(bean1, bean2, bean3), testPath,
                asSet(testPrincipal1, testPrincipal2, testPrincipal3), session, installLog);

        verify(jackrabbitAccessControlList, never()).removeAccessControlEntry(any(JackrabbitAccessControlEntry.class));

        verify(aceBeanInstallerIncremental, never()).installPrivileges(any(AceBean.class), any(Principal.class),
                any(JackrabbitAccessControlList.class), any(Session.class), any(AccessControlManager.class));

    }

    @Test
    public void testSimplePrivilegesAcesRemoved() throws Exception {

        // make bean1 and bea
        JackrabbitAccessControlEntry ace1 = aceBeanToAce(bean1);
        JackrabbitAccessControlEntry ace2 = aceBeanToAce(bean2);
        JackrabbitAccessControlEntry ace3 = aceBeanToAce(bean3);
        doReturn(new JackrabbitAccessControlEntry[] { ace1, ace2, ace3 }).when(jackrabbitAccessControlList).getAccessControlEntries();

        aceBeanInstallerIncremental.installAcl(
                Collections.<AceBean> emptySet(), testPath,
                asSet(testPrincipal1, testPrincipal2, testPrincipal3), session, installLog);

        verify(jackrabbitAccessControlList).removeAccessControlEntry(ace1);
        verify(jackrabbitAccessControlList).removeAccessControlEntry(ace2);
        verify(jackrabbitAccessControlList).removeAccessControlEntry(ace3);

        verify(aceBeanInstallerIncremental, never()).installPrivileges(any(AceBean.class), any(Principal.class),
                any(JackrabbitAccessControlList.class), any(Session.class), any(AccessControlManager.class));

    }

    @Test
    public void testGetPrincipalAceBeansForActionAceBeanIsCalledToResolveActions() throws Exception {

        // read maps to one simple bean
        doReturn(asSet(bean1)).when(aceBeanInstallerIncremental).getPrincipalAceBeansForActionAceBean(beanWithAction1, session);
        // read,create,modify,delete maps to two beans
        doReturn(asSet(bean2, bean2Content)).when(aceBeanInstallerIncremental).getPrincipalAceBeansForActionAceBean(beanWithAction2,
                session);

        aceBeanInstallerIncremental.installAcl(
                asSet(beanWithAction1, beanWithAction2), testPath,
                asSet(testPrincipal1, testPrincipal2), session, installLog);

        verify(jackrabbitAccessControlList, never()).removeAccessControlEntry(any(JackrabbitAccessControlEntry.class));

        verify(aceBeanInstallerIncremental).installPrivileges(eq(bean1), eq(new PrincipalImpl(testPrincipal1)),
                eq(jackrabbitAccessControlList), eq(session), eq(accessControlManager));
        verify(aceBeanInstallerIncremental).installPrivileges(eq(bean2), eq(new PrincipalImpl(testPrincipal2)),
                eq(jackrabbitAccessControlList), eq(session), eq(accessControlManager));
        verify(aceBeanInstallerIncremental).installPrivileges(eq(bean2Content), eq(new PrincipalImpl(testPrincipal2)),
                eq(jackrabbitAccessControlList), eq(session), eq(accessControlManager));

    }


    @Test
    public void testGetPrincipalAceBeansForActionRead() throws Exception {

        AceBean bean1Clone = bean1.clone();
        bean1Clone.setPrincipalName(FAKE_PRINCIPAL_ID);

        // test simple read bean
        doReturn(new JackrabbitAccessControlEntry[] {
                aceBeanToAce(bean1Clone)
        }).when(jackrabbitAccessControlList).getAccessControlEntries();

        Set<AceBean> resultAceBeans = aceBeanInstallerIncremental.getPrincipalAceBeansForActionAceBean(beanWithAction1,
                session);

        assertEquals(1, resultAceBeans.size());

        Iterator<AceBean> resultAceBeansIt = resultAceBeans.iterator();
        AceBean firstResult = resultAceBeansIt.next();

        assertEquals(bean1.getPrincipalName(), firstResult.getPrincipalName());
        assertEquals(bean1.getPermission(), firstResult.getPermission());
        assertEquals(bean1.getJcrPath(), firstResult.getJcrPath());
        assertArrayEquals(bean1.getPrivileges(), firstResult.getPrivileges());
        assertArrayEquals(null, firstResult.getActions());
        assertTrue(firstResult.getRestrictions().isEmpty());
    }

    @Test
    public void testGetPrincipalAceBeansForActionReadCreateModifyDelete() throws Exception {

        // test read,create,modify.delete
        AceBean bean2Clone = bean2.clone();
        bean2Clone.setPrincipalName(FAKE_PRINCIPAL_ID);
        AceBean bean2ContentClone = bean2Content.clone();
        bean2ContentClone.setPrincipalName(FAKE_PRINCIPAL_ID);

        doReturn(new JackrabbitAccessControlEntry[] {
                aceBeanToAce(bean2Clone), aceBeanToAce(bean2ContentClone)
        }).when(jackrabbitAccessControlList).getAccessControlEntries();

        Set<AceBean> resultAceBeans = aceBeanInstallerIncremental.getPrincipalAceBeansForActionAceBean(beanWithAction2,
                session);

        assertEquals(2, resultAceBeans.size());

        Iterator<AceBean> resultAceBeansIt = resultAceBeans.iterator();
        AceBean firstResult = resultAceBeansIt.next();

        assertEquals(bean2.getPrincipalName(), firstResult.getPrincipalName());
        assertEquals(bean2.getPermission(), firstResult.getPermission());
        assertEquals(bean2.getJcrPath(), firstResult.getJcrPath());
        assertArrayEquals(bean2.getPrivileges(), firstResult.getPrivileges());
        assertArrayEquals(null, firstResult.getActions());
        assertTrue(firstResult.getRestrictions().isEmpty());

        AceBean secondResult = resultAceBeansIt.next();

        assertEquals(bean2Content.getPrincipalName(), secondResult.getPrincipalName());
        assertEquals(bean2Content.getPermission(), secondResult.getPermission());
        assertEquals(bean2Content.getJcrPath(), secondResult.getJcrPath());
        assertArrayEquals(bean2Content.getPrivileges(), secondResult.getPrivileges());
        assertArrayEquals(null, secondResult.getActions());
        assertEquals(1, secondResult.getRestrictions().size());
        assertEquals(AceBean.RESTRICTION_NAME_GLOB, secondResult.getRestrictions().get(0).getName());
        assertEquals("*/jcr:content*", secondResult.getRestrictions().get(0).getValue());
    }

    public static <T> Set<T> asSet(T... objects) {
        return new LinkedHashSet<T>(Arrays.asList(objects));
    }

    public static AceBean createTestBean(String path, String principalName, boolean isAllow, String privileges, String actions,
            Restriction... restrictions) {
        AceBean testBean = new AceBean();
        testBean.setJcrPath(path);
        testBean.setPrincipalName(principalName);
        testBean.setAuthorizableId(principalName);
        testBean.setPermission(isAllow ? "allow" : "deny");
        testBean.setPrivilegesString(privileges);
        testBean.setActions(YamlConfigReader.parseActionsString(actions));
        testBean.setRestrictions(Arrays.asList(restrictions));
        return testBean;
    }

    private String createComparablePrivSet(String privsIn) throws RepositoryException {
        return aceBeanInstallerIncremental.privilegesToComparableSet(privsIn.split(" *, *"), accessControlManager);
    }

    public static JackrabbitAccessControlEntry aceBeanToAce(final AceBean bean) {

        return new JackrabbitAccessControlEntry() {

            @Override
            public Principal getPrincipal() {
                return new PrincipalImpl(bean.getPrincipalName());
            }

            @Override
            public Privilege[] getPrivileges() {
                List<Privilege> privileges = new ArrayList<Privilege>();

                for (final String priv : bean.getPrivileges()) {
                    privileges.add(new TestPrivilege(priv));
                }

                return privileges.toArray(new Privilege[privileges.size()]);
            }

            @Override
            public boolean isAllow() {
                return bean.isAllow();
            }

            @Override
            public String[] getRestrictionNames() throws RepositoryException {
                List<String> names = new ArrayList<String>();
                for (Restriction restriction : bean.getRestrictions()) {
                    names.add(restriction.getName());
                }
                return names.toArray(new String[names.size()]);
            }

            @Override
            public Value getRestriction(String name) throws ValueFormatException, RepositoryException {

                for (final Restriction restriction : bean.getRestrictions()) {
                    if (restriction.getName().equals(name)) {
                        return new TestValue(restriction.getValue());
                    }

                }
                return null;
            }

            @Override
            public Value[] getRestrictions(String name) throws RepositoryException {
                List<Value> values = new ArrayList<Value>();
                for (final Restriction restriction : bean.getRestrictions()) {
                    if (restriction.getName().equals(name)) {
                        values.add(new TestValue(restriction.getValue()));
                    }
                }
                return values.toArray(new Value[values.size()]);
            }


        };

    }

    private static final class TestPrivilege implements Privilege {
        private final String priv;
        private final Privilege[] aggregatePrivileges;

        private TestPrivilege(String priv) {
            this.priv = priv;
            this.aggregatePrivileges = new Privilege[0];
        }

        private TestPrivilege(String priv, String[] aggregatePrivilegesStrArr) {
            this.priv = priv;
            aggregatePrivileges = new Privilege[aggregatePrivilegesStrArr.length];
            for (int i = 0; i < aggregatePrivilegesStrArr.length; i++) {
                aggregatePrivileges[i] = new TestPrivilege(aggregatePrivilegesStrArr[i]);
            }
        }

        @Override
        public String getName() {
            return priv;
        }

        @Override
        public boolean isAbstract() {
            return false;
        }

        @Override
        public boolean isAggregate() {
            return aggregatePrivileges.length > 0;
        }

        @Override
        public Privilege[] getDeclaredAggregatePrivileges() {
            return aggregatePrivileges;
        }

        @Override
        public Privilege[] getAggregatePrivileges() {
            return aggregatePrivileges;
        }

        @Override
        public String toString() {
            return "[TestPrivilege " + priv + " aggregate of " + Arrays.toString(aggregatePrivileges) + "]";
        }

    }

    static final class TestValue implements Value {
        private final String val;

        TestValue(String val) {
            this.val = val;
        }

        @Override
        public int getType() {
            return PropertyType.STRING;
        }

        @Override
        public String getString() throws ValueFormatException, IllegalStateException, RepositoryException {
            return val;
        }

        @Override
        public InputStream getStream() throws RepositoryException {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getLong() throws ValueFormatException, RepositoryException {
            throw new UnsupportedOperationException();
        }

        @Override
        public double getDouble() throws ValueFormatException, RepositoryException {
            throw new UnsupportedOperationException();
        }

        @Override
        public BigDecimal getDecimal() throws ValueFormatException, RepositoryException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Calendar getDate() throws ValueFormatException, RepositoryException {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean getBoolean() throws ValueFormatException, RepositoryException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Binary getBinary() throws RepositoryException {
            throw new UnsupportedOperationException();
        }
    }



}
