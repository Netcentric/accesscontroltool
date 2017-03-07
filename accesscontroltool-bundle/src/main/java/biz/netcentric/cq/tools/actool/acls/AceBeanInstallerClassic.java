/*
 * (C) Copyright 2016 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.acls;

import java.security.Principal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.security.AccessControlEntry;
import javax.jcr.security.AccessControlException;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.Privilege;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlEntry;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.security.util.CqActions;

import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.helper.AcHelper;
import biz.netcentric.cq.tools.actool.helper.AccessControlUtils;
import biz.netcentric.cq.tools.actool.helper.RestrictionsHolder;
import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;

/** The way ACEs were installed in version one is still available and can be configured in "global_config" section by setting
 * "installAclsIncrementally=false". */
@Service
@Component
public class AceBeanInstallerClassic extends BaseAceBeanInstaller implements AceBeanInstaller {

    private static final Logger LOG = LoggerFactory.getLogger(AceBeanInstallerClassic.class);

    /** Installs a full set of ACE beans that form an ACL for the path
     * 
     * @throws RepositoryException */
    protected void installAcl(Set<AceBean> aceBeanSetFromConfig, String path, Set<String> principalsToRemoveAcesFor, Session session,
            AcInstallationHistoryPojo history) throws RepositoryException {

        // Remove all config contained authorizables from ACL of this path
        int countRemoved = AccessControlUtils.deleteAllEntriesForPrincipalsFromACL(session,
                path, principalsToRemoveAcesFor.toArray(new String[principalsToRemoveAcesFor.size()]));
        final String message = "Deleted " + countRemoved + " ACEs for configured principals from path " + path;
        LOG.debug(message);
        history.addVerboseMessage(message);

        // Set ACL in repo with permissions from merged config
        for (final AceBean bean : aceBeanSetFromConfig) {

            LOG.debug("Writing bean to repository {}", bean);

            final Principal currentPrincipal = AcHelper.getPrincipal(session, bean);

            if (currentPrincipal == null) {
                final String errMessage = "Could not find definition for authorizable "
                        + bean.getPrincipalName() + " in groups config while installing ACE for: "
                        + bean.getJcrPath() + "! Skipped installation of ACEs for this authorizable!\n";
                LOG.error(errMessage);
                history.addError(errMessage);
                continue;

            } else {
                history.addVerboseMessage("Starting installation of bean: \n" + bean);
                installAce(bean, session, currentPrincipal, history);
            }
        }

        history.incCountAclsChanged();

    }
    
    /** Installs the AccessControlEntry being represented by this bean in the repository
    *
    * @throws NoSuchMethodException */
   private void installAce(AceBean aceBean, final Session session, Principal principal,
            AcInstallationHistoryPojo history) throws RepositoryException {

        if (aceBean.isInitialContentOnlyConfig()) {
            return;
        }

        final AccessControlManager acMgr = session.getAccessControlManager();

        JackrabbitAccessControlList acl = AccessControlUtils.getModifiableAcl(acMgr, aceBean.getJcrPath());
        if (acl == null) {
            final String msg = "Skipped installing privileges/actions for non existing path: " + aceBean.getJcrPath();
            LOG.debug(msg);
            history.addMessage(msg);
            return;
        }

        // first install actions
        final JackrabbitAccessControlList newAcl = installActions(aceBean, principal, acl, session, acMgr, history);
        if (acl != newAcl) {
            history.addVerboseMessage("Added action(s) for path: " + aceBean.getJcrPath()
                    + ", principal: " + principal.getName() + ", actions: "
                    + aceBean.getActionsString() + ", allow: " + aceBean.isAllow());
            removeRedundantPrivileges(aceBean, session);
            acl = newAcl;
        }

        // then install (remaining) privileges
        if (installPrivileges(aceBean, principal, acl, session, acMgr)) {
            history.addVerboseMessage("added privilege(s) for path: " + aceBean.getJcrPath()
                    + ", principal: " + principal.getName() + ", privileges: "
                    + aceBean.getPrivilegesString() + ", allow: " + aceBean.isAllow());
        }
        acMgr.setPolicy(aceBean.getJcrPath(), acl);

   }
    

    /** Installs the CQ actions in the repository.
     *
     * @param aceBean
     * @param principal
     * @param acl
     * @param session
     * @param acMgr
     * @return either the same acl as given in the parameter {@code acl} if no actions have been installed otherwise the new
     *         AccessControlList (comprising the entres being installed for the actions).
     * @throws RepositoryException */
    private JackrabbitAccessControlList installActions(AceBean aceBean, Principal principal, JackrabbitAccessControlList acl,
            Session session, AccessControlManager acMgr, AcInstallationHistoryPojo history) throws RepositoryException {
        final Map<String, Boolean> actionMap = aceBean.getActionMap();
        if (actionMap.isEmpty()) {
            return acl;
        }

        final CqActions cqActions = new CqActions(session);
        final Collection<String> inheritedAllows = cqActions.getAllowedActions(
                aceBean.getJcrPath(), Collections.singleton(principal));
        // this does always install new entries
        cqActions.installActions(aceBean.getJcrPath(), principal, actionMap, inheritedAllows);

        // since the aclist has been modified, retrieve it again
        final JackrabbitAccessControlList newAcl = AccessControlUtils.getAccessControlList(session, aceBean.getJcrPath());
        final RestrictionsHolder restrictions = getRestrictions(aceBean, session, acl);

        if (!aceBean.getRestrictions().isEmpty()) {
            // additionally set restrictions on the installed actions (this is not supported by CQ Security API)
            addAdditionalRestriction(aceBean, acl, newAcl, restrictions);
        }
        return newAcl;
    }

