package biz.netcentric.cq.tools.actool.aceservice;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.jcr.AccessDeniedException;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.ValueFormatException;
import javax.jcr.lock.LockException;
import javax.jcr.query.InvalidQueryException;
import javax.jcr.security.AccessControlEntry;
import javax.jcr.security.AccessControlException;
import javax.jcr.security.AccessControlManager;
import javax.jcr.version.VersionException;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.StopWatch;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.principal.PrincipalManager;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.jcr.api.SlingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableCreator;
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableDumpUtils;
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableInstallationHistory;
import biz.netcentric.cq.tools.actool.comparators.NodeCreatedComparator;
import biz.netcentric.cq.tools.actool.helper.AceBean;
import biz.netcentric.cq.tools.actool.helper.AcHelper;
import biz.netcentric.cq.tools.actool.helper.AclBean;
import biz.netcentric.cq.tools.actool.helper.AclDumpUtils;
import biz.netcentric.cq.tools.actool.installationhistory.AcHistoryService;
import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;


@Service

@Component(
		metatype = true,
		label = "ACE Service",
		description = "Service that installs groups & ACEs according to textual configuration files")


@Properties({
	@Property(label = "Configuration storage path", description = "enter CRX path where ACE configuration gets stored", name = AceServiceImpl.ACE_SERVICE_CONFIGURATION_PATH, value = ""),
	@Property(label = "ACL query exclude paths", name = AceServiceImpl.ACE_SERVICE_EXCLUDE_PATHS_PATH, value = {"/home", "/jcr:system", "/tmp"})
})

public class AceServiceImpl implements AceService{

	static final String ACE_SERVICE_CONFIGURATION_PATH = "AceService.configurationPath";
	static final String ACE_SERVICE_EXCLUDE_PATHS_PATH = "AceService.queryExcludePaths";

	@Reference
	private SlingRepository repository;

	@Reference
	AcHistoryService acHistoryService;

	private static final Logger LOG = LoggerFactory.getLogger(AceServiceImpl.class);
	private static final String PROPERTY_CONFIGURATION_PATH = "AceService.configurationPath";
	private boolean isExecuting = false;
	private String configurationPath;
	private String[] queryExcludePaths;


	@Activate
	public void activate(@SuppressWarnings("rawtypes") final Map properties) throws Exception {
		this.configurationPath = PropertiesUtil.toString(properties.get(PROPERTY_CONFIGURATION_PATH), "");
		this.queryExcludePaths = PropertiesUtil.toStringArray(properties.get("AceService.queryExcludePaths"),null);
	}

	

	@Override
	public void returnAceDumpAsFile(final SlingHttpServletRequest request, final SlingHttpServletResponse response, Session session, int mapOrder, int aceOrder) {
		try {
			Map<String, Set<AceBean>> aclDumpMap = AcHelper.getCorrectedAceDump(AcHelper.createAceMap(request, mapOrder, aceOrder, this.queryExcludePaths));
			AclDumpUtils.returnAceDumpAsFile(response, aclDumpMap , mapOrder);
		} catch (ValueFormatException e) {
			LOG.error("ValueFormatException in AceServiceImpl: {}", e);
		} catch (IllegalStateException e) {
			LOG.error("IllegalStateException in AceServiceImpl: {}", e);
		} catch (IOException e) {
			LOG.error("IOException in AceServiceImpl: {}", e);
		} catch (RepositoryException e) {
			LOG.error("RepositoryException in AceServiceImpl: {}", e);
		}

	}

	@Override
	public String getCompletePathBasedDumpsAsString() {
		return getCompleteDump(2,2);
	}

	@Override
	public String getCompletePrincipalBasedDumpsAsString() {
		return getCompleteDump(1,1);
	}


