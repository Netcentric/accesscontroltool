package biz.netcentric.cq.tools.actool.dumpservice;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.jcr.AccessDeniedException;
import javax.jcr.ItemExistsException;
import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.ValueFormatException;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.security.AccessControlEntry;
import javax.jcr.version.VersionException;
import javax.servlet.ServletOutputStream;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.jcr.api.SlingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.day.cq.commons.Externalizer;
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.comparators.AcePathComparator;
import biz.netcentric.cq.tools.actool.comparators.AcePermissionComparator;
import biz.netcentric.cq.tools.actool.comparators.AuthorizableBeanIDComparator;
import biz.netcentric.cq.tools.actool.comparators.JcrCreatedComparator;
import biz.netcentric.cq.tools.actool.configuration.CqActionsMapping;
import biz.netcentric.cq.tools.actool.helper.AcHelper;
import biz.netcentric.cq.tools.actool.helper.AccessControlUtils;
import biz.netcentric.cq.tools.actool.helper.AceBean;
import biz.netcentric.cq.tools.actool.helper.AceWrapper;
import biz.netcentric.cq.tools.actool.helper.AclBean;
import biz.netcentric.cq.tools.actool.helper.Constants;
import biz.netcentric.cq.tools.actool.helper.QueryHelper;
import biz.netcentric.cq.tools.actool.installationhistory.HistoryUtils;

@Service

@Component(
		metatype = true,
		label = "AC Dump Service",
		description = "Service that creates dumps of the current AC configurations (groups&ACEs)")
@Properties({

	@Property(label = "Number of dumps to save", name = DumpserviceImpl.DUMP_SERVICE_NR_OF_SAVED_DUMPS, value = "5", description = "number of last dumps which get saved in CRX under /var/statistics/achistory"),
	@Property(label = "Include user ACEs in dumps", name = DumpserviceImpl.DUMP_INCLUDE_USERS, boolValue = false, description = "if selected, also user based ACEs (and their respective users) get added to dumps"),
	@Property(label = "filtered dump", name = DumpserviceImpl.DUMP_IS_FILTERED, boolValue = true, description = "if selected, ACEs of cq actions modify, create and delete containing a repGlob get also added to dumps in section: " + Constants.USER_CONFIGURATION_KEY),
	@Property(label = "include legacy ACEs", name = DumpserviceImpl.DUMP_IS_SHOW_LEGACY_ACES, boolValue = false, description = "if selected, legacy ACEs (ACEs without group/user under /home) get also added to dumps in section: " + Constants.LEGACY_ACE_DUMP_SECTION_KEY),
	@Property(label = "AC query exclude paths", name = DumpserviceImpl.DUMP_SERVICE_EXCLUDE_PATHS_PATH, value = {"/home", "/jcr:system", "/tmp"}, description = "direct children of jcr:root which get excluded from all dumps (also from internal dumps)")
})

public class DumpserviceImpl implements Dumpservice{
	
	private static final Logger LOG = LoggerFactory.getLogger(DumpserviceImpl.class);

	private static final String DUMP_FILE_EXTENSION = ".yaml";
	private static final String DUMP_NODE_PREFIX = "dump_";
	
	public final static int PRINCIPAL_BASED_SORTING = 1;
	public final static int PATH_BASED_SORTING = 2;

	public final static int DENY_ALLOW_ACL_SORTING = 1;
	public final static int NO_ACL_SORTING = 2;

	protected static final int NR_OF_DUMPS_TO_SAVE_DEFAULT = 5;
	static final String DUMP_SERVICE_EXCLUDE_PATHS_PATH = "DumpService.queryExcludePaths";
	static final String DUMP_SERVICE_NR_OF_SAVED_DUMPS = "DumpService.nrOfSavedDumps";
	static final String DUMP_INCLUDE_USERS = "DumpService.includeUsers";
	static final String DUMP_IS_FILTERED = "DumpService.isFiltered";
	static final String DUMP_IS_SHOW_LEGACY_ACES = "DumpService.isShowLegacyAces";
	
