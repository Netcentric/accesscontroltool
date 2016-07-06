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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.apache.jackrabbit.api.security.JackrabbitAccessControlEntry;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.security.util.CqActions;

import biz.netcentric.cq.tools.actool.dumpservice.AcDumpElement;
import biz.netcentric.cq.tools.actool.dumpservice.AcDumpElementVisitor;
import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;

/** @author jochenkoschorke This class is used to store data of an AcessControlEntry. Objects of this class get created during the reading
 *         of the configuration file in order to set the corresponding ACEs in the system on the one hand and to store data during the
 *         reading of existing ACEs before writing the data back to a dump or configuration file again on the other hand. */
public class AceBean implements AcDumpElement {

    public static final Logger LOG = LoggerFactory.getLogger(AceBean.class);

    private String jcrPath;
    private String actionsStringFromConfig;
    private String privilegesString;
    private String principal;
    private String permission;
    private String[] actions;
    private String assertedExceptionString;
    private Map<String, List<String>> restrictionMap = new HashMap<>();

    private String initialContent;

    public static final String RESTRICTION_NAME_GLOB = "rep:glob";

    public String getAssertedExceptionString() {
        return assertedExceptionString;
    }

    public void setAssertedExceptionString(final String assertedException) {
        assertedExceptionString = assertedException;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permissionString) {
        permission = permissionString;
    }

    public void clearActions() {
        actions = null;
        actionsStringFromConfig = "";
    }

    public String getPrincipalName() {
        return principal;
    }

    public void setPrincipal(String principal) {
        this.principal = principal;
    }

    public String getJcrPath() {
        return jcrPath;
    }

    public void setJcrPath(String jcrPath) {
        this.jcrPath = jcrPath;
    }

    public boolean isAllow() {
        return "allow".equalsIgnoreCase(permission);
    }

    public Map<String, List<String>> getRestrictions() {
        return restrictionMap;
    }

    public void setRestrictions(final Map<String, ?> currentAceDefinition, Object restrictions, String oldStyleRepGlob) {
        if (restrictions != null) {
            if (!(restrictions instanceof Map)) {
                throw new IllegalArgumentException("If 'restrictions' is provided for an AC entry, it needs to be a map.");
            }
            Map<String, ?> restrictionsMap = (Map<String, ?>) restrictions;
            for (final String key : restrictionsMap.keySet()) {
                final String value = (String) restrictionsMap.get(key);
                final String[] values = value.split(",");
                addRestriction(key, new ArrayList<String>(Arrays.asList(values)));
            }
        }

        if (oldStyleRepGlob != null) {
            if (restrictionMap.containsKey(RESTRICTION_NAME_GLOB)) {
                throw new IllegalArgumentException("Usage of restrictions -> rep:glob and repGlob on top level cannot be mixed.");
            }
            addRestriction(RESTRICTION_NAME_GLOB, Arrays.asList(oldStyleRepGlob));
        }

    }

    void setRestrictionsMap(Map<String, List<String>> restrictionsMap) {
        restrictionMap = restrictionsMap;
    }

    private void addRestriction(final String restrictionName, final List<String> restrictionValue) {
        restrictionMap.put(restrictionName, restrictionValue);
    }

    public String getRepGlob() {
        List<String> list = restrictionMap.get(RESTRICTION_NAME_GLOB);
        return (list != null) && !list.isEmpty() ? list.get(0) : null;
    }

    public String getActionsString() {
        if (actions != null) {
            final StringBuilder sb = new StringBuilder();
            for (final String action : actions) {
                sb.append(action).append(",");
            }
            return StringUtils.chomp(sb.toString(), ",");
        }
        return "";
    }

    public String getActionsStringFromConfig() {
        return actionsStringFromConfig;
    }

    public void setActions(String[] actions) {
        this.actions = actions;
    }

    public void setActionsStringFromConfig(String actionsString) {
        actionsStringFromConfig = actionsString;
    }

    public String[] getActions() {
        return actions;
    }

    public String getPrivilegesString() {
        return privilegesString;
    }

    public String[] getPrivileges() {
        if (StringUtils.isNotBlank(privilegesString)) {
            return privilegesString.split(",");
        }
        return null;
    }

    public void setPrivilegesString(String privilegesString) {
        this.privilegesString = privilegesString;
    }

    public String getInitialContent() {
        return initialContent;
    }

    public void setInitialContent(String initialContent) {
        this.initialContent = initialContent;
    }

