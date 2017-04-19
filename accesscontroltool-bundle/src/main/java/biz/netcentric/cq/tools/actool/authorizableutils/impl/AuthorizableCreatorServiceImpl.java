/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.authorizableutils.impl;

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
import javax.jcr.ValueFormatException;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.AuthorizableExistsException;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.oak.spi.security.principal.PrincipalImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableCreatorException;
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableCreatorService;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizablesConfig;
import biz.netcentric.cq.tools.actool.helper.AcHelper;
import biz.netcentric.cq.tools.actool.helper.AccessControlUtils;
import biz.netcentric.cq.tools.actool.helper.Constants;
import biz.netcentric.cq.tools.actool.helper.ContentHelper;
import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;

@Service
@Component(metatype = true, label = "AC AuthorizableCreatorService", description = "Service that installs groups according to textual configuration files")
public class AuthorizableCreatorServiceImpl implements
        AuthorizableCreatorService {

    private static final Logger LOG = LoggerFactory.getLogger(AuthorizableCreatorServiceImpl.class);

    private static final String PATH_SEGMENT_SYSTEMUSERS = "system";

    private static final String PRINCIPAL_EVERYONE = "everyone";

    // not using org.apache.jackrabbit.oak.spi.security.authentication.external.basic.DefaultSyncContext.REP_EXTERNAL_ID since it is an
    // optional dependency and not available in AEM 6.1
    public static final String REP_EXTERNAL_ID = "rep:externalId";

    @Reference(cardinality = ReferenceCardinality.OPTIONAL_UNARY)
    ExternalGroupCreatorServiceImpl externalGroupCreatorService;

    @Override
    public void createNewAuthorizables(
            AuthorizablesConfig authorizablesConfigBeans,
            final Session session, AcInstallationHistoryPojo status)
            throws RepositoryException, AuthorizableCreatorException {

        Set<String> authorizablesFromConfigurations = authorizablesConfigBeans.getAuthorizableIds();
        for (AuthorizableConfigBean authorizableConfigBean : authorizablesConfigBeans) {

            status.addVerboseMessage(LOG, "Starting installation of authorizable bean: " + authorizableConfigBean.toString());

            installAuthorizableConfigurationBean(session,
                    authorizableConfigBean, status, authorizablesFromConfigurations);
        }


    }


    private void installAuthorizableConfigurationBean(final Session session,
            AuthorizableConfigBean authorizableConfigBean,
            AcInstallationHistoryPojo history, Set<String> authorizablesFromConfigurations)
            throws AccessDeniedException,
            UnsupportedRepositoryOperationException, RepositoryException,
            AuthorizableExistsException, AuthorizableCreatorException {

        String authorizableId = authorizableConfigBean.getAuthorizableId();
        LOG.debug("- start installation of authorizable: {}", authorizableId);

        UserManager userManager = AccessControlUtils.getUserManagerAutoSaveDisabled(session);
        ValueFactory vf = session.getValueFactory();

        // if current authorizable from config doesn't exist yet
        Authorizable authorizableToInstall = userManager.getAuthorizable(authorizableId);
        if (authorizableToInstall == null) {
            authorizableToInstall = createNewAuthorizable(authorizableConfigBean, history, userManager, session);
        }
        // if current authorizable from config already exists in repository
        else {

            // update name for both groups and users
            setAuthorizableProperties(authorizableToInstall, authorizableConfigBean, session);
            // update password for users
            if (!authorizableToInstall.isGroup() && !authorizableConfigBean.isSystemUser()
                    && StringUtils.isNotBlank(authorizableConfigBean.getPassword())) {
                ((User) authorizableToInstall).changePassword(authorizableConfigBean.getPassword());
            }

            // move authorizable if path changed (retaining existing members)
            handleRecreationOfAuthorizableIfNecessary(session, authorizableConfigBean, history, userManager);

            applyGroupMembershipConfigIsMemberOf(history, authorizableConfigBean, userManager, session);

        }

        applyGroupMembershipConfigMembers(authorizableConfigBean, history, authorizableId, userManager, authorizablesFromConfigurations);

        if (StringUtils.isNotBlank(authorizableConfigBean.getMigrateFrom()) && authorizableConfigBean.isGroup()) {
            migrateFromOldGroup(authorizableConfigBean, userManager, history);
        }

    }

    /** This is only relevant for members that point to groups/users not contained in configuration.
     * {@link biz.netcentric.cq.tools.actool.configreader.YamlConfigurationMerger#ensureIsMemberOfIsUsedWherePossible()} ensures that
     * regular relationships between groups contained in config are kept in isMemberOf */
    @SuppressWarnings("unchecked")
    void applyGroupMembershipConfigMembers(AuthorizableConfigBean authorizableConfigBean, AcInstallationHistoryPojo history,
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
            // take configuration 'allowExternalGroupNamesRegEx' into account (and remove matching groups from further handling)
            relevantMembersInRepo = removeExternalGroupsThatAreUntouchedByConfiguration(relevantMembersInRepo, history);

            Set<String> membersToAdd = new HashSet<String>(CollectionUtils.subtract(membersInConfig, relevantMembersInRepo));
            Set<String> membersToRemove = new HashSet<String>(CollectionUtils.subtract(relevantMembersInRepo, membersInConfig));

            if (!membersToAdd.isEmpty()) {
                history.addVerboseMessage(LOG,
                        "Adding " + membersToAdd.size() + " external members to group " + authorizableConfigBean.getAuthorizableId());
                for (String member : membersToAdd) {
                    Authorizable memberGroup = userManager.getAuthorizable(member);
                    if (memberGroup == null) {
                        throw new IllegalStateException(
                                "Member " + member + " does not exist and cannot be added as external member to group "
                                        + authorizableConfigBean.getAuthorizableId());
                    }
                    installedGroup.addMember(memberGroup);
                    history.addVerboseMessage(LOG,
                            "Adding " + member + " as external member to group " + authorizableConfigBean.getAuthorizableId());
                }

            }

            if (!membersToRemove.isEmpty()) {
                history.addVerboseMessage(LOG,
                        "Removing " + membersToRemove.size() + " external members to group " + authorizableConfigBean.getAuthorizableId());
                for (String member : membersToRemove) {
                    Authorizable memberGroup = userManager.getAuthorizable(member);
                    installedGroup.removeMember(memberGroup);
                    history.addVerboseMessage(LOG,
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

            if (member != null && !member.isGroup() // if user
                    && !member.getPath().startsWith(Constants.USERS_ROOT + "/system/") // but not system user
                    && !member.getID().equals(Constants.USER_ANONYMOUS) // and not anonymous
            ) {
                // not relevant for further handling
                relevantMembersIt.remove();
            }
        }

        return relevantMembers;
    }

    private Set<String> removeExternalGroupsThatAreUntouchedByConfiguration(Set<String> relevantMembersInRepo,
            AcInstallationHistoryPojo history) {
        Set<String> relevantMembers = new HashSet<String>(relevantMembersInRepo);
        Pattern keepExistingMembershipsForGroupNamesRegEx = history.getAcConfiguration().getGlobalConfiguration().getKeepExistingMembershipsForGroupNamesRegEx();
        if (keepExistingMembershipsForGroupNamesRegEx != null) {
            Iterator<String> relevantMembersIt = relevantMembers.iterator();
            while (relevantMembersIt.hasNext()) {
                String member = relevantMembersIt.next();
                if (keepExistingMembershipsForGroupNamesRegEx.matcher(member).matches()) {
                    relevantMembersIt.remove();
                }
            }
        }

        return relevantMembers;

    }

    private Set<String> getDeclaredMembers(Group installedGroup) throws RepositoryException {
        Set<String> membersInRepo = new HashSet<String>();
        Iterator<Authorizable> currentMemberInRepo = installedGroup.getDeclaredMembers();
        while (currentMemberInRepo.hasNext()) {
            membersInRepo.add(currentMemberInRepo.next().getID());
        }
        return membersInRepo;
    }

    private void migrateFromOldGroup(AuthorizableConfigBean authorizableConfigBean, UserManager userManager,
            AcInstallationHistoryPojo status) throws RepositoryException {
        Authorizable groupForMigration = userManager.getAuthorizable(authorizableConfigBean.getMigrateFrom());

        String authorizableId = authorizableConfigBean.getAuthorizableId();

        if (groupForMigration == null) {
            status.addMessage(LOG, "Group " + authorizableConfigBean.getMigrateFrom()
                    + " does not exist (specified as migrateFrom in group "
                    + authorizableId + ") - no action taken");
            return;
        }
        if (!groupForMigration.isGroup()) {
            status.addWarning(LOG, "Specifying a user in 'migrateFrom' does not make sense (migrateFrom="
                    + authorizableConfigBean.getMigrateFrom() + " in " + authorizableId + ")");
            return;
        }

        status.addMessage(LOG, "Migrating from group " + authorizableConfigBean.getMigrateFrom()
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
            status.addMessage(LOG, "- Taking over " + usersFromGroupToTakeOver.size() + " member users from group "
                    + authorizableConfigBean.getMigrateFrom() + " to group " + authorizableId);
            Group currentGroup = (Group) userManager.getAuthorizable(authorizableId);
            for (Authorizable user : usersFromGroupToTakeOver) {
                currentGroup.addMember(user);
            }
        }

        groupForMigration.remove();
        status.addMessage(LOG, "- Deleted group " + authorizableConfigBean.getMigrateFrom());

    }

    private void handleRecreationOfAuthorizableIfNecessary(final Session session,
            AuthorizableConfigBean principalConfigBean,
            AcInstallationHistoryPojo history,
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
            history.addMessage(LOG, "Found change of intermediate path for " + existingAuthorizable.getID() + ": "
                    + intermediatedPathOfExistingAuthorizable + " -> " + authorizablePathFromBean);
        }

        // using "" to compare non-external (both sides) to true
        String externalIdExistingAuthorizable = StringUtils
                .defaultIfEmpty(AcHelper.valuesToString(existingAuthorizable.getProperty(REP_EXTERNAL_ID)), "");
        String externalIdConfig = StringUtils.defaultIfEmpty(principalConfigBean.getExternalId(), "");

        boolean externalIdHasChanged = !StringUtils.equals(externalIdExistingAuthorizable, externalIdConfig);

        if (externalIdHasChanged) {
            history.addMessage(LOG, "Found change of external id of " + existingAuthorizable.getID() + ": '"
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
            ValueFactory vf = session.getValueFactory();
            Authorizable newAuthorizable = createNewAuthorizable(principalConfigBean, history, userManager, session);

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

            history.addMessage(LOG, "Recreated authorizable " + newAuthorizable + " at path " + newAuthorizable.getPath()
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

    private void applyGroupMembershipConfigIsMemberOf(AcInstallationHistoryPojo status,
            AuthorizableConfigBean authorizableConfigBean, UserManager userManager, Session session)
            throws RepositoryException, ValueFormatException,
            UnsupportedRepositoryOperationException,
            AuthorizableExistsException, AuthorizableCreatorException {
        String[] memberOf = authorizableConfigBean.getMemberOf();
        String authorizableId = authorizableConfigBean.getAuthorizableId();

        Authorizable currentGroupFromRepository = userManager.getAuthorizable(authorizableId);
        Set<String> membershipGroupsFromConfig = getMembershipGroupsFromConfig(memberOf);
        Set<String> membershipGroupsFromRepository = getMembershipGroupsFromRepository(currentGroupFromRepository);

        applyGroupMembershipConfigIsMemberOf(authorizableId, status, userManager, session, membershipGroupsFromConfig,
                membershipGroupsFromRepository);
    }

    private Authorizable createNewAuthorizable(
            AuthorizableConfigBean principalConfigBean,
            AcInstallationHistoryPojo status,
            UserManager userManager, Session session)
            throws AuthorizableExistsException, RepositoryException,
            AuthorizableCreatorException {

        boolean isGroup = principalConfigBean.isGroup();
        String authorizableId = principalConfigBean.getAuthorizableId();

        Authorizable newAuthorizable = null;

        if (isGroup) {
            newAuthorizable = createNewGroup(userManager, principalConfigBean, status, session);
            LOG.info("Successfully created new group: {}", authorizableId);
        } else {
            if (StringUtils.isNotEmpty(principalConfigBean.getExternalId())) {
                throw new IllegalStateException("External IDs are not supported for users (" + principalConfigBean.getAuthorizableId()
                        + " is using '" + principalConfigBean.getExternalId()
                        + "') - use a ootb sync handler to have users automatically created.");
            }

            newAuthorizable = createNewUser(userManager, principalConfigBean, status, session);
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
    void applyGroupMembershipConfigIsMemberOf(String authorizableId,
            AcInstallationHistoryPojo status, UserManager userManager, Session session,
            Set<String> membershipGroupsFromConfig,
            Set<String> membershipGroupsFromRepository)
            throws RepositoryException, AuthorizableExistsException,
            AuthorizableCreatorException {
        LOG.debug("mergeMemberOfGroups() for {}", authorizableId);

        // membership to everyone cannot be removed or added => take it out from both lists
        membershipGroupsFromConfig.remove(PRINCIPAL_EVERYONE);
        membershipGroupsFromRepository.remove(PRINCIPAL_EVERYONE);

        status.addVerboseMessage(LOG, "Authorizable " + authorizableId + " isMemberOf(repo)=" + membershipGroupsFromRepository);
        status.addVerboseMessage(LOG, "Authorizable " + authorizableId + " isMemberOf(conifg)=" + membershipGroupsFromConfig);

        Set<String> validatedMembershipGroupsFromConfig = validateAssignedGroups(userManager, session, authorizableId,
                membershipGroupsFromConfig, status);

        Collection<String> unChangedMembers = CollectionUtils.intersection(membershipGroupsFromRepository,
                validatedMembershipGroupsFromConfig);
        status.addVerboseMessage(LOG, "Authorizable " + authorizableId + " remains member of groups " + unChangedMembers);

        Collection<String> toBeAddedMembers = CollectionUtils.subtract(validatedMembershipGroupsFromConfig, membershipGroupsFromRepository);
        status.addVerboseMessage(LOG, "Authorizable " + authorizableId + " will be added as member of " + toBeAddedMembers);

        Collection<String> toBeRemovedMembers = CollectionUtils.subtract(membershipGroupsFromRepository,
                validatedMembershipGroupsFromConfig);
        Set<String> toBeSkippedFromRemovalMembers = new HashSet<String>();

        Pattern ignoredMembershipsPattern = status.getAcConfiguration().getGlobalConfiguration().getKeepExistingMembershipsForGroupNamesRegEx();

        Iterator<String> toBeRemovedMembersIt = toBeRemovedMembers.iterator();
        while (toBeRemovedMembersIt.hasNext()) {
            String groupId = toBeRemovedMembersIt.next();
            if ((ignoredMembershipsPattern != null) && ignoredMembershipsPattern.matcher(groupId).find()) {
                toBeSkippedFromRemovalMembers.add(groupId);
                toBeRemovedMembersIt.remove();
            }
        }
        status.addVerboseMessage(LOG, "Authorizable " + authorizableId + " will be removed from members of " + toBeRemovedMembers);

        if (!toBeSkippedFromRemovalMembers.isEmpty()) {
            status.addVerboseMessage(LOG, "Authorizable " + authorizableId + " remains member of groups "
                    + toBeSkippedFromRemovalMembers + " (due to configured ignoredMembershipsPattern=" + ignoredMembershipsPattern + ")");

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

        if (!toBeAddedMembers.isEmpty() && !toBeAddedMembers.isEmpty()) {
            status.addVerboseMessage(LOG,
                    "Membership Change: Authorizable " + authorizableId + " was added to " + toBeAddedMembers.size()
                            + " and removed from " + toBeRemovedMembers.size() + " groups");
        }

    }

    private Authorizable createNewGroup(
            final UserManager userManager,
            AuthorizableConfigBean principalConfigBean,
            AcInstallationHistoryPojo status, Session session)
            throws AuthorizableExistsException, RepositoryException,
            AuthorizableCreatorException {

        String groupID = principalConfigBean.getAuthorizableId();
        String intermediatePath = principalConfigBean.getPath();

        // create new Group
        Group newGroup = null;
        try {

            if (StringUtils.isNotEmpty(principalConfigBean.getExternalId())) {

                if (externalGroupCreatorService == null) {
                    throw new IllegalStateException("External IDs are not availabe for your AEM version ("
                            + principalConfigBean.getAuthorizableId() + " is using '" + principalConfigBean.getExternalId() + "')");
                }
                newGroup = (Group) externalGroupCreatorService.createGroupWithExternalId(userManager, principalConfigBean, status, session);
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

        addMembersToReferencingAuthorizables(newGroup, principalConfigBean, userManager, session, status);

        setAuthorizableProperties(newGroup, principalConfigBean, session);
        return newGroup;
    }

    private void setAuthorizableProperties(Authorizable authorizable, AuthorizableConfigBean principalConfigBean,
            Session session)
            throws RepositoryException {

        String profileContent = principalConfigBean.getProfileContent();
        if (StringUtils.isNotBlank(profileContent)) {
            ContentHelper.importContent(session, authorizable.getPath() + "/profile", profileContent);
        }

        String preferencesContent = principalConfigBean.getPreferencesContent();
        if (StringUtils.isNotBlank(preferencesContent)) {
            ContentHelper.importContent(session, authorizable.getPath() + "/preferences", preferencesContent);
        }

        ValueFactory vf = session.getValueFactory();

        String name = principalConfigBean.getName();
        if (StringUtils.isNotBlank(name)) {
            if (authorizable.isGroup()) {
                authorizable.setProperty("profile/givenName", vf.createValue(name));
            } else {
                String givenName = StringUtils.substringBeforeLast(name, " ");
                String familyName = StringUtils.substringAfterLast(name, " ");
                authorizable.setProperty("profile/givenName", vf.createValue(givenName));
                authorizable.setProperty("profile/familyName", vf.createValue(familyName));
            }
        }

        String description = principalConfigBean.getDescription();
        if (StringUtils.isNotBlank(description)) {
            authorizable.setProperty("profile/aboutMe", vf.createValue(description));
        }
    }

    private Authorizable createNewUser(
            final UserManager userManager,
            AuthorizableConfigBean principalConfigBean,
            AcInstallationHistoryPojo status,
            Session session)
            throws AuthorizableExistsException, RepositoryException,
            AuthorizableCreatorException {
        String authorizableId = principalConfigBean.getAuthorizableId();
        String password = principalConfigBean.getPassword();
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
        setAuthorizableProperties(newUser, principalConfigBean, session);

        addMembersToReferencingAuthorizables(newUser, principalConfigBean, userManager, session, status);

        return newUser;
    }

    private void addMembersToReferencingAuthorizables(Authorizable authorizable, AuthorizableConfigBean principalConfigBean,
            final UserManager userManager, Session session, AcInstallationHistoryPojo status)
            throws RepositoryException, AuthorizableCreatorException {
        String authorizableId = principalConfigBean.getAuthorizableId();
        String[] memberOf = principalConfigBean.getMemberOf();
        if ((authorizable != null) && (memberOf != null) && (memberOf.length > 0)) {
            // add group to groups according to configuration
            Set<String> referencingAuthorizablesToBeChanged = validateAssignedGroups(userManager, session, authorizableId,
                    new HashSet<String>(Arrays.asList(memberOf)), status);
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
     * @param status
     * @return Set of authorizables which the current authorizable is a member of
     * @throws RepositoryException
     * @throws AuthorizableCreatorException if one of the authorizables contained in membersOf array is a user */
    Set<String> validateAssignedGroups(
            final UserManager userManager, Session session, final String authorizablelId,
            final Set<String> isMemberOf, AcInstallationHistoryPojo status) throws RepositoryException,
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

                AuthorizableConfigBean configBeanForIsMemberOf = status.getAcConfiguration().getAuthorizablesConfig()
                        .getAuthorizableConfig(memberOfAuthorizable);

                if (configBeanForIsMemberOf != null) {


                    Group newGroup = (Group) createNewGroup(userManager, configBeanForIsMemberOf, status, session);

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
