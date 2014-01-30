package biz.netcentric.cq.tools.actool.authorizableutils;


import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.jcr.AccessDeniedException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.ValueFactory;
import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.AuthorizableExistsException;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.jcr.api.SlingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;

public class AuthorizableCreator {

	private static final Logger LOG = LoggerFactory.getLogger(AuthorizableCreator.class);

	public static void createNewAuthorizables(Map<String, LinkedHashSet<AuthorizableConfigBean>> principalMap, final Session session, AcInstallationHistoryPojo status, AuthorizableInstallationHistory authorizableInstallationHistory) throws AccessDeniedException, UnsupportedRepositoryOperationException, RepositoryException, AuthorizableCreatorException{


		Set<String> principalSet = principalMap.keySet();


		for(String principalId : principalSet){

			LinkedHashSet<AuthorizableConfigBean> currentPrincipalData = principalMap.get(principalId);
			Iterator <AuthorizableConfigBean> it = currentPrincipalData.iterator();

			String groupName = null;
			String description = null;
			String intermediatePath = null;
			String[] memberOf = null;
			boolean isGroup = false;
			String password = "";

			while(it.hasNext()){

				AuthorizableConfigBean tmpPricipalConfigBean = it.next();
				status.addVerboseMessage("start installation of authorizable bean: " + tmpPricipalConfigBean.toString());
				// get values from beans 

				groupName = tmpPricipalConfigBean.getPrincipalName();
				description = tmpPricipalConfigBean.getDescription();
				intermediatePath = tmpPricipalConfigBean.getPath();
				memberOf = tmpPricipalConfigBean.getMemberOf();
				isGroup = tmpPricipalConfigBean.isGroup();
				password = tmpPricipalConfigBean.getPassword();
			}

			installAuthorizableConfigurationBean(session, principalId, groupName, memberOf, isGroup,
					password, status, authorizableInstallationHistory);

		}

	}
	private static void installAuthorizableConfigurationBean(final Session session, String principalId, String name, String[] memberOf,
			boolean isGroup, String password, AcInstallationHistoryPojo status, AuthorizableInstallationHistory authorizableInstallationHistory ) throws AccessDeniedException,
			UnsupportedRepositoryOperationException, RepositoryException,
			AuthorizableExistsException, AuthorizableCreatorException {

		LOG.info("- start installation of authorizable: {}", principalId);

		JackrabbitSession js = (JackrabbitSession) session;
		UserManager userManager = js.getUserManager();


		// Since the persistence of the installation should only take place if no error occured and certain test were successful
		// the autosave gets disabled. Therefore an explicit session.save() is necessary to persist the changes
		userManager.autoSave(false);

		//		PrincipalManager principalManager = js.getPrincipalManager();


		// if current a authorizable from config doesn't yet exist
		ValueFactory vf = session.getValueFactory();
		if(userManager.getAuthorizable(principalId) == null) {
			//	Group newGroup = userManager.createGroup(groupID, new PrincipalImpl(groupName), intermediatePath);
			if(isGroup){
				createNewGroup(userManager, principalId, name, memberOf, status, authorizableInstallationHistory, vf );
				authorizableInstallationHistory.addNewCreatedAuthorizabe(principalId);
				LOG.info("Successfully created new Group: {}", principalId);
			} else{
				createNewUser(userManager, principalId, memberOf, password, status, authorizableInstallationHistory, vf );
				LOG.info("Successfully created new User: {}", principalId);
				authorizableInstallationHistory.addNewCreatedAuthorizabe(principalId);
			}
		}

		// if current authorizable from config already exists in repository
		else{

			String message = "Problem while trying to create new authorizable: " + principalId + ". Authorizable already exists!";
			LOG.warn(message);
			Authorizable currentGroupFromRepository = userManager.getAuthorizable(principalId);


			// Set which holds all other groups which the current Group from config is a member of
			Set<String> membershipGroupsFromConfig = new HashSet<String>();
			if(memberOf != null){ // member of at least one other groups
				for(String s : memberOf){
					membershipGroupsFromConfig.add(s);
				}
			}

			Set<String> membershipGroupsFromRepository = new HashSet<String>();
			Iterator<Group> memberOfGroupsIterator = (currentGroupFromRepository).memberOf();

			// build Set which contains the all Groups of which the existingGroup is a member of

			while(memberOfGroupsIterator.hasNext()){
				Authorizable memberOfGroup = memberOfGroupsIterator.next();
				membershipGroupsFromRepository.add(memberOfGroup.getID());

			}
			
			
			
			
			// create snapshot bean
			String authorizableName = "";
			
//			// TODO: save all properties in snapshot bean (currentGroupFromRepository.getPropertyNames())
//			Iterator <String> propertyNameIt = currentGroupFromRepository.getPropertyNames("profile");
//			Map<String,Value> propertyNameSet = new HashMap<String,Value>();
//			while(propertyNameIt.hasNext()){
//				String propertyName  = propertyNameIt.next();
//				if(currentGroupFromRepository.hasProperty(propertyName)){
//				propertyNameSet.put(propertyName, currentGroupFromRepository.getProperty(propertyName)[0]);
//				}
//			}
			if(currentGroupFromRepository.getProperty("profile/givenName") != null){
				authorizableName = currentGroupFromRepository.getProperty("profile/givenName")[0].getString();
			}
			authorizableInstallationHistory.addAuthorizable(currentGroupFromRepository.getID(), authorizableName, currentGroupFromRepository.getPath(), membershipGroupsFromRepository);
			
			
			
			
			
			LOG.info("...checking differences");

			//  group in repo doesn't have any members and group in config doesn't have any members
			//  do nothing
			if(!isMemberOfOtherGroup(currentGroupFromRepository) && membershipGroupsFromConfig.isEmpty()){
				LOG.info("{}: authorizable in repo is not member of any other group and group in config is not member of any other group. No change necessary here!", principalId);
			}

			//  group in repo is not member of any other group  but group in config is member of at least one other group
			//  transfer members to group in repo

			else if(!isMemberOfOtherGroup(currentGroupFromRepository) && !membershipGroupsFromConfig.isEmpty()){
				LOG.info("{}: authorizable in repo is not member of any other group  but authorizable in config is member of at least one other group", principalId);

				for(String group : membershipGroupsFromConfig){
					LOG.info("{}: add authorizable to members of group {} in repository", principalId, group);
					Group membershipGroup = (Group)userManager.getAuthorizable(group);
					if(membershipGroup != null){
						if(StringUtils.equals(group, principalId)){
							String warning = "Attempt to add a group as member of itself (" + group + ").";
							LOG.warn(warning);
							status.addWarning(warning);
						}else{
							membershipGroup.addMember(userManager.getAuthorizable(principalId));
						}

					}else{
						LOG.warn("Group: {} doesn't yet exist. Create group!", group);
						Group newGroup = userManager.createGroup(group);
						newGroup.addMember(userManager.getAuthorizable(principalId));
					}
				}

				//  group in repo is member of at least one other group and group in config is not member of any other group

			}else if(isMemberOfOtherGroup(currentGroupFromRepository) && membershipGroupsFromConfig.isEmpty()){

				LOG.info("{}: authorizable in repo is member of at least one other group and authorizable in config is not member of any other group", principalId);
				//  delete memberOf groups of that group in repo
				for(String group : membershipGroupsFromRepository){
					LOG.info("{}: delete authorizable from members of group {} in repository", principalId, group);
					((Group)userManager.getAuthorizable(group)).removeMember(userManager.getAuthorizable(principalId));
				}
			}

			//  group in repo does have members and group in config does have members

			else if(isMemberOfOtherGroup(currentGroupFromRepository) && !membershipGroupsFromConfig.isEmpty()){
				// are both groups members of exactly the same groups?
				if(membershipGroupsFromRepository.equals(membershipGroupsFromConfig)){
					// do nothing!
					LOG.info("{}: authorizable in repo  and authorizable in config are members of the same group(s). No change necessary here!", principalId);
				}else{
					LOG.info("{}: authorizable in repo is member of at least one other group and authorizable in config is member of at least one other group", principalId);

					// loop through memberOf-groups of group from config 

					for(String group : membershipGroupsFromRepository){
						// is current group also contained in memberOf-groups property of existing group?
						if(membershipGroupsFromConfig.contains(group)){
							continue;

						}else{
							// if not delete that group of membersOf-property of existing group

							LOG.info("delete {} from members of group {} in repository", principalId, group);
							((Group)userManager.getAuthorizable(group)).removeMember(userManager.getAuthorizable(principalId));
						}
					}
					for(String group : membershipGroupsFromConfig){

						// is current group also contained in memberOf-groups property of repo group?

						if(membershipGroupsFromRepository.contains(group)){
							continue;
						}else{

							// if not add that group to membersOf-property of existing group

							LOG.info("add {} to members of group {} in repository", principalId, group);
							if(StringUtils.equals(group, principalId)){
								String warning = "Attempt to add a group as member of itself (" + group + ").";
								LOG.warn(warning);
								status.addWarning(warning);
							}else{
								((Group)userManager.getAuthorizable(group)).addMember(userManager.getAuthorizable(principalId));
							}
						}
					}
				}
			}
		}
	}