    private void addAdditionalRestriction(AceBean aceBean, JackrabbitAccessControlList oldAcl, JackrabbitAccessControlList newAcl,
            RestrictionsHolder restrictions)
            throws RepositoryException {
        final List<AccessControlEntry> changedAces = getModifiedAces(oldAcl, newAcl);
        if (!changedAces.isEmpty()) {
            for (final AccessControlEntry newAce : changedAces) {
                addRestrictionIfNotSet(newAcl, restrictions, newAce);
            }
        } else {
            // check cornercase: yaml file contains 2 ACEs with same action same principal same path but one with additional restriction
            // (e.g. read and repGlob: '')
            // in that case old and new acl contain the same elements (equals == true) and in both lists the last ace contains the action
            // without restriction
            // for that group
            final AccessControlEntry lastOldAce = oldAcl.getAccessControlEntries()[oldAcl.getAccessControlEntries().length - 1];
            final AccessControlEntry lastNewAce = newAcl.getAccessControlEntries()[newAcl.getAccessControlEntries().length - 1];

            if (lastOldAce.equals(lastNewAce) && lastNewAce.getPrincipal().getName().equals(aceBean.getPrincipalName())) {
                addRestrictionIfNotSet(newAcl, restrictions, lastNewAce);

            } else {
                throw new IllegalStateException("No new entries have been set for AccessControlList at " + aceBean.getJcrPath());
            }
        }
    }

    private void addRestrictionIfNotSet(JackrabbitAccessControlList newAcl, RestrictionsHolder restrictions,
            AccessControlEntry newAce)
                    throws RepositoryException, AccessControlException, UnsupportedRepositoryOperationException, SecurityException {
        if (!(newAce instanceof JackrabbitAccessControlEntry)) {
            throw new IllegalStateException(
                    "Can not deal with non JackrabbitAccessControlEntrys, but entry is of type " + newAce.getClass().getName());
        }
        final JackrabbitAccessControlEntry ace = (JackrabbitAccessControlEntry) newAce;
        // only extend those AccessControlEntries which do not yet have a restriction

        if (ace.getRestrictionNames().length == 0) {
            // modify this AccessControlEntry by adding the restriction
            extendExistingAceWithRestrictions(newAcl, ace, restrictions);
        }
    }

    @SuppressWarnings("unchecked")
    private List<AccessControlEntry> getModifiedAces(final JackrabbitAccessControlList oldAcl, JackrabbitAccessControlList newAcl)
            throws RepositoryException {
        final List<AccessControlEntry> oldAces = Arrays.asList(oldAcl.getAccessControlEntries());
        final List<AccessControlEntry> newAces = Arrays.asList(newAcl.getAccessControlEntries());
        return (List<AccessControlEntry>) CollectionUtils.subtract(newAces, oldAces);

    }


    private void removeRedundantPrivileges(AceBean aceBean, Session session) throws RepositoryException {
        final Set<String> cleanedPrivileges = removeRedundantPrivileges(session, aceBean.getPrivileges(), aceBean.getActions());
        aceBean.setPrivilegesString(StringUtils.join(cleanedPrivileges, ","));
    }

    /** Modifies the privileges so that privileges already covered by actions are removed. This is only a best effort operation as one
     * action can lead to privileges on multiple nodes.
     *
     * @throws RepositoryException */
    private static Set<String> removeRedundantPrivileges(Session session, String[] privileges, String[] actions)
            throws RepositoryException {
        final CqActions cqActions = new CqActions(session);
        final Set<String> cleanedPrivileges = new HashSet<String>();
        if (privileges == null) {
            return cleanedPrivileges;
        }
        cleanedPrivileges.addAll(Arrays.asList(privileges));
        if (actions == null) {
            return cleanedPrivileges;
        }
        for (final String action : actions) {
            @SuppressWarnings("deprecation")
            final Set<Privilege> coveredPrivileges = cqActions.getPrivileges(action);
            for (final Privilege coveredPrivilege : coveredPrivileges) {
                cleanedPrivileges.remove(coveredPrivilege.getName());
            }
        }
        return cleanedPrivileges;
    }


    private void extendExistingAceWithRestrictions(JackrabbitAccessControlList accessControlList,
            JackrabbitAccessControlEntry accessControlEntry, RestrictionsHolder restrictions)
                    throws SecurityException, UnsupportedRepositoryOperationException, RepositoryException {

        // 1. add new entry
        if (!accessControlList.addEntry(accessControlEntry.getPrincipal(), accessControlEntry.getPrivileges(), accessControlEntry.isAllow(),
                restrictions.getSingleValuedRestrictionsMap(), restrictions.getMultiValuedRestrictionsMap())) {
            throw new IllegalStateException("Could not add entry, probably because it was already there!");
        }
        // we assume the entry being added is the last one
        final AccessControlEntry newAccessControlEntry = accessControlList.getAccessControlEntries()[accessControlList.size() - 1];
        // 2. put it to the right position now!
        accessControlList.orderBefore(newAccessControlEntry, accessControlEntry);
        // 3. remove old entry
        accessControlList.removeAccessControlEntry(accessControlEntry);
    }

}
