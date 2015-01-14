/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.helper;

import java.security.Principal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.jcr.AccessDeniedException;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.security.AccessControlEntry;
import javax.jcr.security.AccessControlException;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.AccessControlPolicy;
import javax.jcr.security.AccessControlPolicyIterator;
import javax.jcr.security.Privilege;
import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlEntry;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlManager;
import org.apache.jackrabbit.api.security.principal.PrincipalManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import biz.netcentric.cq.tools.actool.configuration.CqActionsMapping;
import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;
import com.day.cq.security.util.CqActions;

/**
 * This class provides common access control related utilities.
 */

public class AccessControlUtils {

    private AccessControlUtils() {
    }

    private static final Logger LOG = LoggerFactory
            .getLogger(AccessControlUtils.class);

    /**
     * Retrieves the {@link Privilege}s from the specified privilege names.
     *
     * @param session
     *            The editing session.
     * @param privilegeNames
     *            The privilege names.
     * @return An array of privileges.
     * @throws RepositoryException
     *             If an error occurs or if {@code privilegeNames} contains an
     *             unknown/invalid privilege name.
     */
    public static Privilege[] privilegesFromNames(Session session,
            String... privilegeNames) throws RepositoryException {
        return privilegesFromNames(session.getAccessControlManager(),
                privilegeNames);
    }

    /**
     * Retrieves the {@link Privilege}s from the specified privilege names.
     *
     * @param accessControlManager
     *            The access control manager.
     * @param privilegeNames
     *            The privilege names.
     * @return An array of privileges.
     * @throws RepositoryException
     *             If an error occurs or if {@code privilegeNames} contains an
     *             unknown/invalid privilege name.
     */
    public static Privilege[] privilegesFromNames(
            AccessControlManager accessControlManager, String... privilegeNames)
            throws RepositoryException {
        Set<Privilege> privileges = new HashSet<Privilege>(
                privilegeNames.length);
        for (String privName : privilegeNames) {
            privileges.add(accessControlManager.privilegeFromName(privName));
        }
        return privileges.toArray(new Privilege[privileges.size()]);
    }

    /**
     * Retrieves the names of the specified privileges.
     *
     * @param privileges
     *            One or more privileges.
     * @return The names of the specified privileges.
     */
    public static String[] namesFromPrivileges(Privilege... privileges) {
        if (privileges == null || privileges.length == 0) {
            return new String[0];
        } else {
            String[] names = new String[privileges.length];
            for (int i = 0; i < privileges.length; i++) {
                names[i] = privileges[i].getName();
            }
            return names;
        }
    }

    /**
     * Utility that combines
     * {@link AccessControlManager#getApplicablePolicies(String)} and
     * {@link AccessControlManager#getPolicies(String)} to retrieve a modifiable
     * {@code JackrabbitAccessControlList} for the given path.<br>
     *
     * Note that the policy must be
     * {@link AccessControlManager#setPolicy(String, javax.jcr.security.AccessControlPolicy)
     * reapplied} and the changes must be saved in order to make the AC
     * modifications take effect.
     *
     * @param session
     *            The editing session.
     * @param absPath
     *            The absolute path of the target node.
     * @return A modifiable access control list or null if there is none.
     * @throws RepositoryException
     *             If an error occurs.
     */
    public static JackrabbitAccessControlList getAccessControlList(
            Session session, String absPath) throws RepositoryException {
        AccessControlManager acMgr = session.getAccessControlManager();
        return getAccessControlList(acMgr, absPath);
    }

    public static JackrabbitAccessControlList getAccessControlPolicies(
            Session session, Principal principal)
            throws UnsupportedRepositoryOperationException, RepositoryException {
        JackrabbitAccessControlManager acMgr = (JackrabbitAccessControlManager) session
                .getAccessControlManager();
        if (acMgr.getPolicies(principal).length > 0) {
            JackrabbitAccessControlList jACL = (JackrabbitAccessControlList) acMgr
                    .getPolicies(principal)[0];
            return jACL;
        }
        return null;
    }

