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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.ValueFormatException;
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

import biz.netcentric.cq.tools.actool.comparators.AcePermissionComparator;
import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.helper.AcHelper;
import biz.netcentric.cq.tools.actool.helper.AccessControlUtils;
import biz.netcentric.cq.tools.actool.helper.ContentHelper;
import biz.netcentric.cq.tools.actool.helper.Restriction;
import biz.netcentric.cq.tools.actool.helper.RestrictionModel;
import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;

@Service
@Component
public class AceBeanInstallerImpl implements AceBeanInstaller {

    private static final Logger LOG = LoggerFactory.getLogger(AceBeanInstallerImpl.class);


    @Override
    public void installPathBasedACEs(
            final Map<String, Set<AceBean>> pathBasedAceMapFromConfig,
            final Session session,
            final AcInstallationHistoryPojo history) throws Exception {

        final Set<String> paths = pathBasedAceMapFromConfig.keySet();

        LOG.debug("Paths in merged config = {}", paths);

        final String msg = "Found " + paths.size() + "  paths in config";
        LOG.debug(msg);
        history.addVerboseMessage(msg);

        // loop through all nodes from config
        for (final String path : paths) {

            final Set<AceBean> aceBeanSetFromConfig = pathBasedAceMapFromConfig
                    .get(path); // Set which holds the AceBeans of the current path in configuration

            // check if the path even exists
            final boolean pathExits = AccessControlUtils.getModifiableAcl(session.getAccessControlManager(), path) != null;
            if (!pathExits) {
                if (!ContentHelper.createInitialContent(session, history, path, aceBeanSetFromConfig)) {
                    final String msgNonExistingPath = "Skipped installing privileges/actions for non existing path: " + path;
                    LOG.debug(msgNonExistingPath);
                    history.addMessage(msgNonExistingPath);
                    continue;
                }
            }

            // order entries (denies in front of allows)
            final Set<AceBean> orderedAceBeanSetFromConfig = new TreeSet<AceBean>(
                    new AcePermissionComparator());
            orderedAceBeanSetFromConfig.addAll(aceBeanSetFromConfig);

            // remove ACL of that path from ACLs from repo so that after the
            // loop has ended only paths are left which are not contained in
            // current config
            for (final AceBean bean : orderedAceBeanSetFromConfig) {
                AccessControlUtils.deleteAllEntriesForAuthorizableFromACL(session,
                        path, bean.getPrincipalName());
                final String message = "deleted all ACEs of authorizable "
                        + bean.getPrincipalName()
                        + " from ACL of path: " + path;
                LOG.debug(message);
                history.addVerboseMessage(message);
            }
            writeAcBeansToRepository(session, history, orderedAceBeanSetFromConfig);
        }
    }