	private static boolean isMemberOfOtherGroup(Authorizable group) throws RepositoryException{
		Iterator<Group> it = group.declaredMemberOf();

		if(!it.hasNext()){
			return false;
		}
		return true;
	}



	private static void createNewGroup(final UserManager userManager, final String groupID, String name, final String[] memberOf,  AcInstallationHistoryPojo status, AuthorizableInstallationHistory authorizableInstallationHistory, ValueFactory vf )
					throws AuthorizableExistsException, RepositoryException, AuthorizableCreatorException {
		Group newGroup = null;
		if(memberOf != null && memberOf.length > 0){

			// add group to groups according to configuration
			Set<Authorizable> assignedGroups = validateAssignedGroups(userManager, groupID, memberOf, status, authorizableInstallationHistory );

			if(!assignedGroups.isEmpty()){

				try{
					newGroup = userManager.createGroup(groupID);
				}catch(AuthorizableExistsException e){
					LOG.warn("Group {} already exists in system!", groupID);
					newGroup = (Group) userManager.getAuthorizable(groupID);
				}
				LOG.info("start adding {} to assignedGroups", groupID);
				for(Authorizable authorizable : assignedGroups){

					((Group)authorizable).addMember(newGroup);
					LOG.info("added to {} ", authorizable);
				}
			}
		}
		else{
			newGroup = userManager.createGroup(groupID);
		}
		if(StringUtils.isNotBlank(name)){
			newGroup.setProperty("profile/givenName", vf.createValue(name));
		}
	}

