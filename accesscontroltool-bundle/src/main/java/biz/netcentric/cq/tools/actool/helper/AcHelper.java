package biz.netcentric.cq.tools.actool.helper;


import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import javax.jcr.AccessDeniedException;
import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.ValueFormatException;
import javax.jcr.query.InvalidQueryException;
import javax.jcr.query.Query;
import javax.jcr.query.QueryResult;
import javax.jcr.security.AccessControlEntry;
import javax.jcr.security.AccessControlList;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.AccessControlPolicy;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.api.security.principal.PrincipalManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.comparators.AcePermissionComparator;
import biz.netcentric.cq.tools.actool.configuration.CqActionsMapping;
import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;

public class AcHelper {

	static final Logger LOG = LoggerFactory.getLogger(AcHelper.class);

	public static int ACE_ORDER_DENY_ALLOW = 1;
	public static int ACE_ORDER_NONE = 2;

	public static int PRINCIPAL_BASED_ORDER = 1;
	public static int PATH_BASED_ORDER = 2;

	public static Set<AclBean> getACLDump(final Session session, final String[] excludePaths) throws RepositoryException{

		List <String> excludeNodesList = Arrays.asList(excludePaths);

		// check excludePaths for existence
		for(String path : excludeNodesList){
			try {
				if(!session.itemExists(path)){
					LOG.error("Query exclude path: {} doesn't exist in repository! AccessControl installation aborted! Check exclude paths in OSGi configuration of AceService!", path );
					throw new IllegalArgumentException("Query exclude path: " + path + " doesn't exist in repository! AccessControl installation aborted! Check exclude paths in OSGi configuration of AceService!");
				}
			} catch (RepositoryException e) {
				LOG.error("RepositoryException: {}", e);
				throw e;
			}
		}

		Set<Node> resultNodeSet = getRepPolicyNodes(session, excludeNodesList);
		Set<AclBean> accessControBeanSet = new LinkedHashSet<AclBean>();

		// assemble big query result set using the query results of the child paths of jcr:root node
		for(Node node : resultNodeSet){
			try {
				accessControBeanSet.add(new AclBean(AccessControlUtils.getAccessControlList(session, node.getParent().getPath()), node.getParent().getPath()));
			} catch (AccessDeniedException e) {
				LOG.error("AccessDeniedException: {}", e);
			} catch (ItemNotFoundException e) {
				LOG.error("ItemNotFoundException: {}", e);
			} catch (RepositoryException e) {
				LOG.error("RepositoryException: {}", e);
			}
		}
		return accessControBeanSet;
	}

	public static Set<AclBean> getAuthorizablesAcls(final Session session, String authorizableId) throws InvalidQueryException, RepositoryException{
		Set<Node> nodeSet = new LinkedHashSet<Node>();
		String query =  "/jcr:root//*[(@jcr:primaryType = 'rep:GrantACE' or @jcr:primaryType = 'rep:DenyACE' ) and (@rep:principalName = '" + authorizableId + "')]";
		nodeSet.addAll(getNodes(session, query ));

		AccessControlManager aMgr = session.getAccessControlManager();
		AccessControlList acl;
		Set <AclBean> aclSet = new LinkedHashSet<AclBean>();
		for(Node node : nodeSet){
			acl = (AccessControlList) aMgr.getPolicies(node.getParent().getParent().getPath())[0];
			AclBean aclBean = new AclBean();
			aclBean.setParentPath(node.getParent().getParent().getPath());
			aclBean.setAcl((JackrabbitAccessControlList)acl);
			aclBean.setJcrPath(node.getParent().getPath());
			aclSet.add(aclBean);
		}
		return aclSet;
	}