	private String getCompleteDump(int aclMapKeyOrder, int mapOrder){
		Session session = null;
		try {
			session = repository.loginAdministrative(null);
			Map<String, Set<AceBean>> aclDumpMap = AcHelper.getCorrectedAceDump(AcHelper.createAclDumpMap(session, aclMapKeyOrder, AcHelper.ACE_ORDER_NONE, queryExcludePaths));
			Set <String> groups = AcHelper.getGroupsFromHome(session);
			Set<AuthorizableConfigBean> authorizableBeans = AuthorizableDumpUtils.returnGroupBeans(session);

			return AclDumpUtils.returnConfigurationDumpAsString(aclDumpMap, authorizableBeans, mapOrder);
		} catch (ValueFormatException e) {
			LOG.error("ValueFormatException in AceServiceImpl: {}", e);
		} catch (IllegalStateException e) {
			LOG.error("IllegalStateException in AceServiceImpl: {}", e);
		} catch (IOException e) {
			LOG.error("IOException in AceServiceImpl: {}", e);
		} catch (RepositoryException e) {
			LOG.error("RepositoryException in AceServiceImpl: {}", e);
		}finally{
			if(session != null){
				session.logout();
			}
		}
		return null;

	}
	

	private void installConfigurationFromYamlList(final List mergedConfigurations, AcInstallationHistoryPojo history, final Session session, Set<AuthorizableInstallationHistory> authorizableHistorySet, Map<String, Set<AceBean>> repositoryDumpAceMap) throws Exception  {

		Map<String, LinkedHashSet<AuthorizableConfigBean>> authorizablesMapfromConfig  = (Map<String, LinkedHashSet<AuthorizableConfigBean>>) mergedConfigurations.get(0);
		Map<String, Set<AceBean>> aceMapFromConfig = (Map<String, Set<AceBean>>) mergedConfigurations.get(1);

		if(aceMapFromConfig == null){
			String message = "ace config not found in YAML file! installation aborted!";
			LOG.error(message);
			throw new IllegalArgumentException(message);
		}

		installAuthorizables(history, authorizableHistorySet, authorizablesMapfromConfig);
		installAces(history, session, repositoryDumpAceMap, authorizablesMapfromConfig, aceMapFromConfig); 
	}



	private void installAces(AcInstallationHistoryPojo history, final Session session,
			Map<String, Set<AceBean>> repositoryDumpAceMap,
			Map<String, LinkedHashSet<AuthorizableConfigBean>> authorizablesMapfromConfig,
			Map<String, Set<AceBean>> aceMapFromConfig) throws Exception {
		String message;
		// --- installation of ACEs from configuration ---

		Map<String, Set<AceBean>> pathBasedAceMapFromConfig = AcHelper.getPathBasedAceMap(aceMapFromConfig, AcHelper.ACE_ORDER_DENY_ALLOW);

		LOG.info("--- start installation of access control configuration ---");

		if(repositoryDumpAceMap != null){ 
			Set<String> authorizablesSet = authorizablesMapfromConfig.keySet();
			AcHelper.installPathBasedACEs(pathBasedAceMapFromConfig, repositoryDumpAceMap, authorizablesSet, session, history);
		}else{
			message = "Could not create dump of repository ACEs (null). Installation aborted!";
			history.addMessage(message);
			LOG.error(message);
		}
	}



