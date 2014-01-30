package biz.netcentric.cq.tools.actool.configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.security.Privilege;

import org.apache.commons.lang.StringUtils;

import biz.netcentric.cq.tools.actool.helper.AceBean; 


public class CqActionsMapping {
	
	

	static public Map<String, List <String>> map = new HashMap <String, List <String>>();
	static List <String> repWriteList = new ArrayList<String>(Arrays.asList(new String[] {"rep:write"}));
	static List <String> jcrAllList = new ArrayList<String>(Arrays.asList(new String[] {"jcr:all"}));
	static List <String> jcrWriteList = new ArrayList<String>(Arrays.asList(new String[] {"jcr:write"}));
	static { 
		map.put("rep:write", new ArrayList<String>( Arrays.asList(new String[] { "jcr:modifyProperties","jcr:addChildNodes","jcr:removeNode","jcr:removeChildNodes","jcr:nodeTypeManagement"})));
		map.put("jcr:write", new ArrayList<String>( Arrays.asList(new String[] { "jcr:addChildNodes","jcr:removeNode","jcr:modifyProperties","jcr:removeChildNodes"})));
		map.put("jcr:all", new ArrayList<String>( Arrays.asList(new String[] { "jcr:workspaceManagement",
				"jcr:lifecycleManagement",
				"jcr:versionManagement",
				"jcr:lockManagement",
				"crx:replicate",
				"jcr:read",
				"jcr:modifyAccessControl",
				"rep:write",
				"rep:privilegeManagement",
				"jcr:nodeTypeManagement",
				"jcr:namespaceManagement",
				"jcr:write",
				"jcr:nodeTypeDefinitionManagement",
				"jcr:retentionManagement",
				"jcr:readAccessControl"})));

		map.put("read",new ArrayList<String>( Arrays.asList(new String[] {"jcr:read"})));
		map.put("modify", new ArrayList<String>( Arrays.asList(new String[] { "jcr:modifyProperties", "jcr:lockManagement", "jcr:versionManagement" })));
		map.put("create", new ArrayList<String>( Arrays.asList(new String[] { "jcr:addChildNodes", "jcr:nodeTypeManagement" })));
		map.put("delete", new ArrayList<String>( Arrays.asList(new String[] { "jcr:removeChildNodes", "jcr:removeNode" })));
		map.put("acl_read", new ArrayList<String>( Arrays.asList(new String[] { "jcr:readAccessControl"})));
		map.put("acl_edit", new ArrayList<String>( Arrays.asList(new String[] { "jcr:modifyAccessControl"})));
		map.put("replicate", new ArrayList<String>( Arrays.asList(new String[] { "crx:replicate"})));

	}
	
	public static String getCqActions(final Privilege[] jcrPrivileges){
		StringBuilder sb = new StringBuilder(); 
		for(Privilege p : jcrPrivileges){
			sb.append(p.getName()).append(",");
		}
		return getCqActions(StringUtils.chomp(sb.toString()));
	}
	
	public static String getCqActions(final String[] jcrPrivileges){
		StringBuilder sb = new StringBuilder(); 
		for(String s : jcrPrivileges){
			sb.append(s).append(",");
		}
		return getCqActions(StringUtils.chomp(sb.toString()));
	}