	public static String deleteAces(final Session session, Set<AclBean> aclSet, String authorizableId) throws AccessDeniedException, PathNotFoundException, ItemNotFoundException, RepositoryException{
		AccessControlManager aMgr = session.getAccessControlManager();
		StringBuilder message = new StringBuilder();

		for(AclBean aclBean : aclSet){
			if(aclBean != null){
				for (AccessControlEntry ace : aclBean.getAcl().getAccessControlEntries()) {
					if(StringUtils.equals(ace.getPrincipal().getName(), authorizableId)){
						String parentNodePath = aclBean.getParentPath();
						String acePath = aclBean.getJcrPath();
						aclBean.getAcl().removeAccessControlEntry(ace);
						aMgr.setPolicy(aclBean.getParentPath(), aclBean.getAcl());
						LOG.info("removed ACE {} from ACL of node: {}", acePath, parentNodePath );
						message.append("removed ACE: " + acePath + " from ACL of node:" + parentNodePath  + "\n");

					}
				}
			}
		}
		return message.toString();
	}
	/**
	 * Method that returns a set containing all rep:policy nodes from repository excluding those contained in paths which are excluded from search
	 * @param session
	 * @param excludePaths paths which are excluded from search
	 * @return all rep:policy nodes delivered by query
	 */
	public static Set<Node> getRepPolicyNodes(final Session session, final List<String> excludePaths){
		NodeIterator nodeIt  = null;
		try {
			nodeIt = session.getRootNode().getNodes();
		} catch (RepositoryException e) {
			LOG.error("Exception: {}", e);
		}

		Set<String> paths = new TreeSet<String>();
		while(nodeIt.hasNext()){
			String currentPath = null;
			Node currentNode = nodeIt.nextNode();
			try {
				currentPath = currentNode.getPath();
			} catch (RepositoryException e) {
				LOG.error("Exception: {}", e);
			}


			try {
				if(!currentNode.hasProperty("rep:AuthorizableFolder")){
					if(!excludePaths.contains(currentPath)){
						paths.add(currentPath);
					}
				}
			} catch (RepositoryException e) {
				LOG.error("Exception: {}", e);
			}
		}
		Set<Node> nodes = new LinkedHashSet<Node>();
		try {
			for(String path : paths){
				String query = "/jcr:root" + path + "//*[(@jcr:primaryType = 'rep:ACL') ]";
				nodes.addAll(getNodes(session, query ));
			}
		} catch (InvalidQueryException e) {
			LOG.error("InvalidQueryException: {}",e);
		} catch (RepositoryException e) {
			LOG.error("RepositoryException: {}",e);
		}
		return nodes;
	}

	public static Set<Node> getNodes(final Session session, final String xpathQuery) throws InvalidQueryException, RepositoryException{
		Set<Node> nodes = new HashSet<Node>();

		Query query = session.getWorkspace().getQueryManager().createQuery(xpathQuery, Query.XPATH );
		QueryResult queryResult = query.execute();
		NodeIterator nit = queryResult.getNodes();
		List<String> paths = new ArrayList<String>();
		
		while (nit.hasNext()) {
			// get the next rep:policy node
			Node node = nit.nextNode();
			LOG.debug("adding node: {} to node set", node.getPath());
			paths.add(node.getPath());
			nodes.add(node);
		}
		return nodes;
	}



	public static Set <String> getUsersFromHome( final Session session) throws RepositoryException{

		Set <String> users = new TreeSet<String>();
		String queryStringUsers = "//*[(@jcr:primaryType = 'rep:User')]" ;
		Query queryUsers = session.getWorkspace().getQueryManager().createQuery(queryStringUsers, Query.XPATH);
		QueryResult queryResultUsers = queryUsers.execute();
		NodeIterator nitUsers = queryResultUsers.getNodes();
		
		while(nitUsers.hasNext()){
			Node node = nitUsers.nextNode();
			String tmp = node.getProperty( "rep:principalName").getString();
			users.add(tmp);
		}
		return users;
	}


	public static Set <String> getGroupsFromHome( final Session session) throws InvalidQueryException, RepositoryException{
		Set <String> groups = new TreeSet<String>();
		String queryStringGroups = "//*[(@jcr:primaryType = 'rep:Group')]" ;
		Query queryGroups = session.getWorkspace().getQueryManager().createQuery(queryStringGroups, Query.XPATH);
		QueryResult queryResultGroups = queryGroups.execute();
		NodeIterator nitGroups = queryResultGroups.getNodes();
		
		while(nitGroups.hasNext()){
			Node node = nitGroups.nextNode();
			String tmp = node.getProperty( "rep:principalName").getString();
			groups.add(tmp);
		}
		return groups;
	}


