package biz.netcentric.cq.tools.actool.aceservice;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.ValueFormatException;
import javax.jcr.query.InvalidQueryException;

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
import org.apache.sling.api.resource.ResourceResolver;
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
		immediate = true,
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
	private String[] excludePaths;


	@Activate
	public void activate(@SuppressWarnings("rawtypes") final Map properties) throws Exception {
		this.configurationPath = PropertiesUtil.toString(properties.get(PROPERTY_CONFIGURATION_PATH), "");
		this.excludePaths = PropertiesUtil.toStringArray(properties.get("AceService.queryExcludePaths"),null);
	}

	

	@Override
	public void returnAceDumpAsFile(final SlingHttpServletRequest request, final SlingHttpServletResponse response, Session session, int mapOrder, int aceOrder) {
		try {
			Map<String, Set<AceBean>> aclDumpMap = AcHelper.getCorrectedAceDump(AcHelper.createAceMap(request, mapOrder, aceOrder, this.excludePaths));
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
			Map<String, Set<AceBean>> aclDumpMap = AcHelper.getCorrectedAceDump(AcHelper.createAclDumpMap(session, aclMapKeyOrder, AcHelper.ACE_ORDER_NONE, excludePaths));
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
	

	@Override
	public void purge(ResourceResolver resourceResolver,
			String[] purgePaths) {
		try {
			AcHelper.purgeACLs(resourceResolver, purgePaths);
		} catch (Exception e) {
			LOG.error("Exception in AceServiceImpl: {}", e);
		}
	}

	

	@Override
	public void installConfigurationFromString(final List mergedConfigurations, AcInstallationHistoryPojo status, Session session, Set<AuthorizableInstallationHistory> authorizableHistorySet, Map<String, Set<AceBean>> repoDump) throws Exception {


		if(session != null){
			LOG.info("received Access Control Configuration data from string");
			installConfigurationFromYamlList(false, mergedConfigurations, status, session, authorizableHistorySet, repoDump);
		}

	}

	private void installConfigurationFromYamlList(final boolean dryRun, final List mergedConfigurations, AcInstallationHistoryPojo status, final Session session, Set<AuthorizableInstallationHistory> authorizableHistorySet, Map<String, Set<AceBean>> repositoryDumpAceMap) throws Exception  {

		Map<String, LinkedHashSet<AuthorizableConfigBean>> authorizablesMapfromConfig  = (Map<String, LinkedHashSet<AuthorizableConfigBean>>) mergedConfigurations.get(0);
		Map<String, Set<AceBean>> aceMapFromConfig = (Map<String, Set<AceBean>>) mergedConfigurations.get(1);

		if(aceMapFromConfig == null){
			String message = "ace config not found in YAML file! installation aborted!";
			LOG.error(message);
			throw new IllegalArgumentException(message);
		}

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
			AuthorizableCreator.createNewAuthorizables(authorizablesMapfromConfig, authorizableInstallationSession, status, authorizableInstallationHistory);
			authorizableInstallationSession.save();
		}catch (Exception e){
			status.setException(e.toString());
			throw e;
		}finally{
			if(authorizableInstallationSession != null){
				authorizableInstallationSession.logout();
			}
		}

		String message = "finished installation of groups configuration without errors!";
		status.addMessage(message);
		LOG.info(message);

		// --- installation of ACEs from configuration ---

		Map<String, Set<AceBean>> pathBasedAceMapFromConfig = AcHelper.getPathBasedAceMap(aceMapFromConfig, AcHelper.ACE_ORDER_DENY_ALLOW);

		LOG.info("--- start installation of access control configuration ---");

		if(repositoryDumpAceMap != null){ 
			Set<String> authorizablesSet = authorizablesMapfromConfig.keySet();
			AcHelper.installPathBasedACEs(pathBasedAceMapFromConfig, repositoryDumpAceMap, authorizablesSet, session, status);

			if(!dryRun){

				message ="finished (transient) installation of access control configuration without errors!";
				status.addMessage(message);
				LOG.info(message +"  (dryRun == false)");
			}else{
				message = "finished installation of access control configuration without errors! (dryRun == true)";
				status.addMessage(message);
				LOG.info(message);
			}
		}else{
			message = "Could not create dump of repository ACEs (null). Installation aborted!";
			status.addMessage(message);
			LOG.error(message);
		} 
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
			List mergedConfigurations = AcHelper.getMergedConfigurations(session,newestConfigurations, history);

			if(newestConfigurations != null){

				String message = "start installation of merged configurations";
				LOG.info(message);
				history.addMessage(message);

				Map<String, Set<AceBean>> repositoryDumpAceMap = null;
				LOG.info("start building dump from repository");
				repositoryDumpAceMap = AcHelper.createAclDumpMap(session, AcHelper.PATH_BASED_ORDER, AcHelper.ACE_ORDER_NONE, this.excludePaths);
				installConfigurationFromString(mergedConfigurations, history, session, authorizableInstallationHistorySet, repositoryDumpAceMap);

				// if everything went fine (no exceptions), save the session
				// thus persisting the changed ACLs
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
	 * @param path parent path in repository where one or several configurations are stored underneath
	 * @param session admin session
	 * @return set containing paths to the newest configurations
	 * @throws Exception 
	 */
	public Map<String, String> getNewestConfigurationNodes(final String path, final Session session, AcInstallationHistoryPojo history) throws Exception {
		Node configurationRootNode = null;
		Set<Node> configs = new LinkedHashSet<Node>();
		if(path.isEmpty()){
			String message = "no configuration path configured! please check the configuration of AcService!";
			LOG.error(message);
			throw new IllegalArgumentException(message);
		}
		try{
			configurationRootNode = session.getNode(path);
		}catch(RepositoryException e){
			String message = "no configuration node found specified by given path! please check the configuration of AcService!";
			history.addWarning(message);
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
				String message = "ACL root configuration node " + path + " doesn't have any children!";
				LOG.warn(message);
				history.addWarning(message);
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

	@Override
	public String purgeAuthorizable(String authorizableId) {
		Session session = null;
		String message = "";
		String message2 = "";
		try {
			try {
				session = repository.loginAdministrative(null);
				JackrabbitSession js = (JackrabbitSession) session;
				UserManager userManager = js.getUserManager();
				userManager.autoSave(false);
				PrincipalManager principalManager = js.getPrincipalManager();

				// deletion of authorizable from /home

				if(principalManager.hasPrincipal(authorizableId)){
					Authorizable authorizable = userManager.getAuthorizable(authorizableId);
					authorizable.remove();

					message = "removed authorizable: " + authorizableId + "/n";
				}else{
					message = "deletion of authorizable: " + authorizableId + " failed! Reason: authorizable doesn't exist\n" ;
				}
			} catch (RepositoryException e) {
				message = "deletion of authorizable: " + authorizableId + " failed! Reason: " + e.toString();
				LOG.error("Exception: ", e);
			}

			// deletion of all ACE of that autorizable


			try {
				Set<AclBean> nodes =  AcHelper.getAuthorizablesAcls(session, authorizableId);
				message2 =  AcHelper.deleteAces(session, nodes, authorizableId);
				session.save();
			} catch (InvalidQueryException e) {
				message2 = "\n deletion of ACEs failed! Reason: InvalidQueryException: " + e.toString() + "\n";
				e.printStackTrace();
			} catch (RepositoryException e) {
				message2 = message2 + " deletion of ACEs failed! Reason: RepositoryException: " + e.toString();
				e.printStackTrace();
			}

		}finally{
			if(session != null){
				session.logout();
			}
		}
		return message + message2;
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
	public String checkPrincipalPermissions(String principalID, String path) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String[] getExcludePaths() {
		return this.excludePaths;
	}

	@Override
	public String getConfigurationRootPath() {
		return this.configurationPath;
	}

	@Override
	public Set<String> getFoundConfigPaths() {

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


}
