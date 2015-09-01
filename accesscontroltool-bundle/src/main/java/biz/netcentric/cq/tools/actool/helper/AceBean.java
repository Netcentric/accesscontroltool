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

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.Value;
import javax.jcr.ValueFormatException;
import javax.jcr.security.AccessControlEntry;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.Privilege;

import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlEntry;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;

import biz.netcentric.cq.tools.actool.dumpservice.AcDumpElement;
import biz.netcentric.cq.tools.actool.dumpservice.AcDumpElementVisitor;
import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;

import com.day.cq.security.util.CqActions;

/**
 * 
 * @author jochenkoschorke This class is used to store data of an
 *         AcessControlEntry. Objects of this class get created during the
 *         reading of the configuration file in order to set the corresponding
 *         ACEs in the system on the one hand and to store data during the
 *         reading of existing ACEs before writing the data back to a dump or
 *         configuration file again on the other hand.
 */
public class AceBean implements AcDumpElement {

    private String jcrPath;
    private String repGlob;
    private String actionsStringFromConfig;
    private String privilegesString;
    private String principal;
    private String permission;
    private String[] actions;
    private String assertedExceptionString;
    
    
    public static final String RESTRICTION_NAME_GLOB = "rep:glob";

    public String getAssertedExceptionString() {
        return assertedExceptionString;
    }

    public void setAssertedExceptionString(final String assertedException) {
        this.assertedExceptionString = assertedException;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permissionString) {
        this.permission = permissionString;
    }

    public void clearActions() {
        this.actions = null;
        this.actionsStringFromConfig = "";
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
        return "allow".equalsIgnoreCase(this.permission);
    }

    public String getRepGlob() {
        return repGlob;
    }

    public void setRepGlob(String repGlob) {
        this.repGlob = repGlob;
    }

