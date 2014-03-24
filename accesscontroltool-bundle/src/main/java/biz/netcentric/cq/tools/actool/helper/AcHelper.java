package biz.netcentric.cq.tools.actool.helper;


import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.ValueFormatException;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.principal.PrincipalManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.comparators.AcePermissionComparator;
import biz.netcentric.cq.tools.actool.configuration.CqActionsMapping;
import biz.netcentric.cq.tools.actool.dumpservice.Dumpservice;
import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;

public class AcHelper {
	
	private AcHelper() {}

	public static final Logger LOG = LoggerFactory.getLogger(AcHelper.class);

	public static int ACE_ORDER_DENY_ALLOW = 1;
	public static int ACE_ORDER_NONE = 2;
	public static int ACE_ORDER_ALPHABETICAL = 3;

	public static int PRINCIPAL_BASED_ORDER = 1;
	public static int PATH_BASED_ORDER = 2;

//	public static Map <String, Set<AceBean>> createPrincipalBasedAceDump(final Session session, final String[] excludePaths) throws AccessDeniedException, UnsupportedRepositoryOperationException, IllegalArgumentException, RepositoryException{
//
//		Map <String, Set<AceBean>> groupBasedAceMap = new HashMap<String, Set<AceBean>>();
//
//		Set<AclBean> aclBeanSet = DumpserviceImpl.getACLDump(session, excludePaths);
//
//
//		JackrabbitSession js = (JackrabbitSession) session;
//		PrincipalManager pm = js.getPrincipalManager();
//		Iterator<Principal> pmIter = pm.getPrincipals( PrincipalManager.SEARCH_TYPE_GROUP);
//
//		// predefine keys based on found groups and add empty sets for storing ACEs
//		while(pmIter.hasNext()){
//			Set<AceBean> aceSet = null;
//			aceSet  = new HashSet<AceBean>();
//			groupBasedAceMap.put(pmIter.next().getName(), aceSet);
//		}
//
//
//		// loop through all aclBeans and build up hash map which contains found groups as keys and set of respective ACEs as value
//		for(AclBean aclBean : aclBeanSet){
//			if(aclBean.getAcl() != null){
//				for(AccessControlEntry ace : aclBean.getAcl().getAccessControlEntries()){
//
//					if(groupBasedAceMap.get(ace.getPrincipal().getName()) != null){
//						AceWrapper tmpBean = new AceWrapper(ace, aclBean.getJcrPath());
//						AceBean tmpAceBean = getAceBean(tmpBean);
//						groupBasedAceMap.get(ace.getPrincipal().getName()).add(tmpAceBean);
//
//					}else{
//						LOG.warn("Found Principal without entry under home/groups: {}", ace.getPrincipal().getName());
//					}
//				}
//			}
//		}
//		return groupBasedAceMap;
//	}

	public static AceBean getAceBean(final AceWrapper ace) throws ValueFormatException, IllegalStateException, RepositoryException{
		AceBean aceBean = new AceBean();

		aceBean.setActions(CqActionsMapping.getCqActions(ace.getPrivilegesString()).split(","));
		aceBean.setAllow(ace.isAllow());
		aceBean.setJcrPath(ace.getJcrPath());
		aceBean.setPrincipal(ace.getPrincipal().getName());
		aceBean.setPrivilegesString(ace.getPrivilegesString());
		aceBean.setRepGlob(ace.getRestrictionAsString("rep:glob"));

		return aceBean;
	}



	/**
	 * Method which installs all ACE contained in the configurations. if an ACL is already existing in CRX the ACEs from the config get merged into the ACL (the ones from config overwrite the ones in CRX)
	 * ACEs belonging to groups which are not contained in any configuration don't get altered
	 * @param pathBasedAceMapFromConfig map containing the ACE data from the merged configurations path based
	 * @param repositoryDumpedAceMap map containing the ACL data from the repository dump, path based
	 * @param authorizablesSet set which contains all group names contained in the configurations
	 * @param session
	 * @param out 
	 * @param history history object
	 * @throws Exception
	 */