	public static Map <String, Set<AceBean>> createPrincipalBasedAceDump(final Session session, final String[] excludePaths) throws AccessDeniedException, UnsupportedRepositoryOperationException, IllegalArgumentException, RepositoryException{

		Map <String, Set<AceBean>> groupBasedAceMap = new HashMap<String, Set<AceBean>>();

		Set<AclBean> aclBeanSet = getACLDump(session, excludePaths);


		JackrabbitSession js = (JackrabbitSession) session;
		PrincipalManager pm = js.getPrincipalManager();
		Iterator<Principal> pmIter = pm.getPrincipals( PrincipalManager.SEARCH_TYPE_GROUP);

		// predefine keys based on found groups and add empty sets for storing ACEs
		while(pmIter.hasNext()){
			Set<AceBean> aceSet = null;
			aceSet  = new HashSet<AceBean>();
			groupBasedAceMap.put(pmIter.next().getName(), aceSet);
		}


		// loop through all aclBeans and build up hash map which contains found groups as keys and set of respective ACEs as value
		for(AclBean aclBean : aclBeanSet){
			if(aclBean.getAcl() != null){
				for(AccessControlEntry ace : aclBean.getAcl().getAccessControlEntries()){

					if(groupBasedAceMap.get(ace.getPrincipal().getName()) != null){
						AceWrapper tmpBean = new AceWrapper(ace, aclBean.getJcrPath());
						AceBean tmpAceBean = getAceBean(tmpBean);
						groupBasedAceMap.get(ace.getPrincipal().getName()).add(tmpAceBean);

					}else{
						LOG.warn("Found Principal without entry under home/groups: {}", ace.getPrincipal().getName());
					}
				}
			}
		}
		return groupBasedAceMap;
	}

	private static AceBean getAceBean(AceWrapper ace) throws ValueFormatException, IllegalStateException, RepositoryException{
		AceBean aceBean = new AceBean();

		aceBean.setActions(CqActionsMapping.getCqActions(ace.getPrivilegesString()).split(","));
		aceBean.setAllow(ace.isAllow());
		aceBean.setJcrPath(ace.getJcrPath());
		aceBean.setPrincipal(ace.getPrincipal().getName());
		aceBean.setPrivilegesString(ace.getPrivilegesString());
		aceBean.setRepGlob(ace.getRestrictionAsString("rep:glob"));

		return aceBean;
	}



	public static void purgeACLs(final ResourceResolver resourceResolver, final String[] paths) throws Exception {
		Session session = resourceResolver.adaptTo(Session.class);
		
		for (int i = 0; i < paths.length; i++) {
			if (StringUtils.isNotBlank(paths[i])) {
				String query = "/jcr:root" + paths[i].trim() + "//rep:policy";
				Iterator<Resource> results = resourceResolver.findResources(query, Query.XPATH);
				AccessControlManager accessManager = session.getAccessControlManager();
				
				while (results.hasNext()) {
					Resource res = results.next().getParent();
					if (res != null) {
						AccessControlPolicy[] policies = accessManager.getPolicies(res.getPath());
						for (int j = 0; j<policies.length; j++) {
							accessManager.removePolicy(res.getPath(), policies[j]);   
						}
						                           
					}
				}
				
			}
		}
		session.save();
		
	}

