package biz.netcentric.cq.tools.actool.authorizableutils.impl;

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

    private static final Logger LOG = LoggerFactory
            .getLogger(AuthorizableCreatorServiceImpl.class);

    AcInstallationHistoryPojo status;
    Map<String, LinkedHashSet<AuthorizableConfigBean>> principalMapFromConfig;
    AuthorizableInstallationHistory authorizableInstallationHistory;

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
                status.addVerboseMessage("start installation of authorizable bean: "
                        + tmpPricipalConfigBean.toString());
            }

            installAuthorizableConfigurationBean(session,
                    tmpPricipalConfigBean, status,
                    authorizableInstallationHistory);
        }

    }

    private void installAuthorizableConfigurationBean(final Session session,
            AuthorizableConfigBean principalConfigBean,
            AcInstallationHistoryPojo history,
            AuthorizableInstallationHistory authorizableInstallationHistory)
            throws AccessDeniedException,
            UnsupportedRepositoryOperationException, RepositoryException,
            AuthorizableExistsException, AuthorizableCreatorException {

        String principalId = principalConfigBean.getPrincipalID();
        LOG.info("- start installation of authorizable: {}", principalId);

        UserManager userManager = getUsermanager(session);
        ValueFactory vf = session.getValueFactory();

        // if current authorizable from config doesn't yet exist
        if (userManager.getAuthorizable(principalId) == null) {
            createNewAuthorizable(principalConfigBean, history,
                    authorizableInstallationHistory, userManager, vf);
        }
        // if current authorizable from config already exists in repository
        else {

            handleIntermediatePath(session, principalConfigBean, history,
                    authorizableInstallationHistory, userManager);
            mergeGroup(history, authorizableInstallationHistory,
                    principalConfigBean, userManager);
        }
    }

    private UserManager getUsermanager(Session session)
            throws AccessDeniedException,
            UnsupportedRepositoryOperationException, RepositoryException {
        JackrabbitSession js = (JackrabbitSession) session;
        UserManager userManager = js.getUserManager();
        // Since the persistence of the installation should only take place if
        // no error occured and certain test were successful
        // the autosave gets disabled. Therefore an explicit session.save() is
        // necessary to persist the changes
        try {
            userManager.autoSave(false);
        } catch (UnsupportedRepositoryOperationException e) {
            // check added for AEM 6.0
            LOG.warn("disabling autoSave not possible with this user manager!");
        }
        return userManager;
    }

    private void handleIntermediatePath(final Session session,
            AuthorizableConfigBean principalConfigBean,
            AcInstallationHistoryPojo history,
            AuthorizableInstallationHistory authorizableInstallationHistory,
            UserManager userManager) throws RepositoryException,
            UnsupportedRepositoryOperationException,
            AuthorizableExistsException, AuthorizableCreatorException,
            PathNotFoundException, VersionException, LockException,
            ConstraintViolationException, AccessDeniedException {

        String principalId = principalConfigBean.getPrincipalID();

        // compare intermediate paths
        Authorizable existingAuthorizable = userManager
                .getAuthorizable(principalId);

        if (existingAuthorizable.isGroup()) {
            Group existingGroup = (Group) existingAuthorizable;
            String intermediatedPathOfExistingGroup = existingGroup.getPath()
                    .substring(0, existingGroup.getPath().lastIndexOf("/"));
            if (!StringUtils.equals(intermediatedPathOfExistingGroup,
                    principalConfigBean.getPath())) {
                StringBuilder message = new StringBuilder();
                message.append("found change of intermediate path:").append(
                        "\n");
                message.append(
                        "existing group: " + existingGroup.getID()
                                + " has intermediate path: "
                                + intermediatedPathOfExistingGroup)
                        .append("\n");
                message.append(
                        "group from config: "
                                + principalConfigBean.getPrincipalID()
                                + " has intermediate path: "
                                + principalConfigBean.getPath()).append("\n");

                // save members of existing group before deletion
                Set<Authorizable> membersOfDeletedGroup = new HashSet<Authorizable>();
                Iterator<Authorizable> memberIt = existingGroup
                        .getDeclaredMembers();
                while (memberIt.hasNext()) {
                    membersOfDeletedGroup.add(memberIt.next());
                }

                // delete existingGroup;
                existingGroup.remove();

                // create group again using values form config
                ValueFactory vf = session.getValueFactory();
                Group newGroup = (Group) createNewAuthorizable(
                        principalConfigBean, history,
                        authorizableInstallationHistory, userManager, vf);

                // add members of deleted group
                for (Authorizable authorizable : membersOfDeletedGroup) {
                    newGroup.addMember(authorizable);
                }

                deleteOldIntermediatePath(session,
                        session.getNode(intermediatedPathOfExistingGroup));

                message.append("recreated group with new intermediate path!");
                history.addMessage(message.toString());
                LOG.warn(message.toString());

            }
        }
    }

    /**
     * // deletes old intermediatePath parent node and all empty parent nodes up
     * to /home/groups or /home/users
     * 
     * @param session
     * @param oldIntermediateNode
     * @throws RepositoryException
     * @throws PathNotFoundException
     * @throws VersionException
     * @throws LockException
     * @throws ConstraintViolationException
     * @throws AccessDeniedException
     */
    private void deleteOldIntermediatePath(final Session session,
            Node oldIntermediateNode) throws RepositoryException,
            PathNotFoundException, VersionException, LockException,
            ConstraintViolationException, AccessDeniedException {

        // if '/home/groups' or '/home/users' was intermediatedNode, these must
        // not get deleted!
        // also node to be deleted has to be empty, so no other authorizables
        // stored under this path get deleted
        while (!StringUtils.equals("/home/groups",
                oldIntermediateNode.getPath())
                && !StringUtils.equals("/home/users",
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

        String message = "Problem while trying to create new authorizable: "
                + principalId + ". Authorizable already exists!";
        LOG.warn(message);

        Authorizable currentGroupFromRepository = userManager
                .getAuthorizable(principalId);
        Set<String> membershipGroupsFromConfig = getMembershipGroupsFromConfig(memberOf);
        Set<String> membershipGroupsFromRepository = getMembershipGroupsFromRepository(currentGroupFromRepository);

        // create snapshot bean
        String authorizableName = "";

        if (currentGroupFromRepository.getProperty("profile/givenName") != null) {
            authorizableName = currentGroupFromRepository
                    .getProperty("profile/givenName")[0].getString();
        }
        authorizableInstallationHistory.addAuthorizable(
                currentGroupFromRepository.getID(), authorizableName,
                currentGroupFromRepository.getPath(),
                membershipGroupsFromRepository);

        mergeMemberOfGroups(principalId, status, userManager,
                currentGroupFromRepository, membershipGroupsFromConfig,
                membershipGroupsFromRepository);
    }

    private Authorizable createNewAuthorizable(
            AuthorizableConfigBean principalConfigBean,
            AcInstallationHistoryPojo status,
            AuthorizableInstallationHistory authorizableInstallationHistory,
            UserManager userManager, ValueFactory vf)
            throws AuthorizableExistsException, RepositoryException,
            AuthorizableCreatorException {

        String[] memberOf = principalConfigBean.getMemberOf();
        boolean isGroup = principalConfigBean.isGroup();
        String principalId = principalConfigBean.getPrincipalID();
        String password = principalConfigBean.getPassword();

        Authorizable newAuthorizable = null;
        if (isGroup) {
            newAuthorizable = createNewGroup(userManager, principalConfigBean,
                    status, authorizableInstallationHistory, vf,
                    principalMapFromConfig);
            authorizableInstallationHistory
                    .addNewCreatedAuthorizabe(principalId);
            LOG.info("Successfully created new Group: {}", principalId);
        } else {
            newAuthorizable = createNewUser(userManager, principalId, memberOf,
                    password, status, authorizableInstallationHistory, vf,
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
        String name = principalConfigBean.getPrincipalName();
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
        if (memberOf != null && memberOf.length > 0) {
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

        if (StringUtils.isNotBlank(name)) {
            newGroup.setProperty("profile/givenName", vf.createValue(name));
        }
        return newGroup;
    }

    private Authorizable createNewUser(
            final UserManager userManager,
            final String userID,
            final String[] memberOf,
            final String password,
            AcInstallationHistoryPojo status,
            AuthorizableInstallationHistory authorizableInstallationHistory,
            ValueFactory vf,
            Map<String, LinkedHashSet<AuthorizableConfigBean>> principalMapFromConfig)
            throws AuthorizableExistsException, RepositoryException,
            AuthorizableCreatorException {
        User newUser = null;
        if (memberOf != null && memberOf.length > 0) {

            // add group to groups according to configuration
            Set<Authorizable> authorizables = validateAssignedGroups(
                    userManager, userID, memberOf);

            if (!authorizables.isEmpty()) {
                newUser = userManager.createUser(userID, password);
                for (Authorizable authorizable : authorizables) {
                    ((Group) authorizable).addMember(newUser);
                }
            }
        } else {
            newUser = userManager.createUser(userID, password);
        }
        return newUser;
    }

    /**
     * Validates the authorizables in 'membersOf' array of a given authorizable.
     * Validation fails if an authorizable is a user If an authorizable
     * contained in membersOf array doesn't exist it gets created and the
     * current authorizable gets added as a member
     * 
     * @param out
     * @param userManager
     * @param authorizablelID
     *            the ID of authorizable to validate
     * @param memberOf
     *            String array that contains the groups which the authorizable
     *            should be a member of
     * @return Set of authorizables which the current authorizable is a member
     *         of
     * @throws RepositoryException
     * @throws AuthorizableCreatorException
     *             if one of the authorizables contained in membersOf array is a
     *             user
     */
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