	public static void installPathBasedACEs(final Map<String, Set<AceBean>> pathBasedAceMapFromConfig, final Map<String, Set<AceBean>> repositoryDumpedAceMap, final Set<String> authorizablesSet, final Session session, final AcInstallationHistoryPojo history) throws Exception{

		Set<String> paths = pathBasedAceMapFromConfig.keySet();

		history.addVerboseMessage("found: " + paths.size() + "  paths in merged config");
		history.addVerboseMessage("found: " + authorizablesSet.size() + " authorizables in merged config");

		// counters for history output
		long aclsProcessedCounter = 0;
		long aclBeansProcessed = 0;

		// loop through all nodes from config
		for(String path : paths){

			Set<AceBean> aceBeanSetFromConfig = pathBasedAceMapFromConfig.get(path); // Set which holds the AceBeans of the current path in configuration
			Set<AceBean> aceBeanSetFromRepo = repositoryDumpedAceMap.get(path);  // Set which holds the AceBeans of the current path in dump from repository

			if(aceBeanSetFromRepo != null){
				aclBeansProcessed+=aceBeanSetFromConfig.size();
				history.addVerboseMessage("\n installing ACE: " + aceBeanSetFromConfig.toString());

				// get merged ACL
				aceBeanSetFromConfig = getMergedACL(aceBeanSetFromConfig, aceBeanSetFromRepo, authorizablesSet);

				// delete ACL in repo
				PurgeHelper.purgeAcl(session, path);
				aclsProcessedCounter++;
			}

			// remove ACL of that path from ACLs from repo so that after the loop has ended only paths are left which are not contained in current config
			repositoryDumpedAceMap.remove(path);
			resetAclInRepository(session, history, aceBeanSetFromConfig);
		}

		// loop through ACLs which are NOT contained in the configuration 

		for(Map.Entry<String, Set<AceBean>> entry : repositoryDumpedAceMap.entrySet()){
			Set<AceBean> acl = entry.getValue();
			for(AceBean aceBean : acl){

				// if the ACL form repo contains an ACE regarding an authorizable from the groups config then delete all ACEs from this authorizable from current ACL

				if(authorizablesSet.contains(aceBean.getPrincipalName())){
					AccessControlUtils.deleteAuthorizableFromACL(session, aceBean.getJcrPath(), aceBean.getPrincipalName());
					String message = "deleted all ACEs of authorizable "+aceBean.getPrincipalName()+" from ACL of path: "+ aceBean.getJcrPath();
					LOG.info(message);
					history.addVerboseMessage(message);
				}
			}
			aclsProcessedCounter++;
		}
		history.addVerboseMessage("processed: " + aclsProcessedCounter + " ACLs in total");
	}

	private static void resetAclInRepository(final Session session, final AcInstallationHistoryPojo history, final Set<AceBean> aceBeanSetFromConfig)
			throws RepositoryException, UnsupportedRepositoryOperationException {
		
		JackrabbitSession js = (JackrabbitSession) session;
		PrincipalManager pm = js.getPrincipalManager();
		
		

		// reset ACL in repo with permissions from merged ACL
		for(AceBean bean : aceBeanSetFromConfig){

			Principal currentPrincipal = pm.getPrincipal(bean.getPrincipalName());

			if(currentPrincipal == null){
				String warningMessage = "Could not find definition for authorizable " + bean.getPrincipalName() + " in groups config while installing ACE for: "+bean.getJcrPath()+"! Don't install ACEs for this authorizable!\n";
				LOG.warn(warningMessage);
				history.addWarning(warningMessage);

				continue;
			}
			else{
				history.addVerboseMessage("starting installation of bean: \n" + bean);
				// check if path exists in CRX
				if(session.itemExists(bean.getJcrPath())){
					installBean(session, history, bean, currentPrincipal);
				}else{
					String warningMessage = "path: " + bean.getJcrPath() + " doesn't exist in repository. Cancelled installation for this ACE!";
					LOG.warn(warningMessage);	
					history.addWarning(warningMessage);
					continue;
				}

			}
		}
		
		
	}

