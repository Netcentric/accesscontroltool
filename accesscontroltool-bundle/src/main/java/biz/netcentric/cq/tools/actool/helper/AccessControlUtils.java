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
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.jcr.AccessDeniedException;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This class provides common access control related utilities. Mostly a copy of org.apache.jackrabbit.commons.AccessControlUtils. */
public class AccessControlUtils {

    private static final Logger LOG = LoggerFactory.getLogger(AccessControlUtils.class);

    private AccessControlUtils() {
    }

    /** Retrieves the {@link Privilege}s from the specified privilege names.
     *
     * @param session The editing session.
     * @param privilegeNames The privilege names.
     * @return An array of privileges.
     * @throws RepositoryException If an error occurs or if {@code privilegeNames} contains an unknown/invalid privilege name. */
    public static Privilege[] privilegesFromNames(Session session,
            String... privilegeNames) throws RepositoryException {
        return privilegesFromNames(session.getAccessControlManager(),
                privilegeNames);
    }

    /** Retrieves the {@link Privilege}s from the specified privilege names.
     *
     * @param accessControlManager The access control manager.
     * @param privilegeNames The privilege names.
     * @return An array of privileges.
     * @throws RepositoryException If an error occurs or if {@code privilegeNames} contains an unknown/invalid privilege name. */
    public static Privilege[] privilegesFromNames(
            AccessControlManager accessControlManager, String... privilegeNames)
                    throws RepositoryException {
        final Set<Privilege> privileges = new HashSet<Privilege>(
                privilegeNames.length);
        for (final String privName : privilegeNames) {
            privileges.add(accessControlManager.privilegeFromName(privName));
        }
        return privileges.toArray(new Privilege[privileges.size()]);
    }

    /** Retrieves the names of the specified privileges.
     *
     * @param privileges One or more privileges.
     * @return The names of the specified privileges. */
    public static String[] namesFromPrivileges(Privilege... privileges) {
        if ((privileges == null) || (privileges.length == 0)) {
            return new String[0];
        } else {
            final String[] names = new String[privileges.length];
            for (int i = 0; i < privileges.length; i++) {
                names[i] = privileges[i].getName();
            }
            return names;
        }
    }

    /** Utility that combines {@link AccessControlManager#getApplicablePolicies(String)} and
     * {@link AccessControlManager#getPolicies(String)} to retrieve a modifiable {@code JackrabbitAccessControlList} for the given path.<br>
     *
     * Note that the policy must be {@link AccessControlManager#setPolicy(String, javax.jcr.security.AccessControlPolicy) reapplied} and the
     * changes must be saved in order to make the AC modifications take effect.
     *
     * @param session The editing session.
     * @param absPath The absolute path of the target node.
     * @return A modifiable access control list or null if there is none.
     * @throws RepositoryException If an error occurs. */
    public static JackrabbitAccessControlList getAccessControlList(
            Session session, String absPath) throws RepositoryException {
        final AccessControlManager acMgr = session.getAccessControlManager();
        return getAccessControlList(acMgr, absPath);
    }

    public static JackrabbitAccessControlList getAccessControlPolicies(
            Session session, Principal principal)
                    throws UnsupportedRepositoryOperationException, RepositoryException {
        final JackrabbitAccessControlManager acMgr = (JackrabbitAccessControlManager) session
                .getAccessControlManager();
        if (acMgr.getPolicies(principal).length > 0) {
            final JackrabbitAccessControlList jACL = (JackrabbitAccessControlList) acMgr
                    .getPolicies(principal)[0];
            return jACL;
        }
        return null;
    }

    /** Utility that combines {@link AccessControlManager#getApplicablePolicies(String)} and
     * {@link AccessControlManager#getPolicies(String)} to retrieve a modifiable {@code JackrabbitAccessControlList} for the given path.<br>
     *
     * Note that the policy must be {@link AccessControlManager#setPolicy(String, javax.jcr.security.AccessControlPolicy) reapplied} and the
     * changes must be saved in order to make the AC modifications take effect.
     *
     * @param accessControlManager The {@code AccessControlManager} .
     * @param absPath The absolute path of the target node.
     * @return A modifiable access control list or null if there is none.
     * @throws RepositoryException If an error occurs. */
    public static JackrabbitAccessControlList getAccessControlList(
            AccessControlManager accessControlManager, String absPath)
                    throws RepositoryException {
        // try applicable (new) ACLs
        final AccessControlPolicyIterator itr = accessControlManager
                .getApplicablePolicies(absPath);
        while (itr.hasNext()) {
            final AccessControlPolicy policy = itr.nextAccessControlPolicy();
            if (policy instanceof JackrabbitAccessControlList) {
                return (JackrabbitAccessControlList) policy;
            }
        }

        // try if there is an acl that has been set before
        final AccessControlPolicy[] pcls = accessControlManager.getPolicies(absPath);
        for (final AccessControlPolicy policy : pcls) {
            if (policy instanceof JackrabbitAccessControlList) {
                return (JackrabbitAccessControlList) policy;
            }
        }

        // no policy found
        LOG.warn("no policy found for path: {}", absPath);
        return null;
    }