    @Override
    public String toString() {
        return "AceBean [jcrPath=" + jcrPath + "\n" + ", actionsStringFromConfig=" + actionsStringFromConfig
                + "\n"
                + ", privilegesString=" + privilegesString + "\n" + ", principal=" + principal + "\n" + ", permission=" + permission
                + ", actions="
                + Arrays.toString(actions) + "\n" + ", assertedExceptionString=" + assertedExceptionString + "\n" + ", restrictionMap="
                + restrictionMap + "\n"
                + ", initialContent=" + initialContent + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = (prime * result) + Arrays.hashCode(actions);
        result = (prime * result) + ((actionsStringFromConfig == null) ? 0 : actionsStringFromConfig.hashCode());
        result = (prime * result) + ((assertedExceptionString == null) ? 0 : assertedExceptionString.hashCode());
        result = (prime * result) + ((initialContent == null) ? 0 : initialContent.hashCode());
        result = (prime * result) + ((jcrPath == null) ? 0 : jcrPath.hashCode());
        result = (prime * result) + ((permission == null) ? 0 : permission.hashCode());
        result = (prime * result) + ((principal == null) ? 0 : principal.hashCode());
        result = (prime * result) + ((privilegesString == null) ? 0 : privilegesString.hashCode());
        result = (prime * result) + ((restrictionMap == null) ? 0 : restrictionMap.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final AceBean other = (AceBean) obj;
        if (!Arrays.equals(actions, other.actions)) {
            return false;
        }
        if (actionsStringFromConfig == null) {
            if (other.actionsStringFromConfig != null) {
                return false;
            }
        } else if (!actionsStringFromConfig.equals(other.actionsStringFromConfig)) {
            return false;
        }
        if (assertedExceptionString == null) {
            if (other.assertedExceptionString != null) {
                return false;
            }
        } else if (!assertedExceptionString.equals(other.assertedExceptionString)) {
            return false;
        }
        if (initialContent == null) {
            if (other.initialContent != null) {
                return false;
            }
        } else if (!initialContent.equals(other.initialContent)) {
            return false;
        }
        if (jcrPath == null) {
            if (other.jcrPath != null) {
                return false;
            }
        } else if (!jcrPath.equals(other.jcrPath)) {
            return false;
        }
        if (permission == null) {
            if (other.permission != null) {
                return false;
            }
        } else if (!permission.equals(other.permission)) {
            return false;
        }
        if (principal == null) {
            if (other.principal != null) {
                return false;
            }
        } else if (!principal.equals(other.principal)) {
            return false;
        }
        if (privilegesString == null) {
            if (other.privilegesString != null) {
                return false;
            }
        } else if (!privilegesString.equals(other.privilegesString)) {
            return false;
        }
        if (restrictionMap == null) {
            if (other.restrictionMap != null) {
                return false;
            }
        } else if (!restrictionMap.equals(other.restrictionMap)) {
            return false;
        }
        return true;
    }

    @Override
    public void accept(AcDumpElementVisitor acDumpElementVisitor) {
        acDumpElementVisitor.visit(this);
    }

    private void removeRedundantPrivileges(Session session) throws RepositoryException {
        final Set<String> cleanedPrivileges = removeRedundantPrivileges(session, getPrivileges(), getActions());
        privilegesString = StringUtils.join(cleanedPrivileges, ",");
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
    private RestrictionModel getRestrictions(Session session, JackrabbitAccessControlList acl)
            throws ValueFormatException, UnsupportedRepositoryOperationException, RepositoryException {
        final Collection<String> supportedRestrictionNames = Arrays.asList(acl.getRestrictionNames());
        if (!restrictionMap.isEmpty()) {
            return getRestrictions(session.getValueFactory(), acl, supportedRestrictionNames);

        } else {
            return RestrictionModel.empty();
        }
    }

    private RestrictionModel getRestrictions(final ValueFactory valueFactory, final JackrabbitAccessControlList acl,
            final Collection<String> supportedRestrictionNames)
                    throws ValueFormatException, UnsupportedRepositoryOperationException, RepositoryException {
        for (final String restrictionName : restrictionMap.keySet()) {
            if (!supportedRestrictionNames.contains(restrictionName)) {
                throw new IllegalStateException(
                        "The AccessControlList at " + acl.getPath() + " does not support setting " + restrictionName + " restrictions!");
            }
        }

        List<Restriction> restrictions = new ArrayList<Restriction>();

        for (final String restrictionName : restrictionMap.keySet()) {
            final int restrictionMapSize = restrictionMap.get(restrictionName).size();
            if (restrictionMapSize > 1) {
                final Value[] values = new Value[restrictionMapSize];
                for (int i = 0; i < restrictionMapSize; i++) {
                    final Value value = valueFactory.createValue(restrictionMap.get(restrictionName).get(i),
                            acl.getRestrictionType(restrictionName));
                    values[i] = value;
                }
                restrictions.add(new Restriction(restrictionName, values));
            } else if (restrictionMap.get(restrictionName).size() == 1) {
                final Value value = valueFactory.createValue(restrictionMap.get(restrictionName).get(0),
                        acl.getRestrictionType(restrictionName));
                restrictions.add(new Restriction(restrictionName, value));
            }
        }
        return new RestrictionModel(restrictions);
    }

    /** Creates an action map being used in {@link CqActions#installActions(String, Principal, Map, Collection)} out of the set actions on
     * this bean.
     *
     * @return a map containing actions as keys and booleans representing {@code true} for allow and {@code false} for deny. */
    private Map<String, Boolean> getActionMap() {
        if (actions == null) {
            return Collections.emptyMap();
        }
        final Map<String, Boolean> actionMap = new HashMap<String, Boolean>();
        for (final String action : actions) {
            actionMap.put(action, isAllow());
        }
        return actionMap;
    }

    /** Installs the CQ actions in the repository.
     *
     * @param principal
     * @param acl
     * @param session
     * @param acMgr
     * @return either the same acl as given in the parameter {@code acl} if no actions have been installed otherwise the new
     *         AccessControlList (comprising the entres being installed for the actions).
     * @throws RepositoryException
     * @throws SecurityException
     * @throws NoSuchMethodException */
    private JackrabbitAccessControlList installActions(Principal principal, JackrabbitAccessControlList acl, Session session,
            AccessControlManager acMgr, AcInstallationHistoryPojo history) throws RepositoryException, SecurityException {
        final Map<String, Boolean> actionMap = getActionMap();
        if (actionMap.isEmpty()) {
            return acl;
        }

        final CqActions cqActions = new CqActions(session);
        final Collection<String> inheritedAllows = cqActions.getAllowedActions(
                getJcrPath(), Collections.singleton(principal));
        // this does always install new entries
        cqActions.installActions(getJcrPath(), principal, actionMap,
                inheritedAllows);

        // since the aclist has been modified, retrieve it again
        final JackrabbitAccessControlList newAcl = AccessControlUtils.getAccessControlList(session, getJcrPath());
        final RestrictionModel restrictions = getRestrictions(session, acl);

        if (restrictionMap.isEmpty()) {
            return newAcl;
        }
        // additionally set restrictions on the installed actions (this is not supported by CQ Security API)
        addAdditionalRestriction(acl, newAcl, restrictions);
        return newAcl;
    }

    private void addAdditionalRestriction(JackrabbitAccessControlList oldAcl, JackrabbitAccessControlList newAcl,
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

            if (lastOldAce.equals(lastNewAce) && lastNewAce.getPrincipal().getName().equals(getPrincipalName())) {
                addRestrictionIfNotSet(newAcl, restrictions, lastNewAce);

            } else {
                throw new IllegalStateException("No new entries have been set for AccessControlList at " + getJcrPath());
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

    private boolean installPrivileges(Principal principal, JackrabbitAccessControlList acl, Session session, AccessControlManager acMgr)
            throws RepositoryException {
        // then install remaining privileges
        final Set<Privilege> privileges = AccessControlUtils.getPrivilegeSet(getPrivileges(), acMgr);
        if (!privileges.isEmpty()) {
            final RestrictionModel restrictions = getRestrictions(session, acl);
            if (!restrictions.isEmpty()) {
                acl.addEntry(principal, privileges
                        .toArray(new Privilege[privileges.size()]), isAllow(),
                        restrictions.getSingleValuedRestrictionsMap(), restrictions.getMultiValuedRestrictionsMap());
            } else {
                acl.addEntry(principal, privileges
                        .toArray(new Privilege[privileges.size()]), isAllow());
            }
            return true;
        }
        return false;
    }

    /** Installs the AccessControlEntry being represented by this bean in the repository
     *
     * @throws SecurityException
     * @throws NoSuchMethodException */
    public void install(final Session session, Principal principal,
            AcInstallationHistoryPojo history) throws RepositoryException, SecurityException {

        if (isInitialContentOnlyConfig()) {
            return;
        }

        final AccessControlManager acMgr = session.getAccessControlManager();

        JackrabbitAccessControlList acl = AccessControlUtils.getModifiableAcl(
                acMgr, getJcrPath());
        if (acl == null) {
            final String msg = "Skipped installing privileges/actions for non existing path: " + getJcrPath();
            LOG.debug(msg);
            history.addMessage(msg);
            return;
        }

        // first install actions
        final JackrabbitAccessControlList newAcl = installActions(principal, acl, session, acMgr, history);
        if (acl != newAcl) {
            history.addVerboseMessage("added action(s) for path: " + getJcrPath()
                    + ", principal: " + principal.getName() + ", actions: "
                    + getActionsString() + ", allow: " + isAllow());
            removeRedundantPrivileges(session);
            acl = newAcl;
        }

        // then install (remaining) privileges
        if (installPrivileges(principal, acl, session, acMgr)) {
            history.addVerboseMessage("added privilege(s) for path: " + getJcrPath()
                    + ", principal: " + principal.getName() + ", privileges: "
                    + getPrivilegesString() + ", allow: " + isAllow());
        }
        acMgr.setPolicy(getJcrPath(), acl);
    }

    public boolean isInitialContentOnlyConfig() {
        return StringUtils.isNotBlank(initialContent)
                && StringUtils.isBlank(permission)
                && StringUtils.isBlank(privilegesString)
                && StringUtils.isBlank(actionsStringFromConfig);
    }

}
