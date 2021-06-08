/*
 * (C) Copyright 2016 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.aceinstaller;

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

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlEntry;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.oak.spi.security.principal.PrincipalImpl;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.aem.AemCqActionsSupport;
import biz.netcentric.cq.tools.actool.aem.AemCqActionsSupport.AemCqActions;
import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.helper.AccessControlUtils;
import biz.netcentric.cq.tools.actool.helper.RestrictionsHolder;
import biz.netcentric.cq.tools.actool.history.InstallationLogger;

/** The way ACEs were installed in version one is still available and can be configured in "global_config" section by setting
 * "installAclsIncrementally=false". */
@Component()
public class AceBeanInstallerClassic extends BaseAceBeanInstaller implements AceBeanInstaller {

    private static final Logger LOG = LoggerFactory.getLogger(AceBeanInstallerClassic.class);

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy=ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY)
    volatile AemCqActionsSupport aemCqActionsSupport;
    
    /** Installs a full set of ACE beans that form an ACL for the path
     * 
     * @throws RepositoryException */
    protected void installAcl(Set<AceBean> aceBeanSetFromConfig, String path, Set<String> principalsToRemoveAcesFor, Session session,
            InstallationLogger installLog) throws RepositoryException {

        // Remove all config contained authorizables from ACL of this path
        int countRemoved = AccessControlUtils.deleteAllEntriesForPrincipalsFromACL(session,
                path, principalsToRemoveAcesFor.toArray(new String[principalsToRemoveAcesFor.size()]));

        installLog.addVerboseMessage(LOG, "Deleted " + countRemoved + " ACEs for configured principals from path " + path);

        // Set ACL in repo with permissions from merged config
        for (final AceBean bean : aceBeanSetFromConfig) {

            LOG.debug("Writing bean to repository {}", bean);

            Principal currentPrincipal = new PrincipalImpl(bean.getPrincipalName());
            installAce(bean, session, currentPrincipal, installLog);

        }

        installLog.incCountAclsChanged();

    }
    
    /** Installs the AccessControlEntry being represented by this bean in the repository
    *
    * @throws NoSuchMethodException */
   private void installAce(AceBean aceBean, final Session session, Principal principal,
            InstallationLogger installLog) throws RepositoryException {

        if (aceBean.isInitialContentOnlyConfig()) {
            return;
        }

        final AccessControlManager acMgr = session.getAccessControlManager();

        JackrabbitAccessControlList acl = AccessControlUtils.getModifiableAcl(acMgr, aceBean.getJcrPathForPolicyApi());
        if (acl == null) {
            installLog.addMessage(LOG, "Skipped installing privileges/actions for non existing path: " + aceBean.getJcrPath());
            return;
        }

        // first install actions
        final JackrabbitAccessControlList newAcl = installActions(aceBean, principal, acl, session, acMgr, installLog);
        if (acl != newAcl) {
            installLog.addVerboseMessage(LOG, "Added action(s) for path: " + aceBean.getJcrPath()
                    + ", principal: " + principal.getName() + ", actions: "
                    + aceBean.getActionsString() + ", allow: " + aceBean.isAllow());
            removeRedundantPrivileges(aceBean, session);
            acl = newAcl;
        }

        // then install (remaining) privileges
        if (installPrivileges(aceBean, principal, acl, session, acMgr)) {
            installLog.addVerboseMessage(LOG, "Added privilege(s) for path: " + aceBean.getJcrPath()
                    + ", principal: " + principal.getName() + ", privileges: "
                    + aceBean.getPrivilegesString() + ", allow: " + aceBean.isAllow());
        }

        if (!acl.isEmpty()) {
            acMgr.setPolicy(aceBean.getJcrPathForPolicyApi(), acl);
        } else {
            acMgr.removePolicy(aceBean.getJcrPathForPolicyApi(), acl);
        }

   }
    

    /** Installs the CQ actions in the repository.
     * 
     * @return either the same acl as given in the parameter {@code acl} if no actions have been installed otherwise the new
     *         AccessControlList (comprising the entres being installed for the actions).
     * @throws RepositoryException */
    private JackrabbitAccessControlList installActions(AceBean aceBean, Principal principal, JackrabbitAccessControlList acl,
            Session session, AccessControlManager acMgr, InstallationLogger installLog) throws RepositoryException {
        final Map<String, Boolean> actionMap = aceBean.getActionMap();
        if (actionMap.isEmpty()) {
            return acl;
        }
        
        if(aemCqActionsSupport==null) {
            throw new IllegalArgumentException("actions can only be used when using AC Tool in AEM (package com.day.cq.security.util with class CqActions is not available)");
        }
        
        final AemCqActions cqActions = aemCqActionsSupport.getCqActions(session);
        final Collection<String> inheritedAllows = cqActions.getAllowedActions(
                aceBean.getJcrPathForPolicyApi(), Collections.singleton(principal));
        // this does always install new entries
        cqActions.installActions(aceBean.getJcrPathForPolicyApi(), principal, actionMap, inheritedAllows);

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
    private Set<String> removeRedundantPrivileges(Session session, String[] privileges, String[] actions)
            throws RepositoryException {
        final AemCqActions cqActions = aemCqActionsSupport.getCqActions(session);
        final Set<String> cleanedPrivileges = new HashSet<String>();
        if (privileges == null) {
            return cleanedPrivileges;
        }
        cleanedPrivileges.addAll(Arrays.asList(privileges));
        if (actions == null) {
            return cleanedPrivileges;
        }
        for (final String action : actions) {
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