    private void writeAcBeansToRepository(final Session session,
            final AcInstallationHistoryPojo history,
            final Set<AceBean> aceBeanSetFromConfig)
                    throws RepositoryException, UnsupportedRepositoryOperationException, NoSuchMethodException, SecurityException {

        // reset ACL in repo with permissions from merged ACL
        for (final AceBean bean : aceBeanSetFromConfig) {

            LOG.debug("Writing bean to repository {}", bean);

            final Principal currentPrincipal = AcHelper.getPrincipal(session, bean);

            if (currentPrincipal == null) {
                final String errMessage = "Could not find definition for authorizable "
                        + bean.getPrincipalName()
                        + " in groups config while installing ACE for: "
                        + bean.getJcrPath()
                        + "! Skipped installation of ACEs for this authorizable!\n";
                LOG.error(errMessage);
                history.addError(errMessage);
                continue;

            } else {
                history.addVerboseMessage("starting installation of bean: \n"
                        + bean);
                install(bean, session, currentPrincipal, history);
            }
        }
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
     * @throws RepositoryException
     * @throws SecurityException
     * @throws NoSuchMethodException */
    private JackrabbitAccessControlList installActions(AceBean aceBean, Principal principal, JackrabbitAccessControlList acl,
            Session session, AccessControlManager acMgr, AcInstallationHistoryPojo history) throws RepositoryException, SecurityException {
        final Map<String, Boolean> actionMap = aceBean.getActionMap();
        if (actionMap.isEmpty()) {
            return acl;
        }

        final CqActions cqActions = new CqActions(session);
        final Collection<String> inheritedAllows = cqActions.getAllowedActions(
                aceBean.getJcrPath(), Collections.singleton(principal));
        // this does always install new entries
        cqActions.installActions(aceBean.getJcrPath(), principal, actionMap,
                inheritedAllows);

        // since the aclist has been modified, retrieve it again
        final JackrabbitAccessControlList newAcl = AccessControlUtils.getAccessControlList(session, aceBean.getJcrPath());
        final RestrictionModel restrictions = getRestrictions(aceBean, session, acl);

        if (aceBean.getRestrictions().isEmpty()) {
            return newAcl;
        }
        // additionally set restrictions on the installed actions (this is not supported by CQ Security API)
        addAdditionalRestriction(aceBean, acl, newAcl, restrictions);
        return newAcl;
    }

    private void addAdditionalRestriction(AceBean aceBean, JackrabbitAccessControlList oldAcl, JackrabbitAccessControlList newAcl,
            RestrictionModel restrictions)
                    throws RepositoryException, AccessControlException, UnsupportedRepositoryOperationException, SecurityException {
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

    private void addRestrictionIfNotSet(JackrabbitAccessControlList newAcl, RestrictionModel restrictions,
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
            AccessControlUtils.extendExistingAceWithRestrictions(newAcl, ace, restrictions);
        }
    }

    @SuppressWarnings("unchecked")
    private List<AccessControlEntry> getModifiedAces(final JackrabbitAccessControlList oldAcl, JackrabbitAccessControlList newAcl)
            throws RepositoryException {
        final List<AccessControlEntry> oldAces = Arrays.asList(oldAcl.getAccessControlEntries());
        final List<AccessControlEntry> newAces = Arrays.asList(newAcl.getAccessControlEntries());
        return (List<AccessControlEntry>) CollectionUtils.subtract(newAces, oldAces);

    }

    private boolean installPrivileges(AceBean aceBean, Principal principal, JackrabbitAccessControlList acl, Session session,
            AccessControlManager acMgr)
            throws RepositoryException {
        // then install remaining privileges
        final Set<Privilege> privileges = AccessControlUtils.getPrivilegeSet(aceBean.getPrivileges(), acMgr);
        if (!privileges.isEmpty()) {
            final RestrictionModel restrictions = getRestrictions(aceBean, session, acl);
            if (!restrictions.isEmpty()) {
                acl.addEntry(principal, privileges
                        .toArray(new Privilege[privileges.size()]), aceBean.isAllow(),
                        restrictions.getSingleValuedRestrictionsMap(), restrictions.getMultiValuedRestrictionsMap());
            } else {
                acl.addEntry(principal, privileges
                        .toArray(new Privilege[privileges.size()]), aceBean.isAllow());
            }
            return true;
        }
        return false;
    }

    /** Installs the AccessControlEntry being represented by this bean in the repository
     *
     * @throws SecurityException
     * @throws NoSuchMethodException */
    private void install(AceBean aceBean, final Session session, Principal principal,
            AcInstallationHistoryPojo history) throws RepositoryException, SecurityException {

        if (aceBean.isInitialContentOnlyConfig()) {
            return;
        }

        final AccessControlManager acMgr = session.getAccessControlManager();

        JackrabbitAccessControlList acl = AccessControlUtils.getModifiableAcl(
                acMgr, aceBean.getJcrPath());
        if (acl == null) {
            final String msg = "Skipped installing privileges/actions for non existing path: " + aceBean.getJcrPath();
            LOG.debug(msg);
            history.addMessage(msg);
            return;
        }

        // first install actions
        final JackrabbitAccessControlList newAcl = installActions(aceBean, principal, acl, session, acMgr, history);
        if (acl != newAcl) {
            history.addVerboseMessage("added action(s) for path: " + aceBean.getJcrPath()
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

    /** Creates a RestrictionHolder object containing 2 restriction maps being used in
     * {@link JackrabbitAccessControlList#addEntry(Principal, Privilege[], boolean, Map, Map)} out of the set actions on this bean.
     *
     * @param session the session
     * @param acl the access control list for which this restriction map should be used
     * @return RestrictionMapsHolder containing 2 maps with restriction names as keys and restriction values as values
     *         (singleValuedRestrictionsMap) and values[] (multiValuedRestrictionsMap).
     * @throws ValueFormatException
     * @throws UnsupportedRepositoryOperationException
     * @throws RepositoryException */
    private RestrictionModel getRestrictions(AceBean aceBean, Session session, JackrabbitAccessControlList acl)
            throws ValueFormatException, UnsupportedRepositoryOperationException, RepositoryException {
        final Collection<String> supportedRestrictionNames = Arrays.asList(acl.getRestrictionNames());
        if (!aceBean.getRestrictions().isEmpty()) {
            return getRestrictions(aceBean, session.getValueFactory(), acl, supportedRestrictionNames);
        } else {
            return RestrictionModel.empty();
        }
    }

    private RestrictionModel getRestrictions(AceBean aceBean, final ValueFactory valueFactory, final JackrabbitAccessControlList acl,
            final Collection<String> supportedRestrictionNames)
                    throws ValueFormatException, UnsupportedRepositoryOperationException, RepositoryException {
        Map<String, List<String>> restrictionsMap = aceBean.getRestrictions();
        for (final String restrictionName : restrictionsMap.keySet()) {
            if (!supportedRestrictionNames.contains(restrictionName)) {
                throw new IllegalStateException(
                        "The AccessControlList at " + acl.getPath() + " does not support setting " + restrictionName + " restrictions!");
            }
        }

        List<Restriction> restrictions = new ArrayList<Restriction>();

        for (final String restrictionName : restrictionsMap.keySet()) {
            final int restrictionMapSize = restrictionsMap.get(restrictionName).size();
            if (restrictionMapSize > 1) {
                final Value[] values = new Value[restrictionMapSize];
                for (int i = 0; i < restrictionMapSize; i++) {
                    final Value value = valueFactory.createValue(restrictionsMap.get(restrictionName).get(i),
                            acl.getRestrictionType(restrictionName));
                    values[i] = value;
                }
                restrictions.add(new Restriction(restrictionName, values));
            } else if (restrictionsMap.get(restrictionName).size() == 1) {
                final Value value = valueFactory.createValue(restrictionsMap.get(restrictionName).get(0),
                        acl.getRestrictionType(restrictionName));
                restrictions.add(new Restriction(restrictionName, value));
            }
        }
        return new RestrictionModel(restrictions);
    }

}