	public static String purgeACLs(final Session session, final String path) throws Exception {

		StringBuilder message = new StringBuilder();
		if (StringUtils.isNotBlank(path)) {
			String queryString = "/jcr:root" + path.trim() + "//rep:policy";
			Node startNode = session.getNode(path);
			//				Iterator<Node> results = resourceResolver.findResources(query, Query.XPATH);
			Query query = session.getWorkspace().getQueryManager().createQuery(queryString, Query.XPATH );
			QueryResult result = query.execute();
			NodeIterator nodeIterator = result.getNodes();

			AccessControlManager accessManager = session.getAccessControlManager();

			while (nodeIterator.hasNext()) {
				Node res = nodeIterator.nextNode().getParent();
				if (res != null) {
					AccessControlPolicy[] policies = accessManager.getPolicies(res.getPath());
					for (int j = 0; j<policies.length; j++) {
						accessManager.removePolicy(res.getPath(), policies[j]);   
					}
					message.append("Removed all policies from node " + res.getPath() + ".\n");

				}
			}
			message.append("\n\nCompleted removing ACLs from path: " + path + " and it's subpaths!");
		}

		session.save();

		return message.toString();
	}

	public static void purgeAcl(final Session session, final String path) throws Exception {

		if (StringUtils.isNotBlank(path)) {
			AccessControlManager accessManager = session.getAccessControlManager();
			Node node = session.getNode(path);

			AccessControlPolicy[] policies = accessManager.getPolicies(node.getPath());
			for (int i = 0; i < policies.length; i++) {
				accessManager.removePolicy(node.getPath(), policies[i]);   
				LOG.info("Removed all policies from node " + node.getPath() + ".\n");                            
			}
		}
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
	
	public static void installPathBasedACEs(final Map<String, Set<AceBean>> pathBasedAceMapFromConfig, final Map<String, Set<AceBean>> repositoryDumpedAceMap, Set<String> authorizablesSet, final Session session, final AcInstallationHistoryPojo history) throws Exception{
		long addingActionsCounter = 0;
		JackrabbitSession js = (JackrabbitSession) session;
		PrincipalManager pm = js.getPrincipalManager();

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
				purgeAcl(session, path);
				aclsProcessedCounter++;
			}

			// remove ACL of that path from ACLs from repo so that after the loop has ended only paths are left which are not contained in current config
			repositoryDumpedAceMap.remove(path);

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

					if(session.itemExists(bean.getJcrPath())){
						if(bean.getActions() != null){
							addingActionsCounter++;

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
					}else{
						String warningMessage = "path: " + bean.getJcrPath() + " doesn't exist in repository. Cancelled application for this ACL!";
						LOG.warn(warningMessage);	
						history.addWarning(warningMessage);
						continue;
					}

				}
			}
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
		history.addVerboseMessage("addingActionsCounter: " + addingActionsCounter );
	}

	/**
	 * Method that merges an ACL from configuration in a ACL from CRX both having the same parent. ACEs in CRX belonging to a group which is defined in the configuration get replaced 
	 * by ACEs from the configuration. Other ACEs don't get changed.
	 * @param aclfromConfig Set containing an ACL from configuration
	 * @param aclFomRepository Set containing an ACL from repository dump
	 * @param authorizablesSet Set containing the names of all groups contained in the configurations(s)
	 * @return merged Set
	 */
	static Set<AceBean> getMergedACL(Set<AceBean> aclfromConfig, Set<AceBean> aclFomRepository, Set<String> authorizablesSet) {

		// build a Set which contains all authorizables from the current ACL from config for the current node
		Set<String> authorizablesInAclFromConfig = new LinkedHashSet<String>();

		//Set for storage of the new ACL that'll replace the one in repo, containing ordered ACEs (denies before allows) 
		Set<AceBean> orderedMergedSet = new TreeSet<AceBean>(new AcePermissionComparator());

		for(AceBean aceBean : aclfromConfig){
			authorizablesInAclFromConfig.add(aceBean.getPrincipalName());
			orderedMergedSet.add(aceBean);
		}
		LOG.info("authorizablesInAclFromConfig: {}", authorizablesInAclFromConfig);

		// loop through the ACL from repository
		for(AceBean aceBeanFromRepository : aclFomRepository){
			// if the ACL from config doesn't contain an ACE from the current authorizable

			// if the ACL form repo contains an authorizable from the groups config but the ACL from the config does not - "delete" the respective ACE by not adding it to the orderedMergedSet
			if(!authorizablesInAclFromConfig.contains(aceBeanFromRepository.getPrincipalName()) && !authorizablesSet.contains(aceBeanFromRepository.getPrincipalName())){
				// add the ACL from repo
				orderedMergedSet.add(aceBeanFromRepository);
			}

		}
		//		aclFomRepository.removeAll(deleteSet);

		return orderedMergedSet;
	}

	public static Map <String, Set<AceBean>> createAceMap(final SlingHttpServletRequest request, final int keyOrdering, int aclOrdering, final String[] excludePaths) throws ValueFormatException, IllegalStateException, RepositoryException{
		Session session = request.getResourceResolver().adaptTo(Session.class);
		return createAclDumpMap(session, keyOrdering, aclOrdering, excludePaths);
	}
	/**
	 * returns a Map with holds either principal or path based ACE data
	 * @param request
	 * @param keyOrdering either principals (AceHelper.PRINCIPAL_BASED_ORDERING) or node paths (AceHelper.PATH_BASED_ORDERING) as keys
	 * @param aclOrdering specifies whether the allow and deny ACEs within an ACL should be divided in separate blocks (first deny then allow)
	 * @return
	 * @throws ValueFormatException
	 * @throws IllegalStateException
	 * @throws RepositoryException
	 */
	public static Map <String, Set<AceBean>> createAclDumpMap(final Session session, final int keyOrdering, final int aclOrdering, final String[] excludePaths) throws ValueFormatException, IllegalArgumentException, IllegalStateException, RepositoryException{

		Map <String, Set<AceBean>> aceMap = null;

		if(keyOrdering == PRINCIPAL_BASED_ORDER){ // principal based
			aceMap = new HashMap<String, Set<AceBean>>();
		}else if(keyOrdering == PATH_BASED_ORDER){ // path based
			aceMap = new LinkedHashMap<String, Set<AceBean>>();
		}

		Set<AclBean> aclBeanSet = getACLDump(session, excludePaths);

		// build a set containing all ACE found in the original order
		for(AclBean aclBean : aclBeanSet){
			if(aclBean.getAcl() == null){
				continue;
			}
			for(AccessControlEntry ace : aclBean.getAcl().getAccessControlEntries()){
				AceWrapper tmpBean = new AceWrapper(ace, aclBean.getJcrPath());
				AceBean tmpAceBean = getAceBean(tmpBean);

				Set<AceBean> aceSet = null;

				if(aclOrdering == ACE_ORDER_NONE){
					aceSet = new LinkedHashSet<AceBean>();
				}else if(aclOrdering == ACE_ORDER_DENY_ALLOW){
					aceSet = new TreeSet<AceBean>(new biz.netcentric.cq.tools.actool.comparators.AcePermissionComparator());
				}

				aceSet.add(tmpAceBean);

				if(keyOrdering == PRINCIPAL_BASED_ORDER){
					if(!aceMap.containsKey(tmpAceBean.getPrincipalName())){
						aceMap.put(tmpBean.getPrincipal().getName(), aceSet);
						//						checkPrincipalHomeEntry(session, tmpBean.getPrincipal().getName());
					}else{
						aceMap.get(tmpBean.getPrincipal().getName()).add(tmpAceBean);
					}
				}else if(keyOrdering == PATH_BASED_ORDER){ 
					if(!aceMap.containsKey(tmpBean.getJcrPath())){
						aceMap.put(tmpBean.getJcrPath(), aceSet);
						//						checkPrincipalHomeEntry(session, tmpBean.getPrincipal().getName());
					}else{
						aceMap.get(tmpBean.getJcrPath()).add(tmpAceBean);
					}
				}
			}
		}

		return aceMap;
	}


	public static Map <String, Set<AceBean>> getAcePlainDump(Map <String, Set<AceBean>> rawAceDump){

		for(Map.Entry<String, Set<AceBean>> entry : rawAceDump.entrySet()){
			Set aceSet = entry.getValue();
			Iterator <AceBean> it = aceSet.iterator();
			Set <AceBean> deleteSet = new HashSet <AceBean>();
			
			while(it.hasNext()){
				AceBean aceBean = it.next();
				if(StringUtils.equals(aceBean.getRepGlob(), "*/jcr:content*")){
					// action allow:create
					if(aceBean.getPrivilegesString().contains("jcr:addChildNodes") && aceBean.getPrivilegesString().contains("jcr:nodeTypeManagement")){
						deleteSet.add(aceBean);
					}
					// action allow:delete
					else if(aceBean.getPrivilegesString().contains("jcr:removeNode") && aceBean.getPrivilegesString().contains("jcr:removeChildNodes")){
						deleteSet.add(aceBean);
					}
				}
			}
			aceSet.removeAll(deleteSet);
		}
		return rawAceDump;
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
					if(sorting == 2){
						aceSet  = new HashSet<AceBean>();
					}
					else if (sorting == 1){
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
	 * @return List which contains the combined groups configurations as first element and the combined ACE configurations as second element
	 * @throws RepositoryException
	 */
	public static List  getMergedConfigurations(final Session session, Map<String, String> newestConfigurations, AcInstallationHistoryPojo history) throws RepositoryException{
		List c = new ArrayList<Map>();
		Map<String, LinkedHashSet<AuthorizableConfigBean>> mergedAuthorizablesMapfromConfig = new LinkedHashMap<String, LinkedHashSet<AuthorizableConfigBean>>();
		Map<String, Set<AceBean>> mergedAceMapFromConfig = new LinkedHashMap<String, Set<AceBean>>();
		Set<String> groupsFromAllConfig = new HashSet<String>();
		
		for(Map.Entry<String, String> entry : newestConfigurations.entrySet()){
			String message = "start merging configuration data from: " + entry.getKey();
			history.addVerboseMessage(message);
			Yaml yaml = new Yaml();
			List<LinkedHashMap>  yamlList =  (List<LinkedHashMap>) yaml.load(entry.getValue());

			Map<String, LinkedHashSet<AuthorizableConfigBean>> authorizablesMapfromConfig = ConfigReader.getAuthorizableConfigurationBeans(yamlList);
			Set<String> groupsFromCurrentConfig = authorizablesMapfromConfig.keySet();
			
				// check if any group contained in the current configuration is already contained in one of the previous ones
				// if this is the case the installation gets aborted!
				if(CollectionUtils.containsAny(groupsFromAllConfig, groupsFromCurrentConfig))  {
					String errorMessage = "double group definition found: ";
					
					// find the name of the doubled defined group and add it to error message
					for(String group : groupsFromCurrentConfig){
						if(groupsFromAllConfig.contains(group)){
							errorMessage = errorMessage + group;
							break;
						}
					}
					throw new IllegalArgumentException(errorMessage);
				
			}
			groupsFromAllConfig.addAll(groupsFromCurrentConfig);
			Map<String, Set<AceBean>> aceMapFromConfig = ConfigReader.getAceConfigurationBeans(session, yamlList, groupsFromCurrentConfig);

			mergedAuthorizablesMapfromConfig.putAll(authorizablesMapfromConfig);
			mergedAceMapFromConfig.putAll(aceMapFromConfig);
		}
		c.add(mergedAuthorizablesMapfromConfig);
		c.add(mergedAceMapFromConfig);
		return c;
	}
	
	
    public static boolean isEqualBean(AceBean bean1, AceBean bean2){
    	if(bean1.getJcrPath().equals(bean2.getJcrPath()) 
    			&& bean1.getPrincipalName().equals(bean2.getPrincipalName())
    			&& bean1.isAllow() == bean2.isAllow()
    			&& bean1.getRepGlob().equals(bean2.getRepGlob())
    			&& bean1.getPermission().equals(bean2.getPermission())
    			&& bean1.getPrivilegesString().equals(bean2.getPrivilegesString())
    			){
    		return true;
    	}
    	return false;
    	
    }
}




