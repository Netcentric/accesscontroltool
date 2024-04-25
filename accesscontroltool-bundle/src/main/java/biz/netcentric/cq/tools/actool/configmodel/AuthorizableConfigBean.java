/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configmodel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import biz.netcentric.cq.tools.actool.configmodel.pkcs.Key;
import biz.netcentric.cq.tools.actool.dumpservice.AcDumpElement;
import biz.netcentric.cq.tools.actool.dumpservice.AcDumpElementVisitor;
import biz.netcentric.cq.tools.actool.externalusermanagement.ExternalGroupManagement;

public class AuthorizableConfigBean implements AcDumpElement {

    private String authorizableId;
    private String principalName;

    // (non-technical) name/email/description (profile properties)
    private String name; 
    private String email;
    private String description;

    private String[] isMemberOf;
    private String[] members;

    private String path;
    private String password;

    private String externalId;

    private String profileContent;
    private String preferencesContent;
    private String socialContent;

    private String migrateFrom;

    private String unmanagedAcePathsRegex;
    private Pattern unmanagedExternalIsMemberOfRegex;
    private Pattern unmanagedExternalMembersRegex;

    private boolean isGroup = true;
    private boolean isSystemUser = false;

    private String disabled;

    private boolean virtual;
    private boolean appendToKeyStore;
    private String keyStorePassword;
    private Map<String, Key> keys;
    private List<String> impersonationAllowedFor;

    /**
     * if {@true} synchronized with external user managed. Synchronization happens via all active services of type {@link ExternalGroupManagement}
     */
    private boolean externalSync;
    
    public String getAuthorizableId() {
        return authorizableId;
    }

    public void setAuthorizableId(final String authorizableId) {
        this.authorizableId = authorizableId;
    }

    public String getPrincipalName() {
        return principalName;
    }

