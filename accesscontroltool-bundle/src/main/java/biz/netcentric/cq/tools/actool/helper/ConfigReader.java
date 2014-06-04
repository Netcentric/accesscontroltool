package biz.netcentric.cq.tools.actool.helper;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.InvalidQueryException;
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

     public static Map<String, Set<AceBean>> getAceConfigurationBeans(final Session session, final List<LinkedHashMap> yamlList, final Set<String> groupsFromConfig) throws RepositoryException {

          // build ACE Beans
          LinkedHashMap aceConfigMap = yamlList.get(1);
          List<LinkedHashMap> aclList = (List<LinkedHashMap>) aceConfigMap.get(Constants.ACE_CONFIGURATION_KEY);
          if(aclList == null){
               LOG.error("ACL configuration not found in YAML configuration file");
               return null;
          }


          Map<String, Set<AceBean>> aceMapFromConfig = getPreservedOrderdAceMap(session, aclList, groupsFromConfig); // group based Map from config file
          return aceMapFromConfig;
     }

     public static Map<String, LinkedHashSet<AuthorizableConfigBean>> getAuthorizableConfigurationBeans(final List<LinkedHashMap> yamlList) {

          // build principal Beans
          LinkedHashMap authorizableConfigMap = yamlList.get(0);
          List<LinkedHashMap> authorizableList = (List<LinkedHashMap>) authorizableConfigMap.get(Constants.GROUP_CONFIGURATION_KEY);
          if(authorizableList == null){
               LOG.error("group configuration not found in YAML configuration file");
               return null;
          }
          Map<String, LinkedHashSet<AuthorizableConfigBean>> principalsMap = getAuthorizablesMap(authorizableList);
          return principalsMap;
     }

     // ----- group config helper methods ----------
    
     private static Map<String, LinkedHashSet<AuthorizableConfigBean>> getAuthorizablesMap(final List <LinkedHashMap> yamlMap) {
          Set<String> alreadyProcessedGroups = new HashSet<String>();
          Map<String, LinkedHashSet<AuthorizableConfigBean>> principalMap = new LinkedHashMap <String, LinkedHashSet<AuthorizableConfigBean>>();

          if(yamlMap != null){
               for(LinkedHashMap currentMap : yamlMap){

                    Set <Object> principalSet = currentMap.keySet();

                    // loop through authorizables (keys of the map)
                    for(Object currentPrincipalKey : principalSet){


                         // explicit cast to String since value is object
                         String currentPrincipal = String.valueOf(currentPrincipalKey);

                         if(!alreadyProcessedGroups.add(currentPrincipal)){
                              throw new IllegalArgumentException("There is more than one group definition for group: " + currentPrincipal);
                         }
                         LOG.info("start reading group configuration");
                         LOG.info("Found principal: {} in config", currentPrincipal);


                         LinkedHashSet<AuthorizableConfigBean> tmpSet = new LinkedHashSet<AuthorizableConfigBean>();
                         principalMap.put((String)currentPrincipal,  tmpSet);

                         List<LinkedHashMap> currentPrincipalData = (List<LinkedHashMap>) currentMap.get(currentPrincipalKey);

                         if(currentPrincipalData != null && !currentPrincipalData.isEmpty()){

                              for(LinkedHashMap currentPrincipalDataMap : currentPrincipalData){
                                   Set<String> principalDataKeySet = currentPrincipalDataMap.keySet();
                                   AuthorizableConfigBean tmpPrincipalConfigBean = new AuthorizableConfigBean();

                                   validateAuthorizableId(currentPrincipal, tmpPrincipalConfigBean);

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

                                             validateMemberOf(currentPrincipal, tmpPrincipalConfigBean, currentEntryValue);
                                        }
                                        // TODO: validation
                                        else if(StringUtils.equals(GROUP_CONFIG_PROPERTY_PATH, entry )){
                                             tmpPrincipalConfigBean.setPath(currentEntryValue);
                                        }
                                        // TODO: validation
                                        else if(StringUtils.equals(GROUP_CONFIG_PROPERTY_IS_GROUP, entry )){
                                             tmpPrincipalConfigBean.memberOf(new Boolean(currentEntryValue));
                                        }
                                       
                                        else if(StringUtils.equals(GROUP_CONFIG_PROPERTY_PASSWORD, entry )){
                                             tmpPrincipalConfigBean.setPassword(currentEntryValue);
                                        }
                                   }
                                   principalMap.get(currentPrincipal).add(tmpPrincipalConfigBean);

                              }
                         }
                    }
               }
               Set<String> keySet = principalMap.keySet();

          }
          return principalMap;
     }
    
     // ------ validator methods for groups ----

     private static void validateMemberOf(String currentPrincipal, AuthorizableConfigBean tmpPrincipalConfigBean, String currentEntryValue) {
          if(StringUtils.isNotBlank(currentEntryValue)){
               if(currentEntryValue != null){

                    String[] groups = currentEntryValue.split(",");

                    for(int i = 0; i < groups.length; i++){

                         // remove leading and trailing blanks from groupname
                         groups[i] = StringUtils.strip(groups[i]);

                         if(!Validators.isValidAuthorizableId(groups[i])){
                              LOG.error("Validation error while reading group property of authorizable:{}, invalid authorizable name: {}", currentPrincipal, groups[i]);
                              throw new IllegalArgumentException("Validation error while reading group property of authorizable: " + currentPrincipal + ", invalid authorizable name: " + groups[i]);
                         }
                    }

                    tmpPrincipalConfigBean.setMemberOf(groups);
               }
          }
     }

     private static void validateAuthorizableId(String currentPrincipal, AuthorizableConfigBean tmpPrincipalConfigBean) {
          if(Validators.isValidAuthorizableId((String)currentPrincipal)){
               tmpPrincipalConfigBean.setPrincipalID((String)currentPrincipal);
          }
          else{
               String message = "Validation error while reading group data: invalid authorizable name: " + (String)currentPrincipal;
               LOG.error(message);
               throw new IllegalArgumentException(message);

          }
     }

    
     // ----- ACE config helper methods ----------
    
     private static Map<String, Set<AceBean>> getPreservedOrderdAceMap(final Session session, final List <LinkedHashMap> aceYamlMap, Set<String> groupsFromCurrentConfig) throws RepositoryException {
          Set<String> alreadyProcessedGroups = new HashSet<String>();
          Map<String, Set<AceBean>> aceMap = new LinkedHashMap <String, Set<AceBean>>();

          if(aceYamlMap != null){
               for(LinkedHashMap currentPrincipalAceMap : aceYamlMap){

                    Set<Object> keySet = currentPrincipalAceMap.keySet();

                    for(Object principalKey : keySet){
                         String principal = String.valueOf(principalKey);

                         if(!alreadyProcessedGroups.add(principal)){
                              throw new IllegalArgumentException("There is more than one ACE definition block for group: " + principal);
                         }
                         List<LinkedHashMap> aceDefinitions = (List<LinkedHashMap>) currentPrincipalAceMap.get(principal);
                         LOG.info("start reading ACE configuration of authorizable: {}", principal);
                        
                         // if current principal is not yet in map, add new key and empty set for storing the pricipals ACE beans
                         if(aceMap.get(principal) == null){
                              Set<AceBean> tmpSet = new LinkedHashSet<AceBean>();
                              aceMap.put(principal,  tmpSet);
                         }

                         // mandatory Properties which have to be existent in every ACE definition in configuration file
                         boolean isPathDefined = false;
                         boolean isActionOrPrivilegeDefined = false;
                        
                         // holds the number of the currently processed Ace bean for the current principal; used in exceptions to give additional information
                         long aceBeanCounter = 1;

                         if(aceDefinitions != null && !aceDefinitions.isEmpty()){

                              for(LinkedHashMap currentAceDefinition : aceDefinitions){
                                   Set<String> aceProperties = currentAceDefinition.keySet();
                                   AceBean tmpAclBean = new AceBean();

                                   for(String currentAceProperty : aceProperties){
                                       
                                        String logString = "\n" + " " + currentAceProperty + ": " + currentAceDefinition.get(currentAceProperty);
                                        LOG.info(logString);


                                        String currentEntryValue = (String)currentAceDefinition.get(currentAceProperty);
                                        if(currentEntryValue == null){
                                             currentEntryValue = "";
                                        }
                                       
                                        // ---- setup ACE bean -----
                                       
                                        // set authorizable ID
                                        validateAuthorizableId(groupsFromCurrentConfig, principal, tmpAclBean);
                                                                                                             
                                        // set path
                                        if(StringUtils.equals(ACE_CONFIG_PROPERTY_PATH, currentAceProperty )){
                                             isPathDefined = validateAcePath(principal, aceBeanCounter, tmpAclBean, currentEntryValue);
                                        }

                                        // validate actions
        								else if(StringUtils.equals(ACE_CONFIG_PROPERTY_ACTIONS, currentAceProperty )){
        									isActionOrPrivilegeDefined = validateActions(principal,isActionOrPrivilegeDefined,aceBeanCounter, tmpAclBean,currentEntryValue);
        								}

        								// validate privileges
        								else if(StringUtils.equals(ACE_CONFIG_PROPERTY_PRIVILEGES, currentAceProperty )){
        									isActionOrPrivilegeDefined = validatePrivileges(principal,isActionOrPrivilegeDefined,aceBeanCounter, tmpAclBean,currentEntryValue);
        								}

                                        // set permission
                                        else if(StringUtils.equals(ACE_CONFIG_PROPERTY_PERMISSION, currentAceProperty )){
                                             validatePermission(principal, aceBeanCounter, tmpAclBean, currentEntryValue);
                                        }

                                        // set globbing
                                        else if(StringUtils.equals(ACE_CONFIG_PROPERTY_GLOB, currentAceProperty )){
                                             validateGlobbing(principal, aceBeanCounter, tmpAclBean, currentEntryValue);
                                        }
                                   }
                                   // in case a mandatory property is missing, missingPropertiesString gets some text
                                   String missingPropertiesString = getMissingPropertiesString(isPathDefined,  isActionOrPrivilegeDefined);

                                   if(missingPropertiesString.isEmpty()){

                                        // --- handle wildcards ---
                                        if(tmpAclBean.getJcrPath().contains("*")){
                                             handleWildcards(session, aceMap, principal, tmpAclBean);
                                        }else{
                                             aceMap.get(principal).add(tmpAclBean);
                                        }
                                        aceBeanCounter++;
                                   }else{
                                        LOG.error("Mandatory property/properties in ACE definition nr." + aceBeanCounter + " of authorizable : {} missing: {}! Installation aborted!", principal, missingPropertiesString);
                                        throw new IllegalArgumentException("Mandatory property/properties in ACE definition nr." + aceBeanCounter + " of authorizable: " + principal + " missing: " + missingPropertiesString + "! Installation aborted!");
                                   }
                              }
                         }
                    }
               }

          }
          return aceMap;
     }

     private static void handleWildcards(final Session session, Map<String, Set<AceBean>> aceMap, String principal, AceBean tmpAclBean) throws InvalidQueryException,
               RepositoryException {
          // perform query using the path containing wildcards
          String query = "/jcr:root" + tmpAclBean.getJcrPath();
          Set<Node> result = QueryHelper.getNodes(session, query);

          // if there are nodes in repository matching the wildcard-query
          // replace the bean holding the wildcard path by beans having the found paths
          Set<AceBean> replaceBeans = new HashSet<AceBean>();

          if(!result.isEmpty()){

               for(Node node : result){
                    // ignore rep:policy nodes
                    if(!node.getPath().contains("/rep:policy")){
                         AceBean replacementBean = new AceBean();
                         replacementBean.setJcrPath(node.getPath());
                         replacementBean.setActions(tmpAclBean.getActions());
                         replacementBean.setAllow(tmpAclBean.isAllow());
                         replacementBean.setPrincipal(tmpAclBean.getPrincipalName());
                         replacementBean.setPrivilegesString(tmpAclBean.getPrivilegesString());

                         if(!aceMap.get(principal).add(replacementBean)){
                              LOG.warn("wildcard replacement: replacing bean: " + tmpAclBean + ", with bean " + replacementBean + " failed!");
                         }else{
                              LOG.info("wildcard replacement: replaced bean: " + tmpAclBean + ", with bean " + replacementBean);
                         }
                    }
               }
          }
     }
    
     private static String getMissingPropertiesString(final boolean isPathDefined, final boolean isActionOrPermissionDefined) {
          String missingPropertiesString = "";
          if(!isPathDefined) {
               missingPropertiesString = missingPropertiesString + "path,";
          }

          if(!isActionOrPermissionDefined) {
               missingPropertiesString = missingPropertiesString + "actions or permissions,";
          }
          return StringUtils.chomp(missingPropertiesString, ",");
     }
    
    
     // ------ validator methods for ACEs ----

     private static void validateGlobbing(String principal, long counter, AceBean tmpAclBean, String currentEntryValue) {
          if(Validators.isValidRegex(currentEntryValue)){
               tmpAclBean.setRepGlob(currentEntryValue);
          }
          else{

               LOG.error("Validation error while reading ACE data: invalid globbing expression: {}", currentEntryValue);
               throw new IllegalArgumentException("Validation error while reading ACE definition nr." + counter + " of authorizable: " + principal + ",  invalid globbing expression: " + currentEntryValue);
          }
     }

     private static void validatePermission(String principal, long counter, AceBean tmpAclBean, String currentEntryValue) {
          if(Validators.isValidPermission(currentEntryValue)){
               tmpAclBean.setAllow(new Boolean(StringUtils.equals("allow", currentEntryValue) ? "true" : "deny"));
               tmpAclBean.setIsAllowProvide(true);
          }
          else{

               LOG.error("Validation error while reading ACE data: invalid permission: {}", currentEntryValue);
               throw new IllegalArgumentException("Validation error while reading ACE definition nr." + counter + " of authorizable: " + principal + ", invalid permission: " + currentEntryValue);
          }
     }

     private static boolean validatePrivileges(String principal,
 			boolean isActionOrPrivilegeDefined, long counter,
 			AceBean tmpAclBean, String currentEntryValue) {
 		if(StringUtils.isNotBlank(currentEntryValue)){

 			// validation
 			if("null".equals(currentEntryValue)){
 				tmpAclBean.setPrivilegesString("");
 			}else if(!currentEntryValue.isEmpty()){
 				String[] privileges = currentEntryValue.split(",");
 				for(int i = 0; i < privileges.length; i++){

 					// remove leading and trailing blanks from privilege name
 					privileges[i] = StringUtils.strip(privileges[i]);

 					if(!Validators.isValidJcrPrivilege(privileges[i])){
 						LOG.error("Validation error while reading ACE data: invalid jcr privilege: {}", privileges[i]);
 						throw new IllegalArgumentException("Validation error while reading ACE definition nr." + counter + " of authorizable: " + principal + ",  invalid jcr privilege: " + privileges[i]);
 					}
 				}
 				tmpAclBean.setPrivilegesString(currentEntryValue);
 				isActionOrPrivilegeDefined = true;
 			}
 		}
 		return isActionOrPrivilegeDefined;
 	}

 	private static boolean validateActions(String principal,
 			boolean isActionOrPrivilegeDefined, long counter,
 			AceBean tmpAclBean, String currentEntryValue) {
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
 			isActionOrPrivilegeDefined = true;

 		}
 		return isActionOrPrivilegeDefined;
 	}

     private static boolean validateAcePath(String principal,long counter, AceBean tmpAclBean, String currentEntryValue) {
          boolean isPathDefined = false;
          if(Validators.isValidNodePath(currentEntryValue)){
               tmpAclBean.setJcrPath(currentEntryValue);    
               isPathDefined = true;
          }
          else{
            LOG.error("Validation error while reading ACE data: invalid path: {}", currentEntryValue);
               throw new IllegalArgumentException("Validation error while reading ACE definition nr." + counter + " of authorizable: " + principal + ", invalid path: " + currentEntryValue);
          }
          return isPathDefined;
     }

     private static void validateAuthorizableId(Set<String> groupsFromCurrentConfig, String principal, AceBean tmpAclBean) {
          // validate authorizable name format
          if(Validators.isValidAuthorizableId(principal)){

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
     }




}