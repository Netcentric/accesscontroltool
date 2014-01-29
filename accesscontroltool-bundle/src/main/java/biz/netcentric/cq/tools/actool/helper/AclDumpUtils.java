package biz.netcentric.cq.tools.actool.helper;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.servlet.ServletOutputStream;
import org.apache.sling.api.SlingHttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableDumpUtils;
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
		sb.append("<br /><br />");

		for(String mapKey : keys){

			Set<AceBean> aceBeanSet = aceMap.get(mapKey);

			sb.append(Constants.DUMP_INDENTATION_KEY + "- " + mapKey + ":");
			sb.append("<br />");
			for(AceBean bean : aceBeanSet){
				bean = CqActionsMapping.getAlignedPermissionBean(bean);

				sb.append("<br />");
				if(mapOrder == PATH_BASED_SORTING){
					sb.append(Constants.DUMP_INDENTATION_FIRST_PROPERTY + "- principal: " + bean.getPrincipalName()).append("<br />");
				}else if(mapOrder == PRINCIPAL_BASED_SORTING){
					sb.append(Constants.DUMP_INDENTATION_FIRST_PROPERTY + "- path: " + bean.getJcrPath()).append("<br />");
				}
				sb.append(Constants.DUMP_INDENTATION_PROPERTY + "permission: " + bean.getPermission()).append("<br />");
				sb.append(Constants.DUMP_INDENTATION_PROPERTY + "actions: " + bean.getActionsString()).append("<br />");
				sb.append(Constants.DUMP_INDENTATION_PROPERTY + "privileges: " + bean.getPrivilegesString()).append("<br />");
				sb.append(Constants.DUMP_INDENTATION_PROPERTY + "repGlob: ");
				if(!bean.getRepGlob().isEmpty()){
					sb.append("'" + bean.getRepGlob() + "'");
				}
				sb.append("<br />");


			}
			sb.append("<br />");
		}
		sb.append("<br />");



		return sb;
	}

	public static StringBuilder returnAceDiff(final StringBuilder sb, final Map<String, Set<AceBean>> aceMapFromRepository, final Map<String, Set<AceBean>> aceMapFromConfigs,Set<String> groupsFromConfigs, final int mapOrder) throws IOException{

		Set<String> aceKeysFromRepository = aceMapFromRepository.keySet();
		Set<String> aceKeysFromConfigs = aceMapFromConfigs.keySet();
		
		sb.append("<div>");
		sb.append("<div style= 'position: absolute; top: 30px; left:400px; width:50%; height: 100px;'>");
		renderLegend(sb);
		sb.append("<script language=\"JavaScript\">");
		sb.append("function resizeText(multiplier) {");
		sb.append("var elem = document.getElementById(\"aclDiff\");");
		sb.append("var currentSize = elem.style.fontSize || 1;");
		sb.append("elem.style.fontSize = ( parseFloat(currentSize) + (multiplier * 0.2)) + \"em\";");
		sb.append("}");
		sb.append("</script>");
        sb.append("<input name=\"reSize\" type=\"button\" value=\"&nbsp;+&nbsp;\" onclick=\"resizeText(1)\" />");
        sb.append("<input name=\"reSize2\" type=\"button\" value=\"&nbsp;-&nbsp;\" onclick=\"resizeText(-1)\" />");
        sb.append("<br /><br />");
        sb.append("</div>");
        
        sb.append("<div id = 'aclDiff'>");

		sb.append("- " + Constants.ACE_CONFIGURATION_KEY + ":") ;
		sb.append("<br /><br />");

		Map<String, Set <AceBeanColorWrapper>> diffMap = new TreeMap<String, Set<AceBeanColorWrapper>>();

		// loop through all mapKeys from dump
		for(String mapKey : aceKeysFromRepository){

			Set<AceBean> aceBeanSetFromRepository = aceMapFromRepository.get(mapKey);

			// if same mapKey is also existing in config
			Set<AceBean> aceBeanSetFromConfig = null;

			if(aceMapFromConfigs.get(mapKey) != null){
				aceBeanSetFromConfig = aceMapFromConfigs.get(mapKey);
			}
			// compare all ACEs in ACLs

			examineAclFromRepository(diffMap, mapKey, aceBeanSetFromRepository,
					aceBeanSetFromConfig, groupsFromConfigs);
			
			// if ACE is also contained in config
			if(aceBeanSetFromConfig != null){
				examineAclInConfig(diffMap, mapKey, aceBeanSetFromRepository,
						aceBeanSetFromConfig);
			}

		}
		
		// loop through all keys from configurations
		for(String mapKey : aceKeysFromConfigs){
			
			// if path from config is not in repository dump
			if(aceMapFromRepository.get(mapKey) == null){
				// mark every ACE of this path as orange
				if(diffMap.get(mapKey) == null){
					diffMap.put(mapKey, new HashSet <AceBeanColorWrapper>());
				}
				Set <AceBean> aceBeans = aceMapFromConfigs.get(mapKey);
				for(AceBean aceBean : aceBeans){
					diffMap.get(mapKey).add(new AceBeanColorWrapper("orange", aceBean));
					
				}
			}
		}

		
		
		// write the hole map as html
		for(Map.Entry<String, Set <AceBeanColorWrapper>> entry : diffMap.entrySet()){
			Set<AceBeanColorWrapper> aceSet = entry.getValue();
			
			sb.append(entry.getKey()); // write key, either path or group
			sb.append("<br />");
			// write ACEs
			
			for(AceBeanColorWrapper aceBean : aceSet){
				String backgroundColor = "";
				
				if(aceBean.getColor().equals("black")){
					backgroundColor = "#C8C8C8";
				}else if(aceBean.getColor().equals("red")){
					backgroundColor = "#FFAAAA";
				}else if(aceBean.getColor().equals("orange")){
					backgroundColor = "#FFEE66";
				}else if(aceBean.getColor().equals("green")){
					backgroundColor = "#CCFF99";
				}
				sb.append("<div style='background-color: " + backgroundColor + "'>");
				writeBeanAsHtml(sb, PATH_BASED_SORTING, aceBean);
				sb.append("</div>");
			}
			
		}
		sb.append("<br />");
		sb.append("</div>");
		sb.append("</div>");
		return sb;
	}

	private static void renderLegend(final StringBuilder sb) {
		sb.append(HtmlConstants.FONT_COLOR_GREEN);
		sb.append("&#9608; - contained in config and in repository");
		sb.append(HtmlConstants.FONT_COLOR_SUCCESS_HTML_CLOSE);
		sb.append("<br />");

		sb.append(HtmlConstants.FONT_COLOR_ORANGE);
		sb.append("&#9608; - contained in config and not in repository");
		sb.append(HtmlConstants.FONT_COLOR_SUCCESS_HTML_CLOSE);
		sb.append("<br />");

		sb.append(HtmlConstants.FONT_COLOR_RED);
		sb.append("&#9608; - not contained in config but in repository");
		sb.append(HtmlConstants.FONT_COLOR_SUCCESS_HTML_CLOSE);
		sb.append("<br />");

		sb.append(HtmlConstants.FONT_COLOR_BLACK);
		sb.append("&#9608; - not related to group in config");
		sb.append(HtmlConstants.FONT_COLOR_SUCCESS_HTML_CLOSE);
		sb.append("<br /><br />");
	}

	private static void examineAclInConfig(
			Map<String, Set<AceBeanColorWrapper>> diffMap, String mapKey,
			Set<AceBean> aceBeanSetFromRepository,
			Set<AceBean> aceBeanSetFromConfig) {
		for(AceBean aceBeanFromConfig : aceBeanSetFromConfig){
			boolean isEqual = false;
			// loop through ACL from repo
			for(AceBean aceBeanfromRepo2 : aceBeanSetFromRepository){
				aceBeanFromConfig = CqActionsMapping.getConvertedPrivilegeBean(aceBeanFromConfig);
				aceBeanfromRepo2 = CqActionsMapping.getConvertedPrivilegeBean(aceBeanfromRepo2);

				// if ACE is equal to one in repo
				if(AcHelper.isEqualBean(aceBeanFromConfig, aceBeanfromRepo2)){
					isEqual = true;
					break;
				}
			}
			if(!isEqual){
				diffMap.get(mapKey).add(new AceBeanColorWrapper("orange", aceBeanFromConfig));
			}
		}
	}

	private static void examineAclFromRepository(
			Map<String, Set<AceBeanColorWrapper>> diffMap, String mapKey,
			Set<AceBean> aceBeanSetFromRepository,
			Set<AceBean> aceBeanSetFromConfig, Set<String> groupsFromConfigs) {
		
		// loop through ACL from repo
		for(AceBean aceBeanfromRepo : aceBeanSetFromRepository){

			// if same path is also in config?
			if(aceBeanSetFromConfig != null){


				boolean isEqual = false;

				// loop through ACL from config and check if bean from repo is equal to a bean from config
				for(AceBean aceBeanFromConfig : aceBeanSetFromConfig){

					aceBeanFromConfig = CqActionsMapping.getConvertedPrivilegeBean(aceBeanFromConfig);
					aceBeanfromRepo = CqActionsMapping.getConvertedPrivilegeBean(aceBeanfromRepo);

					// if ACE is equal to one in repo
					if(AcHelper.isEqualBean(aceBeanFromConfig, aceBeanfromRepo)){
						isEqual = true;
						break;
					}
				}

				if(!diffMap.containsKey(mapKey)){
					diffMap.put(mapKey, new HashSet <AceBeanColorWrapper>());
				}
				String color = "";
				if(isEqual){
					color = "green";

				}else{
					color = "red";
				}
				diffMap.get(mapKey).add(new AceBeanColorWrapper(color, aceBeanfromRepo));
			}

			// if path is not in config
			else{
				if(!diffMap.containsKey(mapKey)){
					diffMap.put(mapKey, new HashSet <AceBeanColorWrapper>());
				}
				String color = "";
				if(!groupsFromConfigs.contains(aceBeanfromRepo.getPrincipalName())){
					color = "black";
				}
				else{
					color = "red";
				}
				diffMap.get(mapKey).add(new AceBeanColorWrapper(color, aceBeanfromRepo));
			}
		}
	}

	private static void writeBeanAsHtml(final StringBuilder sb, final int mapOrder, final AceBeanColorWrapper bean) {
		sb.append("<br />");
		sb.append("<font color='" + bean.getColor() + "'>");
		if(mapOrder == PATH_BASED_SORTING){
			sb.append(Constants.DUMP_INDENTATION_FIRST_PROPERTY + "- principal: " + bean.getAceBean().getPrincipalName()).append("<br />");
		}else if(mapOrder == PRINCIPAL_BASED_SORTING){
			sb.append(Constants.DUMP_INDENTATION_FIRST_PROPERTY + "- path: " + bean.getAceBean().getJcrPath()).append("<br />");
		}
		sb.append(Constants.DUMP_INDENTATION_PROPERTY + "permission: " + bean.getAceBean().getPermission()).append("<br />");
		sb.append(Constants.DUMP_INDENTATION_PROPERTY + "actions: " + bean.getAceBean().getActionsString()).append("<br />");
		sb.append(Constants.DUMP_INDENTATION_PROPERTY + "privileges: " + bean.getAceBean().getPrivilegesString()).append("<br />");
		sb.append(Constants.DUMP_INDENTATION_PROPERTY + "repGlob: ");
		if(!bean.getAceBean().getRepGlob().isEmpty()){
			sb.append("'" + bean.getAceBean().getRepGlob() + "'");
		}
		sb.append("<br />");
		sb.append("</font>");
		sb.append("<br />");
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
}