	private static void createNewUser(final UserManager userManager, final String userID, final String[] memberOf,
			final String password,  AcInstallationHistoryPojo status, AuthorizableInstallationHistory authorizableInstallationHistory, ValueFactory vf ) throws AuthorizableExistsException,
			RepositoryException, AuthorizableCreatorException {

		if(memberOf != null && memberOf.length > 0){

			// add group to groups according to configuration
			Set<Authorizable> authorizables = validateAssignedGroups(userManager, userID, memberOf, status, authorizableInstallationHistory );

			if(!authorizables.isEmpty()){
				User newUser = userManager.createUser(userID, password);
				for(Authorizable authorizable : authorizables){
					((Group)authorizable).addMember(newUser);
				}
			}
		}
		else{
			User newUser = userManager.createUser(userID, password);
		}
	}
	/**
	 * Validates the authorizables in membersOf array of a given authorizable.Validation fails if an authorizable is a user
	 * If an authorizable contained in membersOf array doesn't exist it gets created and the current authorizable gets added as a member
	 * @param out
	 * @param userManager
	 * @param authorizablelID the ID of authorizable to validate
	 * @param memberOf String array that contains the groups which the authorizable should be a member of
	 * @return Set of authorizables which the current authorizable is a member of
	 * @throws RepositoryException
	 * @throws AuthorizableCreatorException if one of the authorizables contained in membersOf array is a user
	 */
	private static Set<Authorizable>  validateAssignedGroups(final UserManager userManager, final String authorizablelID,
			final String[] memberOf,  AcInstallationHistoryPojo status, AuthorizableInstallationHistory authorizableInstallationHistory ) throws RepositoryException, AuthorizableCreatorException {

		Set<Authorizable> authorizableSet = new HashSet<Authorizable>();
		for(String principal : memberOf){

			Authorizable authorizable = userManager.getAuthorizable(principal);

			// validation 

			// check if authorizable is existing in system
			if(authorizable != null){

				// check if authorizable is a group
				if(authorizable.isGroup()){
					authorizableSet.add(authorizable);
				}
				else{
					String message = "Failed to add authorizable " + authorizablelID + "to autorizable " + principal + "! Authorizable is not a group";
					LOG.warn(message);
					
					throw new AuthorizableCreatorException("Failed to add authorizable " + authorizablelID + "to autorizable " + principal + "! Authorizable is not a group");
				}
				// if authorizable doesn't exist yet, it gets created and the current authorizable gets added as a member
			}else{
				Authorizable newGroup = userManager.createGroup(principal);
				authorizableSet.add(newGroup);
				authorizableInstallationHistory.addNewCreatedAuthorizabe(newGroup.getID());
				LOG.warn("Failed to add group: {} to authorizable: {}. Didn't find this authorizable under /home! Created group", authorizablelID, principal);

				
			}
		}
		return authorizableSet;
	}

