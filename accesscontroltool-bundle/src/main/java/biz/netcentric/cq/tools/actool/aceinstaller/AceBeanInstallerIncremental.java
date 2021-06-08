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
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.security.AccessControlEntry;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.Privilege;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.oak.spi.security.principal.PrincipalImpl;
import org.apache.sling.jcr.api.SlingRepository;
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
import biz.netcentric.cq.tools.actool.configmodel.Restriction;
import biz.netcentric.cq.tools.actool.helper.AcHelper;
import biz.netcentric.cq.tools.actool.helper.AccessControlUtils;
import biz.netcentric.cq.tools.actool.helper.Constants;
import biz.netcentric.cq.tools.actool.history.InstallationLogger;

@Component
public class AceBeanInstallerIncremental extends BaseAceBeanInstaller implements AceBeanInstaller {

    @Reference(policyOption=ReferencePolicyOption.GREEDY)
    private SlingRepository slingRepository;

    private static final Logger LOG = LoggerFactory.getLogger(AceBeanInstallerIncremental.class);

    private Map<String, Set<AceBean>> actionsToPrivilegesMapping = new ConcurrentHashMap<String, Set<AceBean>>();

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy=ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY)
    volatile AemCqActionsSupport aemCqActionsSupport;
    
    /** Installs a full set of ACE beans that form an ACL for the path
     * 
     * @throws RepositoryException */
    protected void installAcl(Set<AceBean> aceBeanSetFromConfig, String path, Set<String> principalsInConfiguration, Session session,
            InstallationLogger installLog) throws RepositoryException {

        boolean hadPendingChanges = session.hasPendingChanges();

        int countDeleted = 0;
        int countAdded = 0;
        int countNoChange = 0;
        int countOutsideConfig = 0;

        StringBuilder diffLog = new StringBuilder();

        aceBeanSetFromConfig = transformActionsIntoPrivileges(aceBeanSetFromConfig, session, installLog);
        aceBeanSetFromConfig = filterInitialContentOnlyNodes(aceBeanSetFromConfig);
        aceBeanSetFromConfig = filterDuplicates(aceBeanSetFromConfig, session);

        List<AceBean> configuredAceEntries = new ArrayList<AceBean>(aceBeanSetFromConfig);
        int currentPositionConfig = 0;

        boolean changeHasBeenFound = false;
            
        AccessControlManager acMgr = session.getAccessControlManager();

        JackrabbitAccessControlList acl = getAccessControlList(acMgr, path);
        Iterator<AccessControlEntry> aceIt = Arrays.asList(acl.getAccessControlEntries()).iterator();
        while (aceIt.hasNext()) {
            AccessControlEntry ace = aceIt.next();
            AceBean actualAceBean = AcHelper.getAceBean(ace, acl);

            String acePrincipalName = actualAceBean.getPrincipalName();
            String actualAceBeanCompareStr = toAceCompareString(actualAceBean, acMgr);

            if (!principalsInConfiguration.contains(acePrincipalName)) {
                countOutsideConfig++;
                diffLog.append("    OUTSIDE (not in Config) " + actualAceBeanCompareStr + "\n");
                continue;
            }

            AceBean configuredAceAtThisLocation;
            if (currentPositionConfig < configuredAceEntries.size()) {
                configuredAceAtThisLocation = configuredAceEntries.get(currentPositionConfig);
            } else {
                // LOG.info("There are now fewer ACEs configured at path " + path + " than there was before");
                changeHasBeenFound = true;
                configuredAceAtThisLocation = null; // setting explicitly to null
            }

            String configuredAceAtThisLocationCompareStr = toAceCompareString(configuredAceAtThisLocation, acMgr);
            boolean dumpEqualToConfig = StringUtils.equals(actualAceBeanCompareStr, configuredAceAtThisLocationCompareStr);

            if (!changeHasBeenFound && !dumpEqualToConfig) {
                String configBeanStr = configuredAceAtThisLocationCompareStr;
                diffLog.append("<<< CHANGE (Repo Version)   " + actualAceBeanCompareStr
                        + "\n>>> CHANGE (Config Version) " + configBeanStr + "\n");
            }

            if (changeHasBeenFound || !dumpEqualToConfig) {
                changeHasBeenFound = true; // first difference means we delete the rest of the acl and recreate it in the following loop
                acl.removeAccessControlEntry(ace);
                countDeleted++;

                diffLog.append("    DELETED (from Repo)     " + actualAceBeanCompareStr + "\n");

                continue; // we do not touch currentPositionConfig anymore, we'll have to recreate from there
            }

            currentPositionConfig++; // found equal ACE, compare next pair
            countNoChange++;
            diffLog.append("    UNCHANGED               " + actualAceBeanCompareStr + "\n");

        }

        // install missing - this can be either because not all configured ACEs were found (append) or because a change was detected and old
        // aces have been deleted

        for (int i = currentPositionConfig; i < configuredAceEntries.size(); i++) {
            AceBean aceBeanToAppend = configuredAceEntries.get(i);

            installPrivileges(aceBeanToAppend, new PrincipalImpl(aceBeanToAppend.getPrincipalName()), acl, session, acMgr);
            diffLog.append("    APPENDED (from Config)  " + toAceCompareString(aceBeanToAppend, acMgr) + "\n");

            countAdded++;
        }

        if (countAdded > 0 || countDeleted > 0) {
            acMgr.setPolicy(StringUtils.isNotBlank(path) ? path : /* repo level permission */null, acl);

            installLog.incCountAclsChanged();

            installLog.addVerboseMessage(LOG, "Update result at path " + path + ": O=" + countOutsideConfig + " N="
                    + countNoChange + " D=" + countDeleted + " A=" + countAdded
                    + (LOG.isDebugEnabled() ? "\nDIFF at " + path + "\n" + diffLog : ""));

        } else {
            installLog.incCountAclsNoChange();
        }

        if (!hadPendingChanges) {
            if (session.hasPendingChanges()) {
                hadPendingChanges = true;
                installLog.addMessage(LOG, "Path " + path + " introduced pending changes to the session");
            }
        }

    }

    // When using actions, it often happens that the second entry produced (with the rep:glob '*/jcr:content*') is a duplicate
    // Also without this, a potential effective duplicate in config would be detected as change of incremental run when it is
    // really not since jackrabbit ignores adding a duplicate entry to ACL
    private Set<AceBean> filterDuplicates(Set<AceBean> aceBeanSetFromConfig, Session session)
            throws UnsupportedRepositoryOperationException, RepositoryException {

        LinkedHashSet<AceBean> filteredAceBeans = new LinkedHashSet<AceBean>(aceBeanSetFromConfig);
        Iterator<AceBean> aceBeansIt = filteredAceBeans.iterator();
        Set<String> aceCompareKeysToAvoidDuplicates = new HashSet<String>();
        while (aceBeansIt.hasNext()) {
            String aceCompareKey = toAceCompareString(aceBeansIt.next(), session.getAccessControlManager());
            if (aceCompareKeysToAvoidDuplicates.contains(aceCompareKey)) {
                aceBeansIt.remove();
            } else {
                aceCompareKeysToAvoidDuplicates.add(aceCompareKey);
            }

        }
        return filteredAceBeans;
    }

    private Set<AceBean> filterInitialContentOnlyNodes(Set<AceBean> aceBeanSetFromConfig) {
        Set<AceBean> aceBeanSetNoInitialContentOnlyNodes = new LinkedHashSet<AceBean>();
        for (AceBean aceBean : aceBeanSetFromConfig) {
            if (!aceBean.isInitialContentOnlyConfig()) {
                aceBeanSetNoInitialContentOnlyNodes.add(aceBean);
            }

        }
        return aceBeanSetNoInitialContentOnlyNodes;
    }

    // to be overwritten in JUnit Test
    protected JackrabbitAccessControlList getAccessControlList(AccessControlManager acMgr, String path) throws RepositoryException {
        JackrabbitAccessControlList acl = AccessControlUtils.getModifiableAcl(acMgr, path);
        return acl;
    }

    private Set<AceBean> transformActionsIntoPrivileges(Set<AceBean> aceBeanSetFromConfig, Session session,
            InstallationLogger installLog) throws RepositoryException {


        Set<AceBean> aceBeanSetWithPrivilegesOnly = new LinkedHashSet<AceBean>();
        for (AceBean origAceBean : aceBeanSetFromConfig) {
            if (origAceBean.getActionMap().isEmpty()) {
                aceBeanSetWithPrivilegesOnly.add(origAceBean);
                continue;
            }

            Set<AceBean> aceBeansForActionEntry = getPrincipalAceBeansForActionAceBeanCached(origAceBean, session, installLog);
            for (AceBean aceBeanResolvedFromAction : aceBeansForActionEntry) {
                aceBeanSetWithPrivilegesOnly.add(aceBeanResolvedFromAction);
            }
        }

        return aceBeanSetWithPrivilegesOnly;
    }

    private Set<AceBean> getPrincipalAceBeansForActionAceBeanCached(AceBean origAceBean, Session session,
            InstallationLogger installLog) throws RepositoryException {
        
        String cacheKey = (definesContent(origAceBean.getJcrPathForPolicyApi(), session) ? "definesContent" : "simple")
                + "-" + origAceBean.getPermission() + "-" + getRestrictionsComparable(origAceBean.getRestrictions()) + "-"
                + Arrays.toString(origAceBean.getActions());
        
        if (actionsToPrivilegesMapping.containsKey(cacheKey)) {
            installLog.incCountActionCacheHit();
            LOG.trace("Cache hit for key " + cacheKey);
            Set<AceBean> cachedAceBeansForActions = actionsToPrivilegesMapping.get(cacheKey);
            Set<AceBean> principalCorrectedAceBeansForActions = new LinkedHashSet<AceBean>();
            for (AceBean aceBean : cachedAceBeansForActions) {
                AceBean clone = aceBean.clone();
                clone.setPrincipalName(origAceBean.getPrincipalName());
                principalCorrectedAceBeansForActions.add(clone);
            }
            return principalCorrectedAceBeansForActions;
        } else {
            installLog.incCountActionCacheMiss();

            Set<AceBean> aceBeansForActionEntry = null;
            Session newSession =  slingRepository.loginService(null, null);
            try {
                Session relevantSessionToUse;
                if (newSession.nodeExists(origAceBean.getJcrPath())) {
                    // a new session is needed to ensure no pending changes are introduced (even if there would not be real pending changes
                    // since we add and remove, but session.hasPendingChanges() is true then).
                    // The new session is not saved(), its only function is to produce the action->privileges mapping with the ootb class
                    // CqActions
                    relevantSessionToUse = newSession;
                } else {
                    // if the path was just only created in this session via initialContent
                    relevantSessionToUse = session;
                    LOG.warn("Reusing main session for path {} since the node was only just created in that session via 'initialContent'",
                            origAceBean.getJcrPath());
                }
                aceBeansForActionEntry = getPrincipalAceBeansForActionAceBean(origAceBean, relevantSessionToUse);
            } finally {
                newSession.logout();
            }

            LOG.debug("Adding to cache: {}={}", cacheKey, aceBeansForActionEntry);
            actionsToPrivilegesMapping.put(cacheKey, aceBeansForActionEntry);


            return aceBeansForActionEntry;
        }

    }

    Set<AceBean> getPrincipalAceBeansForActionAceBean(AceBean origAceBean, Session session) throws RepositoryException {

        Set<AceBean> aceBeansForActionEntry = new LinkedHashSet<AceBean>();

        Principal testActionMapperPrincipal = getTestActionMapperPrincipal();
        applyCqActions(origAceBean, session, testActionMapperPrincipal);

        JackrabbitAccessControlList newAcl = getAccessControlList(session.getAccessControlManager(), origAceBean.getJcrPathForPolicyApi());

        boolean isFirst = true;
        for (AccessControlEntry newAce : newAcl.getAccessControlEntries()) {
            if (!newAce.getPrincipal().equals(testActionMapperPrincipal)) {
                continue;
            }

            AceBean privilegesAceBeanForAction = AcHelper.getAceBean(newAce, newAcl);
            privilegesAceBeanForAction.setPrincipalName(origAceBean.getPrincipalName());

            // handle restrictions
            if (isFirst) {
                if (origAceBean.containsRestriction(AceBean.RESTRICTION_NAME_GLOB)
                        && privilegesAceBeanForAction.containsRestriction(AceBean.RESTRICTION_NAME_GLOB)) {
                    throw new IllegalArgumentException(
                            "When using actions that produce rep:glob restrictions (e.g. for page paths), rep:glob cannot be configured (origAceBean="
                                    + origAceBean.getRestrictions() + ", privilegesAceBeanForAction="
                                    + privilegesAceBeanForAction.getRestrictions() + "), check configuration for "
                                    + origAceBean);
                } else {
                    // other restrictions are just taken over
                    privilegesAceBeanForAction.getRestrictions().addAll(origAceBean.getRestrictions());
                }
            }

            aceBeansForActionEntry.add(privilegesAceBeanForAction);

            // remove the fake entry again
            newAcl.removeAccessControlEntry(newAce);
            isFirst = false;
        }
        AccessControlManager acMgr = session.getAccessControlManager();
        acMgr.setPolicy(origAceBean.getJcrPath(), newAcl);

        // handle privileges
        AceBean firstMappedBean = aceBeansForActionEntry.iterator().next(); // apply additional privileges only to first bean
        Set<String> newPrivilegesFirstMappedBean = new LinkedHashSet<String>();
        // first add regular privileges
        if (firstMappedBean.getPrivileges() != null) {
            newPrivilegesFirstMappedBean.addAll(Arrays.asList(firstMappedBean.getPrivileges()));
        }
        Set<String> flatSetPrincipalsOfFirstMappedBean = flatSetResolvedAggregates(firstMappedBean.getPrivileges(), acMgr, true);
        if (origAceBean.getPrivileges() != null) {
            for (String origBeanPrivString : origAceBean.getPrivileges()) {
                if (!flatSetPrincipalsOfFirstMappedBean.contains(origBeanPrivString)) {
                    newPrivilegesFirstMappedBean.add(origBeanPrivString);
                }
            }
        }
        firstMappedBean.setPrivilegesString(StringUtils.join(newPrivilegesFirstMappedBean, ","));

        if (LOG.isDebugEnabled()) {
            StringBuilder buf = new StringBuilder();
            buf.append("CqActions at path " + origAceBean.getJcrPath()
                    + " with authorizableId=" + origAceBean.getAuthorizableId() + "/" + testActionMapperPrincipal.getName() + " produced \n");
            for (AceBean aceBean : aceBeansForActionEntry) {
                buf.append("   " + toAceCompareString(aceBean, acMgr) + "\n");
            }
            LOG.debug(buf.toString());
        }

        return aceBeansForActionEntry;

    }

    Principal getTestActionMapperPrincipal() {
        String groupPrincipalId = "actool-tester-action-mapper"; // does not have to exist since the ACEs for it are not saved
        Principal principal = new PrincipalImpl(groupPrincipalId);
        return principal;
    }

    void applyCqActions(AceBean origAceBean, Session session, Principal principal) throws RepositoryException {

        if (origAceBean.getActionMap().isEmpty()) {
            return;
        }

        if (aemCqActionsSupport == null) {
            throw new IllegalArgumentException(
                    "actions can only be used when using AC Tool in AEM (package com.day.cq.security.util with class CqActions is not available)");
        }

        AemCqActions cqActions = aemCqActionsSupport.getCqActions(session);
        Collection<String> inheritedAllows = cqActions.getAllowedActions(origAceBean.getJcrPathForPolicyApi(),
                Collections.singleton(principal));
        // this does always install new entries
        cqActions.installActions(origAceBean.getJcrPath(), principal, origAceBean.getActionMap(), inheritedAllows);

    }

    private Set<String> flatSetResolvedAggregates(String[] privNames, AccessControlManager acMgr, boolean includeAggregates)
            throws RepositoryException {
        if (privNames == null) {
            return Collections.emptySet();
        }
        final Set<String> privileges = new HashSet<String>();
        for (final String name : privNames) {
            final Privilege p = acMgr.privilegeFromName(name);
            if (!p.isAggregate() || includeAggregates) {
                privileges.add(p.getName());
            }
            if (p.isAggregate()) { // add "sub privileges" as well
                for (Privilege subPriv : p.getDeclaredAggregatePrivileges()) {
                    Set<String> subPrivileges = flatSetResolvedAggregates(new String[] { subPriv.getName() }, acMgr, includeAggregates);
                    privileges.addAll(subPrivileges);
                }
            }
        }
        return privileges;
    }

    boolean definesContent(String pagePath, Session session) throws RepositoryException {
        if (pagePath == null || pagePath.equals("/") || aemCqActionsSupport==null) {
            return false;
        }
        try {
            return aemCqActionsSupport.definesContent(session.getNode(pagePath));
        } catch (PathNotFoundException e) {
            return false;
        }

    }

    private String toAceCompareString(AceBean aceBean, AccessControlManager acMgr) throws RepositoryException {
        if (aceBean == null) {
            return "null";
        }

        List<Restriction> restrictionsSorted = getRestrictionsComparable(aceBean.getRestrictions());

        String nonAggregatePrivsNormalized = privilegesToComparableSet(aceBean.getPrivileges(), acMgr);

        String aceCompareStr = aceBean.getPrincipalName() + " " + aceBean.getPermission() + " " + nonAggregatePrivsNormalized
                + Arrays.toString(restrictionsSorted.toArray());
        return aceCompareStr;
    }

    private List<Restriction> getRestrictionsComparable(List<Restriction> restrictions) {
        List<Restriction> restrictionsSorted = new ArrayList<Restriction>(restrictions);
        Collections.sort(restrictionsSorted, new Comparator<Restriction>() {

            @Override
            public int compare(Restriction r1, Restriction r2) {
                return Collator.getInstance().compare(r1.getName(), r2.getName());
            }
        });
        return restrictionsSorted;
    }

    String privilegesToComparableSet(String[] privileges, AccessControlManager acMgr) throws RepositoryException {
        return new TreeSet<String>(flatSetResolvedAggregates(privileges, acMgr, false)).toString();
    }




}