    /**
     * Utility that combines
     * {@link AccessControlManager#getApplicablePolicies(String)} and
     * {@link AccessControlManager#getPolicies(String)} to retrieve a modifiable
     * {@code JackrabbitAccessControlList} for the given path.<br>
     *
     * Note that the policy must be
     * {@link AccessControlManager#setPolicy(String, javax.jcr.security.AccessControlPolicy)
     * reapplied} and the changes must be saved in order to make the AC
     * modifications take effect.
     *
     * @param accessControlManager
     *            The {@code AccessControlManager} .
     * @param absPath
     *            The absolute path of the target node.
     * @return A modifiable access control list or null if there is none.
     * @throws RepositoryException
     *             If an error occurs.
     */
    public static JackrabbitAccessControlList getAccessControlList(
            AccessControlManager accessControlManager, String absPath)
            throws RepositoryException {
        // try applicable (new) ACLs
        AccessControlPolicyIterator itr = accessControlManager
                .getApplicablePolicies(absPath);
        while (itr.hasNext()) {
            AccessControlPolicy policy = itr.nextAccessControlPolicy();
            if (policy instanceof JackrabbitAccessControlList) {
                return (JackrabbitAccessControlList) policy;
            }
        }

        // try if there is an acl that has been set before
        AccessControlPolicy[] pcls = accessControlManager.getPolicies(absPath);
        for (AccessControlPolicy policy : pcls) {
            if (policy instanceof JackrabbitAccessControlList) {
                return (JackrabbitAccessControlList) policy;
            }
        }

        // no policy found
        LOG.warn("no policy found for path: {}", absPath);
        return null;
    }

    /**
     * A utility method to add a new access control entry.<br>
     * Please note, that calling {@link javax.jcr.Session#save()()} is required
     * in order to persist the changes.
     *
     * @param session
     *            The editing session.
     * @param absPath
     *            The absolute path of the target node.
     * @param principal
     *            The principal to grant/deny privileges to.
     * @param privilegeNames
     *            The names of the privileges to grant or deny.
     * @param isAllow
     *            {@code true} to grant; {@code false} otherwise.
     * @return {@code true} if the node's ACL was modified and the session has
     *         pending changes.
     * @throws RepositoryException
     *             If an error occurs.
     */
    public static boolean addAccessControlEntry(Session session,
            String absPath, Principal principal, String[] privilegeNames,
            boolean isAllow) throws RepositoryException {
        return addAccessControlEntry(session, absPath, principal,
                privilegesFromNames(session, privilegeNames), isAllow);
    }

    /**
     * A utility method to add a new access control entry. Please note, that a
     * call to {@link javax.jcr.Session#save()()} is required in order to
     * persist the changes.
     *
     * @param session
     *            The editing session
     * @param absPath
     *            The absolute path of the target node.
     * @param principal
     *            The principal to grant/deny privileges to.
     * @param privileges
     *            The privileges to grant or deny
     * @param isAllow
     *            {@code true} to grant; {@code false} otherwise;
     * @return {@code true} if the node's ACL was modified and the session has
     *         pending changes.
     * @throws RepositoryException
     *             If an error occurs.
     */
    public static boolean addAccessControlEntry(Session session,
            String absPath, Principal principal, Privilege[] privileges,
            boolean isAllow) throws RepositoryException {
        JackrabbitAccessControlList acl = getAccessControlList(session, absPath);
        if (acl != null) {
            if (acl.addEntry(principal, privileges, isAllow)) {
                session.getAccessControlManager().setPolicy(absPath, acl);
                return true;
            } // else: not modified
        } // else: no acl found.

        return false;
    }