	private void installAuthorizables(AcInstallationHistoryPojo history, Set<AuthorizableInstallationHistory> authorizableHistorySet, Map<String, LinkedHashSet<AuthorizableConfigBean>> authorizablesMapfromConfig)
			throws RepositoryException, Exception {
		// --- installation of Authorizables from configuration ---

		LOG.info("--- start installation of Authorizable Configuration ---");

		// create own session for installation of authorizables since these have to be persisted in order
		// to have the principals available when installing the ACEs

		// therefore the installation of all ACEs from all configurations uses an own session (which get passed as
		// parameter to this method), which only get saved when no exception was thrown during the installation of the ACEs

		// in case of an exception during the installation of the ACEs the performed installation of authorizables from config 
		// has to be reverted using the rollback method
		Session authorizableInstallationSession = repository.loginAdministrative(null);
		try{
			// only save session if no exceptions occured
			AuthorizableInstallationHistory authorizableInstallationHistory = new AuthorizableInstallationHistory();
			authorizableHistorySet.add(authorizableInstallationHistory);
			AuthorizableCreator.createNewAuthorizables(authorizablesMapfromConfig, authorizableInstallationSession, history, authorizableInstallationHistory);
			authorizableInstallationSession.save();
		}catch (Exception e){
			history.setException(e.toString());
			throw e;
		}finally{
			if(authorizableInstallationSession != null){
				authorizableInstallationSession.logout();
			}
		}

		String message = "finished installation of groups configuration without errors!";
		history.addMessage(message);
		LOG.info(message);
	}
	/**
	 * executes the installation of the existing configurations
	 */
	@Override
	public AcInstallationHistoryPojo execute() { 
		String path = this.getConfigurationRootPath();
		StopWatch sw = new StopWatch();
		sw.start();
		this.isExecuting = true;
		Session session = null;
		AcInstallationHistoryPojo history = new AcInstallationHistoryPojo();

		Set<AuthorizableInstallationHistory> authorizableInstallationHistorySet = new LinkedHashSet<AuthorizableInstallationHistory>();

		try {
			session = repository.loginAdministrative(null);

			Map<String, String> newestConfigurations = getNewestConfigurationNodes(path, session, history);
			List mergedConfigurations = AcHelper.getMergedConfigurations(session, newestConfigurations, history);

			if(newestConfigurations != null){

				String message = "start installation of merged configurations";
				LOG.info(message);
				history.addMessage(message);

				Map<String, Set<AceBean>> repositoryDumpAceMap = null;
				LOG.info("start building dump from repository");
				repositoryDumpAceMap = AcHelper.createAclDumpMap(session, AcHelper.PATH_BASED_ORDER, AcHelper.ACE_ORDER_NONE, this.queryExcludePaths);
				
				installConfigurationFromYamlList(mergedConfigurations, history, session, authorizableInstallationHistorySet, repositoryDumpAceMap);

				// if everything went fine (no exceptions), save the session
				// thus persisting the changed ACLs
				message ="finished (transient) installation of access control configuration without errors!";
				history.addMessage(message);
				
				session.save();
				history.addMessage("persisted changes of ACLs");


			}
		} catch (Exception e) {
			// in case an installation of an ACE configuration
			// threw an exception, logout from this session
			// otherwise changes made on the ACLs would get persisted

			session.logout();

			LOG.error("Exception in AceServiceImpl: {}", e);
			history.setException(e.toString());

			for(AuthorizableInstallationHistory authorizableInstallationHistory : authorizableInstallationHistorySet){
				try {
					String message = "performing authorizable installation rollback(s)";
					LOG.info(message);
					history.addMessage(message);
					AuthorizableCreator.performRollback(repository, authorizableInstallationHistory, history);
				} catch (RepositoryException e1) {
					LOG.error("Exception: ", e1);
				}
			}
		}finally{
			session.logout();
			sw.stop();
			long executionTime = sw.getTime();
			LOG.info("installation of AccessControlConfiguration took: {} ms", executionTime);
			history.setExecutionTime(executionTime);
			this.isExecuting = false;
			acHistoryService.persistHistory(history, this.configurationPath);

		}
		return history;
	}

