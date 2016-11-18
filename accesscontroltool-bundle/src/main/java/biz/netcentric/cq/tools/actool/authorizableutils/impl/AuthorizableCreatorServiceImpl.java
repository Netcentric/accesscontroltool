/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.authorizableutils.impl;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.jcr.AccessDeniedException;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.ValueFactory;
import javax.jcr.ValueFormatException;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.version.VersionException;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.AuthorizableExistsException;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.jcr.api.SlingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableBean;
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableCreatorException;
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableCreatorService;
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableInstallationHistory;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
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

    AcInstallationHistoryPojo status;
    Map<String, Set<AuthorizableConfigBean>> principalMapFromConfig;
    AuthorizableInstallationHistory authorizableInstallationHistory;

    @Override
    public void createNewAuthorizables(
            Map<String, Set<AuthorizableConfigBean>> principalMapFromConfig,
            final Session session, AcInstallationHistoryPojo status,
            AuthorizableInstallationHistory authorizableInstallationHistory)
                    throws AccessDeniedException,
                    UnsupportedRepositoryOperationException, RepositoryException,
                    AuthorizableCreatorException {

        this.status = status;
        this.principalMapFromConfig = principalMapFromConfig;
        this.authorizableInstallationHistory = authorizableInstallationHistory;

        Set<String> groupsFromConfigurations = principalMapFromConfig.keySet();

        for (String principalId : groupsFromConfigurations) {

            Set<AuthorizableConfigBean> currentPrincipalData = principalMapFromConfig
                    .get(principalId);
            Iterator<AuthorizableConfigBean> it = currentPrincipalData
                    .iterator();

            AuthorizableConfigBean tmpPricipalConfigBean = null;
            while (it.hasNext()) {

                tmpPricipalConfigBean = it.next();
                status.addVerboseMessage("Starting installation of authorizable bean: "
                        + tmpPricipalConfigBean.toString());
            }

            installAuthorizableConfigurationBean(session,
                    tmpPricipalConfigBean, status,
                    authorizableInstallationHistory);
        }

    }

    private void installAuthorizableConfigurationBean(final Session session,
            AuthorizableConfigBean authorizableConfigBean,
            AcInstallationHistoryPojo history,
            AuthorizableInstallationHistory authorizableInstallationHistory)
                    throws AccessDeniedException,
                    UnsupportedRepositoryOperationException, RepositoryException,
                    AuthorizableExistsException, AuthorizableCreatorException {

        String principalId = authorizableConfigBean.getPrincipalID();
        LOG.debug("- start installation of authorizable: {}", principalId);

        UserManager userManager = AccessControlUtils.getUserManagerAutoSaveDisabled(session);
        ValueFactory vf = session.getValueFactory();

        // if current authorizable from config doesn't exist yet
        Authorizable authorizableToInstall = userManager.getAuthorizable(principalId);
        if (authorizableToInstall == null) {
            authorizableToInstall = createNewAuthorizable(authorizableConfigBean, history,
                    authorizableInstallationHistory, userManager, vf, session);
        }
        // if current authorizable from config already exists in repository
        else {

            // update name for both groups and users
            setAuthorizableProperties(authorizableToInstall, vf, authorizableConfigBean, session);
            // update password for users
            if (!authorizableToInstall.isGroup() && !authorizableConfigBean.isSystemUser()
                    && StringUtils.isNotBlank(authorizableConfigBean.getPassword())) {
                ((User) authorizableToInstall).changePassword(authorizableConfigBean.getPassword());
            }

            // move authorizable if path changed (retaining existing members)
            handleIntermediatePath(session, authorizableConfigBean, history,
                    authorizableInstallationHistory, userManager);

            mergeGroup(history, authorizableInstallationHistory,
                    authorizableConfigBean, userManager);

        }

        if (authorizableConfigBean.isGroup()) {
            // this has to be added explicitly here (all other memberships are maintained isMemberOf)
            Group installedGroup = (Group) userManager.getAuthorizable(principalId);
            Authorizable anonymous = userManager.getAuthorizable(Constants.USER_ANONYMOUS);
            if (authorizableConfigBean.membersContainsAnonymous()) {
                installedGroup.addMember(anonymous);
            } else {
                installedGroup.removeMember(anonymous);
            }
        }

        if (StringUtils.isNotBlank(authorizableConfigBean.getMigrateFrom()) && authorizableConfigBean.isGroup()) {
            migrateFromOldGroup(authorizableConfigBean, userManager);
        }

    }

    private void migrateFromOldGroup(AuthorizableConfigBean authorizableConfigBean, UserManager userManager) throws RepositoryException {
        Authorizable groupForMigration = userManager.getAuthorizable(authorizableConfigBean.getMigrateFrom());

        String principalId = authorizableConfigBean.getPrincipalID();

        if (groupForMigration == null) {
            status.addMessage("Group " + authorizableConfigBean.getMigrateFrom()
                    + " does not exist (specified as migrateFrom in group "
                    + principalId + ") - no action taken");
            return;
        }
        if (!groupForMigration.isGroup()) {
            status.addWarning("Specifying a user in 'migrateFrom' does not make sense (migrateFrom="
                    + authorizableConfigBean.getMigrateFrom() + " in " + principalId + ")");
            return;
        }

        status.addMessage("Migrating from group " + authorizableConfigBean.getMigrateFrom()
                + "  to " + principalId);

        Set<Authorizable> usersFromGroupToTakeOver = new HashSet<Authorizable>();
        Iterator<Authorizable> membersIt = ((Group) groupForMigration).getMembers();
        while (membersIt.hasNext()) {
            Authorizable member = membersIt.next();
            if (!member.isGroup()) {
                usersFromGroupToTakeOver.add(member);
            }
        }

        if (!usersFromGroupToTakeOver.isEmpty()) {
            status.addMessage("- Taking over " + usersFromGroupToTakeOver.size() + " member users from group "
                    + authorizableConfigBean.getMigrateFrom() + " to group " + principalId);
            Group currentGroup = (Group) userManager.getAuthorizable(principalId);
            for (Authorizable user : usersFromGroupToTakeOver) {
                currentGroup.addMember(user);
            }
        }

        groupForMigration.remove();
        status.addMessage("- Deleted group " + authorizableConfigBean.getMigrateFrom());

    }

    private void handleIntermediatePath(final Session session,
            AuthorizableConfigBean principalConfigBean,
            AcInstallationHistoryPojo history,
            AuthorizableInstallationHistory authorizableInstallationHistory,
            UserManager userManager) throws RepositoryException, AuthorizableCreatorException {

        String principalId = principalConfigBean.getPrincipalID();

        // compare intermediate paths
        Authorizable existingAuthorizable = userManager.getAuthorizable(principalId);

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
        if (!StringUtils.equals(intermediatedPathOfExistingAuthorizable, authorizablePathFromBean)) {
            StringBuilder message = new StringBuilder();
            message.append("found change of intermediate path:\n"
                    + "existing authorizable: " + existingAuthorizable.getID() + " has intermediate path: "
                    + intermediatedPathOfExistingAuthorizable + "\n"
                    + "authorizable from config: " + principalConfigBean.getPrincipalID() + " has intermediate path: "
                    + authorizablePathFromBean
                    + "\n");

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
            Authorizable newAuthorizable = createNewAuthorizable(
                    principalConfigBean, history,
                    authorizableInstallationHistory, userManager, vf, session);

            int countMovedMembersOfGroup = 0;
            if (newAuthorizable.isGroup()) {
                Group newGroup = (Group) newAuthorizable;
                // add members of deleted group
                for (Authorizable authorizable : membersOfDeletedGroup) {
                    newGroup.addMember(authorizable);
                    countMovedMembersOfGroup++;
                }
            }

            deleteOldIntermediatePath(session,
                    session.getNode(intermediatedPathOfExistingAuthorizable));

            message.append("recreated authorizable with new intermediate path! "
                    + (newAuthorizable.isGroup() ? "(retained " + countMovedMembersOfGroup + " members of group)" : ""));
            history.addMessage(message.toString());
            LOG.info(message.toString());

        }

    }

    /** // deletes old intermediatePath parent node and all empty parent nodes up to /home/groups or /home/users
     *
     * @param session
     * @param oldIntermediateNode
     * @throws RepositoryException
     * @throws PathNotFoundException
     * @throws VersionException
     * @throws LockException
     * @throws ConstraintViolationException
     * @throws AccessDeniedException */
    private void deleteOldIntermediatePath(final Session session,
            Node oldIntermediateNode) throws RepositoryException,
                    PathNotFoundException, VersionException, LockException,
                    ConstraintViolationException, AccessDeniedException {

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

    private void mergeGroup(AcInstallationHistoryPojo status,
            AuthorizableInstallationHistory authorizableInstallationHistory,
            AuthorizableConfigBean principalConfigBean, UserManager userManager)
                    throws RepositoryException, ValueFormatException,
                    UnsupportedRepositoryOperationException,
                    AuthorizableExistsException, AuthorizableCreatorException {
        String[] memberOf = principalConfigBean.getMemberOf();
        String principalId = principalConfigBean.getPrincipalID();

        LOG.debug("Authorizable {} already exists", principalId);

        Authorizable currentGroupFromRepository = userManager.getAuthorizable(principalId);
        Set<String> membershipGroupsFromConfig = getMembershipGroupsFromConfig(memberOf);
        Set<String> membershipGroupsFromRepository = getMembershipGroupsFromRepository(currentGroupFromRepository);

        // create snapshot bean
        authorizableInstallationHistory.addAuthorizable(
                currentGroupFromRepository.getID(), getAuthorizableName(currentGroupFromRepository),
                currentGroupFromRepository.getPath(),
                membershipGroupsFromRepository);

        mergeMemberOfGroups(principalId, status, userManager, membershipGroupsFromConfig, membershipGroupsFromRepository);
    }

    private String getAuthorizableName(Authorizable currentGroupFromRepository) throws RepositoryException, ValueFormatException {
        String authorizableName = "";

        if (currentGroupFromRepository.getProperty("profile/givenName") != null) {
            authorizableName = currentGroupFromRepository
                    .getProperty("profile/givenName")[0].getString();
        }
        return authorizableName;
    }

    private Authorizable createNewAuthorizable(
            AuthorizableConfigBean principalConfigBean,
            AcInstallationHistoryPojo status,
            AuthorizableInstallationHistory authorizableInstallationHistory,
            UserManager userManager, ValueFactory vf, Session session)
                    throws AuthorizableExistsException, RepositoryException,
                    AuthorizableCreatorException {

        boolean isGroup = principalConfigBean.isGroup();
        String principalId = principalConfigBean.getPrincipalID();

        Authorizable newAuthorizable = null;
        if (isGroup) {
            newAuthorizable = createNewGroup(userManager, principalConfigBean,
                    status, authorizableInstallationHistory, vf,
                    principalMapFromConfig, session);
            authorizableInstallationHistory
                    .addNewCreatedAuthorizable(principalId);
            LOG.info("Successfully created new Group: {}", principalId);
        } else {
            newAuthorizable = createNewUser(userManager, principalConfigBean, status, authorizableInstallationHistory, vf,
                    principalMapFromConfig, session);
            LOG.info("Successfully created new User: {}", principalId);
            authorizableInstallationHistory
                    .addNewCreatedAuthorizable(principalId);
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
    void mergeMemberOfGroups(String principalId,
            AcInstallationHistoryPojo status, UserManager userManager,
            Set<String> membershipGroupsFromConfig,
            Set<String> membershipGroupsFromRepository)
                    throws RepositoryException, AuthorizableExistsException,
                    AuthorizableCreatorException {
        LOG.debug("mergeMemberOfGroups() for {}", principalId);

        // membership to everyone cannot be removed or added => take it out from both lists
        membershipGroupsFromConfig.remove(PRINCIPAL_EVERYONE);
        membershipGroupsFromRepository.remove(PRINCIPAL_EVERYONE);

        logAndVerboseHistoryMessage(status, "Principal " + principalId + " isMemberOf(repo)=" + membershipGroupsFromRepository);
        logAndVerboseHistoryMessage(status, "Principal " + principalId + " isMemberOf(conifg)=" + membershipGroupsFromConfig);

        Set<String> validatedMembershipGroupsFromConfig = validateAssignedGroups(userManager, principalId, membershipGroupsFromConfig);

        Collection<String> unChangedMembers = CollectionUtils.intersection(membershipGroupsFromRepository,
                validatedMembershipGroupsFromConfig);
        logAndVerboseHistoryMessage(status, "Principal " + principalId + " remains member of groups " + unChangedMembers);

        Collection<String> toBeAddedMembers = CollectionUtils.subtract(validatedMembershipGroupsFromConfig, membershipGroupsFromRepository);
        logAndVerboseHistoryMessage(status, "Principal " + principalId + " will be added as member of " + toBeAddedMembers);

        Collection<String> toBeRemovedMembers = CollectionUtils.subtract(membershipGroupsFromRepository,
                validatedMembershipGroupsFromConfig);
        Set<String> toBeSkippedFromRemovalMembers = new HashSet<String>();

        Pattern ignoredMembershipsPattern = status.getAcConfiguration().getGlobalConfiguration().getAllowExternalGroupNamesRegEx();

        Iterator<String> toBeRemovedMembersIt = toBeRemovedMembers.iterator();
        while (toBeRemovedMembersIt.hasNext()) {
            String groupId = toBeRemovedMembersIt.next();
            if ((ignoredMembershipsPattern != null) && ignoredMembershipsPattern.matcher(groupId).find()) {
                toBeSkippedFromRemovalMembers.add(groupId);
                toBeRemovedMembersIt.remove();
            }
        }
        logAndVerboseHistoryMessage(status, "Principal " + principalId + " will be removed from members of " + toBeRemovedMembers);

        if (!toBeSkippedFromRemovalMembers.isEmpty()) {
            logAndVerboseHistoryMessage(status, "Principal " + principalId + " remains member of groups "
                    + toBeSkippedFromRemovalMembers + " (due to configured ignoredMembershipsPattern=" + ignoredMembershipsPattern + ")");

        }

        // perform changes

        Authorizable currentAuthorizable = userManager.getAuthorizable(principalId);

        for (String groupId : toBeAddedMembers) {
            LOG.debug("Membership Change: Adding {} to members of group {} in repository", principalId, groupId);
            Authorizable targetAuthorizable = userManager.getAuthorizable(groupId);
            ((Group) targetAuthorizable).addMember(currentAuthorizable);
        }

        for (String groupId : toBeRemovedMembers) {
            LOG.debug("Membership Change: Removing {} from members of group {} in repository", principalId, groupId);
            Authorizable targetAuthorizable = userManager.getAuthorizable(groupId);
            ((Group) targetAuthorizable).removeMember(currentAuthorizable);
        }

        if (!toBeAddedMembers.isEmpty() && !toBeAddedMembers.isEmpty()) {
            logAndVerboseHistoryMessage(status,
                    "Membership Change: Principal " + principalId + " was added to " + toBeAddedMembers.size()
                            + " and removed from " + toBeRemovedMembers.size() + " groups");
        }

    }

    private void logAndVerboseHistoryMessage(AcInstallationHistoryPojo status, String msg) {
        LOG.debug(msg);
        status.addVerboseMessage(msg);
    }

    private Authorizable createNewGroup(
            final UserManager userManager,
            AuthorizableConfigBean principalConfigBean,
            AcInstallationHistoryPojo status,
            AuthorizableInstallationHistory authorizableInstallationHistory,
            ValueFactory vf,
            Map<String, Set<AuthorizableConfigBean>> principalMapFromConfig, Session session)
                    throws AuthorizableExistsException, RepositoryException,
                    AuthorizableCreatorException {

        String groupID = principalConfigBean.getPrincipalID();
        String intermediatePath = principalConfigBean.getPath();

        // create new Group
        Group newGroup = null;
        try {
            newGroup = userManager.createGroup(new PrincipalImpl(groupID),
                    intermediatePath);
        } catch (AuthorizableExistsException e) {
            LOG.warn("Group {} already exists in system!", groupID);
            newGroup = (Group) userManager.getAuthorizable(groupID);
        }

        addMembersToReferencingAuthorizables(newGroup, principalConfigBean, userManager);

        setAuthorizableProperties(newGroup, vf, principalConfigBean, session);
        return newGroup;
    }

    private void setAuthorizableProperties(Authorizable authorizable, ValueFactory vf, AuthorizableConfigBean principalConfigBean,
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

        String name = principalConfigBean.getPrincipalName();
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
            AuthorizableInstallationHistory authorizableInstallationHistory,
            ValueFactory vf,
            Map<String, Set<AuthorizableConfigBean>> principalMapFromConfig, Session session)
                    throws AuthorizableExistsException, RepositoryException,
                    AuthorizableCreatorException {
        String principalId = principalConfigBean.getPrincipalID();
        String password = principalConfigBean.getPassword();
        boolean isSystemUser = principalConfigBean.isSystemUser();
        String intermediatePath = principalConfigBean.getPath();

        User newUser = null;
        if (isSystemUser) {
            newUser = userManagerCreateSystemUserViaReflection(userManager, principalId, intermediatePath, status);
        } else {
            newUser = userManager.createUser(principalId, password, new PrincipalImpl(principalId), intermediatePath);
        }
        setAuthorizableProperties(newUser, vf, principalConfigBean, session);

        addMembersToReferencingAuthorizables(newUser, principalConfigBean, userManager);

        return newUser;
    }

    private void addMembersToReferencingAuthorizables(Authorizable authorizable, AuthorizableConfigBean principalConfigBean,
            final UserManager userManager) throws RepositoryException, AuthorizableCreatorException {
        String principalId = principalConfigBean.getPrincipalID();
        String[] memberOf = principalConfigBean.getMemberOf();
        if ((authorizable != null) && (memberOf != null) && (memberOf.length > 0)) {
            // add group to groups according to configuration
            Set<String> referencingAuthorizablesToBeChanged = validateAssignedGroups(userManager, principalId,
                    new HashSet<String>(Arrays.asList(memberOf)));
            if (!referencingAuthorizablesToBeChanged.isEmpty()) {
                LOG.debug("start adding {} to assignedGroups", principalId);
                for (String referencingAuthorizableToBeChangedId : referencingAuthorizablesToBeChanged) {
                    Group referencingAuthorizableToBeChanged = (Group) userManager.getAuthorizable(referencingAuthorizableToBeChangedId);
                    referencingAuthorizableToBeChanged.addMember(authorizable);
                    LOG.debug("added to {} ", referencingAuthorizableToBeChanged);
                }
            }
        }
    }

    // using reflection with fallback to create a system user in order to be backwards compatible
    private User userManagerCreateSystemUserViaReflection(UserManager userManager, String userID, String intermediatePath,
            AcInstallationHistoryPojo status)
                    throws RepositoryException {

        // make sure all relative intermediate paths get the prefix suffix (but don't touch absolute paths)
        String systemPrefix = "system/";
        if ((intermediatePath != null) && !intermediatePath.startsWith(systemPrefix) && !intermediatePath.startsWith("/")) {
            intermediatePath = systemPrefix + intermediatePath;
        }

        try {
            Method method = userManager.getClass().getMethod("createSystemUser", String.class, String.class);
            User user = (User) method.invoke(userManager, userID, intermediatePath);

            return user;
        } catch (Throwable e) {
            if (e instanceof InvocationTargetException) {
                e = ((InvocationTargetException) e).getTargetException();
            }
            status.addError("Could not create system user " + userID + ". e:" + e);
        }

        return null;
    }

    /** Validates the authorizables in 'membersOf' array of a given authorizable. Validation fails if an authorizable is a user.
     *
     * If an authorizable contained in membersOf array doesn't exist it gets created and the current authorizable gets added as a member.
     *
     * @param userManager
     * @param authorizablelID the ID of authorizable to validate
     * @param isMemberOf String array that contains the groups which the authorizable should be a member of
     * @return Set of authorizables which the current authorizable is a member of
     * @throws RepositoryException
     * @throws AuthorizableCreatorException if one of the authorizables contained in membersOf array is a user */
    Set<String> validateAssignedGroups(
            final UserManager userManager, final String authorizablelID,
            final Set<String> isMemberOf) throws RepositoryException,
                    AuthorizableCreatorException {

        Set<String> authorizableSet = new HashSet<String>();
        for (String memberOfPrincipal : isMemberOf) {

            if (StringUtils.equals(authorizablelID, memberOfPrincipal)) {
                throw new AuthorizableCreatorException("Cannot add authorizable " + authorizablelID + " as member of itself.");
            }

            Authorizable authorizable = userManager.getAuthorizable(memberOfPrincipal);

            // validation

            // if authorizable is existing in system
            if (authorizable != null) {

                // check if authorizable is a group
                if (authorizable.isGroup()) {
                    authorizableSet.add(authorizable.getID());
                } else {
                    String message = "Failed to add authorizable "
                            + authorizablelID + " to autorizable " + memberOfPrincipal
                            + "! Authorizable is not a group";
                    throw new AuthorizableCreatorException(message);
                }
                // if authorizable doesn't exist yet, it gets created and the
                // current authorizable gets added as a member
            } else {
                // check if authorizable is contained in any of the
                // configurations
                if (principalMapFromConfig.keySet().contains(memberOfPrincipal)) {

                    // get authorizable intermediatePath
                    Set<AuthorizableConfigBean> authorizableConfigSet = principalMapFromConfig.get(memberOfPrincipal);
                    Iterator<AuthorizableConfigBean> it = authorizableConfigSet.iterator();
                    AuthorizableConfigBean authorizableConfigBean = null;
                    while (it.hasNext()) {
                        authorizableConfigBean = it.next();
                    }
                    // create authorizable

                    // if authorizableConfigBean.getPath() returns an empty
                    // string (no path defined in configuration) the standard
                    // path gets used
                    Group newGroup = userManager.createGroup(
                            new PrincipalImpl(memberOfPrincipal),
                            authorizableConfigBean.getPath());
                    authorizableSet.add(newGroup.getID());
                    authorizableInstallationHistory.addNewCreatedAuthorizable(newGroup.getID());
                    LOG.info("Created group to be able to add {} to group {} ", authorizablelID, memberOfPrincipal);
                } else {
                    String message = "Failed to add group: "
                            + authorizablelID
                            + " as member to authorizable: "
                            + memberOfPrincipal
                            + ". Neither found this authorizable ("
                            + memberOfPrincipal
                            + ") in any of the configurations nor installed in the system!";
                    LOG.error(message);

                    throw new AuthorizableCreatorException(message);
                }
            }
        }
        return authorizableSet;
    }

    @Override
    public void performRollback(SlingRepository repository,
            AuthorizableInstallationHistory authorizableInstallationHistory,
            AcInstallationHistoryPojo history) throws RepositoryException {
        Session session = repository.loginAdministrative(null);
        ValueFactory vf = session.getValueFactory();
        try {
            JackrabbitSession js = (JackrabbitSession) session;
            UserManager userManager = js.getUserManager();

            // if groups was newly created delete it

            Set<String> newCreatedAuthorizables = authorizableInstallationHistory
                    .getNewCreatedAuthorizables();
            String message = "starting rollback of authorizables...";
            history.addWarning(message);

            if (!newCreatedAuthorizables.isEmpty()) {
                history.addWarning("performing Groups rollback!");

                for (String authorizableName : newCreatedAuthorizables) {
                    Authorizable authorizable = userManager.getAuthorizable(authorizableName);
                    if (authorizable != null) {
                        authorizable.remove();
                        message = "removed authorizable " + authorizableName + " from the system!";
                        LOG.info(message);
                        history.addWarning(message);
                    } else {
                        message = "Can't remove authorizable " + authorizableName + " from the system!";
                        LOG.error(message);
                        history.addError(message);
                    }
                }
            }

            // if not compare the changes and reset them to the prevoius state

            Set<AuthorizableBean> authorizableBeans = authorizableInstallationHistory
                    .getAuthorizableBeans();

            for (AuthorizableBean snapshotBean : authorizableBeans) {
                Authorizable authorizable = userManager
                        .getAuthorizable(snapshotBean.getName());

                if (authorizable != null) {
                    history.addMessage("found changed authorizable:"
                            + authorizable.getID());

                    // check memberOf groups

                    Iterator<Group> it = authorizable.memberOf();
                    Set<String> memberOfGroups = new HashSet<String>();
                    while (it.hasNext()) {
                        memberOfGroups.add(it.next().getID());
                    }

                    if (snapshotBean.getAuthorizablesSnapshot().equals(
                            memberOfGroups)) {
                        history.addMessage("No change found in memberOfGroups of authorizable: "
                                + authorizable.getID());
                    } else {
                        history.addMessage("changes found in memberOfGroups of authorizable: "
                                + authorizable.getID());

                        // delete membership of currently set memberOf groups
                        Iterator<Group> it2 = authorizable.memberOf();
                        while (it2.hasNext()) {
                            Group group = it2.next();
                            group.removeMember(authorizable);
                            history.addWarning("removed authorizable: "
                                    + authorizable.getID()
                                    + " from members of group: "
                                    + group.getID());
                        }
                        // reset the state from snapshot bean
                        for (String group : snapshotBean
                                .getAuthorizablesSnapshot()) {
                            Authorizable groupFromSnapshot = userManager
                                    .getAuthorizable(group);
                            if (groupFromSnapshot != null) {
                                ((Group) groupFromSnapshot)
                                        .addMember(authorizable);
                                history.addWarning("add authorizable: "
                                        + authorizable.getID()
                                        + " to members of group: "
                                        + groupFromSnapshot.getID() + " again");
                            }
                        }
                    }
                    String authorizableName = "";
                    if (authorizable.hasProperty("profile/givenName")) {
                        authorizableName = authorizable
                                .getProperty("profile/givenName")[0]
                                        .getString();
                    }
                    if (snapshotBean.getName().equals(authorizableName)) {
                        history.addMessage("No change found in name of authorizable: "
                                + authorizable.getID());
                    } else {

                        history.addMessage("change found in name of authorizable: "
                                + authorizable.getID());
                        authorizable.setProperty("profile/givenName",
                                vf.createValue(snapshotBean.getName()));
                        history.addMessage("changed name of authorizable from: "
                                + authorizableName
                                + " back to: "
                                + snapshotBean.getName());
                    }
                    // TODO: compare other properties as well (name, path,...)
                }
            }

        } finally {
            if (session != null) {
                session.save();
                session.logout();
            }
        }
    }
}