    public static void addActions(Session session, AceBean aceBean,
            Principal principal, AcInstallationHistoryPojo history)
            throws RepositoryException {

        boolean isAllow = aceBean.isAllow();
        String[] actions = aceBean.getActions();
        CqActions cqActions = new CqActions(session);
        String absPath = aceBean.getJcrPath();
        String globString = aceBean.getRepGlob();
        String[] privNames = aceBean.getPrivileges();
        Map<String, Boolean> actionMap = new HashMap<String, Boolean>();

        if (actions != null) {
            for (String action : actions) {
                actionMap.put(action, isAllow);
            }

            Collection<String> inheritedAllows = new HashSet<String>();
            inheritedAllows.add("read");

            LOG.info("setting actions for path: {} and principal {}", absPath,
                    principal.getName());
            cqActions.installActions(absPath, principal, actionMap,
                    inheritedAllows);
        } else {
            String message = "Could not install Actions for " + aceBean
                    + ", no actions defined!";
            history.addWarning(message);

        }
        // if(privNames != null){
        // installPermissions(session, absPath, principal, isAllow, globString,
        // privNames);
        // }

    }

    private static Principal getEveryonePrincipal(Session session)
            throws RepositoryException {
        if (session instanceof JackrabbitSession) {
            return ((JackrabbitSession) session).getPrincipalManager()
                    .getEveryone();
        } else {
            throw new UnsupportedOperationException(
                    "Failed to retrieve everyone principal: JackrabbitSession expected.");
        }
    }

    /**
     * 
     * @param session
     * @param path
     *            path which is restricted by ACL
     * @param authorizable
     *            authorizable for which a repGlob gets added
     * @param globString
     * @param actions
     * @throws UnsupportedRepositoryOperationException
     * @throws RepositoryException
     */
    static void setPermissionAndRestriction(final Session session,
            final AceBean bean, final String authorizable)
            throws UnsupportedRepositoryOperationException, RepositoryException {
        String path = bean.getJcrPath();
        String globString = bean.getRepGlob();
        String[] actions = bean.getActions();
        String[] beanPrivileges = bean.getPrivileges();

        ValueFactory vf = session.getValueFactory();
        AccessControlManager accessControlManager = session
                .getAccessControlManager();
        JackrabbitSession js = (JackrabbitSession) session;
        PrincipalManager principalManager = js.getPrincipalManager();

        JackrabbitAccessControlList acl = AccessControlUtils.getModifiableAcl(
                accessControlManager, path);

        // AccessControlPolicy[] ps = accessControlManager.getPolicies(path); //
        // or getApplicablePolicies()
        // JackrabbitAccessControlList acl = (JackrabbitAccessControlList)
        // ps[0];

        if (acl != null) {

            // get ACEs of the node
            AccessControlEntry[] aces = acl.getAccessControlEntries();

            // loop through ACEs and find the one of the given principal
            for (AccessControlEntry ace : aces) {
                JackrabbitAccessControlEntry jace = (JackrabbitAccessControlEntry) ace;

                // if an ACE for current authorizable has been found
                if (StringUtils.equals(jace.getPrincipal().getName(),
                        authorizable)) {

                    // check if the privileges of the current ACE match the
                    // action(s) given in parameter
                    Privilege[] privileges = jace.getPrivileges();
                    String actionsString = CqActionsMapping
                            .getCqActions(privileges);

                    if (Arrays.asList(actions).containsAll(
                            Arrays.asList(actionsString.split(",")))) {
                        acl.removeAccessControlEntry(jace);
                        Map<String, Value> restrictions = new HashMap<String, Value>();

                        if (StringUtils.isNotBlank(globString)) {
                            restrictions.put("rep:glob",
                                    vf.createValue(globString));
                            AcHelper.LOG.info("set rep:Glob: {} in path {}",
                                    globString, path);
                        }

                        if (beanPrivileges != null) {
                            Set<Privilege> newPrivileges = AccessControlUtils
                                    .getPrivilegeSet(beanPrivileges,
                                            accessControlManager);
                            for (Privilege privilege : privileges) {
                                newPrivileges.add(privilege);
                            }
                            privileges = (Privilege[]) newPrivileges
                                    .toArray(new Privilege[newPrivileges.size()]);
                        }

                        // exchange old ACE with new one
                        if (StringUtils.isNotBlank(globString)) {
                            acl.addEntry(
                                    principalManager.getPrincipal(authorizable),
                                    privileges, jace.isAllow(), restrictions);
                        } else {
                            acl.addEntry(
                                    principalManager.getPrincipal(authorizable),
                                    privileges, jace.isAllow());
                        }

                        // bind new policy
                        accessControlManager.setPolicy(path, acl);
                        break;
                    }

                }
            }
        }
    }

