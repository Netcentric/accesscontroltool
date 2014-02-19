package biz.netcentric.cq.tools.actool.helper;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.jcr.AccessDeniedException;
import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.ValueFormatException;
import javax.jcr.security.AccessControlEntry;
import javax.servlet.ServletOutputStream;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.principal.PrincipalManager;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.SlingHttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableDumpUtils;
import biz.netcentric.cq.tools.actool.comparators.AcePathComparator;
import biz.netcentric.cq.tools.actool.comparators.AcePermissionComparator;
import biz.netcentric.cq.tools.actool.configuration.CqActionsMapping;
import biz.netcentric.cq.tools.actool.installationhistory.HtmlConstants;


public class AclDumpUtils {

	private static final Logger LOG = LoggerFactory.getLogger(AclDumpUtils.class);
	public final static int PRINCIPAL_BASED_SORTING = 1;
	public final static int PATH_BASED_SORTING = 2;
	
	public final static int DENY_ALLOW_ACL_SORTING= 1;
	public final static int NO_ACL_SORTING = 2;
	

	/**
	 * returns a dump of the ACEs installed in the system using a PrintWriter.
	 * @param out PrintWriter
	 * @param aceMap map containing all ACE data, either path based or group based
	 * @param mapOrdering 
	 * @param aceOrdering
	 */
	public static void returnAceDump(final PrintWriter out, Map<String, Set<AceBean>> aceMap, final int mapOrdering, final int aceOrdering){

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

	public static void returnAceDumpAsFile(final SlingHttpServletResponse response, final Map<String, Set<AceBean>> aceMap, final int mapOrder) throws IOException{
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

	public static String returnConfigurationDumpAsString(final Map<String, Set<AceBean>> aceMap, final Set<AuthorizableConfigBean> authorizableSet, final int mapOrder) throws IOException{

		StringBuilder sb = new StringBuilder(20000);
		AuthorizableDumpUtils.getAuthorizableConfigAsString(sb, authorizableSet);
		returnAceDumpAsString(sb, aceMap, mapOrder);

		return sb.toString();
	}

	public static StringBuilder returnAceDumpAsString(final StringBuilder sb, final Map<String, Set<AceBean>> aceMap, final int mapOrder) throws IOException{

		Set<String> keys = aceMap.keySet();
		sb.append("- " + Constants.ACE_CONFIGURATION_KEY + ":") ;
		sb.append("\n\n");

		for(String mapKey : keys){

			Set<AceBean> aceBeanSet = aceMap.get(mapKey);

			sb.append(Constants.DUMP_INDENTATION_KEY + "- " + mapKey + ":");
			sb.append("\n");
			for(AceBean bean : aceBeanSet){
				bean = CqActionsMapping.getAlignedPermissionBean(bean);

				sb.append("\n");
				if(mapOrder == PATH_BASED_SORTING){
					sb.append(Constants.DUMP_INDENTATION_FIRST_PROPERTY + "- principal: " + bean.getPrincipalName()).append("\n");
				}else if(mapOrder == PRINCIPAL_BASED_SORTING){
					sb.append(Constants.DUMP_INDENTATION_FIRST_PROPERTY + "- path: " + bean.getJcrPath()).append("\n");
				}
				sb.append(Constants.DUMP_INDENTATION_PROPERTY + "permission: " + bean.getPermission()).append("\n");
				sb.append(Constants.DUMP_INDENTATION_PROPERTY + "actions: " + bean.getActionsString()).append("\n");
				sb.append(Constants.DUMP_INDENTATION_PROPERTY + "privileges: " + bean.getPrivilegesString()).append("\n");
				sb.append(Constants.DUMP_INDENTATION_PROPERTY + "repGlob: ");
				if(!bean.getRepGlob().isEmpty()){
					sb.append("'" + bean.getRepGlob() + "'");
				}
				sb.append("\n");


			}
			sb.append("\n");
		}
		sb.append("\n");
		return sb;
	}



	public static void returnConfigurationDumpAsFile(final SlingHttpServletResponse response,
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
				AuthorizableDumpUtils.writeAuthorizableConfigToStream(authorizableSet, outStream);
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


	private static ServletOutputStream writeAclConfigToStream(
			Map<String, Set<AceBean>> aceMap, final int mapOrder,
			ServletOutputStream outStream) throws IOException {
		Set<String> keys = aceMap.keySet();
		outStream.println("- " + Constants.ACE_CONFIGURATION_KEY + ":") ;
		outStream.println() ;

		for(String mapKey : keys){

			Set<AceBean> aceBeanSet = aceMap.get(mapKey);

			outStream.println(Constants.DUMP_INDENTATION_KEY + "- " + mapKey + ":");

			for(AceBean bean : aceBeanSet){
				bean = CqActionsMapping.getAlignedPermissionBean(bean);

				outStream.println();
				if(mapOrder == PATH_BASED_SORTING){
					outStream.println(Constants.DUMP_INDENTATION_FIRST_PROPERTY + "- principal: " + bean.getPrincipalName());
				}else if(mapOrder == PRINCIPAL_BASED_SORTING){
					outStream.println(Constants.DUMP_INDENTATION_FIRST_PROPERTY + "- path: " + bean.getJcrPath());
				}
				outStream.println(Constants.DUMP_INDENTATION_PROPERTY + "permission: " + bean.getPermission());
				outStream.println(Constants.DUMP_INDENTATION_PROPERTY + "actions: " + bean.getActionsString());
				outStream.println(Constants.DUMP_INDENTATION_PROPERTY + "privileges: " + bean.getPrivilegesString());
				outStream.print(Constants.DUMP_INDENTATION_PROPERTY + "repGlob: ");
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

	public static String getDumplLinks(){
		StringBuilder sb = new StringBuilder(); 
		sb.append("path based dump <a href = '" + Constants.ACE_SERVLET_PATH + "?dumpAll=true&keyOrder=pathBased&aceOrder=denyallow'> (download)</a>");
		sb.append("<br />");
		sb.append("group based dump <a href = '" + Constants.ACE_SERVLET_PATH + "?dumpAll=true&aceOrder=denyallow'> (download)</a>");

		return sb.toString();
	}

	public static Set<AclBean> getACLDump(final Session session, final String[] excludePaths) throws RepositoryException{

		List <String> excludeNodesList = Arrays.asList(excludePaths);

		// check excludePaths for existence
		for(String path : excludeNodesList){
			try {
				if(!session.itemExists(path)){
					AcHelper.LOG.error("Query exclude path: {} doesn't exist in repository! AccessControl installation aborted! Check exclude paths in OSGi configuration of AceService!", path );
					throw new IllegalArgumentException("Query exclude path: " + path + " doesn't exist in repository! AccessControl installation aborted! Check exclude paths in OSGi configuration of AceService!");
				}
			} catch (RepositoryException e) {
				AcHelper.LOG.error("RepositoryException: {}", e);
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
				AcHelper.LOG.error("AccessDeniedException: {}", e);
			} catch (ItemNotFoundException e) {
				AcHelper.LOG.error("ItemNotFoundException: {}", e);
			} catch (RepositoryException e) {
				AcHelper.LOG.error("RepositoryException: {}", e);
			}
		}
		return accessControBeanSet;
	}

	/**
	 * returns a Map with holds either principal or path based ACE data
	 * @param request
	 * @param keyOrder either principals (AceHelper.PRINCIPAL_BASED_ORDERING) or node paths (AceHelper.PATH_BASED_ORDERING) as keys
	 * @param aclOrdering specifies whether the allow and deny ACEs within an ACL should be divided in separate blocks (first deny then allow)
	 * @return
	 * @throws ValueFormatException
	 * @throws IllegalStateException
	 * @throws RepositoryException
	 */
	public static Map <String, Set<AceBean>> createAclDumpMap(final Session session, final int keyOrder, final int aclOrdering, final String[] excludePaths) throws ValueFormatException, IllegalArgumentException, IllegalStateException, RepositoryException{

		UserManager um = ((JackrabbitSession)session).getUserManager();
		Map <String, Set<AceBean>> aceMap = null;

		if(keyOrder == AcHelper.PRINCIPAL_BASED_ORDER){ // principal based
			aceMap = new TreeMap<String, Set<AceBean>>();
		}else if(keyOrder == AcHelper.PATH_BASED_ORDER){ // path based
			aceMap = new TreeMap<String, Set<AceBean>>();
		}

		Set<AclBean> aclBeanSet = getACLDump(session, excludePaths);

		// build a set containing all ACE found in the original order
		for(AclBean aclBean : aclBeanSet){
			if(aclBean.getAcl() == null){
				continue;
			}
			for(AccessControlEntry ace : aclBean.getAcl().getAccessControlEntries()){
				AceWrapper tmpBean = new AceWrapper(ace, aclBean.getJcrPath());
				AceBean tmpAceBean = AcHelper.getAceBean(tmpBean);
				CqActionsMapping.getAggregatedPrivilegesBean(tmpAceBean);
				Set<AceBean> aceSet = null;

				if(aclOrdering == AcHelper.ACE_ORDER_NONE){
					aceSet = new LinkedHashSet<AceBean>();
				}else if(aclOrdering == AcHelper.ACE_ORDER_DENY_ALLOW){
					aceSet = new TreeSet<AceBean>(new AcePermissionComparator());
				}else if(aclOrdering == AcHelper.ACE_ORDER_ALPHABETICAL){
					aceSet = new TreeSet<AceBean>(new AcePathComparator());
				}

				// only add bean if authorizable is a group
				Authorizable authorizable = um.getAuthorizable(tmpAceBean.getPrincipalName());
				
				if(authorizable != null && authorizable.isGroup()){
					aceSet.add(tmpAceBean);

					if(keyOrder == AcHelper.PRINCIPAL_BASED_ORDER){
						if(!aceMap.containsKey(tmpAceBean.getPrincipalName())){
							aceMap.put(tmpBean.getPrincipal().getName(), aceSet);
						}else{
							aceMap.get(tmpBean.getPrincipal().getName()).add(tmpAceBean);
						}
					}else if(keyOrder == AcHelper.PATH_BASED_ORDER){ 
						if(!aceMap.containsKey(tmpBean.getJcrPath())){
							aceMap.put(tmpBean.getJcrPath(), aceSet);
						}else{
							aceMap.get(tmpBean.getJcrPath()).add(tmpAceBean);
						}
					}
				}
			}
		}

		return aceMap;
	}
}