	private String[] queryExcludePaths;
	private int nrOfSavedDumps;
	private boolean includeUsersInDumps = false;
	private boolean isFilteredDump = false;
	private boolean isShowLegacyAces = false;
	
	@Reference
	private SlingRepository repository;

	@Reference
	private ResourceResolverFactory resourceResolverFactory;

	@Activate
	public void activate(@SuppressWarnings("rawtypes") final Map properties) throws Exception {
		this.queryExcludePaths = PropertiesUtil.toStringArray(properties.get(DUMP_SERVICE_EXCLUDE_PATHS_PATH),null);
		this.nrOfSavedDumps = PropertiesUtil.toInteger(properties.get(DUMP_SERVICE_NR_OF_SAVED_DUMPS), NR_OF_DUMPS_TO_SAVE_DEFAULT);
		this.includeUsersInDumps = PropertiesUtil.toBoolean(properties.get(DUMP_INCLUDE_USERS), false);
		this.isFilteredDump = PropertiesUtil.toBoolean(properties.get(DUMP_IS_FILTERED), true);
		this.isShowLegacyAces = PropertiesUtil.toBoolean(properties.get(DUMP_IS_SHOW_LEGACY_ACES), true);
	}
	public boolean isIncludeUsers(){
		return this.includeUsersInDumps;
	}
	public boolean isShowLegacyAces(){
		return this.isShowLegacyAces;
	}
	@Override
	public String[] getQueryExcludePaths() {
		return this.queryExcludePaths;
	}
	
