/*
 * (C) Copyright 2016 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.aceinstaller;

import static biz.netcentric.cq.tools.actool.history.AcInstallationLog.msHumanReadable;

import java.security.Principal;
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
import javax.jcr.ValueFormatException;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.Privilege;

import org.apache.commons.lang.time.StopWatch;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.comparators.AcePermissionComparator;
import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.configmodel.Restriction;
import biz.netcentric.cq.tools.actool.helper.AccessControlUtils;
import biz.netcentric.cq.tools.actool.helper.ContentHelper;
import biz.netcentric.cq.tools.actool.helper.RestrictionsHolder;
import biz.netcentric.cq.tools.actool.history.AcInstallationLog;

/** Base Class */
public abstract class BaseAceBeanInstaller implements AceBeanInstaller {

    private static final Logger LOG = LoggerFactory.getLogger(BaseAceBeanInstaller.class);

    @Override
    public void installPathBasedACEs(
            final Map<String, Set<AceBean>> pathBasedAceMapFromConfig,
            final Session session,
            final AcInstallationLog history, Set<String> principalsToRemoveAcesFor,
            boolean intermediateSaves) throws Exception {

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        final Set<String> paths = pathBasedAceMapFromConfig.keySet();

        history.addVerboseMessage(LOG, "Found " + paths.size() + "  paths in config");
        LOG.trace("Paths with ACEs: {}", paths);

        if (intermediateSaves) {
            history.addMessage(LOG, "Will save ACL for each path to session due to configuration option intermediateSaves=true - "
                    + "rollback functionality is disabled.");
        }

        // loop through all nodes from config
        for (final String path : paths) {

            final Set<AceBean> aceBeanSetFromConfig = pathBasedAceMapFromConfig
                    .get(path); // Set which holds the AceBeans of the current path in configuration

            // check if the path even exists
            final boolean pathExits = AccessControlUtils.getModifiableAcl(session.getAccessControlManager(), path) != null;
            if (!pathExits) {
                if (!ContentHelper.createInitialContent(session, history, path, aceBeanSetFromConfig)) {
                    history.addVerboseMessage(LOG, "Skipped installing privileges/actions for non existing path: " + path);
                    history.incCountAclsPathDoesNotExist();
                    continue;
                }
            }

            // order entries (denies in front of allows)
            final Set<AceBean> orderedAceBeanSetFromConfig = new TreeSet<AceBean>(
                    new AcePermissionComparator());
            orderedAceBeanSetFromConfig.addAll(aceBeanSetFromConfig);

            installAcl(orderedAceBeanSetFromConfig, path, principalsToRemoveAcesFor, session, history);

            if (intermediateSaves && session.hasPendingChanges()) {
                history.addVerboseMessage(LOG, "Saved session for path " + path);
                session.save();
            }
        }

        if (history.getMissingParentPathsForInitialContent() > 0) {
            history.addWarning(LOG, "There were " + history.getMissingParentPathsForInitialContent()
                    + " parent paths missing for creation of intial content (those paths were skipped, see verbose log for details)");
        }

        history.addMessage(LOG, "Finished installation of " + paths.size() + " ACLs in "
                + msHumanReadable(stopWatch.getTime())
                + " (changed ACLs=" + history.getCountAclsChanged() + " unchanged ACLs=" + history.getCountAclsUnchanged()
                + " path does not exist=" + history.getCountAclsPathDoesNotExist() + " action cache hit/miss="
                + history.getCountActionCacheHit() + "/" + history.getCountActionCacheMiss() + ")");
    }

    /** Installs a full set of ACE beans that form an ACL for the path
     * 
     * @throws RepositoryException */
    protected abstract void installAcl(Set<AceBean> aceBeanSetFromConfig, String path, Set<String> authorizablesToRemoveAcesFor,
            Session session, AcInstallationLog history) throws RepositoryException;
    

    protected boolean installPrivileges(AceBean aceBean, Principal principal, JackrabbitAccessControlList acl, Session session,
            AccessControlManager acMgr)
            throws RepositoryException {

        final Set<Privilege> privileges = getPrivilegeSet(aceBean.getPrivileges(), acMgr);
        if (!privileges.isEmpty()) {
            final RestrictionsHolder restrictions = getRestrictions(aceBean, session, acl);
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
    protected RestrictionsHolder getRestrictions(AceBean aceBean, Session session, JackrabbitAccessControlList acl)
            throws ValueFormatException, UnsupportedRepositoryOperationException, RepositoryException {

        final Collection<String> supportedRestrictionNames = Arrays.asList(acl.getRestrictionNames());

        if (aceBean.getRestrictions().isEmpty()) {
            return RestrictionsHolder.empty();
        }

        List<Restriction> restrictions = aceBean.getRestrictions();
        for (Restriction restriction : restrictions) {
            if (!supportedRestrictionNames.contains(restriction.getName())) {
                throw new IllegalStateException(
                        "The AccessControlList at " + acl.getPath() + " does not support setting " + restriction.getName()
                                + " restrictions!");
            }
        }

        RestrictionsHolder restrictionsHolder = new RestrictionsHolder(restrictions, session.getValueFactory(), acl);
        return restrictionsHolder;
    }

    /** Converts the given privilege names into a set of privilege objects.
     * 
     * @param privNames (may be {@code null}
     * @param acMgr
     * @return a set of privileges (never {@code null}, but may be empty set)
     * @throws RepositoryException */
    public Set<Privilege> getPrivilegeSet(String[] privNames, AccessControlManager acMgr) throws RepositoryException {
        if (privNames == null) {
            return Collections.emptySet();
        }
        final Set<Privilege> privileges = new HashSet<Privilege>(privNames.length);
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

}
