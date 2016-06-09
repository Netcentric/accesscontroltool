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
    private String repGlob;
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
        return this.assertedExceptionString;
    }

    public void setAssertedExceptionString(final String assertedException) {
        this.assertedExceptionString = assertedException;
    }

    public String getPermission() {
        return this.permission;
    }

    public void setPermission(String permissionString) {
        this.permission = permissionString;
    }

    public void clearActions() {
        this.actions = null;
        this.actionsStringFromConfig = "";
    }

    public String getPrincipalName() {
        return this.principal;
    }

    public void setPrincipal(String principal) {
        this.principal = principal;
    }

    public String getJcrPath() {
        return this.jcrPath;
    }

    public void setJcrPath(String jcrPath) {
        this.jcrPath = jcrPath;
    }

    public boolean isAllow() {
        return "allow".equalsIgnoreCase(this.permission);
    }

    public String getRepGlob() {
        return this.repGlob;
    }

    public void setRepGlob(String repGlob) {
        this.repGlob = repGlob;
    }

    public Map<String, List<String>> getRestrictions() {
        return this.restrictionMap;
    }

    public void setRestrictions(final Map<String, ?> currentAceDefinition, final AceBean tmpAclBean) {
        for (final String key : currentAceDefinition.keySet()) {
            if (key.startsWith("rep:")) {
                final String value = (String) currentAceDefinition.get(key);
                final String[] values = value.split(",");
                tmpAclBean.addRestriction(key, new ArrayList<String>(Arrays.asList(values)));
            }
        }
    }

    public void setRestrictionsMap(Map<String, List<String>> restrictionsMap) {
        this.restrictionMap = restrictionsMap;
    }

    public void addRestriction(final String restrictionName, final List<String> restrictionValue) {
        this.restrictionMap.put(restrictionName, restrictionValue);
    }

    public String getActionsString() {
        if (this.actions != null) {
            final StringBuilder sb = new StringBuilder();
            for (final String action : this.actions) {
                sb.append(action).append(",");
            }
            return StringUtils.chomp(sb.toString(), ",");
        }
        return "";
    }

    public String getActionsStringFromConfig() {
        return this.actionsStringFromConfig;
    }

    public void setActions(String[] actions) {
        this.actions = actions;
    }

    public void setActionsStringFromConfig(String actionsString) {
        this.actionsStringFromConfig = actionsString;
    }

    public String[] getActions() {
        return this.actions;
    }

    public String getPrivilegesString() {
        return this.privilegesString;
    }

    public String[] getPrivileges() {
        if (StringUtils.isNotBlank(this.privilegesString)) {
            return this.privilegesString.split(",");
        }
        return null;
    }

    public void setPrivilegesString(String privilegesString) {
        this.privilegesString = privilegesString;
    }

    public String getInitialContent() {
        return this.initialContent;
    }

    public void setInitialContent(String initialContent) {
        this.initialContent = initialContent;
    }

    @Override
    public String toString() {
        return "AceBean [jcrPath=" + this.jcrPath + "\n" + ", repGlob=" + this.repGlob + "\n" + ", actionsStringFromConfig=" + this.actionsStringFromConfig
                + "\n"
                + ", privilegesString=" + this.privilegesString + "\n" + ", principal=" + this.principal + "\n" + ", permission=" + this.permission
                + ", actions="
                + Arrays.toString(this.actions) + "\n" + ", assertedExceptionString=" + this.assertedExceptionString + "\n" + ", restrictionMap="
                + this.restrictionMap + "\n"
                + ", initialContent=" + this.initialContent + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(this.actions);
        result = prime * result + ((this.actionsStringFromConfig == null) ? 0 : this.actionsStringFromConfig.hashCode());
        result = prime * result + ((this.assertedExceptionString == null) ? 0 : this.assertedExceptionString.hashCode());
        result = prime * result + ((this.initialContent == null) ? 0 : this.initialContent.hashCode());
        result = prime * result + ((this.jcrPath == null) ? 0 : this.jcrPath.hashCode());
        result = prime * result + ((this.permission == null) ? 0 : this.permission.hashCode());
        result = prime * result + ((this.principal == null) ? 0 : this.principal.hashCode());
        result = prime * result + ((this.privilegesString == null) ? 0 : this.privilegesString.hashCode());
        result = prime * result + ((this.repGlob == null) ? 0 : this.repGlob.hashCode());
        result = prime * result + ((this.restrictionMap == null) ? 0 : this.restrictionMap.hashCode());
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
        if (!Arrays.equals(this.actions, other.actions)) {
            return false;
        }
        if (this.actionsStringFromConfig == null) {
            if (other.actionsStringFromConfig != null) {
                return false;
            }
        } else if (!this.actionsStringFromConfig.equals(other.actionsStringFromConfig)) {
            return false;
        }
        if (this.assertedExceptionString == null) {
            if (other.assertedExceptionString != null) {
                return false;
            }
        } else if (!this.assertedExceptionString.equals(other.assertedExceptionString)) {
            return false;
        }
        if (this.initialContent == null) {
            if (other.initialContent != null) {
                return false;
            }
        } else if (!this.initialContent.equals(other.initialContent)) {
            return false;
        }
        if (this.jcrPath == null) {
            if (other.jcrPath != null) {
                return false;
            }
        } else if (!this.jcrPath.equals(other.jcrPath)) {
            return false;
        }
        if (this.permission == null) {
            if (other.permission != null) {
                return false;
            }
        } else if (!this.permission.equals(other.permission)) {
            return false;
        }
        if (this.principal == null) {
            if (other.principal != null) {
                return false;
            }
        } else if (!this.principal.equals(other.principal)) {
            return false;
        }
        if (this.privilegesString == null) {
            if (other.privilegesString != null) {
                return false;
            }
        } else if (!this.privilegesString.equals(other.privilegesString)) {
            return false;
        }
        if (this.repGlob == null) {
            if (other.repGlob != null) {
                return false;
            }
        } else if (!this.repGlob.equals(other.repGlob)) {
            return false;
        }
        if (this.restrictionMap == null) {
            if (other.restrictionMap != null) {
                return false;
            }
        } else if (!this.restrictionMap.equals(other.restrictionMap)) {
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
        this.privilegesString = StringUtils.join(cleanedPrivileges, ",");
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

    /** Creates a restriction map being used in {@link JackrabbitAccessControlList#addEntry(Principal, Privilege[], boolean, Map)} out of
     * the set actions on this bean.
     *
     * @param session the session
     * @param acl the access control list for which this restriction map should be used
     * @return RestrictionMapsHolder with restriction names as keys and restriction values as values.
     * @throws ValueFormatException
     * @throws UnsupportedRepositoryOperationException
     * @throws RepositoryException */
    private RestrictionMapsHolder getRestrictions(Session session, JackrabbitAccessControlList acl)
            throws ValueFormatException, UnsupportedRepositoryOperationException, RepositoryException {
        final Collection<String> supportedRestrictionNames = Arrays.asList(acl.getRestrictionNames());
        if (!this.restrictionMap.isEmpty()) {
            return getRestrictionMaps(session.getValueFactory(), acl, supportedRestrictionNames);
        } else {
            return RestrictionMapsHolder.emptyHolder();
        }
    }

    private RestrictionMapsHolder getRestrictionMaps(final ValueFactory valueFactory, final JackrabbitAccessControlList acl,
            final Collection<String> supportedRestrictionNames)
                    throws ValueFormatException, UnsupportedRepositoryOperationException, RepositoryException {
        for (final String restrictionName : this.restrictionMap.keySet()) {
            if (!supportedRestrictionNames.contains(restrictionName)) {
                throw new IllegalStateException(
                        "The AccessControlList at " + acl.getPath() + " does not support setting " + restrictionName + " restrictions!");
            }
        }
        final Map<String, Value> singleValuedRestrictions = new HashMap<>();
        final Map<String, Value[]> multiValuedRestrictions = new HashMap<>();

        for (final String restrictionName : this.restrictionMap.keySet()) {
            final int restrictionMapSize = this.restrictionMap.get(restrictionName).size();
            if (restrictionMapSize > 1) {
                final Value[] values = new Value[restrictionMapSize];
                for (int i = 0; i < restrictionMapSize; i++) {
                    final Value value = valueFactory.createValue(this.restrictionMap.get(restrictionName).get(i),
                            acl.getRestrictionType(restrictionName));
                    values[i] = value;
                }
                multiValuedRestrictions.put(restrictionName, values);
            } else if (this.restrictionMap.get(restrictionName).size() == 1) {
                final Value value = valueFactory.createValue(this.restrictionMap.get(restrictionName).get(0),
                        acl.getRestrictionType(restrictionName));
                singleValuedRestrictions.put(restrictionName, value);
            }
        }
        return new RestrictionMapsHolder(singleValuedRestrictions, multiValuedRestrictions);
    }

    /** Creates an action map being used in {@link CqActions#installActions(String, Principal, Map, Collection)} out of the set actions on
     * this bean.
     *
     * @return a map containing actions as keys and booleans representing {@code true} for allow and {@code false} for deny. */
    private Map<String, Boolean> getActionMap() {
        if (this.actions == null) {
            return Collections.emptyMap();
        }
        final Map<String, Boolean> actionMap = new HashMap<String, Boolean>();
        for (final String action : this.actions) {
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
        final RestrictionMapsHolder restrictions = getRestrictions(session, acl);

        if (this.restrictionMap.isEmpty()) {
            return newAcl;
        }
        // additionally set restrictions on the installed actions (this is not supported by CQ Security API)
        addAdditionalRestriction(acl, newAcl, restrictions);
        return newAcl;
    }

    private void addAdditionalRestriction(JackrabbitAccessControlList oldAcl, JackrabbitAccessControlList newAcl,
            RestrictionMapsHolder restrictions)
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

    private void addRestrictionIfNotSet(JackrabbitAccessControlList newAcl, RestrictionMapsHolder restrictions,
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
            final RestrictionMapsHolder restrictions = getRestrictions(session, acl);
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
        return StringUtils.isNotBlank(this.initialContent)
                && StringUtils.isBlank(this.permission)
                && StringUtils.isBlank(this.privilegesString)
                && StringUtils.isBlank(this.actionsStringFromConfig);
    }

}