	/**
	 * 
	 * @param configurationsRootPath parent path in repository where one or several configurations are stored underneath
	 * @param session admin session
	 * @return set containing paths to the newest configurations
	 * @throws Exception 
	 */
	public Map<String, String> getNewestConfigurationNodes(final String configurationsRootPath, final Session session, AcInstallationHistoryPojo history) throws Exception {
		Node configurationRootNode = null;
		Set<Node> configs = new LinkedHashSet<Node>();
		if(configurationsRootPath.isEmpty()){
			String message = "no configuration path configured! please check the configuration of AcService!";
			LOG.error(message);
			throw new IllegalArgumentException(message);
		}
		try{
			configurationRootNode = session.getNode(configurationsRootPath);
		}catch(RepositoryException e){
			String message = "no configuration node found specified by given path! please check the configuration of AcService!";
			if(history != null){
			  history.addWarning(message);
			}
			LOG.error(message);
			throw e;
		}

		if(configurationRootNode != null){
			Iterator<Node> childNodesIterator = configurationRootNode.getNodes();

			if(childNodesIterator != null){
				while(childNodesIterator.hasNext()){
					Node folderNode = childNodesIterator.next();

					if(folderNode.hasNodes()){
						Iterator<Node> configNodesIterator = folderNode.getNodes();
						// only take the newest
						Set<Node> projectConfigs = new TreeSet<Node>(new NodeCreatedComparator());
						while(configNodesIterator.hasNext()){
							projectConfigs.add(configNodesIterator.next());
						}
						if(!projectConfigs.isEmpty()){
							// add first (newest) node
							configs.add(projectConfigs.iterator().next());
						}
					}

				}
			}else{
				String message = "ACL root configuration node " + configurationsRootPath + " doesn't have any children!";
				LOG.warn(message);
				if(history != null){
			    	history.addWarning(message);
				}
				return null;
			}
			LOG.info("found following configs: ", configs);
		}

		String configData;
		Map<String,String> configurations = new LinkedHashMap<String,String>();
		for(Node configNode : configs){

			if(configNode.hasProperty("jcr:content/jcr:data")){
				configData = configNode.getProperty("jcr:content/jcr:data").getString();
				if(!configData.isEmpty()){
					LOG.info("found configuration data of node: {}", configNode.getPath());
					configurations.put(configNode.getPath(),configData);
				}else{
					LOG.warn("config data (jcr:content/jcr:data) of node: {} not found!", configNode.getPath());
				}
			}

		}
		return configurations;
	}

	@Override
	public boolean isReadyToStart() {
		String path = this.getConfigurationRootPath();
		Session session = null;
		try {
			session = repository.loginAdministrative(null);
			return !this.getNewestConfigurationNodes(path, session, new AcInstallationHistoryPojo()).isEmpty();
		} catch (Exception e) {

		}finally{
			if(session != null){
				session.logout();
			}
		}
		return false;
	}

	@Override
	public String purgeACL(String path) {
		Session session = null;
		String message = "";
		boolean flag = true;
		try {
			session = repository.loginAdministrative(null);
			AcHelper.purgeAcl(session, path);
			session.save();
		} catch (Exception e) {
			// TO DO: Logging
			flag = false;
			message = e.toString();
			LOG.error("Exception: ", e);
		}finally{
			if(session != null){
				session.logout();
			}
		}
		if(flag){
			return message = "Deleted AccessControlList of node: " + path;
		}
		return "Deletion of ACL failed! Reason:" + message;
	}

	@Override
	public String purgeACLs(String path) {
		Session session = null;
		String message = "";
		boolean flag = true;
		try {
			session = repository.loginAdministrative(null);
			message = AcHelper.purgeACLs(session, path);
			session.save();
		} catch (Exception e) {
			LOG.error("Exception: ", e);
			flag = false;
			message = e.toString();
		}finally{
			if(session != null){
				session.logout();
			}
		}
		if(flag){
			return message;
		}
		return "Deletion of ACL failed! Reason:" + message;
	}
	

	public String purgAuthorizablesFromConfig(){
		Session session = null;
		String message = "";
		try {
			session = repository.loginAdministrative(null);

			Set<String> authorizabesFromConfigurations = this.getAllAuthorizablesFromConfig(session);
			message = purgeAuthorizables(authorizabesFromConfigurations, session);
		} catch (RepositoryException e) {
			LOG.error("RepositoryException: ", e);
		} catch (Exception e) {
			LOG.error("Exception: ", e);
		}finally{
			if(session != null){
				session.logout();
			}
		}
		return message;
	}

	private String purgeAuthorizables(Set<String> authorizableIds, final Session session){

		StringBuilder message = new StringBuilder();
		String message2 = "";
		try {
			JackrabbitSession js = (JackrabbitSession) session;
			UserManager userManager = js.getUserManager();
			userManager.autoSave(false);
			PrincipalManager principalManager = js.getPrincipalManager();
			
			for(String authorizableId : authorizableIds){
				message.append(deleteAuthorizableFromHome(authorizableId,userManager, principalManager));
			}
			
			Set<AclBean> aclBeans = AcHelper.getAuthorizablesAcls(session, authorizableIds);

			message.append(AcHelper.deleteAcesFromAuthorizables(session, authorizableIds, aclBeans));
			session.save();
		} catch (RepositoryException e) {
			message2 = message2 + " deletion of ACEs failed! reason: RepositoryException: " + e.toString();
			LOG.error("RepositoryException: ", e);
		} catch (Exception e) {
			LOG.error("Exception: ", e);
		}

		return message+message2;
	}
	
