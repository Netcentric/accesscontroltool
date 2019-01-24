/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.authorizableinstaller.impl;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;

import javax.jcr.AccessDeniedException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.ValueFactory;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.AuthorizableExistsException;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.oak.spi.security.principal.PrincipalImpl;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.aem.AemCryptoSupport;
import biz.netcentric.cq.tools.actool.authorizableinstaller.AuthorizableCreatorException;
import biz.netcentric.cq.tools.actool.authorizableinstaller.AuthorizableInstallerService;
import biz.netcentric.cq.tools.actool.configmodel.AcConfiguration;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizablesConfig;
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

    private static final String PRINCIPAL_EVERYONE = "everyone";

    // not using org.apache.jackrabbit.oak.spi.security.authentication.external.basic.DefaultSyncContext.REP_EXTERNAL_ID since it is an
    // optional dependency and not available in AEM 6.1
    public static final String REP_EXTERNAL_ID = "rep:externalId";

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policyOption=ReferencePolicyOption.GREEDY)
    ExternalGroupInstallerServiceImpl externalGroupCreatorService;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy=ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY)
    volatile AemCryptoSupport cryptoSupport;

    @Override
    public void installAuthorizables(
            AcConfiguration acConfiguration,
            AuthorizablesConfig authorizablesConfigBeans,
            final Session session, InstallationLogger installLog)
            throws RepositoryException, AuthorizableCreatorException {

        Set<String> authorizablesFromConfigurations = authorizablesConfigBeans.getAuthorizableIds();
        for (AuthorizableConfigBean authorizableConfigBean : authorizablesConfigBeans) {

            installAuthorizableConfigurationBean(session, acConfiguration,
                    authorizableConfigBean, installLog, authorizablesFromConfigurations);
        }


    }

    private void installAuthorizableConfigurationBean(final Session session,
            AcConfiguration acConfiguration,
            AuthorizableConfigBean authorizableConfigBean,
            InstallationLogger installLog, Set<String> authorizablesFromConfigurations)
            throws AccessDeniedException,
            UnsupportedRepositoryOperationException, RepositoryException,
            AuthorizableExistsException, AuthorizableCreatorException {

        String authorizableId = authorizableConfigBean.getAuthorizableId();
        LOG.debug("- start installation of authorizable: {}", authorizableId);

        UserManager userManager = AccessControlUtils.getUserManagerAutoSaveDisabled(session);

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
            setAuthorizableProperties(authorizableToInstall, authorizableConfigBean, session, installLog);
            return;
        }

        if (authorizableToInstall == null) {
            authorizableToInstall = createNewAuthorizable(acConfiguration, authorizableConfigBean, installLog, userManager, session);
        }
        // if current authorizable from config already exists in repository
        else {

            // update name for both groups and users
            setAuthorizableProperties(authorizableToInstall, authorizableConfigBean, session, installLog);
            // update password for users
            if (!authorizableToInstall.isGroup() && !authorizableConfigBean.isSystemUser()
                    && StringUtils.isNotBlank(authorizableConfigBean.getPassword())) {
                setUserPassword(authorizableConfigBean, (User) authorizableToInstall);
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

    }

    void setUserPassword(final AuthorizableConfigBean authorizableConfigBean,
                             final User authorizableToInstall) throws RepositoryException, AuthorizableCreatorException {
        String password = getPassword(authorizableConfigBean);
        authorizableToInstall.changePassword(password);
    }


	private String getPassword(final AuthorizableConfigBean authorizableConfigBean)
			throws AuthorizableCreatorException {
		try {
			String password = authorizableConfigBean.getPassword();
			if (StringUtils.isNotBlank(password) && password.matches("\\{.+}")) {
				if (cryptoSupport == null) {
					throw new IllegalArgumentException("Password with {...} syntax is used but AEM CryptoSupport is missing to unprotect password.");
				}
				password = cryptoSupport.unprotect(password);
			}
			return password;
		} catch (IllegalArgumentException e) {
			throw new AuthorizableCreatorException("Could not decrypt password for user " + authorizableConfigBean.getAuthorizableId() + ": " + e);
		}
	}


    /** This is only relevant for members that point to groups/users not contained in configuration.
     * {@link biz.netcentric.cq.tools.actool.configreader.YamlConfigurationMerger#ensureIsMemberOfIsUsedWherePossible()} ensures that
     * regular relationships between groups contained in config are kept in isMemberOf */
    @SuppressWarnings("unchecked")
    void applyGroupMembershipConfigMembers(AcConfiguration acConfiguration, AuthorizableConfigBean authorizableConfigBean, InstallationLogger installLog,
            String principalId, UserManager userManager, Set<String> authorizablesFromConfigurations) throws RepositoryException {
        if (authorizableConfigBean.isGroup()) {
            String[] membersInConfigArr = authorizableConfigBean.getMembers();

            Group installedGroup = (Group) userManager.getAuthorizable(principalId);

            Set<String> membersInConfig = membersInConfigArr != null ? new HashSet<String>(Arrays.asList(membersInConfigArr))
                    : new HashSet<String>();
            Set<String> relevantMembersInRepo = getDeclaredMembers(installedGroup);

            // ensure authorizables from config itself that are added via isMemberOf are not deleted
            relevantMembersInRepo = new HashSet<String>(CollectionUtils.subtract(relevantMembersInRepo, authorizablesFromConfigurations));
            // ensure regular users are never removed
            relevantMembersInRepo = removeRegularUsers(relevantMembersInRepo, userManager);
            // take configuration 'defaultUnmanagedExternalMembersRegex' into account (and remove matching groups from further handling)
            relevantMembersInRepo = removeExternalMembersUnmanagedByConfiguration(acConfiguration, authorizableConfigBean, relevantMembersInRepo,
                    installLog);

            Set<String> membersToAdd = new HashSet<String>(CollectionUtils.subtract(membersInConfig, relevantMembersInRepo));
            Set<String> membersToRemove = new HashSet<String>(CollectionUtils.subtract(relevantMembersInRepo, membersInConfig));

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
                        "Removing " + membersToRemove.size() + " external members to group " + authorizableConfigBean.getAuthorizableId());
                for (String member : membersToRemove) {
                    Authorizable memberGroup = userManager.getAuthorizable(member);
                    installedGroup.removeMember(memberGroup);
                    installLog.addVerboseMessage(LOG,
                            "Removing " + member + " as external member to group " + authorizableConfigBean.getAuthorizableId());
                }
            }
        }
    }

    private Set<String> removeRegularUsers(Set<String> allMembersFromRepo, UserManager userManager) throws RepositoryException {

        Set<String> relevantMembers = new HashSet<String>(allMembersFromRepo);
        Iterator<String> relevantMembersIt = relevantMembers.iterator();
        while (relevantMembersIt.hasNext()) {
            String memberId = relevantMembersIt.next();
            Authorizable member = userManager.getAuthorizable(memberId);

            if (isRegularUser(member)) {
                // not relevant for further handling
                relevantMembersIt.remove();
            }
        }

        return relevantMembers;
    }

    private boolean isRegularUser(Authorizable member) throws RepositoryException {
      return member != null && !member.isGroup() // if user
          && !member.getPath().startsWith(Constants.USERS_ROOT + "/system/") // but not system user
          && !member.getID().equals(Constants.USER_ANONYMOUS);  // and not anonymous
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

    private Set<String> getDeclaredMembers(Group installedGroup) throws RepositoryException {
        Set<String> membersInRepo = new HashSet<String>();
        Iterator<Authorizable> currentMemberInRepo = installedGroup.getDeclaredMembers();
        while (currentMemberInRepo.hasNext()) {
            Authorizable member = currentMemberInRepo.next();
            if (!isRegularUser(member)) {
                membersInRepo.add(member.getID());
            }
        }
        return membersInRepo;
    }

    private void migrateFromOldGroup(AuthorizableConfigBean authorizableConfigBean, UserManager userManager,
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

        groupForMigration.remove();
        installLog.addMessage(LOG, "- Deleted group " + authorizableConfigBean.getMigrateFrom());

    }

    private void handleRecreationOfAuthorizableIfNecessary(final Session session,
            AcConfiguration acConfiguration, 
            AuthorizableConfigBean principalConfigBean,
            InstallationLogger installLog,
            UserManager userManager) throws RepositoryException, AuthorizableCreatorException {

        String authorizableId = principalConfigBean.getAuthorizableId();

        // compare intermediate paths
        Authorizable existingAuthorizable = userManager.getAuthorizable(authorizableId);

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
            existingAuthorizable.remove();

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
            AuthorizableConfigBean authorizableConfigBean, UserManager userManager, Session session,
            Set<String> authorizablesFromConfigurations) throws RepositoryException, AuthorizableCreatorException {
        String[] memberOf = authorizableConfigBean.getIsMemberOf();

        Authorizable currentGroupFromRepository = userManager.getAuthorizable(authorizableConfigBean.getAuthorizableId());
        Set<String> membershipGroupsFromConfig = getMembershipGroupsFromConfig(memberOf);
        Set<String> membershipGroupsFromRepository = getMembershipGroupsFromRepository(currentGroupFromRepository);

        applyGroupMembershipConfigIsMemberOf(authorizableConfigBean, acConfiguration, installLog, userManager, session,
                membershipGroupsFromConfig,
                membershipGroupsFromRepository, authorizablesFromConfigurations);
    }

    private Authorizable createNewAuthorizable(
            AcConfiguration acConfiguration, 
            AuthorizableConfigBean principalConfigBean,
            InstallationLogger installLog,
            UserManager userManager, Session session)
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

    private Set<String> getMembershipGroupsFromRepository(Authorizable currentGroupFromRepository) throws RepositoryException {
        Set<String> membershipGroupsFromRepository = new HashSet<String>();
        Iterator<Group> memberOfGroupsIterator = currentGroupFromRepository.declaredMemberOf();

        // build Set which contains the all Groups of which the existingGroup is
        // a member of
        while (memberOfGroupsIterator.hasNext()) {
            Authorizable memberOfGroup = memberOfGroupsIterator.next();
            membershipGroupsFromRepository.add(memberOfGroup.getID());
        }
        return membershipGroupsFromRepository;
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
            InstallationLogger installLog, UserManager userManager, Session session,
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
            final UserManager userManager,
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
                newGroup = (Group) externalGroupCreatorService.createGroupWithExternalId(userManager, principalConfigBean, installLog, session);
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

        setAuthorizableProperties(newGroup, principalConfigBean, session, installLog);
        return newGroup;
    }

    void setAuthorizableProperties(Authorizable authorizable, AuthorizableConfigBean principalConfigBean,
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
                throw new IllegalStateException("Property disabled can only be set on users");
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
    }

    private Authorizable createNewUser(
            final UserManager userManager,
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
        setAuthorizableProperties(newUser, principalConfigBean, session, installLog);

        addMembersToReferencingAuthorizables(newUser, authorizablesConfig, principalConfigBean, userManager, session, installLog);

        return newUser;
    }

    private void addMembersToReferencingAuthorizables(Authorizable authorizable, AuthorizablesConfig authorizablesConfig, AuthorizableConfigBean principalConfigBean,
            final UserManager userManager, Session session, InstallationLogger installLog)
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
            final UserManager userManager, AuthorizablesConfig authorizablesConfig, Session session, final String authorizablelId,
            final Set<String> isMemberOf, InstallationLogger installLog) throws RepositoryException,
            AuthorizableCreatorException {

        Set<String> authorizableSet = new HashSet<String>();
        for (String memberOfAuthorizable : isMemberOf) {

            if (StringUtils.equals(authorizablelId, memberOfAuthorizable)) {
                throw new AuthorizableCreatorException("Cannot add authorizable " + authorizablelId + " as member of itself.");
            }

            Authorizable authorizable = userManager.getAuthorizable(memberOfAuthorizable);

            // validation

            // if authorizable is existing in system
            if (authorizable != null) {

                // check if authorizable is a group
                if (authorizable.isGroup()) {
                    authorizableSet.add(authorizable.getID());
                } else {
                    String message = "Failed to add authorizable "
                            + authorizablelId + " to autorizable " + memberOfAuthorizable
                            + "! Authorizable is not a group";
                    throw new AuthorizableCreatorException(message);
                }
                // if authorizable doesn't exist yet, it gets created and the
                // current authorizable gets added as a member
            } else {
                // check if authorizable is contained in any of the
                // configurations

                AuthorizableConfigBean configBeanForIsMemberOf = authorizablesConfig
                        .getAuthorizableConfig(memberOfAuthorizable);

                if (configBeanForIsMemberOf != null) {


                    Group newGroup = (Group) createNewGroup(userManager, authorizablesConfig, configBeanForIsMemberOf, installLog, session);

                    authorizableSet.add(newGroup.getID());
                    LOG.info("Created group to be able to add {} to group {} ", authorizablelId, memberOfAuthorizable);
                } else {
                    String message = "Failed to add group: "
                            + authorizablelId
                            + " as member to authorizable: "
                            + memberOfAuthorizable
                            + ". Neither found this authorizable ("
                            + memberOfAuthorizable
                            + ") in any of the configurations nor installed in the system!";
                    LOG.error(message);

                    throw new AuthorizableCreatorException(message);
                }
            }
        }
        return authorizableSet;
    }




}
