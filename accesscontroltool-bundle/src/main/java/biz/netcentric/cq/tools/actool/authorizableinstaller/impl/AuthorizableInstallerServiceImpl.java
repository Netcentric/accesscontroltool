/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.authorizableinstaller.impl;

import static biz.netcentric.cq.tools.actool.helper.Constants.PRINCIPAL_EVERYONE;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.ValueFactory;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.AuthorizableExistsException;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.oak.spi.security.principal.PrincipalImpl;
import org.apache.sling.api.SlingIOException;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.granite.keystore.KeyStoreNotInitialisedException;
import com.adobe.granite.keystore.KeyStoreService;

import biz.netcentric.cq.tools.actool.authorizableinstaller.AuthorizableCreatorException;
import biz.netcentric.cq.tools.actool.authorizableinstaller.AuthorizableInstallerService;
import biz.netcentric.cq.tools.actool.configmodel.AcConfiguration;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizablesConfig;
import biz.netcentric.cq.tools.actool.configmodel.pkcs.Key;
import biz.netcentric.cq.tools.actool.configmodel.pkcs.RandomPassword;
import biz.netcentric.cq.tools.actool.crypto.DecryptionService;
import biz.netcentric.cq.tools.actool.externalusermanagement.ExternalGroupManagement;
import biz.netcentric.cq.tools.actool.helper.AcHelper;
import biz.netcentric.cq.tools.actool.helper.AccessControlUtils;
import biz.netcentric.cq.tools.actool.helper.Constants;
import biz.netcentric.cq.tools.actool.helper.ContentHelper;
import biz.netcentric.cq.tools.actool.history.InstallationLogger;