	@Override
	public String purgeAuthorizable(String authorizableId) {
		Session session = null;
		String message = "";
		String message2 = "";
		try {

			session = repository.loginAdministrative(null);
			JackrabbitSession js = (JackrabbitSession) session;
			UserManager userManager = js.getUserManager();
			userManager.autoSave(false);
			PrincipalManager principalManager = js.getPrincipalManager();

			// deletion of authorizable from /home

			message = deleteAuthorizableFromHome(authorizableId,
					userManager, principalManager);

			
			// deletion of all ACE of that autorizable
			try {
				Set<AclBean> nodes =  AcHelper.getAuthorizablesAcls(session, authorizableId);
				if(!nodes.isEmpty()){
					message2 =  AcHelper.deleteAcesFromAuthorizable(session, nodes, authorizableId);
					session.save();
				}else{
					message2 = "did not find any ACEs for this principal in repository!";
				}
			} catch (InvalidQueryException e) {
				message2 = "\n deletion of ACEs failed! Reason: InvalidQueryException: " + e.toString() + "\n";
				e.printStackTrace();


			}
		} catch (RepositoryException e) {
			message2 = message2 + " deletion of ACEs failed! reason: RepositoryException: " + e.toString();
			e.printStackTrace();
		}
		finally{
			if(session != null){
				session.logout();
			}
		}
		return message + message2;
	}



	private String deleteAuthorizableFromHome(String authorizableId, UserManager userManager, PrincipalManager principalManager) {
		String message;
		if(principalManager.hasPrincipal(authorizableId)){
			Authorizable authorizable;
			try {
				authorizable = userManager.getAuthorizable(authorizableId);
				authorizable.remove();
			} catch (RepositoryException e) {
				LOG.error("RepositoryException: ", e);
			}
			message = "removed authorizable: " + authorizableId + "from /home\n";
		}else{
			message = "deletion of authorizable: " + authorizableId + " from home failed! Reason: authorizable doesn't exist\n" ;
		}
		return message;
	}

	@Override
	public void returnCompleteDumpAsFile(
			SlingHttpServletResponse response,
			Map<String, Set<AceBean>> aceMap,
			Set<AuthorizableConfigBean> authorizableSet, Session session,
			int mapOrder, int aceOrder) throws IOException {
		AclDumpUtils.returnConfigurationDumpAsFile(response, aceMap, authorizableSet, mapOrder);

	}

	@Override
	public boolean isExecuting() {
		return this.isExecuting;
	}

	

	@Override
	public String[] getQueryExcludePaths() {
		return this.queryExcludePaths;
	}

	@Override
	public String getConfigurationRootPath() {
		return this.configurationPath;
	}

	@Override
	public Set<String> getCurrentConfigurationPaths() {

		Session session = null;
		Set<String> paths = new LinkedHashSet<String>();

		try {
			session = repository.loginAdministrative(null);
			paths = this.getNewestConfigurationNodes(this.configurationPath, session, null).keySet();
		} catch (Exception e) {

		}finally{
			if(session != null){
				session.logout();
			}
		}
		return paths;
	}
	public Set<String> getAllAuthorizablesFromConfig(Session session) throws Exception{
		AcInstallationHistoryPojo history = new AcInstallationHistoryPojo();
		Map<String, String> newestConfigurations = getNewestConfigurationNodes(configurationPath, session, history);
		List mergedConfigurations = AcHelper.getMergedConfigurations(session, newestConfigurations, history);
		return ((Map<String, Set<AceBean>>) mergedConfigurations.get(0)).keySet();
	}
    

}