    public String getActionsString() {
        if (this.actions != null) {
            StringBuilder sb = new StringBuilder();
            for (String action : this.actions) {
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

    @Override
    public String toString() {
        return "AceBean [jcrPath=" + jcrPath + "\n" + ", permission=" + permission
                + "\n" + ", repGlob=" + repGlob + "\n" + ", actionsString="
                + actionsStringFromConfig + "\n" + ", privilegesString="
                + privilegesString + "\n" + ", principal=" + principal + "\n"
                + ", actions=" + Arrays.toString(actions) + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(actions);
        result = prime
                * result
                + ((actionsStringFromConfig == null) ? 0
                        : actionsStringFromConfig.hashCode());
        result = prime * result + ((jcrPath == null) ? 0 : jcrPath.hashCode());
        result = prime * result
                + ((permission == null) ? 0 : permission.hashCode());
        result = prime * result
                + ((principal == null) ? 0 : principal.hashCode());
        result = prime
                * result
                + ((privilegesString == null) ? 0 : privilegesString.hashCode());
        result = prime * result + ((repGlob == null) ? 0 : repGlob.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AceBean other = (AceBean) obj;
        if (!Arrays.equals(actions, other.actions))
            return false;
        if (actionsStringFromConfig == null) {
            if (other.actionsStringFromConfig != null)
                return false;
        } else if (!actionsStringFromConfig
                .equals(other.actionsStringFromConfig))
            return false;
        if (jcrPath == null) {
            if (other.jcrPath != null)
                return false;
        } else if (!jcrPath.equals(other.jcrPath))
            return false;
        if (principal == null) {
            if (other.principal != null)
                return false;
        } else if (!principal.equals(other.principal))
            return false;
        if (permission == null) {
            if (other.permission != null)
                return false;
        } else if (!permission.equals(other.permission))
            return false;
        if (privilegesString == null) {
            if (other.privilegesString != null)
                return false;
        } else if (!privilegesString.equals(other.privilegesString))
            return false;
        if (repGlob == null) {
            if (other.repGlob != null)
                return false;
        } else if (!repGlob.equals(other.repGlob))
            return false;
        return true;
    }

    @Override
    public void accept(AcDumpElementVisitor acDumpElementVisitor) {
        acDumpElementVisitor.visit(this);
    }

	private void removeRedundantPrivileges(Session session) throws RepositoryException {
		Set<String> cleanedPrivileges = removeRedundantPrivileges(session, getPrivileges(), getActions());
		privilegesString = StringUtils.join(cleanedPrivileges, ",");
	}
	
	/** 
	 * Modifies the privileges so that privileges already covered by actions are removed.
	 * This is only a best effort operation as one action can lead to privileges on multiple nodes.
	 * @throws RepositoryException 
	 */
	private static Set<String> removeRedundantPrivileges(Session session, String[] privileges, String[] actions) throws RepositoryException {
		CqActions cqActions = new CqActions(session);
		Set<String> cleanedPrivileges = new HashSet<String>();
		if (privileges == null) {
			return cleanedPrivileges;
		}
		cleanedPrivileges.addAll(Arrays.asList(privileges));
		if (actions == null) {
			return cleanedPrivileges;
		}
		for (String action : actions) {
			@SuppressWarnings("deprecation")
			Set<Privilege> coveredPrivileges = cqActions.getPrivileges(action);
			for (Privilege coveredPrivilege : coveredPrivileges) {
				cleanedPrivileges.remove(coveredPrivilege.getName());
			}
		}
		return cleanedPrivileges;
	}
	
	/**
	 * Creates a restriction map being used in {@link JackrabbitAccessControlList#addEntry(Principal, Privilege[], boolean, Map)} out of the set actions on this bean.
	 *
	 * @param session the session
	 * @param acl the access control list for which this restriction map should be used
	 * @return a map with restriction names as keys and restriction values as values.
	 * @throws ValueFormatException
	 * @throws UnsupportedRepositoryOperationException
	 * @throws RepositoryException
	 */
	private Map<String, Value> getSingleValueRestrictions(Session session, JackrabbitAccessControlList acl) throws ValueFormatException, UnsupportedRepositoryOperationException, RepositoryException {
		Collection<String> supportedRestrictionNames = Arrays.asList(acl.getRestrictionNames());
		if (getRepGlob() != null) {
			if (!supportedRestrictionNames.contains(RESTRICTION_NAME_GLOB)) {
				throw new IllegalStateException("The AccessControlList at " + acl.getPath() + " does not support setting rep:glob restrictions!");
			}
			Value v = session.getValueFactory().createValue(getRepGlob(), acl.getRestrictionType(RESTRICTION_NAME_GLOB));
			return Collections.singletonMap(RESTRICTION_NAME_GLOB, v);
		} else {
			return Collections.emptyMap();
		}
	}
 	
	/**
	 * Creates an action map being used in {@link CqActions#installActions(String, Principal, Map, Collection)} out of the set actions on this bean.
	 * @return a map containing actions as keys and booleans representing {@code true} for allow and {@code false} for deny.
	 */
	private Map<String, Boolean> getActionMap() {
		if (actions == null) {
			return Collections.emptyMap();
		}
		Map<String, Boolean> actionMap = new HashMap<String, Boolean>();
		for (String action : actions) {
            actionMap.put(action, isAllow());
        }
		return actionMap;
	}

	/**
	 * Installs the CQ actions in the repository.
	 * 
	 * @param principal
	 * @param acl
	 * @param session
	 * @param acMgr
	 * @return either the same acl as given in the parameter {@code acl} if no actions have been installed otherwise the new AccessControlList (comprising the entres being installed for the actions).
	 * @throws RepositoryException
	 */
	private JackrabbitAccessControlList installActions(Principal principal, JackrabbitAccessControlList acl, Session session, AccessControlManager acMgr) throws RepositoryException {
		Map<String, Boolean> actionMap = getActionMap();
		if (actionMap.isEmpty()) {
			return acl;
		}
		int previousAclSize = acl.size();
		
		CqActions cqActions = new CqActions(session);
		Collection<String> inheritedAllows = cqActions.getAllowedActions(
				getJcrPath(), Collections.singleton(principal));
		// this does always install new entries
		cqActions.installActions(getJcrPath(), principal, actionMap,
				inheritedAllows);
		
		// since the aclist has been modified, retrieve it again
		JackrabbitAccessControlList newAcl = AccessControlUtils.getAccessControlList(session, getJcrPath());
		Map<String, Value> restrictions = getSingleValueRestrictions(session, acl);
		if (restrictions.isEmpty()) {
			return newAcl;
		}
		// additionally set restrictions on the installed actions (this is not supported by CQ Security API)
		int newAclSize = newAcl.size();
		if (previousAclSize >= newAclSize) {
			throw new IllegalStateException("No new entries have been set for AccessControlList at " + getJcrPath());
		}
		AccessControlEntry[] aces = newAcl.getAccessControlEntries();
		for (int acEntryIndex = previousAclSize; acEntryIndex < newAclSize; acEntryIndex++) {
			if (!(aces[acEntryIndex] instanceof JackrabbitAccessControlEntry)) {
				throw new IllegalStateException("Can not deal with non JackrabbitAccessControlEntrys, but entry is of type " + aces[acEntryIndex].getClass().getName());
			}
			JackrabbitAccessControlEntry ace = (JackrabbitAccessControlEntry)aces[acEntryIndex];
			// only extend those AccessControlEntries which do not yet have a restriction
			if (ace.getRestrictions("rep:glob") == null) {
				// modify this AccessControlEntry by adding the restriction
				AccessControlUtils.extendExistingAceWithRestrictions(newAcl, ace, restrictions);
			}
		}
		return newAcl;
	}
	
	private boolean installPrivileges(Principal principal, JackrabbitAccessControlList acl, Session session, AccessControlManager acMgr) throws RepositoryException {
		// then install remaining privileges
		Set<Privilege> privileges = AccessControlUtils.getPrivilegeSet(getPrivileges(), acMgr);
		if (!privileges.isEmpty()) {
			Map<String, Value> restrictions = getSingleValueRestrictions(session, acl);
			if (!restrictions.isEmpty()) {
				acl.addEntry(principal, (Privilege[]) privileges
						.toArray(new Privilege[privileges.size()]), isAllow(),
						restrictions);
			} else {
				acl.addEntry(principal, (Privilege[]) privileges
						.toArray(new Privilege[privileges.size()]), isAllow());
			}
			return true;
		}
		return false;
	}

	/**
	 * Installs the AccessControlEntry being represented by this bean in the
	 * repository
	 */
	public void install(final Session session, Principal principal,
			AcInstallationHistoryPojo history) throws RepositoryException {
		AccessControlManager acMgr = session.getAccessControlManager();
		
		JackrabbitAccessControlList acl = AccessControlUtils.getModifiableAcl(
				acMgr, getJcrPath());
		if (acl == null) {
			history.addWarning("Skipped installing privileges/actions for non existing path: " + getJcrPath());
			return;
		}
		
		// first install actions
		JackrabbitAccessControlList newAcl = installActions(principal, acl, session, acMgr);
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
}