	@Override
	public void returnAceDumpAsFile(final SlingHttpServletRequest request, final SlingHttpServletResponse response, Session session, int mapOrder, int aceOrder) {
		try {
			Map<String, Set<AceBean>> aclDumpMap = AcHelper.createAceMap(request, mapOrder, aceOrder, this.queryExcludePaths, this);
			this.returnAceDumpAsFile(response, aclDumpMap, mapOrder);
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
		String dump = getCompleteDump(2,2);
		persistDump(dump);
		return dump;
	}

	@Override
	public String getCompletePrincipalBasedDumpsAsString() {
		String dump = getCompleteDump(1,1);
		persistDump(dump);
		return dump;
	}

	private void persistDump(String dump){
		Session session = null;
		try {
			session = repository.loginAdministrative(null);
			Node rootNode = HistoryUtils.getAcHistoryRootNode(session);
			createTransientDumpNode(dump, rootNode);
			session.save();
		} catch (RepositoryException e) {
			LOG.error("RepositoryException: {}", e);
		}finally{
			if(session != null){
				session.logout();
			}
		}
	}
	
	private void createTransientDumpNode(String dump, Node rootNode)throws ItemExistsException, PathNotFoundException, NoSuchNodeTypeException, LockException, VersionException,
	ConstraintViolationException, RepositoryException, ValueFormatException {

		NodeIterator nodeIt = rootNode.getNodes();

		// TreeSet used here since only this type offers the methods first() and last()
		TreeSet<Node> dumpNodes = new TreeSet<Node>(new JcrCreatedComparator());

		Node previousDumpNode = null;

		// get all dump nodes
		while(nodeIt.hasNext()){
			Node currNode = nodeIt.nextNode();

			if(currNode.getName().startsWith(DUMP_NODE_PREFIX)){
				dumpNodes.add(currNode);
			}
		}
		// try to get previous dump node
		if(!dumpNodes.isEmpty()){
			previousDumpNode = dumpNodes.first();
		}
		// is limit of dump nodes to save reached?
		if(dumpNodes.size() > this.nrOfSavedDumps-1){
			Node oldestDumpNode = dumpNodes.last();
			oldestDumpNode.remove();
		}
		Node dumpNode = getNewDumpNode(dump, rootNode);

		// order the newest dump node as first child node of ac root node
		if(previousDumpNode != null){
			rootNode.orderBefore(dumpNode.getName(), previousDumpNode.getName());
		}
	}
	
	private Node getNewDumpNode(String dump, Node rootNode)
			throws ItemExistsException, PathNotFoundException,
			NoSuchNodeTypeException, LockException, VersionException,
			ConstraintViolationException, RepositoryException,
			ValueFormatException {
		// create necessary child node&properties
		long timestamp = System.currentTimeMillis();
		Node dumpNode = rootNode.addNode(DUMP_NODE_PREFIX + timestamp + DUMP_FILE_EXTENSION, "nt:file");

		Node dumpJcrContenNodet = dumpNode.addNode("jcr:content", "nt:resource");
		dumpJcrContenNodet.setProperty("jcr:mimeType", "text/plain");
		dumpJcrContenNodet.setProperty("jcr:encoding", "utf-8");
		dumpJcrContenNodet.setProperty("jcr:data", dump);
		return dumpNode;
	}

	private Node getNewestDumpNode(Set<Node> dumpNodes){
		Iterator it = dumpNodes.iterator();
		Node node = null;
		while(it.hasNext()){
			node =  (Node) it.next();
		}
		return node;
	}

	@Override
	public void returnCompleteDumpAsFile(
			SlingHttpServletResponse response,
			Map<String, Set<AceBean>> aceMap,
			Set<AuthorizableConfigBean> authorizableSet, Session session,
			int mapOrder, int aceOrder) throws IOException {
		this.returnConfigurationDumpAsFile(response, aceMap, authorizableSet, mapOrder);

	}
    
	/**
	 * returns the complete AC dump (groups&ACEs) as String in YAML format
	 * @param keyOrder either principals (AceHelper.PRINCIPAL_BASED_ORDERING) or node paths (AceHelper.PATH_BASED_ORDERING) as keys
	 * @param aclOrdering specifies whether the allow and deny ACEs within an ACL should be divided in separate blocks (first deny then allow)
	 * @return String containing complete AC dump
	 */
	private String getCompleteDump(int aclMapKeyOrder, int mapOrder){
		Session session = null;
		ResourceResolver resourceResolver = null;

		try {
			session = repository.loginAdministrative(null);
			AceDumpData aceDumpData = this.createFilteredAclDumpMap(session, aclMapKeyOrder, AcHelper.ACE_ORDER_ALPHABETICAL, this.queryExcludePaths);
			Map<String, Set<AceBean>> aclDumpMap = aceDumpData.getAceDump();
			Map<String, Set<AceBean>> legacyAclDumpMap = aceDumpData.getLegacyAceDump();
			Set<AuthorizableConfigBean> groupBeans = getGroupBeans(session);
			Set<User> usersFromACEs = getUsersFromAces(mapOrder, session, aclDumpMap);
			Set<AuthorizableConfigBean> userBeans = getUserBeans(usersFromACEs);
			
			resourceResolver = this.resourceResolverFactory.getAdministrativeResourceResolver(null);
			Externalizer externalizer = resourceResolver.adaptTo(Externalizer.class);
			String serverUrl = externalizer.authorLink(resourceResolver, "");

			return this.getConfigurationDumpAsString(aceDumpData, groupBeans, userBeans, mapOrder, serverUrl);
		} catch (ValueFormatException e) {
			LOG.error("ValueFormatException in AceServiceImpl: {}", e);
		} catch (IllegalStateException e) {
			LOG.error("IllegalStateException in AceServiceImpl: {}", e);
		} catch (IOException e) {
			LOG.error("IOException in AceServiceImpl: {}", e);
		} catch (RepositoryException e) {
			LOG.error("RepositoryException in AceServiceImpl: {}", e);
		} catch (LoginException e) {
			LOG.error("LoginException in AceServiceImpl: {}", e);
		}finally{
			if(session != null){
				session.logout();
			}
			if(resourceResolver != null){
				resourceResolver.close();
			}
		}
		return null;
	}
	
    /**
     * get users from ACEs
     * @param mapOrder
     * @param session
     * @param aclDumpMap
     * @return
     * @throws AccessDeniedException
     * @throws UnsupportedRepositoryOperationException
     * @throws RepositoryException
     */
	private Set<User> getUsersFromAces(int mapOrder, Session session, Map<String, Set<AceBean>> aclDumpMap) throws AccessDeniedException,
			UnsupportedRepositoryOperationException, RepositoryException {
		
		Set<User> usersFromACEs = new HashSet<User>();
		UserManager um = ((JackrabbitSession)session).getUserManager();
		
		// if we have a principal ordered ACE map, all authorizables are contained in the keySet of the map
		if(mapOrder == PRINCIPAL_BASED_SORTING){
			Set<String> userIds = new HashSet<String>();
			userIds = aclDumpMap.keySet();
			
			for(String id : userIds){
				Authorizable authorizable = um.getAuthorizable(id);
				if(!authorizable.isGroup()){
					usersFromACEs.add((User)authorizable);
				}
			}
		// if we have a path ordered ACE map, all authorizables are contained in AceBean properties
		}else if(mapOrder == PATH_BASED_SORTING){
			for(Map.Entry<String, Set<AceBean>> entry : aclDumpMap.entrySet()){
				Set<AceBean> aceBeanSet =  entry.getValue();
				
				for(AceBean aceBean : aceBeanSet){
					String principalId = aceBean.getPrincipalName();
					Authorizable authorizable = um.getAuthorizable(principalId);
					if(!authorizable.isGroup()){
						usersFromACEs.add((User)authorizable);
					}
				}
			}
		}
		return usersFromACEs;
	}


	/**
	 * returns a dump of the ACEs installed in the system using a PrintWriter.
	 * @param out PrintWriter
	 * @param aceMap map containing all ACE data, either path based or group based
	 * @param mapOrdering 
	 * @param aceOrdering
	 */
	public void returnAceDump(final PrintWriter out, Map<String, Set<AceBean>> aceMap, final int mapOrdering, final int aceOrdering){

		if(mapOrdering == PATH_BASED_SORTING){
			LOG.debug("path based ordering required therefor getting path based ACE map");
			int aclOrder = NO_ACL_SORTING;

			if(aceOrdering == DENY_ALLOW_ACL_SORTING){
				aclOrder = DENY_ALLOW_ACL_SORTING;
			}
			aceMap = AcHelper.getPathBasedAceMap(aceMap, aclOrder) ;
		}

		Set<String> keySet = aceMap.keySet();
		for(String principal:keySet){
			Set<AceBean> aceBeanSet = aceMap.get(principal);
			out.println("- " + principal + ":");
			for(AceBean bean : aceBeanSet){
				out.println();
				out.println("   - path: " + bean.getJcrPath());
				out.println("     permission: " + bean.getPermission());
				out.println("     actions: " + bean.getActionsString());
				out.println("     privileges: " + bean.getPrivilegesString());
				out.print("     repGlob: ");
				if(!bean.getRepGlob().isEmpty()){
					out.println("'" + bean.getRepGlob() + "'");
				}else{
					out.println();
				}
			}
			out.println();
		}
		out.println();
	}

	public void returnAceDumpAsFile(final SlingHttpServletResponse response, final Map<String, Set<AceBean>> aceMap, final int mapOrder) throws IOException{
		String mimetype =  "application/octet-stream";
		response.setContentType(mimetype);
		ServletOutputStream outStream = null;
		try{
			try {
				outStream = response.getOutputStream();
			} catch (IOException e) {
				LOG.error("Exception in AclDumpUtils: {}", e);
			}

			String fileName = "ACE_Dump_" + new Date(System.currentTimeMillis());
			response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");

			try {

				writeAclConfigToStream(aceMap, mapOrder, outStream);
			} catch (IOException e) {
				LOG.error("Exception in AclDumpUtils: {}", e);
			}
		}finally{
			if(outStream != null){
				outStream.close();
			}
		}
	}

	public String getConfigurationDumpAsString(AceDumpData aceDumpData, final Set<AuthorizableConfigBean> groupSet, final Set<AuthorizableConfigBean> userSet, final int mapOrder, final String serverUrl) throws IOException{
		StringBuilder sb = new StringBuilder(20000);

		// add creation date and URL of current author instance as first line 
		String dumpComment = "Dump created: " + new Date() + " on: " + serverUrl;
		new CompleteAcDump(aceDumpData, groupSet, userSet, mapOrder, dumpComment, this).accept(new AcDumpElementYamlVisitor(mapOrder, sb));
		return sb.toString();
	}


	public void returnConfigurationDumpAsFile(final SlingHttpServletResponse response,
			Map<String, Set<AceBean>> aceMap, Set<AuthorizableConfigBean> authorizableSet, final int mapOrder) throws IOException{

		String mimetype =  "application/octet-stream";
		response.setContentType(mimetype);
		ServletOutputStream outStream = null;
		try{
			try {
				outStream = response.getOutputStream();
			} catch (IOException e) {
				LOG.error("Exception in AclDumpUtils: {}", e);
			}

			String fileName = "ACL_Configuration_Dump_" + new Date(System.currentTimeMillis());
			response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");

			try {
				writeAuthorizableConfigToStream(authorizableSet, outStream);
				outStream.println() ;
				writeAclConfigToStream(aceMap, mapOrder, outStream);
			} catch (IOException e) {
				LOG.error("Exception in AclDumpUtils: {}", e);
			}
		}finally{
			if(outStream != null){
				outStream.close();
			}
		}
	}


	private ServletOutputStream writeAclConfigToStream(Map<String, Set<AceBean>> aceMap, final int mapOrder,ServletOutputStream outStream) throws IOException {
		Set<String> keys = aceMap.keySet();
		outStream.println("- " + Constants.ACE_CONFIGURATION_KEY + ":") ;
		outStream.println() ;

		for(String mapKey : keys){

			Set<AceBean> aceBeanSet = aceMap.get(mapKey);

			outStream.println(AcHelper.getBlankString(AcDumpElementYamlVisitor.DUMP_INDENTATION_KEY) + "- " + mapKey + ":");

			for(AceBean bean : aceBeanSet){
				bean = CqActionsMapping.getAlignedPermissionBean(bean);

				outStream.println();
				if(mapOrder == PATH_BASED_SORTING){
					outStream.println(AcHelper.getBlankString(AcDumpElementYamlVisitor.DUMP_INDENTATION_FIRST_PROPERTY) + "- principal: " + bean.getPrincipalName());
				}else if(mapOrder == PRINCIPAL_BASED_SORTING){
					outStream.println(AcHelper.getBlankString(AcDumpElementYamlVisitor.DUMP_INDENTATION_FIRST_PROPERTY) + "- path: " + bean.getJcrPath());
				}
				outStream.println(AcHelper.getBlankString(AcDumpElementYamlVisitor.DUMP_INDENTATION_PROPERTY ) + "permission: " + bean.getPermission());
				outStream.println(AcHelper.getBlankString(AcDumpElementYamlVisitor.DUMP_INDENTATION_PROPERTY ) + "actions: " + bean.getActionsString());
				outStream.println(AcHelper.getBlankString(AcDumpElementYamlVisitor.DUMP_INDENTATION_PROPERTY ) + "privileges: " + bean.getPrivilegesString());
				outStream.print(AcHelper.getBlankString(AcDumpElementYamlVisitor.DUMP_INDENTATION_PROPERTY ) + "repGlob: ");
				if(!bean.getRepGlob().isEmpty()){
					outStream.println("'" + bean.getRepGlob() + "'");
				}else{
					outStream.println();
				}

			}

			outStream.println();
		}
		outStream.println();
		return outStream;
	}

	public String getDumplLinks(){
		StringBuilder sb = new StringBuilder(); 
		sb.append("path based dump <a href = '" + Constants.ACE_SERVLET_PATH + "?dumpAll=true&keyOrder=pathBased&aceOrder=denyallow'> (download)</a>");
		sb.append("<br />");
		sb.append("group based dump <a href = '" + Constants.ACE_SERVLET_PATH + "?dumpAll=true&aceOrder=denyallow'> (download)</a>");

		return sb.toString();
	}

	public Set<AclBean> getACLDumpBeans(final Session session) throws RepositoryException{

		List <String> excludeNodesList = Arrays.asList(this.queryExcludePaths);

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

		Set<Node> resultNodeSet = QueryHelper.getRepPolicyNodes(session, excludeNodesList);
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
	public AceDumpData createFilteredAclDumpMap(final Session session, final int keyOrder, final int aclOrdering, final String[] excludePaths) throws ValueFormatException, IllegalArgumentException, IllegalStateException, RepositoryException{
		return createAclDumpMap(session, keyOrder, aclOrdering, excludePaths, this.isFilteredDump, this.includeUsersInDumps);
	}
    public AceDumpData createUnfilteredAclDumpMap(final Session session, final int keyOrder, final int aclOrdering, final String[] excludePaths) throws ValueFormatException, IllegalArgumentException, IllegalStateException, RepositoryException{
    	return createAclDumpMap(session, keyOrder, aclOrdering, excludePaths, false, true);
	}
    
	/**
	 * returns a Map with holds either principal or path based ACE data
	 * @param request
	 * @param keyOrder either principals (AceHelper.PRINCIPAL_BASED_ORDERING) or node paths (AceHelper.PATH_BASED_ORDERING) as keys
	 * @param aclOrdering specifies whether the allow and deny ACEs within an ACL should be divided in separate blocks (first deny then allow)
	 * @param isFilterACEs
	 * @param isIncludeUsers
	 * @return
	 * @throws ValueFormatException
	 * @throws IllegalStateException
	 * @throws RepositoryException
	 */
	private AceDumpData createAclDumpMap(final Session session, final int keyOrder, final int aclOrdering, final String[] excludePaths, final boolean isFilterACEs, final boolean isIncludeUsers) throws ValueFormatException, IllegalArgumentException, IllegalStateException, RepositoryException{
		AceDumpData aceDumpData = new AceDumpData();
		UserManager um = ((JackrabbitSession)session).getUserManager();
		Map <String, Set<AceBean>> aceMap = null;
		Map <String, Set<AceBean>> legacyAceMap = new TreeMap<String, Set<AceBean>>();

		if(keyOrder == AcHelper.PRINCIPAL_BASED_ORDER){ // principal based
			aceMap = new TreeMap<String, Set<AceBean>>();
		}else if(keyOrder == AcHelper.PATH_BASED_ORDER){ // path based
			aceMap = new TreeMap<String, Set<AceBean>>();
		}

		Set<AclBean> aclBeanSet = getACLDumpBeans(session);

		// build a set containing all ACE found in the original order
		for(AclBean aclBean : aclBeanSet){
			
			if(aclBean.getAcl() == null){
				continue;
			}
			
			for(AccessControlEntry ace : aclBean.getAcl().getAccessControlEntries()){
				AceWrapper tmpBean = new AceWrapper(ace, aclBean.getJcrPath());
				AceBean tmpAceBean = AcHelper.getAceBean(tmpBean);
				CqActionsMapping.getAggregatedPrivilegesBean(tmpAceBean);

				if(isUnwantedAce(tmpAceBean) && isFilterACEs){
					continue;
				}

				Authorizable authorizable = um.getAuthorizable(tmpAceBean.getPrincipalName());

				// if this group exists under home
				if(authorizable != null){
					if(authorizable.isGroup() || isIncludeUsers){
						addBeanToMap(keyOrder, aclOrdering, aceMap, tmpAceBean);
					}
				}
				// otherwise put in map holding legacy ACEs
				else{
					addBeanToMap(keyOrder, aclOrdering, legacyAceMap, tmpAceBean);
				}
			}
		}
		aceDumpData.setAceDump(aceMap);
		aceDumpData.setLegacyAceDump(legacyAceMap);
		
		return aceDumpData;
	}

	private void addBeanToMap(final int keyOrder, final int aclOrdering, Map<String, Set<AceBean>> aceMap, AceBean aceBean) {
		if(keyOrder == AcHelper.PRINCIPAL_BASED_ORDER){
			String principalName = aceBean.getPrincipalName();
			if(!aceMap.containsKey(principalName)){
				Set<AceBean> aceSet = getNewAceSet(aclOrdering);
				aceSet.add(aceBean);
				aceMap.put(principalName, aceSet);
			}else{
				aceMap.get(principalName).add(aceBean);
			}
		}else if(keyOrder == AcHelper.PATH_BASED_ORDER){ 
			String jcrPath = aceBean.getJcrPath();
			if(!aceMap.containsKey(jcrPath)){
				Set<AceBean> aceSet = getNewAceSet(aclOrdering);
				aceSet.add(aceBean);
				aceMap.put(jcrPath, aceSet);
			}else{
				aceMap.get(jcrPath).add(aceBean);
			}
		}
	}


	/**
	 * removes the name of the group node itself (groupID) from the intermediate path 
	 * @param intermediatePath 
	 * @param groupID
	 * @return corrected path if groupID was found at the end of the intermediatePath, otherwise original path
	 */
	private String getIntermediatePath(String intermediatePath, final String groupID){
		int index = StringUtils.lastIndexOf(intermediatePath, "/"+ groupID);
		if(index != -1){
			intermediatePath = intermediatePath.replace(intermediatePath.substring(index), "");
		}
		return intermediatePath;
	}

	public void returnAuthorizableDumpAsFile(final SlingHttpServletResponse response,
			Set<AuthorizableConfigBean> authorizableSet) throws IOException{
		String mimetype =  "application/octet-stream";
		response.setContentType(mimetype);
		ServletOutputStream outStream = null;
		try {
			outStream = response.getOutputStream();
		} catch (IOException e) {
			LOG.error("Exception in AuthorizableDumpUtils: {}", e);
		}
	
		String fileName = "Authorizable_Dump_" + new Date(System.currentTimeMillis());
		response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
	
		byte[] byteBuffer = new byte[4096];
	
		try {
			writeAuthorizableConfigToStream(authorizableSet, outStream);
		} catch (IOException e) {
			LOG.error("Exception in AuthorizableDumpUtils: {}", e);
		}
		outStream.close();
	}

	public void writeAuthorizableConfigToStream(Set<AuthorizableConfigBean> authorizableSet, ServletOutputStream outStream) throws IOException {
	
		outStream.println("- " + Constants.GROUP_CONFIGURATION_KEY + ":") ;
		outStream.println();
	
		for(AuthorizableConfigBean bean:authorizableSet){
			outStream.println(AcHelper.getBlankString(AcDumpElementYamlVisitor.DUMP_INDENTATION_KEY) + "- " + bean.getPrincipalID() + ":");
			outStream.println();
			outStream.println(AcHelper.getBlankString(AcDumpElementYamlVisitor.DUMP_INDENTATION_FIRST_PROPERTY) + "- name: ");
			outStream.println(AcHelper.getBlankString(AcDumpElementYamlVisitor.DUMP_INDENTATION_PROPERTY) + "memberOf: " + bean.getMemberOfString());
			outStream.println(AcHelper.getBlankString(AcDumpElementYamlVisitor.DUMP_INDENTATION_PROPERTY) + "path: " + bean.getPath());
			outStream.println(AcHelper.getBlankString(AcDumpElementYamlVisitor.DUMP_INDENTATION_PROPERTY) + "isGroup: " + "'" + bean.isGroup() + "'");
			outStream.println();
		}
	}

	/**
	 * method that returns the data of users contained in a set as AuthorizableConfigBeans
	 * @param usersFromACEs set holding users
	 * @return set holding AuthorizableConfigBeans
	 * @throws RepositoryException
	 * @throws UnsupportedRepositoryOperationException
	 */
	public Set<AuthorizableConfigBean> getUserBeans(Set<User> usersFromACEs) throws RepositoryException, UnsupportedRepositoryOperationException {
	
		Set<AuthorizableConfigBean> userBeans = new TreeSet<AuthorizableConfigBean>(new AuthorizableBeanIDComparator());
	
		// add found users from ACEs to set of authorizables
		if(!usersFromACEs.isEmpty()){
			for(User user : usersFromACEs){
				AuthorizableConfigBean newBean = new AuthorizableConfigBean();
				newBean.setPrincipalID(user.getID());
				String intermediatePath = getIntermediatePath(user.getPath(), user.getID());
				newBean.setPath(intermediatePath);
				newBean.setIsGroup(false);
				Set<Authorizable> memberOf = new HashSet<Authorizable>();
				Iterator<Group> it = user.declaredMemberOf();
	
				while(it.hasNext()){
					memberOf.add(it.next());
				}
	
				for(Authorizable membershipGroup : memberOf){
					List<String> memberOfList = new ArrayList<String>();
					memberOfList.add(membershipGroup.getID());
					newBean.setMemberOf(memberOfList);
				}
				userBeans.add(newBean);
			}
		}
		return userBeans;
	}

	/**
	 * method that fetches all groups from /home and returns their data encapsulated in AuthorizableConfigBeans
	 * @param session session with sufficient rights to read group informations
	 * @return set holding AuthorizableConfigBeans
	 * @throws AccessDeniedException
	 * @throws UnsupportedRepositoryOperationException
	 * @throws RepositoryException
	 */
	public Set<AuthorizableConfigBean> getGroupBeans(Session session) throws AccessDeniedException, UnsupportedRepositoryOperationException, RepositoryException{
		JackrabbitSession js = (JackrabbitSession) session;
		UserManager userManager = js.getUserManager();
	
		Set <String> groups = QueryHelper.getGroupsFromHome(session);
		Set <AuthorizableConfigBean> groupBeans = new LinkedHashSet<AuthorizableConfigBean>();
	
		for(String groupId : groups){
	
			Group group = (Group)userManager.getAuthorizable(groupId);
			AuthorizableConfigBean bean = new AuthorizableConfigBean();
	
			bean.setPrincipalID(group.getID());
			Iterator <Group> it = group.declaredMemberOf();
			List<String> memberOfList = new ArrayList<String>();
	
			while(it.hasNext()){
				memberOfList.add(it.next().getID());
			}
			bean.setMemberOf(memberOfList.toArray(new String[memberOfList.size()]));
			bean.setIsGroup(group.isGroup());
			bean.setPath(getIntermediatePath(group.getPath(),group.getID()));
	
			groupBeans.add(bean);
		}
		return groupBeans;
	
	}

	/**
	 * Method that checks if passed ACE is one of the 2 ACEs which belong to the cq actions "create", "delete" and "modify" in an AceDump containing a repGlob. Reason is 
	 * the fact that each of these 2 actions use 2 identical ACEs. one with an additional repGlob and one without. In a dump we only want to have one ACE
	 * for each of these actions without a repGlob.
	 * @param aceBean bean holding the properties of an ACE
	 * @return true if ace belongs to one of the 3 actions
	 */
	private boolean isUnwantedAce(final AceBean aceBean){
		boolean ret = false;
		List <String> tmpList = null;
		if(StringUtils.equals(aceBean.getRepGlob(), "*/jcr:content*")){
			tmpList = new ArrayList<String>(Arrays.asList(aceBean.getPrivilegesString().split(",")));

			// set when action allow/deny:create is used
			if(tmpList.containsAll(CqActionsMapping.map.get("create")) && tmpList.size() == CqActionsMapping.map.get("create").size()){
				ret =  true;
			}
			// set when action allow/deny:delete is used
			else if(tmpList.containsAll(CqActionsMapping.map.get("delete")) && tmpList.size() == CqActionsMapping.map.get("delete").size()){	
				ret = true;
			}
			// set when  action allow/deny:modify and allow create,delete and allow modify,create,delete are used
			else if(tmpList.containsAll(CqActionsMapping.map.get("create")) && tmpList.containsAll(CqActionsMapping.map.get("delete")) && tmpList.size() == (CqActionsMapping.map.get("create").size() + CqActionsMapping.map.get("delete").size())){	
				ret = true;
			}
		}
		return ret;
	}

	private Set<AceBean> getNewAceSet(final int aclOrdering) {
		Set<AceBean> aceSet = null;

		if(aclOrdering == AcHelper.ACE_ORDER_NONE){
			aceSet = new LinkedHashSet<AceBean>();
		}else if(aclOrdering == AcHelper.ACE_ORDER_DENY_ALLOW){
			aceSet = new TreeSet<AceBean>(new AcePermissionComparator());
		}else if(aclOrdering == AcHelper.ACE_ORDER_ALPHABETICAL){
			aceSet = new TreeSet<AceBean>(new AcePathComparator());
		}
		return aceSet;
	}
}