    public void setPrincipalName(final String principalName) {
        this.principalName = principalName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(final String path) {
        this.path = path;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(final String password) {
        this.password = password;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getProfileContent() {
        return profileContent;
    }

    public void setProfileContent(String profileContent) {
        this.profileContent = profileContent;
    }

    public String getPreferencesContent() {
        return preferencesContent;
    }

    public void setPreferencesContent(String preferencesContent) {
        this.preferencesContent = preferencesContent;
    }

    public String getSocialContent() {
        return socialContent;
    }

    public void setSocialContent(String socialContent) {
        this.socialContent = socialContent;
    }

    public boolean isGroup() {
        return isGroup;
    }

    public void setIsGroup(final boolean isGroup) {
        this.isGroup = isGroup;
    }

    public boolean isSystemUser() {
        return isSystemUser;
    }

    public void setIsSystemUser(final boolean isSystemUser) {
        this.isSystemUser = isSystemUser;
    }

    public String[] getIsMemberOf() {
        return isMemberOf;
    }

    public boolean isMemberOfOtherGroups() {
        return isMemberOf != null;
    }

    public String getIsMemberOfString() {
        if (isMemberOf == null) {
            return "";
        }
        return StringUtils.join(isMemberOf, ",");
    }

    public void setIsMemberOf(final String[] memberOf) {
        this.isMemberOf = memberOf;
    }

    public void setIsMemberOf(final List<String> memberOf) {
        if (memberOf != null) {
            this.isMemberOf = memberOf.toArray(new String[memberOf.size()]);
        } else {
            this.isMemberOf = null;
        }
    }

    public void addIsMemberOf(final String member) {
        if (isMemberOf == null) {
            isMemberOf = new String[] { member };
            return;
        }
        final List<String> memberList = new ArrayList<String>();
        memberList.addAll(Arrays.asList(isMemberOf));
        if (!memberList.contains(member)) {
            memberList.add(member);
            isMemberOf = memberList.toArray(new String[memberList.size()]);
        }
    }

    public String getMembersString() {
        if (members == null) {
            return "";
        }

        final StringBuilder membersString = new StringBuilder();

        for (final String group : members) {
            membersString.append(group).append(",");
        }
        return StringUtils.chop(membersString.toString());
    }

    public String[] getMembers() {
        return members;
    }

    public void setMembers(final String[] members) {
        this.members = members;
    }

    public String getDisabled() {
        return disabled;
    }

    public void setDisabled(String disabled) {
        this.disabled = disabled;
    }

    public String getMigrateFrom() {
        return migrateFrom;
    }


    /** Set a group name, from which the users are taken over to this group. The group given is deleted after the run. This property is only
     * to be used temporarily (usually only included in one released version that travels all environments, once all groups are migrated the
     * config should be removed). If not set (the default) nothing happens. If the property points to a group that does not exist (anymore),
     * the property is ignored.
     *
     * @param migrateFrom */
    public void setMigrateFrom(String migrateFrom) {
        this.migrateFrom = migrateFrom;
    }

    public String getUnmanagedAcePathsRegex() {
        return unmanagedAcePathsRegex;
    }

    public void setUnmanagedAcePathsRegex(String unmanagedAcePathsRegex) {
        this.unmanagedAcePathsRegex = unmanagedAcePathsRegex;
    }

    public Pattern getUnmanagedExternalIsMemberOfRegex() {
        return unmanagedExternalIsMemberOfRegex;
    }

    public void setUnmanagedExternalIsMemberOfRegex(String unmanagedExternalIsMemberOfRegex) {
        this.unmanagedExternalIsMemberOfRegex = GlobalConfiguration.stringToRegex(unmanagedExternalIsMemberOfRegex);
    }

    public Pattern getUnmanagedExternalMembersRegex() {
        return unmanagedExternalMembersRegex;
    }

    public void setUnmanagedExternalMembersRegex(String unmanagedExternalMembersRegex) {
        this.unmanagedExternalMembersRegex = GlobalConfiguration.stringToRegex(unmanagedExternalMembersRegex);
    }

    public boolean isVirtual() {
        return virtual;
    }

    public void setVirtual(boolean virtual) {
        this.virtual = virtual;
    }

    public Map<String, Key> getKeys() {
        return keys;
    }

    public void setKeys(Map<String, Key> keys) {
        this.keys = keys;
    }

    public boolean isAppendToKeyStore() {
        return appendToKeyStore;
    }

    public void setAppendToKeyStore(boolean appendToKeyStore) {
        this.appendToKeyStore = appendToKeyStore;
    }

    public List<String> getImpersonationAllowedFor() {
        return impersonationAllowedFor;
    }
    
    public void setImpersonationAllowedFor(List<String> impersonationAllowedFor) {
        this.impersonationAllowedFor = impersonationAllowedFor;
    }

    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    public void setKeyStorePassword(String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("\n" + "id: " + authorizableId + "\n");
        sb.append("name: " + principalName + "\n");
        sb.append("path: " + path + "\n");
        sb.append("isMemberOf: " + getIsMemberOfString() + "\n");
        sb.append("members: " + getMembersString() + "\n");
        sb.append("appendToKeyStore: " + isAppendToKeyStore() + "\n");
        sb.append("keys:" + getKeys() + "\n");
        sb.append("impersonationAllowedFor:" + getImpersonationAllowedFor() + "\n");
        sb.append("externalSync: " + isExternalSync());
        return sb.toString();
    }

    public boolean managesPath(String path, String defaultUnmanagedAcePathsRegex) {
        String effectiveUnmanagedAcePathsRegex = StringUtils.defaultIfEmpty(unmanagedAcePathsRegex, defaultUnmanagedAcePathsRegex);
        if (StringUtils.isNotBlank(effectiveUnmanagedAcePathsRegex)
                && StringUtils.isNotBlank(path) /* not supporting repository permissions here */) {
            boolean pathIsManaged = !path.matches(effectiveUnmanagedAcePathsRegex);
            return pathIsManaged;
        } else {
            return true; // default
        }
    }

    @Override
    public void accept(final AcDumpElementVisitor acDumpElementVisitor) {
        acDumpElementVisitor.visit(this);
    }

    public boolean isExternalSync() {
        return externalSync;
    }

    public void setExternalSync(boolean externalSync) {
        this.externalSync = externalSync;
    }

}
