package biz.netcentric.cq.tools.actool.helper;

import java.io.PrintWriter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;


public class ConfigReader {

	private static final String ACE_CONFIG_PROPERTY_GLOB = "repGlob";
	private static final String ACE_CONFIG_PROPERTY_PERMISSION = "permission";
	private static final String ACE_CONFIG_PROPERTY_PRIVILEGES = "privileges";
	private static final String ACE_CONFIG_PROPERTY_ACTIONS = "actions";
	private static final String ACE_CONFIG_PROPERTY_PATH = "path";

	public static final String GROUP_CONFIG_PROPERTY_MEMBER_OF = "memberOf";
	public static final String GROUP_CONFIG_PROPERTY_PATH = "path";
	public static final String GROUP_CONFIG_PROPERTY_IS_GROUP = "isGroup";
	public static final String GROUP_CONFIG_PROPERTY_PASSWORD = "password";
	public static final String GROUP_CONFIG_PROPERTY_NAME = "name";

	private static final Logger LOG = LoggerFactory.getLogger(ConfigReader.class);

	@SuppressWarnings("rawtypes")

	public static Map<String, Set<AceBean>> getAceConfigurationBeans(final Session session,
			PrintWriter out, List<LinkedHashMap> yamlList, Set<String> groupsFromConfig) throws RepositoryException {

		// build ACE Beans
		LinkedHashMap aceConfigMap = yamlList.get(1);
		List<LinkedHashMap> aclList = (List<LinkedHashMap>) aceConfigMap.get(Constants.ACE_CONFIGURATION_KEY);
		if(aclList == null){
			LOG.error("ACL configuration not found in YAML configuration file");
			return null;
		}
		
		
		Map<String, Set<AceBean>> aceMapFromConfig = getPreservedOrderdAceMap(session,out, aclList, groupsFromConfig); // group based Map from config file
		return aceMapFromConfig;
	}

	public static Map<String, LinkedHashSet<AuthorizableConfigBean>> getAuthorizableConfigurationBeans(
			PrintWriter out, List<LinkedHashMap> yamlList) {

		// build principal Beans
		LinkedHashMap authorizableConfigMap = yamlList.get(0);
		List<LinkedHashMap> authorizableList = (List<LinkedHashMap>) authorizableConfigMap.get(Constants.GROUP_CONFIGURATION_KEY);
		if(authorizableList == null){
			LOG.error("group configuration not found in YAML configuration file");
			return null;
		}
		Map<String, LinkedHashSet<AuthorizableConfigBean>> principalsMap = getAuthorizablesMap(out, authorizableList);
		return principalsMap;
	}