    static Set<Privilege> getPrivilegeSet(String[] privNames,
            AccessControlManager acMgr) throws RepositoryException {
        Set privileges = new HashSet(privNames.length);
        for (String name : privNames) {
            Privilege p = acMgr.privilegeFromName(name);
            if (p.isAggregate())
                privileges.addAll(Arrays.asList(p.getAggregatePrivileges()));
            else {
                privileges.add(p);
            }
        }
        return privileges;
    }

    /**
     * 
     * @param session
     *            admin session
     * @param path
     *            valid node path in CRX
     * @param authorizableID
     *            ID of authorizable to be deleted from ACL of node specified by
     *            path
     * @throws UnsupportedRepositoryOperationException
     * @throws RepositoryException
     */
    public static void deleteAuthorizableFromACL(final Session session,
            final String path, String authorizableID)
            throws UnsupportedRepositoryOperationException, RepositoryException {
        AccessControlManager accessControlManager = session
                .getAccessControlManager();
        JackrabbitSession js = (JackrabbitSession) session;
        PrincipalManager principalManager = js.getPrincipalManager();

        JackrabbitAccessControlList acl = AccessControlUtils.getModifiableAcl(
                accessControlManager, path);
        // get ACEs of the node
        AccessControlEntry[] aces = acl.getAccessControlEntries();

        // loop thorough ACEs and find the one of the given principal
        for (AccessControlEntry ace : aces) {
            JackrabbitAccessControlEntry jace = (JackrabbitAccessControlEntry) ace;
            if (StringUtils.equals(jace.getPrincipal().getName(),
                    authorizableID)) {
                acl.removeAccessControlEntry(jace);
                // bind new policy
                accessControlManager.setPolicy(path, acl);
            }
        }
    }

    static JackrabbitAccessControlList getModifiableAcl(
            AccessControlManager acMgr, String path)
            throws RepositoryException, AccessDeniedException {
        AccessControlPolicy[] existing = null;
        try {
            existing = acMgr.getPolicies(path);
        } catch (PathNotFoundException e) {
            AcHelper.LOG
                    .warn("No node could be found under: {}. Application of ACL for that node cancelled!",
                            path);
        }
        if (existing != null) {
            for (AccessControlPolicy p : existing) {
                if (p instanceof JackrabbitAccessControlList) {
                    return ((JackrabbitAccessControlList) p);
                }
            }

            AccessControlPolicyIterator it = acMgr.getApplicablePolicies(path);
            while (it.hasNext()) {
                AccessControlPolicy p = it.nextAccessControlPolicy();
                if (p instanceof JackrabbitAccessControlList) {
                    return ((JackrabbitAccessControlList) p);
                }
            }

            throw new AccessControlException("No modifiable ACL at " + path);
        }
        return null;
    }

    public static void installPermissions(final Session session,
            final String nodePath, final Principal principal,
            final boolean isAllow, final String globString,
            final String[] privNames) throws RepositoryException {
        AccessControlManager acMgr = session.getAccessControlManager();
        JackrabbitAccessControlList acl = getModifiableAcl(acMgr, nodePath);
        Set<Privilege> privileges = getPrivilegeSet(privNames, acMgr);

        Map<String, Value> restrictions = null;
        if (globString != null) {

            for (String rName : acl.getRestrictionNames()) {
                if ("rep:glob".equals(rName)) {
                    Value v = session.getValueFactory().createValue(globString,
                            acl.getRestrictionType(rName));
                    restrictions = Collections.singletonMap(rName, v);
                    break;
                }
            }

        }
        if (privileges != null) {
            if (restrictions != null) {
                acl.addEntry(principal, (Privilege[]) privileges
                        .toArray(new Privilege[privileges.size()]), isAllow,
                        restrictions);
            } else {
                acl.addEntry(principal, (Privilege[]) privileges
                        .toArray(new Privilege[privileges.size()]), isAllow);
            }
        }
        acMgr.setPolicy(nodePath, acl);
    }

}