	private static void installBean(final Session session,
			final AcInstallationHistoryPojo history,
			AceBean bean, Principal currentPrincipal)
			throws RepositoryException, UnsupportedRepositoryOperationException {
		if(bean.getActions() != null){
			

			// install actions
			history.addVerboseMessage("adding action for path: "+ bean.getJcrPath() + ", principal: " + currentPrincipal.getName() + ", actions: " + bean.getActionsString() + ", permission: " + bean.getPermission());
			AccessControlUtils.addActions(session, bean, currentPrincipal,history); 

			// since CqActions.installActions() doesn't allow to set jcr:privileges and globbing, this is done in a dedicated step

			if(StringUtils.isNotBlank(bean.getRepGlob()) || StringUtils.isNotBlank(bean.getPrivilegesString())){
				AccessControlUtils.setPermissionAndRestriction(session, bean, currentPrincipal.getName());
			}

		}else{
			AccessControlUtils.installPermissions(session, bean.getJcrPath(), currentPrincipal, bean.isAllow(), bean.getRepGlob(), bean.getPrivileges());
		}
		
	}

	/**
	 * Method that merges an ACL from configuration in a ACL from CRX both having the same parent. ACEs in CRX belonging to a group which is defined in the configuration get replaced 
	 * by ACEs from the configuration. Other ACEs don't get changed.
	 * @param aclfromConfig Set containing an ACL from configuration
	 * @param aclFomRepository Set containing an ACL from repository dump
	 * @param authorizablesSet Set containing the names of all groups contained in the configurations(s)
	 * @return merged Set
	 */
	static Set<AceBean> getMergedACL(final Set<AceBean> aclfromConfig, final Set<AceBean> aclFomRepository, final Set<String> authorizablesSet) {

		// build a Set which contains all authorizable ids from the current ACL from config for the current node
		Set<String> authorizablesInAclFromConfig = new LinkedHashSet<String>();

		//Set for storage of the new ACL that'll replace the one in repo, containing ordered ACEs (denies before allows) 
		Set<AceBean> orderedMergedAceSet = new TreeSet<AceBean>(new AcePermissionComparator());

		for(AceBean aceBean : aclfromConfig){
			authorizablesInAclFromConfig.add(aceBean.getPrincipalName());
			orderedMergedAceSet.add(aceBean);
		}
		LOG.info("authorizablesInAclFromConfig: {}", authorizablesInAclFromConfig);

		// loop through the ACL from repository
		for(AceBean aceBeanFromRepository : aclFomRepository){
			// if the ACL from config doesn't contain an ACE from the current authorizable

			// if the ACL from repo contains an authorizable from the groups config but the ACL from the config does not - "delete" the respective ACE by not adding it to the orderedMergedSet
			if(!authorizablesInAclFromConfig.contains(aceBeanFromRepository.getPrincipalName()) && !authorizablesSet.contains(aceBeanFromRepository.getPrincipalName())){
				// add the ACE from repo
				orderedMergedAceSet.add(aceBeanFromRepository);
			}

		}
		return orderedMergedAceSet;
	}

	public static Map <String, Set<AceBean>> createAceMap(final SlingHttpServletRequest request, final int keyOrdering, final int aclOrdering, final String[] excludePaths, Dumpservice dumpservice) throws ValueFormatException, IllegalStateException, RepositoryException{
		Session session = request.getResourceResolver().adaptTo(Session.class);
		return dumpservice.createAclDumpMap(session, keyOrdering, aclOrdering, excludePaths);
	}
	