	private static Map<String, LinkedHashSet<AuthorizableConfigBean>> getAuthorizablesMap(final PrintWriter out, final List <LinkedHashMap> yamlMap) {

		Map<String, LinkedHashSet<AuthorizableConfigBean>> principalMap = new LinkedHashMap <String, LinkedHashSet<AuthorizableConfigBean>>();

		if(yamlMap != null){
			for(LinkedHashMap currentMap : yamlMap){

				Set <Object> principalSet = currentMap.keySet();

				// loop through authorizables (keys of the map)
				for(Object currentPrincipalKey : principalSet){

					// explicit cast to String since value is object
					String currentPrincipal = String.valueOf(currentPrincipalKey);

					LOG.info("start reading group configuration");
					LOG.info("Found principal: {} in config", currentPrincipal);
					if(out != null){
						out.write("Found group: " + currentPrincipal + " in groupConfig\n");
					}

					LinkedHashSet<AuthorizableConfigBean> tmpSet = new LinkedHashSet<AuthorizableConfigBean>();
					principalMap.put((String)currentPrincipal,  tmpSet);

					List<LinkedHashMap> currentPrincipalData = (List<LinkedHashMap>) currentMap.get(currentPrincipalKey);

					if(currentPrincipalData != null && !currentPrincipalData.isEmpty()){

						for(LinkedHashMap currentPrincipalDataMap : currentPrincipalData){
							Set<String> principalDataKeySet = currentPrincipalDataMap.keySet();
							AuthorizableConfigBean tmpPrincipalConfigBean = new AuthorizableConfigBean();

							if(Validators.isValidAuthorizableName((String)currentPrincipal)){
								tmpPrincipalConfigBean.setPrincipalID((String)currentPrincipal);
							}
							else{
								String message = "Validation error while reading group data: invalid authorizable name: " + (String)currentPrincipal;
								LOG.error(message);
								throw new IllegalArgumentException(message);

							}

							for(String entry : principalDataKeySet){

								// explicit cast to String since value is object
								String currentEntryValue = String.valueOf(currentPrincipalDataMap.get(entry));
								if(StringUtils.equals(currentEntryValue, "null")){
									currentEntryValue = "";
								}

								tmpPrincipalConfigBean.setPrincipalID((String)currentPrincipal);
								
								if(StringUtils.equals(GROUP_CONFIG_PROPERTY_NAME, entry )){
									String nameValue = "";
									if(!StringUtils.equals("null", currentEntryValue)){
										nameValue = currentEntryValue;
									}
									tmpPrincipalConfigBean.setAuthorizableName(nameValue);	
								}
								else if(StringUtils.equals(GROUP_CONFIG_PROPERTY_MEMBER_OF, entry )){
									
									if(StringUtils.isNotBlank(currentEntryValue)){
										if(currentEntryValue != null){
											
											String[] groups = currentEntryValue.split(",");
											
											for(int i = 0; i < groups.length; i++){
												
												// remove leading and trailing blanks from groupname
												groups[i] = StringUtils.strip(groups[i]);
											
												if(!Validators.isValidAuthorizableName(groups[i])){
													LOG.error("Validation error while reading group property of authorizable:{}, invalid authorizable name: {}", currentPrincipal, groups[i]);
													throw new IllegalArgumentException("Validation error while reading group property of authorizable: " + currentPrincipal + ", invalid authorizable name: " + groups[i]);
												}
											}

											tmpPrincipalConfigBean.setMemberOf(groups);
										}
									}
								}
								// TODO: validation
								else if(StringUtils.equals(GROUP_CONFIG_PROPERTY_PATH, entry )){
									tmpPrincipalConfigBean.setPath(currentEntryValue);
								}
								// TODO: validation
								else if(StringUtils.equals(GROUP_CONFIG_PROPERTY_IS_GROUP, entry )){
									tmpPrincipalConfigBean.memberOf(new Boolean(currentEntryValue));
								}
								// TODO: validation
								else if(StringUtils.equals(GROUP_CONFIG_PROPERTY_PASSWORD, entry )){
									tmpPrincipalConfigBean.setPassword(currentEntryValue);
								}
							}
							principalMap.get(currentPrincipal).add(tmpPrincipalConfigBean);

						}
					}
				}
			}


			if(out != null){
				out.println("\nPrincipalConfigBeans created out of config file: \n");
			}

			Set<String> keySet = principalMap.keySet();


			if(out != null){
				for(String principal:keySet){
					Set<AuthorizableConfigBean> aceBeanSet = principalMap.get(principal);
					out.println("principal: " + principal);
					for(AuthorizableConfigBean bean : aceBeanSet){
						out.println();
						out.println(" ID: " + bean.getPrincipalID());
						out.println(" name: " + bean.getPrincipalName());
						out.println(" memberOf: " + bean.getMemberOfString());
						out.println(" path: " + bean.getPath());
						out.println(" isGroup: " + bean.isGroup());
						out.println(" password: " + bean.getPassword());
					}
					out.println();
				}
				out.println();
			}
		}
		return principalMap;
	}

