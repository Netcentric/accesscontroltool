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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableCreatorException;
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableCreatorService;
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableInstallationHistory;
import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;

@Service
@Component(metatype = true, label = "AuthorizableCreatorService Service", description = "Service that installs groups according to textual configuration files")
public class AuthorizableCreatorServiceImpl implements
        AuthorizableCreatorService {

    private static final String PATH_HOME_GROUPS = "/home/groups";
    private static final String PATH_HOME_USERS = "/home/users";

    private static final Logger LOG = LoggerFactory
            .getLogger(AuthorizableCreatorServiceImpl.class);

    AcInstallationHistoryPojo status;
    Map<String, LinkedHashSet<AuthorizableConfigBean>> principalMapFromConfig;
    AuthorizableInstallationHistory authorizableInstallationHistory;

    @Override
    public void createNewAuthorizables(
            Map<String, LinkedHashSet<AuthorizableConfigBean>> principalMapFromConfig,
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

            LinkedHashSet<AuthorizableConfigBean> currentPrincipalData = principalMapFromConfig
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
        LOG.info("- start installation of authorizable: {}", principalId);

        UserManager userManager = getUsermanager(session);
        ValueFactory vf = session.getValueFactory();

        // if current authorizable from config doesn't exist yet
        Authorizable authorizableForPrincipalId = userManager.getAuthorizable(principalId);
        if (authorizableForPrincipalId == null) {
            createNewAuthorizable(authorizableConfigBean, history,
                    authorizableInstallationHistory, userManager, vf);
        }
        // if current authorizable from config already exists in repository
        else {

            // update name for both groups and users
            setAuthorizableName(authorizableForPrincipalId, vf, authorizableConfigBean);
            // update password for users
            if (!authorizableForPrincipalId.isGroup() && !authorizableConfigBean.isSystemUser()
                    && StringUtils.isNotBlank(authorizableConfigBean.getPassword())) {
                ((User) authorizableForPrincipalId).changePassword(authorizableConfigBean.getPassword());
            }

            // move authorizable if path changed (retaining existing members)
            handleIntermediatePath(session, authorizableConfigBean, history,
                    authorizableInstallationHistory, userManager);

            mergeGroup(history, authorizableInstallationHistory,
                    authorizableConfigBean, userManager);

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

    private UserManager getUsermanager(Session session)
            throws AccessDeniedException,
            UnsupportedRepositoryOperationException, RepositoryException {
        JackrabbitSession js = (JackrabbitSession) session;
        UserManager userManager = js.getUserManager();
        // Since the persistence of the installation should only take place if
        // no error occured and certain test were successful
        // the autosave gets disabled. Therefore an explicit session.save() is
        // necessary to persist the changes.

        // Try do disable the autosave only in case if changes are automatically persisted
        if (userManager.isAutoSave()) {
            try {
                userManager.autoSave(false);
            } catch (UnsupportedRepositoryOperationException e) {
                // check added for AEM 6.0
                LOG.warn("disabling autoSave not possible with this user manager!");
            }
        }

        return userManager;
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
        if (authorizablePathFromBean.charAt(0) != '/') {
            authorizablePathFromBean = (principalConfigBean.isGroup() ? PATH_HOME_GROUPS : PATH_HOME_USERS) + "/"
                    + authorizablePathFromBean;
        }
        if (!StringUtils.equals(intermediatedPathOfExistingAuthorizable, authorizablePathFromBean)) {
            StringBuilder message = new StringBuilder();
            message.append("found change of intermediate path:\n"
                    + "existing authorizable: " + existingAuthorizable.getID()  + " has intermediate path: "   + intermediatedPathOfExistingAuthorizable +"\n"
                    + "authorizable from config: " + principalConfigBean.getPrincipalID() + " has intermediate path: " + authorizablePathFromBean
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
                    authorizableInstallationHistory, userManager, vf);

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
            LOG.warn(message.toString());

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
        while (!StringUtils.equals(PATH_HOME_GROUPS,
                oldIntermediateNode.getPath())
                && !StringUtils.equals(PATH_HOME_USERS,
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

        mergeMemberOfGroups(principalId, status, userManager,
                currentGroupFromRepository, membershipGroupsFromConfig,
                membershipGroupsFromRepository);
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
            UserManager userManager, ValueFactory vf)
            throws AuthorizableExistsException, RepositoryException,
            AuthorizableCreatorException {

        boolean isGroup = principalConfigBean.isGroup();
        String principalId = principalConfigBean.getPrincipalID();

        Authorizable newAuthorizable = null;
        if (isGroup) {
            newAuthorizable = createNewGroup(userManager, principalConfigBean,
                    status, authorizableInstallationHistory, vf,
                    principalMapFromConfig);
            authorizableInstallationHistory
                    .addNewCreatedAuthorizabe(principalId);
            LOG.info("Successfully created new Group: {}", principalId);
        } else {
            newAuthorizable = createNewUser(userManager, principalConfigBean, status, authorizableInstallationHistory, vf,
                    principalMapFromConfig);
            LOG.info("Successfully created new User: {}", principalId);
            authorizableInstallationHistory
                    .addNewCreatedAuthorizabe(principalId);
        }
        return newAuthorizable;
    }

    private Set<String> getMembershipGroupsFromRepository(
            Authorizable currentGroupFromRepository) throws RepositoryException {
        Set<String> membershipGroupsFromRepository = new HashSet<String>();
        Iterator<Group> memberOfGroupsIterator = currentGroupFromRepository
                .declaredMemberOf();

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

    private void mergeMemberOfGroups(String principalId,
            AcInstallationHistoryPojo status, UserManager userManager,
            Authorizable currentGroupFromRepository,
            Set<String> membershipGroupsFromConfig,
            Set<String> membershipGroupsFromRepository)
            throws RepositoryException, AuthorizableExistsException,
            AuthorizableCreatorException {
        LOG.info("...checking differences");

        // group in repo doesn't have any members and group in config doesn't
        // have any members
        // do nothing
        if (!isMemberOfOtherGroup(currentGroupFromRepository)
                && membershipGroupsFromConfig.isEmpty()) {
            LOG.info(
                    "{}: authorizable in repo is not member of any other group and group in config is not member of any other group. No change necessary here!",
                    principalId);
        }

        // group in repo is not member of any other group but group in config is
        // member of at least one other group
        // transfer members to group in repo

        else if (!isMemberOfOtherGroup(currentGroupFromRepository)
                && !membershipGroupsFromConfig.isEmpty()) {
            mergeMemberOfGroupsFromConfig(principalId, status, userManager,
                    membershipGroupsFromConfig);

            // group in repo is member of at least one other group and group in
            // config is not member of any other group

        } else if (isMemberOfOtherGroup(currentGroupFromRepository)
                && membershipGroupsFromConfig.isEmpty()) {

            mergeMemberOfGroupsFromRepo(principalId, userManager,
                    membershipGroupsFromRepository);
        }

        // group in repo does have members and group in config does have members

        else if (isMemberOfOtherGroup(currentGroupFromRepository)
                && !membershipGroupsFromConfig.isEmpty()) {
            mergeMultipleMembersOfBothGroups(principalId, status, userManager,
                    membershipGroupsFromConfig, membershipGroupsFromRepository);
        }
    }

    private void mergeMemberOfGroupsFromRepo(String principalId,
            UserManager userManager, Set<String> membershipGroupsFromRepository)
            throws RepositoryException {
        LOG.info(
                "{}: authorizable in repo is member of at least one other group and authorizable in config is not member of any other group",
                principalId);
        // delete memberOf groups of that group in repo
        for (String group : membershipGroupsFromRepository) {
            LOG.info(
                    "{}: delete authorizable from members of group {} in repository",
                    principalId, group);
            ((Group) userManager.getAuthorizable(group))
                    .removeMember(userManager.getAuthorizable(principalId));
        }
    }

    private void mergeMemberOfGroupsFromConfig(String principalId,
            AcInstallationHistoryPojo status, UserManager userManager,
            Set<String> membershipGroupsFromConfig) throws RepositoryException,
            AuthorizableExistsException, AuthorizableCreatorException {
        LOG.info(
                "{}: authorizable in repo is not member of any other group  but authorizable in config is member of at least one other group",
                principalId);

        Set<Authorizable> validatedGroups = validateAssignedGroups(userManager,
                principalId, membershipGroupsFromConfig.toArray(new String[0]));

        for (Authorizable membershipGroup : validatedGroups) {
            LOG.info(
                    "{}: add authorizable to members of group {} in repository",
                    principalId, membershipGroup.getID());
            // Group membershipGroup =
            // (Group)userManager.getAuthorizable(group);

            if (StringUtils.equals(membershipGroup.getID(), principalId)) {
                String warning = "Attempt to add a group as member of itself ("
                        + membershipGroup.getID() + ").";
                LOG.warn(warning);
                status.addWarning(warning);
            } else {
                ((Group) membershipGroup).addMember(userManager
                        .getAuthorizable(principalId));
            }
        }
    }

    private void mergeMultipleMembersOfBothGroups(String principalId,
            AcInstallationHistoryPojo status, UserManager userManager,
            Set<String> membershipGroupsFromConfig,
            Set<String> membershipGroupsFromRepository)
            throws RepositoryException, AuthorizableExistsException,
            AuthorizableCreatorException {

        // are both groups members of exactly the same groups?
        if (membershipGroupsFromRepository.equals(membershipGroupsFromConfig)) {
            // do nothing!
            LOG.info(
                    "{}: authorizable in repo  and authorizable in config are members of the same group(s). No change necessary here!",
                    principalId);
        } else {
            LOG.info(
                    "{}: authorizable in repo is member of at least one other group and authorizable in config is member of at least one other group",
                    principalId);

            // loop through memberOf-groups of group from repo

            for (String authorizable : membershipGroupsFromRepository) {
                // is current a also contained in memberOf-groups property of
                // existing group?
                if (membershipGroupsFromConfig.contains(authorizable)) {
                    continue;

                } else {
                    // if not delete that group of membersOf-property of
                    // existing group

                    LOG.info(
                            "delete {} from members of group {} in repository",
                            principalId, authorizable);
                    ((Group) userManager.getAuthorizable(authorizable))
                            .removeMember(userManager
                                    .getAuthorizable(principalId));
                }
            }

            Set<Authorizable> validatedGroups = validateAssignedGroups(
                    userManager, principalId,
                    membershipGroupsFromConfig.toArray(new String[0]));
            for (Authorizable authorizable : validatedGroups) {

                // is current group also contained in memberOf-groups property
                // of repo group?

                if (membershipGroupsFromRepository.contains(authorizable
                        .getID())) {
                    continue;
                } else {

                    // if not add that group to membersOf-property of existing
                    // group

                    LOG.info("add {} to members of group {} in repository",
                            principalId, authorizable);
                    if (StringUtils.equals(authorizable.getID(), principalId)) {
                        String warning = "Attempt to add a group as member of itself ("
                                + authorizable + ").";
                        LOG.warn(warning);
                        status.addWarning(warning);
                    } else {
                        ((Group) authorizable).addMember(userManager
                                .getAuthorizable(principalId));

                    }
                }
            }

        }
    }

    private boolean isMemberOfOtherGroup(Authorizable group)
            throws RepositoryException {
        Iterator<Group> it = group.declaredMemberOf();

        if (!it.hasNext()) {
            return false;
        }
        return true;
    }

    private Authorizable createNewGroup(
            final UserManager userManager,
            AuthorizableConfigBean principalConfigBean,
            AcInstallationHistoryPojo status,
            AuthorizableInstallationHistory authorizableInstallationHistory,
            ValueFactory vf,
            Map<String, LinkedHashSet<AuthorizableConfigBean>> principalMapFromConfig)
            throws AuthorizableExistsException, RepositoryException,
            AuthorizableCreatorException {

        String groupID = principalConfigBean.getPrincipalID();
        String[] memberOf = principalConfigBean.getMemberOf();
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

        // add group to groups according to configuration
        if ((memberOf != null) && (memberOf.length > 0)) {
            Set<Authorizable> assignedGroups = validateAssignedGroups(
                    userManager, groupID, memberOf);

            if (!assignedGroups.isEmpty()) {
                LOG.info("start adding {} to assignedGroups", groupID);
                for (Authorizable authorizable : assignedGroups) {
                    ((Group) authorizable).addMember(newGroup);
                    LOG.info("added to {} ", authorizable);
                }
            }
        }

        setAuthorizableName(newGroup, vf, principalConfigBean);
        return newGroup;
    }

    private void setAuthorizableName(Authorizable authorizable, ValueFactory vf, AuthorizableConfigBean principalConfigBean)
            throws RepositoryException {

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

    }

    private Authorizable createNewUser(
            final UserManager userManager,
            AuthorizableConfigBean principalConfigBean,
            AcInstallationHistoryPojo status,
            AuthorizableInstallationHistory authorizableInstallationHistory,
            ValueFactory vf,
            Map<String, LinkedHashSet<AuthorizableConfigBean>> principalMapFromConfig)
            throws AuthorizableExistsException, RepositoryException,
            AuthorizableCreatorException {
        String principalId = principalConfigBean.getPrincipalID();
        String[] memberOf = principalConfigBean.getMemberOf();
        String password = principalConfigBean.getPassword();
        boolean isSystemUser = principalConfigBean.isSystemUser();
        String intermediatePath = principalConfigBean.getPath();

        User newUser = null;
        if (isSystemUser) {
            newUser = userManagerCreateSystemUserViaReflection(userManager, principalId, intermediatePath, status);
        } else {
            newUser = userManager.createUser(principalId, password, new PrincipalImpl(principalId), intermediatePath);
        }
        setAuthorizableName(newUser, vf, principalConfigBean);

        if ((newUser != null) && (memberOf != null) && (memberOf.length > 0)) {

            // add group to groups according to configuration
            Set<Authorizable> authorizables = validateAssignedGroups(
                    userManager, principalId, memberOf);

            if (!authorizables.isEmpty()) {

                for (Authorizable authorizable : authorizables) {
                    ((Group) authorizable).addMember(newUser);
                }
            }
        }
        return newUser;
    }

    // using reflection with fallback to create a system user in order to be backwards compatible
    public User userManagerCreateSystemUserViaReflection(UserManager userManager, String userID, String intermediatePath,
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

    /** Validates the authorizables in 'membersOf' array of a given authorizable. Validation fails if an authorizable is a user If an
     * authorizable contained in membersOf array doesn't exist it gets created and the current authorizable gets added as a member
     *
     * @param out
     * @param userManager
     * @param authorizablelID the ID of authorizable to validate
     * @param memberOf String array that contains the groups which the authorizable should be a member of
     * @return Set of authorizables which the current authorizable is a member of
     * @throws RepositoryException
     * @throws AuthorizableCreatorException if one of the authorizables contained in membersOf array is a user */
    private Set<Authorizable> validateAssignedGroups(
            final UserManager userManager, final String authorizablelID,
            final String[] memberOf) throws RepositoryException,
            AuthorizableCreatorException {

        Set<Authorizable> authorizableSet = new HashSet<Authorizable>();
        for (String principal : memberOf) {

            Authorizable authorizable = userManager.getAuthorizable(principal);

            // validation

            // if authorizable is existing in system
            if (authorizable != null) {

                // check if authorizable is a group
                if (authorizable.isGroup()) {
                    authorizableSet.add(authorizable);
                } else {
                    String message = "Failed to add authorizable "
                            + authorizablelID + "to autorizable " + principal
                            + "! Authorizable is not a group";
                    LOG.warn(message);

                    throw new AuthorizableCreatorException(
                            "Failed to add authorizable " + authorizablelID
                                    + "to autorizable " + principal
                                    + "! Authorizable is not a group");
                }
                // if authorizable doesn't exist yet, it gets created and the
                // current authorizable gets added as a member
            } else {
                // check if authorizable is contained in any of the
                // configurations
                if (principalMapFromConfig.keySet().contains(principal)) {

                    // get authorizable intermediatePath
                    LinkedHashSet<AuthorizableConfigBean> authorizableConfigSet = principalMapFromConfig
                            .get(principal);
                    Iterator<AuthorizableConfigBean> it = authorizableConfigSet
                            .iterator();
                    AuthorizableConfigBean authorizableConfigBean = null;
                    while (it.hasNext()) {
                        authorizableConfigBean = it.next();
                    }
                    // create authorizable

                    // if authorizableConfigBean.getPath() returns an empty
                    // string (no path defined in configuration) the standard
                    // path gets used
                    Authorizable newGroup = userManager.createGroup(
                            new PrincipalImpl(principal),
                            authorizableConfigBean.getPath());
                    authorizableSet.add(newGroup);
                    authorizableInstallationHistory
                            .addNewCreatedAuthorizabe(newGroup.getID());
                    LOG.warn(
                            "Failed to add group: {} to authorizable: {}. Didn't find this authorizable under /home! Created group",
                            authorizablelID, principal);
                } else {
                    String message = "Failed to add group: "
                            + authorizablelID
                            + " as member to authorizable: "
                            + principal
                            + ". Neither found this authorizable ("
                            + principal
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
                    userManager.getAuthorizable(authorizableName).remove();
                    message = "removed authorizable " + authorizableName
                            + " from the system!";
                    LOG.info(message);
                    history.addWarning(message);
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