	/**
	 * changes a group based ACE map into a path based ACE map
	 * @param groupBasedAceMap
	 * @param sorting specifies whether ACEs get sorted by permissions (all denies followed by all allows)
	 * @return
	 */
	public static Map<String, Set<AceBean>>getPathBasedAceMap(final Map<String, Set<AceBean>> groupBasedAceMap, final int sorting){
		Map<String, Set<AceBean>> pathBasedAceMap = new HashMap<String, Set<AceBean>>(groupBasedAceMap.size());

		// loop through all Sets of groupBasedAceMap
		for (Entry<String, Set<AceBean>> entry : groupBasedAceMap.entrySet()) {
			String principal = entry.getKey();
			
			// get current Set of current principal
			Set<AceBean> tmpSet = entry.getValue();

			for(AceBean bean : tmpSet){

				// set current principal
				bean.setPrincipal(principal);

				// if there isn't already a path key in pathBasedAceMap create a new one and add new Set
				// with current ACE as first entry
				if(pathBasedAceMap.get(bean.getJcrPath()) == null){

					Set<AceBean> aceSet = null;
					if(sorting == AcHelper.ACE_ORDER_NONE){
						aceSet  = new HashSet<AceBean>();
					}
					else if (sorting == AcHelper.ACE_ORDER_DENY_ALLOW){
						aceSet  = new TreeSet<AceBean>(new biz.netcentric.cq.tools.actool.comparators.AcePermissionComparator());
					}

					aceSet.add(bean);
					pathBasedAceMap.put(bean.getJcrPath(), aceSet);
					// add current ACE to Set
				}else{
					pathBasedAceMap.get(bean.getJcrPath()).add(bean);
				}
			}
		}
		return pathBasedAceMap;
	}


	/**
	 * Method that merges several textual AccessControlConfigurations written in YAML format, each consisting of a groups and ACE configuration.
	 * Overall validation ensures that no doubled defined groups in different configuration files are possible
	 * @param session 
	 * @param newestConfigurations map which contains all paths and configuration in YAML format. key is the node path in CRX under which the respective configuration is stored, entry is the textual configuration
	 * @param history history object
	 * @return List which contains the combined groups configurations (as map holding sets of AuthorizableConfigBeans) as first element and the combined ACE configurations (as map holding sets of AceBeans) as second element
	 * @throws RepositoryException
	 */
	public static List  getMergedConfigurations(final Session session, final Map<String, String> newestConfigurations, final AcInstallationHistoryPojo history) throws RepositoryException{
		List c = new ArrayList<Map>();
		Map<String, LinkedHashSet<AuthorizableConfigBean>> mergedAuthorizablesMapfromConfig = new LinkedHashMap<String, LinkedHashSet<AuthorizableConfigBean>>();
		Map<String, Set<AceBean>> mergedAceMapFromConfig = new LinkedHashMap<String, Set<AceBean>>();
		Set<String> groupsFromAllConfig = new HashSet<String>();

		for(Map.Entry<String, String> entry : newestConfigurations.entrySet()){
			String message = "start merging configuration data from: " + entry.getKey();
			
			validateSectionIdentifiers(entry);
			
			history.addMessage(message);
			Yaml yaml = new Yaml();
			List<LinkedHashMap>  yamlList =  (List<LinkedHashMap>) yaml.load(entry.getValue());
			
			validateSectionContentExistence(entry.getKey(), yamlList);
			
			Map<String, LinkedHashSet<AuthorizableConfigBean>> authorizablesMapfromConfig = ConfigReader.getAuthorizableConfigurationBeans(yamlList);
			Set<String> groupsFromCurrentConfig = authorizablesMapfromConfig.keySet();

			validateDoubleGroups(groupsFromAllConfig, groupsFromCurrentConfig, entry.getKey());
			
			groupsFromAllConfig.addAll(groupsFromCurrentConfig);
			Map<String, Set<AceBean>> aceMapFromConfig = ConfigReader.getAceConfigurationBeans(session, yamlList, groupsFromCurrentConfig);

			mergedAuthorizablesMapfromConfig.putAll(authorizablesMapfromConfig);
			mergedAceMapFromConfig.putAll(aceMapFromConfig);
		}
		c.add(mergedAuthorizablesMapfromConfig);
		c.add(mergedAceMapFromConfig);
		return c;
	}