	private static Map<String, Set<AceBean>> getPreservedOrderdAceMap(final Session session,final PrintWriter out, final List <LinkedHashMap> aceYamlMap, Set<String> groupsFromCurrentConfig) throws RepositoryException {

		Map<String, Set<AceBean>> aceMap = new LinkedHashMap <String, Set<AceBean>>();
		
		if(aceYamlMap != null){
			for(LinkedHashMap currentPrincipalAceMap : aceYamlMap){

				Set<Object> keySet = currentPrincipalAceMap.keySet();
				
				
				for(Object principalKey : keySet){
					String principal = String.valueOf(principalKey);

					List<LinkedHashMap> aceData = (List<LinkedHashMap>) currentPrincipalAceMap.get(principal);
					LOG.info("start reading ACE configuration of authorizable: {}", principal);


					if(out != null){
						out.write("\n" + principal);
					}

					if(aceMap.get(principal) == null){
						LinkedHashSet<AceBean> tmpSet = new LinkedHashSet<AceBean>();
						aceMap.put(principal,  tmpSet);
					}


					//mandatory Properties which have to be set per ACE definition
					boolean isPathDefined = false;
					boolean isPermissionDefined = false;
					boolean isActionOrPermissionDefined = false;

					long counter = 1;

					if(aceData != null && !aceData.isEmpty()){

						for(LinkedHashMap map2 : aceData){
							Set<String> keySet2 = map2.keySet();
							AceBean tmpAclBean = new AceBean();

							for(String entry : keySet2){
								String logString = "\n" + " " + entry + ": " + map2.get(entry);
								if(out != null){
									out.write(logString);
								}
								LOG.info(logString);
								
								
								String currentEntryValue = (String)map2.get(entry);
								if(currentEntryValue == null){
									currentEntryValue = "";
								}
								
								// validate authorizable name format
								if(Validators.isValidAuthorizableName(principal)){
									
									// validate if authorizable is contained in config
									if(!groupsFromCurrentConfig.contains(principal)){
										String message = "Validation error while reading ACE definition, authorizable: " + principal + " is not contained in group configuration";
										throw new IllegalArgumentException(message);

									}
									tmpAclBean.setPrincipal(principal);
								}
								else{

									LOG.error("Validation error while reading ACE data: invalid authorizable name: {}", principal);
									throw new IllegalArgumentException("Validation error while reading ACE definition, invalid authorizable name: " + principal);

								}
								
								// validate path
								if(StringUtils.equals(ACE_CONFIG_PROPERTY_PATH, entry )){

									if(Validators.isValidNodePath(currentEntryValue)){
										tmpAclBean.setJcrPath(currentEntryValue);	
										isPathDefined = true;
									}
									else{

										LOG.error("Validation error while reading ACE data: invalid path: {}", currentEntryValue);
										throw new IllegalArgumentException("Validation error while reading ACE definition nr." + counter + " of authorizable: " + principal + ", invalid path: " + currentEntryValue);
									}
								}
								
								// validate actions
								else if(StringUtils.equals(ACE_CONFIG_PROPERTY_ACTIONS, entry )){
									if(StringUtils.isNotBlank(currentEntryValue)){
									
									String[] actions = currentEntryValue.split(",");

									for(int i = 0; i < actions.length; i++){
										
										// remove leading and trailing blanks from action name
										actions[i] = StringUtils.strip(actions[i]);
										
										if(!Validators.isValidAction(actions[i])){
											LOG.error("Validation error while reading ACE data: invalid action: {}", actions[i]);
											throw new IllegalArgumentException("Validation error while reading ACE definition nr." + counter + " of authorizable: " + principal + ",  invalid action: " + actions[i]);
										}
									}

									tmpAclBean.setActions(actions);
									isActionOrPermissionDefined = true;
									
									}
								}
								
								// validate privileges
								else if(StringUtils.equals(ACE_CONFIG_PROPERTY_PRIVILEGES, entry )){
									// TO DO: validation
									if("null".equals(currentEntryValue)){
										tmpAclBean.setPrivilegesString("");
									}else{
									    tmpAclBean.setPrivilegesString(currentEntryValue);
									}
								}
								
								// validate permission
								else if(StringUtils.equals(ACE_CONFIG_PROPERTY_PERMISSION, entry )){
									if(Validators.isValidPermission(currentEntryValue)){
										tmpAclBean.setAllow(new Boolean(StringUtils.equals("allow", currentEntryValue) ? "true" : "deny"));
										tmpAclBean.setIsAllowProvide(true);
										isActionOrPermissionDefined = true;
									}
									else{

										LOG.error("Validation error while reading ACE data: invalid permission: {}", currentEntryValue);
										throw new IllegalArgumentException("Validation error while reading ACE definition nr." + counter + " of authorizable: " + principal + ", invalid permission: " + currentEntryValue);
									}
								}
								
								// validata globbing
								else if(StringUtils.equals(ACE_CONFIG_PROPERTY_GLOB, entry )){
									if(Validators.isValidRegex(currentEntryValue)){
										tmpAclBean.setRepGlob(currentEntryValue);
									}
									else{

										LOG.error("Validation error while reading ACE data: invalid globbing expression: {}", currentEntryValue);
										throw new IllegalArgumentException("Validation error while reading ACE definition nr." + counter + " of authorizable: " + principal + ",  invalid globbing expression: " + currentEntryValue);
									}
								}
							}

							String missingPropertiesString = getMissingPropertiesString(isPathDefined,  isActionOrPermissionDefined);

							if(missingPropertiesString.isEmpty()){
								
								// --- handle wildcards ---
								if(tmpAclBean.getJcrPath().contains("*")){
									
									// perform query using the path containing wildcards
									String query = "/jcr:root" + tmpAclBean.getJcrPath();
									Set<Node> result = AcHelper.getNodes(session, query); 

									// if there are nodes in repository matching the wildcard-query
									// replace the bean holding the wildcard path by beans having the found paths
									Set<AceBean> replaceBeans = new HashSet<AceBean>();
									
									if(!result.isEmpty()){
										
										for(Node node : result){
											if(!node.getPath().contains("/rep:policy")){
												AceBean replaceBean = new AceBean();
												replaceBean.setJcrPath(node.getPath());
												replaceBean.setActions(tmpAclBean.getActions());
												replaceBean.setAllow(tmpAclBean.isAllow());
												replaceBean.setPrincipal(tmpAclBean.getPrincipalName());
												replaceBean.setPrivilegesString(tmpAclBean.getPrivilegesString());

												if(!aceMap.get(principal).add(replaceBean)){
													LOG.warn("wildcard replacement: replacing bean: " + tmpAclBean + ", with bean " + replaceBean + " went wrong!");
												}else{
													LOG.info("wildcard replacement: replaced bean: " + tmpAclBean + ", with bean " + replaceBean);
												}
											}
										}
									}

								}else{
									aceMap.get(principal).add(tmpAclBean);
								}
								counter++;
							}else{
								LOG.error("Mandatory property/properties in ACE definition nr." + counter + " of authorizable : {} missing: {}! Installation aborted!", principal, missingPropertiesString);
								throw new IllegalArgumentException("Mandatory property/properties in ACE definition nr." + counter + " of authorizable: " + principal + " missing: " + missingPropertiesString + "! Installation aborted!");
							}

							if(out != null){
								out.write("\n");
							}
						}

					}
				}
			}
			if(out != null){
				out.println("\nACL-Beans created out of config file: \n");
			}
			Set<String> keySet = aceMap.keySet();
			if(out != null){
				for(String principal:keySet){
					Set<AceBean> aceBeanSet = aceMap.get(principal);
					out.println("principal: " + principal);
					for(AceBean bean : aceBeanSet){
						out.println();
						out.println(" " + ACE_CONFIG_PROPERTY_PATH + ": " + bean.getJcrPath());
						out.println(" " + ACE_CONFIG_PROPERTY_PERMISSION + ": " + bean.getPermission());
						out.println(" " + ACE_CONFIG_PROPERTY_ACTIONS + ": " + bean.getActionsString());
						out.println(" " + ACE_CONFIG_PROPERTY_PRIVILEGES + ": " + bean.getPrivilegesString());
						out.println(" " + ACE_CONFIG_PROPERTY_GLOB + ": " + bean.getRepGlob());
					}
					out.println();
				}
				out.println();
			}
		}
		return aceMap;
	}

	private static String getMissingPropertiesString(final boolean isPathDefined,  final boolean isActionOrPermissionDefined) {
		String missingPropertiesString = "";
		if(!isPathDefined) {
			missingPropertiesString = missingPropertiesString + "path,";
		}
		
		if(!isActionOrPermissionDefined) {
			missingPropertiesString = missingPropertiesString + "actions or permissions,";
		}
		return StringUtils.chomp(missingPropertiesString, ",");
	}

	public static void getPrincipalConfig(final PrintWriter out, final List<LinkedHashMap> yamlMap){

		Map<String, LinkedHashSet<AceBean>> principalMap = new LinkedHashMap <String, LinkedHashSet<AceBean>>();

		for(LinkedHashMap map:yamlMap){
			Set<String> keySet = map.keySet();
			
			for(String principal : keySet){
				List<LinkedHashMap> principalData = (List<LinkedHashMap>) map.get(principal);
				LinkedHashSet<AceBean> tmpSet = new LinkedHashSet<AceBean>();
				principalMap.put(principal,  tmpSet);
				if(principalData != null && !principalData.isEmpty()){
					for(LinkedHashMap map2 : principalData){

					}
				}
			}
		}
	}

}
