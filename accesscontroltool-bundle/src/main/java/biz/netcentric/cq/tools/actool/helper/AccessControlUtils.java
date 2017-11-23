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
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

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

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlEntry;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlManager;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizablesConfig;

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

    /** @param session admin session
     * @param path valid node path in CRX
     * @param principalNames principal names of authorizables to be deleted from ACL of node specified by path
     * @return count ACEs that were removed */
    public static int deleteAllEntriesForPrincipalsFromACL(final Session session,
            String path, String[] principalNames)
            throws UnsupportedRepositoryOperationException, RepositoryException {
        return deleteAllEntriesForPrincipalsFromACL(session, path, principalNames, null);
    }

    /** @return count ACEs that were removed
     * @param session admin session
     * @param path valid node path in CRX
     * @param principalNames principal names of authorizables to be deleted from ACL of node specified by path
     * @param authorizablesConfig authorizables from the configuration  */
    public static int deleteAllEntriesForPrincipalsFromACL(final Session session,
            String path, String[] principalNames, AuthorizablesConfig authorizablesConfig)
                    throws UnsupportedRepositoryOperationException, RepositoryException {
        final AccessControlManager accessControlManager = session.getAccessControlManager();

        if (StringUtils.isBlank(path)) {
            path = null; // for repository permissions null needs to be used
        }

        final JackrabbitAccessControlList acl = AccessControlUtils.getModifiableAcl(accessControlManager, path);
        if (acl == null) {
            // do nothing, if there is no content node at the given path
            return 0;
        }
        // get ACEs of the node
        final AccessControlEntry[] aces = acl.getAccessControlEntries();

        int countRemoved = 0;
        // loop thorough ACEs and find the one of the given principal
        for (final AccessControlEntry ace : aces) {
            final JackrabbitAccessControlEntry jace = (JackrabbitAccessControlEntry) ace;

            String principalNameInCurrentAce = jace.getPrincipal().getName();

            if (shouldBeRemoved(principalNames, principalNameInCurrentAce, path, authorizablesConfig)) {
                acl.removeAccessControlEntry(jace);
                countRemoved++;
            }
        }

        if (countRemoved > 0) {
            // bind new policy
            if (!acl.isEmpty()) {
                accessControlManager.setPolicy(path, acl);
            } else {
                accessControlManager.removePolicy(path, acl);
            }
        }

        return countRemoved;
    }

    /** Retrieves JackrabbitAccessControlList for path.
     * 
     * @param acMgr
     * @param path
     * @return
     * @throws RepositoryException
     * @throws AccessDeniedException */
    public static JackrabbitAccessControlList getModifiableAcl(
            AccessControlManager acMgr, String path) throws RepositoryException, AccessDeniedException {

        if (StringUtils.isBlank(path)) {
            path = null; // repository level permission
        }

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

    /** Returns user manager for session disabling autoSave if applicable.
     * 
     * @param session
     * @return
     * @throws AccessDeniedException
     * @throws UnsupportedRepositoryOperationException
     * @throws RepositoryException */
    public static UserManager getUserManagerAutoSaveDisabled(Session session)
            throws AccessDeniedException, UnsupportedRepositoryOperationException, RepositoryException {

        JackrabbitSession js = (JackrabbitSession) session;
        UserManager userManager = js.getUserManager();
        // Since the persistence of the installation should only take place if
        // no error occured and certain test were successful
        // the autosave gets disabled. Therefore an explicit session.save() is
        // necessary to persist the changes.

        // Try do disable the autosave only in case if changes are automatically persisted
        if (userManager.isAutoSave()) {
            try {
                userManager.autoSave(false);
            } catch (UnsupportedRepositoryOperationException e) {
                // check added for AEM 6.0
                LOG.warn("disabling autoSave not possible with this user manager!");
            }
        }

        return userManager;
    }

    private static boolean shouldBeRemoved(String[] principalNames, Object principalNameInCurrentAce, String path,
            AuthorizablesConfig authorizablesConfig) {
        boolean result = false;

        int indexOf = ArrayUtils.indexOf(principalNames, principalNameInCurrentAce);
        if (indexOf != ArrayUtils.INDEX_NOT_FOUND) {
            result = true;

            if (authorizablesConfig != null) {
                String principalName = principalNames[indexOf];
                AuthorizableConfigBean configForCurrentAce = authorizablesConfig.getAuthorizableConfig(principalName);
                String restrictAcesRegex = configForCurrentAce.getUnmanagedAcePathsRegex();
                if (StringUtils.isNotBlank(restrictAcesRegex) && StringUtils.isNotBlank(path)) {
                    Pattern pattern = Pattern.compile(restrictAcesRegex);
                    result = !pattern.matcher(path).matches();
                }
            }
        }

        return result;
    }
}