	/**
	 * Method that checks if a group in the current configuration file was already defined in another configuration file
	 * which has been already processed
	 * @param groupsFromAllConfig set holding all names of groups of all config files which have already been processed 
	 * @param groupsFromCurrentConfig set holding all names of the groups from the current configuration
	 * @param configPath repository path of current config
	 * @throws IllegalArgumentException
	 */
	private static void validateDoubleGroups(Set<String> groupsFromAllConfig,
			Set<String> groupsFromCurrentConfig, final String configPath) throws IllegalArgumentException{
		
		if(CollectionUtils.containsAny(groupsFromAllConfig, groupsFromCurrentConfig))  {
			String errorMessage = "already defined group: ";

			// find the name of the doubled defined group and add it to error message
			for(String group : groupsFromCurrentConfig){
				if(groupsFromAllConfig.contains(group)){
					errorMessage = errorMessage + group + " found in configuration file: " + configPath + "!";
					errorMessage = errorMessage + " This group was already defined in another configuration file on the system!";
					break;
				}
			}
			throw new IllegalArgumentException(errorMessage);
		}
	}


	/**
	 * Method that checks if both configuration sections (group and ACE) have content
	 * @param configPath repository path of current config
	 * @param yamlList list holding the group and ACE configuration as LinkedHashMap (as returned by YAML parser)
	 * @throws IllegalArgumentException
	 */
	private static void validateSectionContentExistence (
			final String configPath, List<LinkedHashMap> yamlList) throws IllegalArgumentException{
		
		if(yamlList.get(0).get(Constants.GROUP_CONFIGURATION_KEY) == null){
			throw new IllegalArgumentException("Empty group section in configuration file: " + configPath);
		}
		if(yamlList.get(1).get(Constants.ACE_CONFIGURATION_KEY) == null){
			throw new IllegalArgumentException("Empty ACE section in configuration file: " + configPath);
		}
	}
	

	/**
	 * Method that checks if both configuration section identifiers (group and ACE) exist in the current configuration file
	 * @param entry Map.Entry containing a configuration
	 * @throws IllegalArgumentException
	 */
	private static void validateSectionIdentifiers(Map.Entry<String, String> entry) throws IllegalArgumentException{
		
		if(!entry.getValue().contains(Constants.GROUP_CONFIGURATION_KEY)){
			throw new IllegalArgumentException("group section identifier ('" + Constants.GROUP_CONFIGURATION_KEY + "') missing in configuration file: " + entry.getKey());
		}
		if(!entry.getValue().contains(Constants.ACE_CONFIGURATION_KEY)){
			throw new IllegalArgumentException("ACE section identifier ('" + Constants.ACE_CONFIGURATION_KEY + "') missing in configuration file: " + entry.getKey());
		}
	}


	public static boolean isEqualBean(final AceBean bean1, final AceBean bean2){
		Set<String> bean1Privileges = new HashSet<String>(Arrays.asList(bean1.getPrivilegesString().split(",")));
		Set<String> bean2Privileges = new HashSet<String>(Arrays.asList(bean2.getPrivilegesString().split(",")));
		
		if(bean1.getJcrPath().equals(bean2.getJcrPath()) 
				&& bean1.getPrincipalName().equals(bean2.getPrincipalName())
				&& bean1.isAllow() == bean2.isAllow()
				&& bean1.getRepGlob().equals(bean2.getRepGlob())
				&& bean1.getPermission().equals(bean2.getPermission())
				&& bean1Privileges.containsAll(bean2Privileges)
				){
			return true;
		}
		return false;

	}
}