    /** A utility method to add a new access control entry.<br>
     * Please note, that calling {@link javax.jcr.Session#save()} is required in order to persist the changes.
     *
     * @param session The editing session.
     * @param absPath The absolute path of the target node.
     * @param principal The principal to grant/deny privileges to.
     * @param privilegeNames The names of the privileges to grant or deny.
     * @param isAllow {@code true} to grant; {@code false} otherwise.
     * @return {@code true} if the node's ACL was modified and the session has pending changes.
     * @throws RepositoryException If an error occurs. */
    public static boolean addAccessControlEntry(Session session,
            String absPath, Principal principal, String[] privilegeNames,
            boolean isAllow) throws RepositoryException {
        return addAccessControlEntry(session, absPath, principal,
                privilegesFromNames(session, privilegeNames), isAllow);
    }

    /** A utility method to add a new access control entry. Please note, that a call to {@link javax.jcr.Session#save()} is required in
     * order to persist the changes.
     *
     * @param session The editing session
     * @param absPath The absolute path of the target node.
     * @param principal The principal to grant/deny privileges to.
     * @param privileges The privileges to grant or deny
     * @param isAllow {@code true} to grant; {@code false} otherwise;
     * @return {@code true} if the node's ACL was modified and the session has pending changes.
     * @throws RepositoryException If an error occurs. */
    public static boolean addAccessControlEntry(Session session,
            String absPath, Principal principal, Privilege[] privileges,
            boolean isAllow) throws RepositoryException {
        final JackrabbitAccessControlList acl = getAccessControlList(session, absPath);
        if (acl != null) {
            if (acl.addEntry(principal, privileges, isAllow)) {
                session.getAccessControlManager().setPolicy(absPath, acl);
                return true;
            } // else: not modified
        } // else: no acl found.

        return false;
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

    /** Converts the given privilege names into a set of privilege objects.
     * 
     * @param privNames (may be {@code null}
     * @param acMgr
     * @return a set of privileges (never {@code null}, but may be empty set)
     * @throws RepositoryException */
    public static Set<Privilege> getPrivilegeSet(String[] privNames,
            AccessControlManager acMgr) throws RepositoryException {
        if (privNames == null) {
            return Collections.emptySet();
        }
        final Set privileges = new HashSet(privNames.length);
        for (final String name : privNames) {
            final Privilege p = acMgr.privilegeFromName(name);
            if (p.isAggregate()) {
                privileges.addAll(Arrays.asList(p.getAggregatePrivileges()));
            } else {
                privileges.add(p);
            }
        }
        return privileges;
    }

    /** @param session admin session
     * @param path valid node path in CRX
     * @param authorizableID ID of authorizable to be deleted from ACL of node specified by path */
    public static void deleteAllEntriesForAuthorizableFromACL(final Session session,
            final String path, String authorizableID)
                    throws UnsupportedRepositoryOperationException, RepositoryException {
        final AccessControlManager accessControlManager = session
                .getAccessControlManager();

        final JackrabbitAccessControlList acl = AccessControlUtils.getModifiableAcl(
                accessControlManager, path);
        if (acl == null) {
            // do nothing, if there is no content node at the given path
            return;
        }
        // get ACEs of the node
        final AccessControlEntry[] aces = acl.getAccessControlEntries();

        // loop thorough ACEs and find the one of the given principal
        for (final AccessControlEntry ace : aces) {
            final JackrabbitAccessControlEntry jace = (JackrabbitAccessControlEntry) ace;
            if (StringUtils.equals(jace.getPrincipal().getName(),
                    authorizableID)) {
                acl.removeAccessControlEntry(jace);
                // bind new policy
                accessControlManager.setPolicy(path, acl);
            }
        }
    }

    public static JackrabbitAccessControlList getModifiableAcl(
            AccessControlManager acMgr, String path)
                    throws RepositoryException, AccessDeniedException {
        AccessControlPolicy[] existing = null;
        try {
            existing = acMgr.getPolicies(path);
        } catch (final PathNotFoundException e) {
            LOG.debug("No node could be found under: {}. Application of ACL for that node cancelled!", path);
        }
        if (existing != null) {
            for (final AccessControlPolicy p : existing) {
                if (p instanceof JackrabbitAccessControlList) {
                    return ((JackrabbitAccessControlList) p);
                }
            }

            final AccessControlPolicyIterator it = acMgr.getApplicablePolicies(path);
            while (it.hasNext()) {
                final AccessControlPolicy p = it.nextAccessControlPolicy();
                if (p instanceof JackrabbitAccessControlList) {
                    return ((JackrabbitAccessControlList) p);
                }
            }

            throw new AccessControlException("No modifiable ACL at " + path);
        }
        return null;
    }


}