	public static void performRollback(SlingRepository repository, AuthorizableInstallationHistory authorizableInstallationHistory, AcInstallationHistoryPojo history) throws RepositoryException{
		Session session = repository.loginAdministrative(null);
		ValueFactory vf = session.getValueFactory();
		try{
			JackrabbitSession js = (JackrabbitSession) session;
			UserManager userManager = js.getUserManager();

			// if groups was newly created delete it
			
			Set<String> newCreatedAuthorizables = authorizableInstallationHistory.getNewCreatedAuthorizables();
			String message = "starting rollback of authorizables...";
			history.addWarning(message);
			
			if(!newCreatedAuthorizables.isEmpty()){
				history.addWarning("performing Groups rollback!");

				for(String authorizableName : newCreatedAuthorizables){
					userManager.getAuthorizable(authorizableName).remove();
					message = "removed authorizable " + authorizableName + " from the system!";
					LOG.info(message);
					history.addWarning(message);
				}
			}

			// if not compare the changes and reset them to the prevoius state
			
			Set <AuthorizableBean> authorizableBeans = authorizableInstallationHistory.getAuthorizableBeans();

			for(AuthorizableBean snapshotBean : authorizableBeans){
				Authorizable authorizable = userManager.getAuthorizable(snapshotBean.getName());

				if(authorizable != null){
					history.addMessage("found changed authorizable:" + authorizable.getID());

					// check memberOf groups
					
					Iterator <Group> it = authorizable.memberOf();
					Set<String> memberOfGroups = new HashSet<String>();
					while(it.hasNext()){
						memberOfGroups.add(it.next().getID());
					}
					
					if(snapshotBean.getAuthorizablesSnapshot().equals(memberOfGroups)){
						history.addMessage("No change found in memberOfGroups of authorizable: " + authorizable.getID());
					}else{
						history.addMessage("changes found in memberOfGroups of authorizable: " + authorizable.getID());

						// delete membership of currently set memberOf groups
						Iterator <Group> it2 = authorizable.memberOf();
						while(it2.hasNext()){
							Group group = it2.next();
							group.removeMember(authorizable);
							history.addWarning("removed authorizable: " + authorizable.getID() + " from members of group: " + group.getID());
						}
						// reset the state from snapshot bean 
						for(String group : snapshotBean.getAuthorizablesSnapshot()){
							Authorizable groupFromSnapshot = userManager.getAuthorizable(group);
							if(groupFromSnapshot != null){
								((Group)groupFromSnapshot).addMember(authorizable);
								history.addWarning("add authorizable: " + authorizable.getID() + " to members of group: " + groupFromSnapshot.getID() + " again");
							}
						}
					}
					String authorizableName = "";
					if(authorizable.hasProperty("profile/givenName") ){
					authorizableName = authorizable.getProperty("profile/givenName")[0].getString();
					}
					if(snapshotBean.getName().equals(authorizableName)){
						history.addMessage("No change found in name of authorizable: " + authorizable.getID());
					}else{
						
						history.addMessage("change found in name of authorizable: " + authorizable.getID());
						authorizable.setProperty("profile/givenName", vf.createValue(snapshotBean.getName()));
						history.addMessage("changed name of authorizable from: " + authorizableName + " back to: " + snapshotBean.getName());
					}
					// TODO: compare other properties as well (name, path,...)
				}
			}

		}finally{
			if(session != null){
				session.save();
				session.logout();
			}
		}
	}
}