	/**
	 * method that converts jcr:privileges to cq actions 
	 * @param jcrPrivilegesString comma separated String containing jcr:privileges
	 * @return comma separated String containing the assigned cq actions
	 */
	public static String getCqActions(final String jcrPrivilegesString){
		List <String> jcrPrivileges = new ArrayList<String>(Arrays.asList(jcrPrivilegesString.split(",")));

		// replace privilege jcr:all by the respective jcr:privileges
		if(jcrPrivileges.containsAll(jcrAllList)){
			jcrPrivileges.removeAll(jcrAllList); 
			jcrPrivileges.addAll(map.get("jcr:all"));
		}

		// replace privilege rep:write by the respective jcr:privileges
		if(jcrPrivileges.containsAll(repWriteList)){
			jcrPrivileges.removeAll(repWriteList); 
			jcrPrivileges.addAll(map.get("rep:write"));
		}

		// replace privilege jcr:write by the respective jcr:privileges
		if(jcrPrivileges.containsAll(jcrWriteList)){
			jcrPrivileges.removeAll(jcrWriteList); 
			jcrPrivileges.addAll(map.get("jcr:write"));
		}


		// loop through keySet of cqActions. Remove successively all privileges which are associated to a cq action from jcrPrivileges string 
		// and add this actions name to actions string

		Set<String> cqActions = map.keySet();
		String actionsString = "";

		for(String action : cqActions){
			List<String> jcrPrivilegesFromMap =  map.get(action);
			if(jcrPrivileges.containsAll(jcrPrivilegesFromMap)){
				jcrPrivileges.removeAll(jcrPrivilegesFromMap);
				actionsString = actionsString + action + ",";
			}
		}

		// remove last comma from actions string
		actionsString = StringUtils.chop(actionsString);

		if(actionsString.isEmpty()){
			actionsString = "";
		}
		return actionsString;
	}
	
	/**
	 * Method that removes jcr:privileges covered by cq actions from a String
	 * @param privilegesString comma separated String containing jcr:privileges
	 * @param actionsString comma separated String containing cq actions
	 * @return comma separated String containing  jcr:privileges
	 */
	public static String getStrippedPrivilegesString(final String privilegesString, final String actionsString){
		List <String> actions = new ArrayList<String>(Arrays.asList(actionsString.split(",")));
		List <String> jcrPrivileges = new ArrayList<String>(Arrays.asList(privilegesString.split(",")));

		// replace privilege rep:write by the respective jcr:privileges
		if(jcrPrivileges.containsAll(repWriteList)){
			jcrPrivileges.removeAll(repWriteList); 
			jcrPrivileges.addAll(map.get("rep:write"));
		}
		// replace privilege jcr:write by the respective jcr:privileges
		if(jcrPrivileges.containsAll(jcrWriteList)){
			jcrPrivileges.removeAll(jcrWriteList); 
			jcrPrivileges.addAll(map.get("jcr:write"));
		}
		// Don't replace jcr:all

		for(String action : actions){
			List<String> privilegesFromAction = map.get(action);
			if(privilegesFromAction != null){
				jcrPrivileges.removeAll(privilegesFromAction);
			}
		}

		// build new privileges String
		StringBuilder sb = new StringBuilder();
		for (String privilege : jcrPrivileges) {
			sb.append(privilege).append(",");
		}
		return StringUtils.chop(sb.toString());
	}

	/**
	 * method that deletes jvr:privileges from a AceBean which are covered by cq actions stored in the respective bean property
	 * @param bean a AceBean
	 * @return
	 */
	public static AceBean getAlignedPermissionBean(final AceBean bean){
		String alignedPrivileges = getStrippedPrivilegesString(bean.getPrivilegesString(),bean.getActionsString());
		bean.setPrivilegesString(alignedPrivileges);

		// in case privileges property contains "jcr:all" remove all actions, since they're included
		if(alignedPrivileges.contains("jcr:all")){
			bean.clearActions();
		}
		return bean;

	}
	
	public static AceBean getConvertedPrivilegeBean(AceBean bean){
		Set <String> actions = new HashSet<String>();
		Set <String> privileges = new HashSet<String>();
		
		if(bean.getActions() != null){
			actions = new HashSet<String>(Arrays.asList(bean.getActions()));
		}
		if(bean.getPrivileges() != null){
			privileges = new HashSet<String>(Arrays.asList(bean.getPrivileges()));
		}
		
		for(String action : actions){
			privileges.addAll(map.get(action));
		}
		bean.clearActions();
		bean.setActionsString("");
		
		StringBuilder sb = new StringBuilder();
		for (String privilege : privileges) {
			sb.append(privilege).append(",");
		}
		
		bean.setPrivilegesString(StringUtils.chop(sb.toString()));
		
		return bean;
		
	}
}