@org.osgi.service.component.annotations.Component()
public class AuthorizableInstallerServiceImpl implements
        AuthorizableInstallerService {

    private static final Logger LOG = LoggerFactory.getLogger(AuthorizableInstallerServiceImpl.class);

    private static final String PATH_SEGMENT_SYSTEMUSERS = "system";

    // not using org.apache.jackrabbit.oak.spi.security.authentication.external.basic.DefaultSyncContext.REP_EXTERNAL_ID since it is an
    // optional dependency and not available in AEM 6.1
    public static final String REP_EXTERNAL_ID = "rep:externalId";

    private static final String USER_KEYSTORE_FOLDER = "keystore";

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policyOption=ReferencePolicyOption.GREEDY)
    ExternalGroupInstallerServiceImpl externalGroupCreatorService;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policyOption=ReferencePolicyOption.GREEDY)
    ImpersonationInstallerServiceImpl impersonationInstallerService;
    
    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    DecryptionService decryptionService;
    
    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy=ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY)
    volatile KeyStoreService keyStoreService;
    
    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    ResourceResolverFactory resourceResolverFactory;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    SlingRepository repository;

    @Reference(policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MULTIPLE)
    List<ExternalGroupManagement> externalGroupManagementServices;

    @Override
    public void installAuthorizables(
            AcConfiguration acConfiguration,
            AuthorizablesConfig authorizablesConfigBeans,
            final Session session, InstallationLogger installLog)
            throws RepositoryException, AuthorizableCreatorException, LoginException, IOException, GeneralSecurityException {

        AuthInstallerUserManager userManager = new AuthInstallerUserManagerPrefetchingImpl(AccessControlUtils.getUserManagerAutoSaveDisabled(session), session.getValueFactory(), installLog);

        Set<String> authorizablesFromConfigurations = authorizablesConfigBeans.getAuthorizableIds();
        Collection<AuthorizableConfigBean> groupsToSyncWithExternalUserMgmt = new LinkedList<>();
        for (AuthorizableConfigBean authorizableConfigBean : authorizablesConfigBeans) {

            installAuthorizableConfigurationBean(session, userManager, acConfiguration,
                    authorizableConfigBean, installLog, authorizablesFromConfigurations);
            
            if (authorizableConfigBean.isExternalSync() && authorizableConfigBean.isGroup() && !externalGroupManagementServices.isEmpty()) {
                groupsToSyncWithExternalUserMgmt.add(authorizableConfigBean);
            }
        }

        installLog.addMessage(LOG, "Created "+installLog.getCountAuthorizablesCreated() + " authorizables (moved "+installLog.getCountAuthorizablesMoved() + " authorizables)");
        syncWithExternalGroupManagement(groupsToSyncWithExternalUserMgmt, installLog);

    }

    private void syncWithExternalGroupManagement(Collection<AuthorizableConfigBean> groupConfigBeans, InstallationLogger installLog) throws IOException {
        if (groupConfigBeans.isEmpty()) {
            return;
        }
        for (ExternalGroupManagement externalGroupManagement : externalGroupManagementServices) {
            externalGroupManagement.updateGroups(groupConfigBeans);
            installLog.addMessage(LOG, "Synchronized " + groupConfigBeans.size() + " groups with external user management " + externalGroupManagement.getLabel());
        }
    }
    
    private void installAuthorizableConfigurationBean(final Session session,
            AuthInstallerUserManager userManager,
            AcConfiguration acConfiguration,
            AuthorizableConfigBean authorizableConfigBean,
            InstallationLogger installLog, Set<String> authorizablesFromConfigurations)
            throws RepositoryException, AuthorizableCreatorException, IOException, GeneralSecurityException, LoginException {

        String authorizableId = authorizableConfigBean.getAuthorizableId();
        LOG.debug("- start installation of authorizable: {}", authorizableId);

        // if current authorizable from config doesn't exist yet
        Authorizable authorizableToInstall = userManager.getAuthorizable(authorizableId);

        if(StringUtils.equals(authorizableId, PRINCIPAL_EVERYONE)) {
            if (ArrayUtils.isNotEmpty(authorizableConfigBean.getIsMemberOf()) 
                    || ArrayUtils.isNotEmpty(authorizableConfigBean.getMembers())
                    || StringUtils.isNotBlank(authorizableConfigBean.getMigrateFrom())) {
                throw new IllegalArgumentException("The special group " + PRINCIPAL_EVERYONE
                        + " does not support setting properties 'members', 'isMemberOf' and 'migrateFrom'");
            }
            // only setting authorizables properties is supported for everyone
            setAuthorizableProperties(authorizableToInstall, authorizableConfigBean, acConfiguration.getAuthorizablesConfig(), session, installLog);
            return;
        }

        if (authorizableToInstall == null) {
            authorizableToInstall = createNewAuthorizable(acConfiguration, authorizableConfigBean, installLog, userManager, session);
            
            installLog.incCountAuthorizablesCreated();
        }
        // if current authorizable from config already exists in repository
        else {
            // update name for both groups and users
            setAuthorizableProperties(authorizableToInstall, authorizableConfigBean, acConfiguration.getAuthorizablesConfig(), session, installLog);
            // update password for users
            if (!authorizableToInstall.isGroup() && !authorizableConfigBean.isSystemUser()
                    && StringUtils.isNotBlank(authorizableConfigBean.getPassword())) {
                setUserPassword(authorizableConfigBean, (User) authorizableToInstall, installLog);
            }

            // move authorizable if path changed (retaining existing members)
            handleRecreationOfAuthorizableIfNecessary(session, acConfiguration, authorizableConfigBean, installLog, userManager);

            applyGroupMembershipConfigIsMemberOf(installLog, acConfiguration, authorizableConfigBean, userManager, session,
                    authorizablesFromConfigurations);

        }

        applyGroupMembershipConfigMembers(acConfiguration, authorizableConfigBean, installLog, authorizableId, userManager,
                authorizablesFromConfigurations);

        if (StringUtils.isNotBlank(authorizableConfigBean.getMigrateFrom()) && authorizableConfigBean.isGroup()) {
            migrateFromOldGroup(authorizableConfigBean, userManager, installLog);
        }

        if (authorizableConfigBean.getKeys() != null) {
            try {
                installKeys(authorizableConfigBean.isAppendToKeyStore(), (User)authorizableToInstall,  authorizableConfigBean.getKeys(), authorizableId, decryptionService.decrypt(authorizableConfigBean.getKeyStorePassword()), session, installLog);
            } catch (UnsupportedOperationException e) {
                throw new AuthorizableCreatorException(
                        "Could not decrypt key store password for user " + authorizableConfigBean.getAuthorizableId() + ": " + e.getMessage(), e);
            }
        }
    }

    private void installKeys(boolean appendToKeyStore, User user, Map<String, Key> keys, String userId, String keyStorePassword, Session session, InstallationLogger installLog) throws LoginException, SlingIOException, SecurityException, KeyStoreNotInitialisedException, IOException, GeneralSecurityException, UnsupportedRepositoryOperationException, RepositoryException {
        Map<String, Object> authInfo = new HashMap<>();
        authInfo.put(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, session);
        ResourceResolver resolver = resourceResolverFactory.getResourceResolver(authInfo);
        try {
            if (!appendToKeyStore) {
                // always remove old store to start from scratch
                removeKeyStore(resolver, user, installLog);
            }
            installKeys(keys, userId, keyStorePassword, resolver, installLog);
        } finally {
            // the underlying session is not closed(!)
            resolver.close();
        }
    }

    private void removeKeyStore(ResourceResolver resolver, User user, InstallationLogger installLog) throws UnsupportedRepositoryOperationException, RepositoryException, PersistenceException {
        String keyStorePath = user.getPath() + "/" + USER_KEYSTORE_FOLDER;
        Resource keyStoreResource = resolver.getResource(keyStorePath);
        if (keyStoreResource != null) {
            installLog.addMessage(LOG, "Deleting old key store for user  '"+ user.getID() +"'");
            resolver.delete(keyStoreResource);
        } else {
            installLog.addMessage(LOG, "No key store for user  '"+ user.getID() +"' found to delete");
        }
    }

    private void installKeys(Map<String, Key> keys, String userId, String keyStorePassword, ResourceResolver resourceResolver, InstallationLogger installLog) throws SlingIOException, SecurityException, KeyStoreNotInitialisedException, IOException, GeneralSecurityException {
        if (keyStoreService == null) {
            throw new IllegalStateException(
                    "Keys are used on the authorizable which require the AEM KeyStore Service which is missing.");
        }
        if (!keyStoreService.keyStoreExists(resourceResolver, userId)) {
            boolean isRandomPassword = false;
            char[] keyStorePasswordCharArray;
            if (keyStorePassword == null || keyStorePassword.isEmpty()) {
                keyStorePasswordCharArray = RandomPassword.generate(20);
                isRandomPassword = true;
            } else {
                keyStorePasswordCharArray = keyStorePassword.toCharArray();
            }
            keyStoreService.createKeyStore(resourceResolver, userId, keyStorePasswordCharArray);
            installLog.addMessage(LOG, "Created new key store with "+ (isRandomPassword ? "random" : "predefined") + " password for user  '"+ userId +"'");
        } else {
            if (keyStorePassword != null) {
                installLog.addWarning(LOG, "Key store for user " + userId + " does already exist, ignoring configured 'keystorePassword'");
            }
        }
        for (Entry<String, Key> entry : keys.entrySet()) {
            Certificate certificate = entry.getValue().getCertificate();
            if (certificate != null) {
                keyStoreService.addKeyStoreKeyEntry(resourceResolver, userId, entry.getKey(), entry.getValue().getPrivateKey(), new Certificate[] {certificate});
            } else {
                keyStoreService.addKeyStoreKeyPair(resourceResolver, userId, entry.getValue().getKeyPair(), entry.getKey());
            }
            installLog.addMessage(LOG, "Added key with alias '"+ entry.getKey() + "' to keystore of user '" + userId + "'");
        }
    }

    void setUserPassword(final AuthorizableConfigBean authorizableConfigBean,
            final User authorizableToInstall, InstallationLogger installLog) throws RepositoryException, AuthorizableCreatorException {

        String userId = authorizableToInstall.getID();
        String password = getPassword(authorizableConfigBean);
        Session sessionForUser = null;
        try {
            sessionForUser = repository.login(new SimpleCredentials(userId, password.toCharArray()));
            LOG.trace("Could obtain session {} for user {}, will not update password", sessionForUser, userId);
            installLog.addVerboseMessage(LOG, "Password of user " + userId + " has not changed");
        } catch (javax.jcr.LoginException e) {
            LOG.trace("User {} could not log in with existing password", userId, e);
            authorizableToInstall.changePassword(password);
            installLog.addMessage(LOG, "Changed password of user " + userId);
        } finally {
            if (sessionForUser != null) {
                sessionForUser.logout();
            }
        }
    }

    private String getPassword(final AuthorizableConfigBean authorizableConfigBean)
            throws AuthorizableCreatorException {
        try {
            String password = authorizableConfigBean.getPassword();
            return decryptionService.decrypt(password);
        } catch (UnsupportedOperationException e) {
            throw new AuthorizableCreatorException(
                    "Could not decrypt password for user " + authorizableConfigBean.getAuthorizableId() + ": " + e.getMessage(), e);
        }
    }

    /** This is only relevant for members that point to groups/users not contained in configuration.
     * {@link biz.netcentric.cq.tools.actool.configreader.YamlConfigurationMerger#ensureIsMemberOfIsUsedWherePossible()} ensures that
     * regular relationships between groups contained in config are kept in isMemberOf */
    @SuppressWarnings("unchecked")
    void applyGroupMembershipConfigMembers(AcConfiguration acConfiguration, AuthorizableConfigBean authorizableConfigBean, InstallationLogger installLog,
            String authorizableId, AuthInstallerUserManager userManager, Set<String> authorizablesFromConfigurations) throws RepositoryException {
        if (authorizableConfigBean.isGroup()) {

            String[] membersInConfigArr = authorizableConfigBean.getMembers();
            Set<String> membersInConfig = membersInConfigArr != null ? new HashSet<String>(Arrays.asList(membersInConfigArr)) : new HashSet<String>();

            // initial set without regular users (those are never removed because that relationship is typically managed in AEM UI or LDAP/SAML/SSO/etc. )
            Set<String> relevantMembersInRepo = userManager.getDeclaredMembersWithoutRegularUsers(authorizableId);


            // ensure authorizables from config itself that are added via isMemberOf are not deleted
            relevantMembersInRepo = new HashSet<String>(CollectionUtils.subtract(relevantMembersInRepo, authorizablesFromConfigurations));
            
            // take configuration 'defaultUnmanagedExternalMembersRegex' into account (and remove matching groups from further handling)
            relevantMembersInRepo = removeExternalMembersUnmanagedByConfiguration(acConfiguration, authorizableConfigBean, relevantMembersInRepo,
                    installLog);

            Set<String> membersToAdd = new HashSet<String>(CollectionUtils.subtract(membersInConfig, relevantMembersInRepo));
            Set<String> membersToRemove = new HashSet<String>(CollectionUtils.subtract(relevantMembersInRepo, membersInConfig));

            Group installedGroup = (Group) userManager.getAuthorizable(authorizableId);
            if (!membersToAdd.isEmpty()) {
                installLog.addVerboseMessage(LOG,
                        "Adding " + membersToAdd.size() + " external members to group " + authorizableConfigBean.getAuthorizableId());
                for (String member : membersToAdd) {
                    Authorizable memberGroup = userManager.getAuthorizable(member);
                    if (memberGroup == null) {
                        throw new IllegalStateException(
                                "Member " + member + " does not exist and cannot be added as external member to group "
                                        + authorizableConfigBean.getAuthorizableId());
                    }
                    installedGroup.addMember(memberGroup);
                    installLog.addVerboseMessage(LOG,
                            "Adding " + member + " as external member to group " + authorizableConfigBean.getAuthorizableId());
                }

            }

            if (!membersToRemove.isEmpty()) {
                installLog.addVerboseMessage(LOG,
                        "Removing " + membersToRemove.size() + " external members from group " + authorizableConfigBean.getAuthorizableId());
                for (String member : membersToRemove) {
                    Authorizable memberGroup = userManager.getAuthorizable(member);
                    installedGroup.removeMember(memberGroup);
                    installLog.addVerboseMessage(LOG,
                            "Removing " + member + " as external member from group " + authorizableConfigBean.getAuthorizableId());
                }
            }
        }
    }

    private Set<String> removeExternalMembersUnmanagedByConfiguration(AcConfiguration acConfiguration, AuthorizableConfigBean authorizableConfigBean,
            Set<String> relevantMembersInRepo, InstallationLogger installLog) {
        Set<String> relevantMembers = new HashSet<String>(relevantMembersInRepo);

        Pattern unmanagedExternalMembersRegex = authorizableConfigBean.getUnmanagedExternalMembersRegex();
        if (unmanagedExternalMembersRegex == null) {
            unmanagedExternalMembersRegex = acConfiguration.getGlobalConfiguration().getDefaultUnmanagedExternalMembersRegex();
        }

        Set<String> unmanagedMembers = new HashSet<String>();
        if (unmanagedExternalMembersRegex != null) {
            Iterator<String> relevantMembersIt = relevantMembers.iterator();
            while (relevantMembersIt.hasNext()) {
                String member = relevantMembersIt.next();
                if (unmanagedExternalMembersRegex.matcher(member).matches()) {
                    unmanagedMembers.add(member);
                    relevantMembersIt.remove();
                }
            }
        }

        if (!unmanagedMembers.isEmpty()) {
            installLog.addVerboseMessage(LOG,
                    "Not removing members " + unmanagedMembers + " from " + authorizableConfigBean.getAuthorizableId()
                            + " because of unmanagedExternalMembersRegex=" + unmanagedExternalMembersRegex);
        }

        return relevantMembers;

    }

    
    private void migrateFromOldGroup(AuthorizableConfigBean authorizableConfigBean, AuthInstallerUserManager userManager,
            InstallationLogger installLog) throws RepositoryException {
        Authorizable groupForMigration = userManager.getAuthorizable(authorizableConfigBean.getMigrateFrom());

        String authorizableId = authorizableConfigBean.getAuthorizableId();

        if (groupForMigration == null) {
            installLog.addMessage(LOG, "Group " + authorizableConfigBean.getMigrateFrom()
                    + " does not exist (specified as migrateFrom in group "
                    + authorizableId + ") - no action taken");
            return;
        }
        if (!groupForMigration.isGroup()) {
            installLog.addWarning(LOG, "Specifying a user in 'migrateFrom' does not make sense (migrateFrom="
                    + authorizableConfigBean.getMigrateFrom() + " in " + authorizableId + ")");
            return;
        }

        installLog.addMessage(LOG, "Migrating from group " + authorizableConfigBean.getMigrateFrom()
                + "  to " + authorizableId);

        Set<Authorizable> usersFromGroupToTakeOver = new HashSet<Authorizable>();
        Iterator<Authorizable> membersIt = ((Group) groupForMigration).getMembers();
        while (membersIt.hasNext()) {
            Authorizable member = membersIt.next();
            if (!member.isGroup()) {
                usersFromGroupToTakeOver.add(member);
            }
        }

        if (!usersFromGroupToTakeOver.isEmpty()) {
            installLog.addMessage(LOG, "- Taking over " + usersFromGroupToTakeOver.size() + " member users from group "
                    + authorizableConfigBean.getMigrateFrom() + " to group " + authorizableId);
            Group currentGroup = (Group) userManager.getAuthorizable(authorizableId);
            for (Authorizable user : usersFromGroupToTakeOver) {
                currentGroup.addMember(user);
            }
        }

        userManager.removeAuthorizable(groupForMigration);
        installLog.addMessage(LOG, "- Deleted group " + authorizableConfigBean.getMigrateFrom());

    }

    private void handleRecreationOfAuthorizableIfNecessary(final Session session,
            AcConfiguration acConfiguration, 
            AuthorizableConfigBean principalConfigBean,
            InstallationLogger installLog,
            AuthInstallerUserManager userManager) throws RepositoryException, AuthorizableCreatorException {

        String authorizableId = principalConfigBean.getAuthorizableId();

        Authorizable existingAuthorizable = userManager.getAuthorizable(authorizableId);
        
        // compare intermediate paths
        String intermediatedPathOfExistingAuthorizable = existingAuthorizable.getPath()
                .substring(0, existingAuthorizable.getPath().lastIndexOf("/"));

        // Relative paths need to be prefixed with /home/groups (issue #10)
        String authorizablePathFromBean = principalConfigBean.getPath();
        if (StringUtils.isNotEmpty(authorizablePathFromBean) && (authorizablePathFromBean.charAt(0) != '/')) {
            authorizablePathFromBean = (principalConfigBean.isGroup() ? Constants.GROUPS_ROOT : Constants.USERS_ROOT)
                    + (principalConfigBean.isSystemUser() && !authorizablePathFromBean.startsWith(PATH_SEGMENT_SYSTEMUSERS)
                            ? "/" + PATH_SEGMENT_SYSTEMUSERS : "")
                    + "/" + authorizablePathFromBean;
        }

        boolean pathHasChanged = !StringUtils.equals(intermediatedPathOfExistingAuthorizable, authorizablePathFromBean)
                && StringUtils.isNotBlank(principalConfigBean.getPath());

        if (pathHasChanged) {
            installLog.addMessage(LOG, "Found change of intermediate path for " + existingAuthorizable.getID() + ": "
                    + intermediatedPathOfExistingAuthorizable + " -> " + authorizablePathFromBean);
        }

        // using "" to compare non-external (both sides) to true
        String externalIdExistingAuthorizable = StringUtils
                .defaultIfEmpty(AcHelper.valuesToString(existingAuthorizable.getProperty(REP_EXTERNAL_ID)), "");
        String externalIdConfig = StringUtils.defaultIfEmpty(principalConfigBean.getExternalId(), "");

        boolean externalIdHasChanged = !StringUtils.equals(externalIdExistingAuthorizable, externalIdConfig);

        if (externalIdHasChanged) {
            installLog.addMessage(LOG, "Found change of external id of " + existingAuthorizable.getID() + ": '"
                    + externalIdExistingAuthorizable + "' (current) is not '" + externalIdConfig + "' (in config)");
        }

        if (pathHasChanged || externalIdHasChanged) {

            // save members of existing group before deletion
            Set<Authorizable> membersOfDeletedGroup = new HashSet<Authorizable>();
            if (existingAuthorizable.isGroup()) {
                Group existingGroup = (Group) existingAuthorizable;
                Iterator<Authorizable> memberIt = existingGroup.getDeclaredMembers();
                while (memberIt.hasNext()) {
                    membersOfDeletedGroup.add(memberIt.next());
                }
            }

            // delete existingAuthorizable;
            userManager.removeAuthorizable(existingAuthorizable);

            // create group again using values form config
            Authorizable newAuthorizable = createNewAuthorizable(acConfiguration, principalConfigBean, installLog, userManager, session);

            int countMovedMembersOfGroup = 0;
            if (newAuthorizable.isGroup()) {
                Group newGroup = (Group) newAuthorizable;
                // add members of deleted group
                for (Authorizable authorizable : membersOfDeletedGroup) {
                    newGroup.addMember(authorizable);
                    countMovedMembersOfGroup++;
                }
            }

            deleteOldIntermediatePath(session, session.getNode(intermediatedPathOfExistingAuthorizable));

            installLog.addMessage(LOG, "Recreated authorizable " + newAuthorizable + " at path " + newAuthorizable.getPath()
            + (newAuthorizable.isGroup() ? "(retained " + countMovedMembersOfGroup + " members of group)" : ""));

            installLog.incCountAuthorizablesMoved();
        }

    }

    /** Deletes old intermediatePath parent node and all empty parent nodes up to /home/groups or /home/user.
     *
     * @param session
     * @param oldIntermediateNode
     * @throws RepositoryException */
    private void deleteOldIntermediatePath(final Session session,
            Node oldIntermediateNode) throws RepositoryException {

        // if '/home/groups' or '/home/users' was intermediatedNode, these must
        // not get deleted!
        // also node to be deleted has to be empty, so no other authorizables
        // stored under this path get deleted
        while (!StringUtils.equals(Constants.GROUPS_ROOT,
                oldIntermediateNode.getPath())
                && !StringUtils.equals(Constants.USERS_ROOT,
                        oldIntermediateNode.getPath())
                && !oldIntermediateNode.hasNodes()) {
            // delete old intermediatedPath
            Node parent = oldIntermediateNode.getParent();
            session.removeItem(oldIntermediateNode.getPath());

            // go one path level back for next iteration
            oldIntermediateNode = parent;
        }
    }

    private void applyGroupMembershipConfigIsMemberOf(InstallationLogger installLog,
            AcConfiguration acConfiguration,
            AuthorizableConfigBean authorizableConfigBean, AuthInstallerUserManager userManager, Session session,
            Set<String> authorizablesFromConfigurations) throws RepositoryException, AuthorizableCreatorException {

        String authId = authorizableConfigBean.getAuthorizableId();
        Set<String> membershipGroupsFromConfig = getMembershipGroupsFromConfig(authorizableConfigBean.getIsMemberOf());
        Set<String> membershipGroupsFromRepository = userManager.getDeclaredIsMemberOf(authId);

        applyGroupMembershipConfigIsMemberOf(authorizableConfigBean, acConfiguration, installLog, userManager, session,
                membershipGroupsFromConfig,
                membershipGroupsFromRepository, authorizablesFromConfigurations);
    }

    private Authorizable createNewAuthorizable(
            AcConfiguration acConfiguration, 
            AuthorizableConfigBean principalConfigBean,
            InstallationLogger installLog,
            AuthInstallerUserManager userManager, Session session)
            throws AuthorizableExistsException, RepositoryException,
            AuthorizableCreatorException {

        boolean isGroup = principalConfigBean.isGroup();
        String authorizableId = principalConfigBean.getAuthorizableId();

        Authorizable newAuthorizable = null;

        if (isGroup) {
            newAuthorizable = createNewGroup(userManager, acConfiguration.getAuthorizablesConfig(), principalConfigBean, installLog, session);
            LOG.info("Successfully created new group: {}", authorizableId);
        } else {
            if (StringUtils.isNotEmpty(principalConfigBean.getExternalId())) {
                throw new IllegalStateException("External IDs are not supported for users (" + principalConfigBean.getAuthorizableId()
                        + " is using '" + principalConfigBean.getExternalId()
                        + "') - use a ootb sync handler to have users automatically created.");
            }

            newAuthorizable = createNewUser(userManager, acConfiguration.getAuthorizablesConfig(), principalConfigBean, installLog, session);
            LOG.info("Successfully created new user: {}", authorizableId);
        }

        return newAuthorizable;
    }

    private Set<String> getMembershipGroupsFromConfig(String[] memberOf) {
        // Set which holds all other groups which the current Group from config
        // is a member of
        Set<String> membershipGroupsFromConfig = new HashSet<String>();
        if (memberOf != null) { // member of at least one other groups
            for (String s : memberOf) {
                membershipGroupsFromConfig.add(s);
            }
        }
        return membershipGroupsFromConfig;
    }

    @SuppressWarnings("unchecked")
    void applyGroupMembershipConfigIsMemberOf(AuthorizableConfigBean authorizableConfigBean,
            AcConfiguration acConfiguration,
            InstallationLogger installLog, AuthInstallerUserManager userManager, Session session,
            Set<String> membershipGroupsFromConfig,
            Set<String> membershipGroupsFromRepository, Set<String> authorizablesFromConfigurations)
            throws RepositoryException, AuthorizableExistsException,
            AuthorizableCreatorException {

        // membership to everyone cannot be removed or added => take it out from both lists
        membershipGroupsFromConfig.remove(PRINCIPAL_EVERYONE);
        membershipGroupsFromRepository.remove(PRINCIPAL_EVERYONE);

        String authorizableId = authorizableConfigBean.getAuthorizableId();
        installLog.addVerboseMessage(LOG, "Authorizable " + authorizableId + " isMemberOf(repo)=" + membershipGroupsFromRepository);
        installLog.addVerboseMessage(LOG, "Authorizable " + authorizableId + " isMemberOf(config)=" + membershipGroupsFromConfig);

        Set<String> validatedMembershipGroupsFromConfig = validateAssignedGroups(userManager, acConfiguration.getAuthorizablesConfig(), session, authorizableId,
                membershipGroupsFromConfig, installLog);

        Collection<String> unChangedMembers = CollectionUtils.intersection(membershipGroupsFromRepository,
                validatedMembershipGroupsFromConfig);
        installLog.addVerboseMessage(LOG, "Authorizable " + authorizableId + " remains member of groups " + unChangedMembers);

        Collection<String> toBeAddedMembers = CollectionUtils.subtract(validatedMembershipGroupsFromConfig, membershipGroupsFromRepository);
        installLog.addVerboseMessage(LOG, "Authorizable " + authorizableId + " will be added as member of " + toBeAddedMembers);

        Collection<String> toBeRemovedMembers = CollectionUtils.subtract(membershipGroupsFromRepository,
                validatedMembershipGroupsFromConfig);
        Set<String> unmanagedMembers = new HashSet<String>();

        Pattern unmanagedExternalIsMemberOfRegex = authorizableConfigBean.getUnmanagedExternalIsMemberOfRegex();
        if (unmanagedExternalIsMemberOfRegex == null) {
            unmanagedExternalIsMemberOfRegex = acConfiguration.getGlobalConfiguration().getDefaultUnmanagedExternalIsMemberOfRegex();
        }

        Iterator<String> toBeRemovedMembersIt = toBeRemovedMembers.iterator();
        while (toBeRemovedMembersIt.hasNext()) {
            String groupId = toBeRemovedMembersIt.next();
            if (!authorizablesFromConfigurations.contains(groupId) /* generally only consider groups that are not in config as unmanaged */
                    && (unmanagedExternalIsMemberOfRegex != null) && unmanagedExternalIsMemberOfRegex.matcher(groupId).matches()) {
                unmanagedMembers.add(groupId);
                toBeRemovedMembersIt.remove();
            }
        }
        installLog.addVerboseMessage(LOG, "Authorizable " + authorizableId + " will be removed from members of " + toBeRemovedMembers);

        if (!unmanagedMembers.isEmpty()) {
            installLog.addVerboseMessage(LOG, "Authorizable " + authorizableId + " remains member of groups "
                    + unmanagedMembers + " (due to configured unmanagedExternalIsMemberOfRegex="
                    + unmanagedExternalIsMemberOfRegex + ")");

        }

        // perform changes

        Authorizable currentAuthorizable = userManager.getAuthorizable(authorizableId);

        for (String groupId : toBeAddedMembers) {
            LOG.debug("Membership Change: Adding {} to members of group {} in repository", authorizableId, groupId);
            Authorizable targetAuthorizable = userManager.getAuthorizable(groupId);
            ((Group) targetAuthorizable).addMember(currentAuthorizable);
        }
        for (String groupId : toBeRemovedMembers) {
            LOG.debug("Membership Change: Removing {} from members of group {} in repository", authorizableId, groupId);
            Authorizable targetAuthorizable = userManager.getAuthorizable(groupId);
            ((Group) targetAuthorizable).removeMember(currentAuthorizable);
        }

        if (!toBeAddedMembers.isEmpty() && !toBeRemovedMembers.isEmpty()) {
            installLog.addVerboseMessage(LOG,
                    "Membership Change: Authorizable " + authorizableId + " was added to " + toBeAddedMembers.size()
                            + " and removed from " + toBeRemovedMembers.size() + " groups");
        }

    }

    private Authorizable createNewGroup(
            final AuthInstallerUserManager userManager,
            AuthorizablesConfig authorizablesConfig, 
            AuthorizableConfigBean principalConfigBean,
            InstallationLogger installLog, Session session)
            throws AuthorizableExistsException, RepositoryException,
            AuthorizableCreatorException {

        String groupID = principalConfigBean.getAuthorizableId();
        String intermediatePath = principalConfigBean.getPath();

        // create new Group
        Group newGroup = null;
        try {

            if (StringUtils.isNotEmpty(principalConfigBean.getExternalId())) {

                if (externalGroupCreatorService == null) {
                    throw new IllegalStateException("External IDs are not available for your AEM version ("
                            + principalConfigBean.getAuthorizableId() + " is using '" + principalConfigBean.getExternalId() + "')");
                }
                newGroup = (Group) externalGroupCreatorService.createGroupWithExternalId(userManager.getOakUserManager(), principalConfigBean, installLog, session);
                LOG.info("Successfully created new external group: {}", groupID);
            } else {

                PrincipalImpl principalForNewGroup = new PrincipalImpl(groupID);
                if (StringUtils.isNotBlank(intermediatePath)) {
                    newGroup = userManager.createGroup(principalForNewGroup, intermediatePath);
                } else {
                    newGroup = userManager.createGroup(principalForNewGroup);
                }
            }

        } catch (AuthorizableExistsException e) {
            LOG.warn("Group {} already exists in system!", groupID);
            newGroup = (Group) userManager.getAuthorizable(groupID);
        }

        addMembersToReferencingAuthorizables(newGroup, authorizablesConfig, principalConfigBean, userManager, session, installLog);

        setAuthorizableProperties(newGroup, principalConfigBean, authorizablesConfig, session, installLog);
        return newGroup;
    }

    void setAuthorizableProperties(Authorizable authorizable, AuthorizableConfigBean principalConfigBean, AuthorizablesConfig authorizablesConfig,
            Session session, InstallationLogger installationLog)
            throws RepositoryException {

        String profileContent = principalConfigBean.getProfileContent();
        if (StringUtils.isNotBlank(profileContent)) {
            ContentHelper.importContent(session, authorizable.getPath() + "/profile", profileContent);
        }

        String preferencesContent = principalConfigBean.getPreferencesContent();
        if (StringUtils.isNotBlank(preferencesContent)) {
            ContentHelper.importContent(session, authorizable.getPath() + "/preferences", preferencesContent);
        }

        String socialContent = principalConfigBean.getSocialContent();
        if (StringUtils.isNotBlank(socialContent)) {
            ContentHelper.importContent(session, authorizable.getPath() + "/social", socialContent);
        }

        ValueFactory vf = session.getValueFactory();

        String name = principalConfigBean.getName();
        if (StringUtils.isNotBlank(name)) {
            if (authorizable.isGroup()) {
                authorizable.setProperty("profile/givenName", vf.createValue(name));
            } else {
                String givenName;
                String familyName;
                if(name.contains(",")) {
                    String[] nameParts = name.split("\\s*,\\s*", 2);
                    familyName = nameParts[0];
                    givenName = nameParts[1];
                } else {
                    givenName = StringUtils.substringBeforeLast(name, " ");
                    familyName = StringUtils.substringAfterLast(name, " ");
                }
                authorizable.setProperty("profile/givenName", vf.createValue(givenName));
                authorizable.setProperty("profile/familyName", vf.createValue(familyName));
            }
        } else {
            if (StringUtils.isBlank(profileContent)) {
                authorizable.removeProperty("profile/givenName");
                authorizable.removeProperty("profile/familyName");
            }
        }

        String email = principalConfigBean.getEmail();
        if (StringUtils.isNotBlank(email)) {
            authorizable.setProperty("profile/email", vf.createValue(email));
        } else {
            if (StringUtils.isBlank(profileContent)) {
                authorizable.removeProperty("profile/email");
            }
        }
        String description = principalConfigBean.getDescription();
        if (StringUtils.isNotBlank(description)) {
            authorizable.setProperty("profile/aboutMe", vf.createValue(description));
        } else {
            if (StringUtils.isBlank(profileContent)) {
                authorizable.removeProperty("profile/aboutMe");
            }
        }

        String disabled = principalConfigBean.getDisabled();
        if (StringUtils.isNotBlank(disabled)) {
            if (authorizable.isGroup()) {
                throw new IllegalStateException("Property 'disabled' cannot be set on groups");
            }
            // if disabled is set to false, use "reason null" as this will enable the user when calling User.disable()
            String disabledReason = StringUtils.equalsIgnoreCase(disabled, "false") ? null
                    : (StringUtils.equalsIgnoreCase(disabled, "true") ? "User disabled by AC Tool" : /* use text directly */ disabled);
            User user = (User) authorizable;
            boolean currentlyDisabled = user.isDisabled();
            boolean toBeDisabled = disabledReason != null;
            if (currentlyDisabled && !toBeDisabled) {
                installationLog.addMessage(LOG, "Enabling user " + user.getID());
            } else if (!currentlyDisabled && toBeDisabled) {
                installationLog.addMessage(LOG, "Disabling user " + user.getID() + " with reason: " + disabledReason);
            }
            if(currentlyDisabled || toBeDisabled) {
                user.disable(disabledReason);
            }
            
        }
        
        List<String> impersonationAllowedFor = principalConfigBean.getImpersonationAllowedFor();
        if (impersonationAllowedFor != null) {
            if (authorizable.isGroup()) {
                throw new IllegalStateException("Property 'impersonationAllowedFor' cannot be set on groups");
            }
            impersonationInstallerService.setupImpersonation((User) authorizable, impersonationAllowedFor, authorizablesConfig, installationLog);
        }

    }

    private Authorizable createNewUser(
            final AuthInstallerUserManager userManager,
            AuthorizablesConfig authorizablesConfig, 
            AuthorizableConfigBean principalConfigBean,
            InstallationLogger installLog,
            Session session)
            throws AuthorizableExistsException, RepositoryException,
            AuthorizableCreatorException {
        String authorizableId = principalConfigBean.getAuthorizableId();
        String password = getPassword(principalConfigBean);
        boolean isSystemUser = principalConfigBean.isSystemUser();
        String intermediatePath = principalConfigBean.getPath();

        User newUser = null;
        if (isSystemUser) {
            // make sure all relative intermediate paths get the prefix suffix (but don't touch absolute paths)
            String systemPrefix = "system/";
            if ((intermediatePath != null) && !intermediatePath.startsWith(systemPrefix) && !intermediatePath.startsWith("/")) {
                intermediatePath = systemPrefix + intermediatePath;
            }
            newUser = userManager.createSystemUser(authorizableId, intermediatePath);
        } else {
            newUser = userManager.createUser(authorizableId, password, new PrincipalImpl(authorizableId), intermediatePath);
        }
        setAuthorizableProperties(newUser, principalConfigBean, authorizablesConfig, session, installLog);

        addMembersToReferencingAuthorizables(newUser, authorizablesConfig, principalConfigBean, userManager, session, installLog);

        return newUser;
    }

    private void addMembersToReferencingAuthorizables(Authorizable authorizable, AuthorizablesConfig authorizablesConfig, AuthorizableConfigBean principalConfigBean,
            final AuthInstallerUserManager userManager, Session session, InstallationLogger installLog)
            throws RepositoryException, AuthorizableCreatorException {
        String authorizableId = principalConfigBean.getAuthorizableId();
        String[] memberOf = principalConfigBean.getIsMemberOf();
        if ((authorizable != null) && (memberOf != null) && (memberOf.length > 0)) {
            // add group to groups according to configuration
            Set<String> referencingAuthorizablesToBeChanged = validateAssignedGroups(userManager, authorizablesConfig, session, authorizableId,
                    new HashSet<String>(Arrays.asList(memberOf)), installLog);
            if (!referencingAuthorizablesToBeChanged.isEmpty()) {
                LOG.debug("start adding {} to assignedGroups", authorizableId);
                for (String referencingAuthorizableToBeChangedId : referencingAuthorizablesToBeChanged) {
                    Group referencingAuthorizableToBeChanged = (Group) userManager.getAuthorizable(referencingAuthorizableToBeChangedId);
                    referencingAuthorizableToBeChanged.addMember(authorizable);
                    LOG.debug("added to {} ", referencingAuthorizableToBeChanged);
                }
            }
        }
    }

    /** Validates the authorizables in 'membersOf' array of a given authorizable. Validation fails if an authorizable is a user.
     *
     * If an authorizable contained in membersOf array doesn't exist it gets created and the current authorizable gets added as a member.
     *
     * @param userManager
     * @param session
     * @param authorizablelId the ID of authorizable to validate
     * @param isMemberOf String array that contains the groups which the authorizable should be a member of
     * @param installLog
     * @return Set of authorizables which the current authorizable is a member of
     * @throws RepositoryException
     * @throws AuthorizableCreatorException if one of the authorizables contained in membersOf array is a user */
    Set<String> validateAssignedGroups(
            final AuthInstallerUserManager userManager, AuthorizablesConfig authorizablesConfig, Session session, final String authorizablelId,
            final Set<String> isMemberOf, InstallationLogger installLog) throws RepositoryException,
            AuthorizableCreatorException {

        Set<String> authorizableSet = new HashSet<String>();
        for (String memberOfAuthorizable : isMemberOf) {

            if (StringUtils.equals(authorizablelId, memberOfAuthorizable)) {
                throw new AuthorizableCreatorException("Cannot add authorizable " + authorizablelId + " as member of itself.");
            }

            Authorizable authorizable = userManager.getAuthorizable(memberOfAuthorizable);

            // if authorizable is existing in system
            if (authorizable != null) {

                // check if authorizable is a group
                if (authorizable.isGroup()) {
                    authorizableSet.add(authorizable.getID());
                } else {
                    throw new AuthorizableCreatorException("Invalid isMemberOf in in config of '"+authorizablelId+"': "
                            + "Cannot add '"+memberOfAuthorizable+"' because it is a user and not a group!");
                }
                // if authorizable doesn't exist yet, it gets created and the
                // current authorizable gets added as a member
            } else {
                // check if authorizable is contained in any of the
                // configurations

                AuthorizableConfigBean configBeanForIsMemberOf = authorizablesConfig.getAuthorizableConfig(memberOfAuthorizable);

                if (configBeanForIsMemberOf != null) {

                    Group newGroup = (Group) createNewGroup(userManager, authorizablesConfig, configBeanForIsMemberOf, installLog, session);

                    authorizableSet.add(newGroup.getID());
                    LOG.info("Created group to be able to add {} to group {} ", authorizablelId, memberOfAuthorizable);
                } else {
                    throw new AuthorizableCreatorException("Invalid isMemberOf group '"+memberOfAuthorizable+"' in config of '"+authorizablelId+"': "
                            + "Neither found '"+memberOfAuthorizable+"' as already existing group in repository nor in AC Tool config itself!");
                }
            }
        }
        return authorizableSet;
    }

}
