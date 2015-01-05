/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configReader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.InvalidQueryException;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.jcr.api.SlingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.helper.AceBean;
import biz.netcentric.cq.tools.actool.helper.Constants;
import biz.netcentric.cq.tools.actool.helper.QueryHelper;
import biz.netcentric.cq.tools.actool.validators.AceBeanValidator;
import biz.netcentric.cq.tools.actool.validators.AuthorizableValidator;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;

@Service
@Component(metatype = true, label = "AC Yaml Config Reader", description = "Service that installs groups & ACEs according to textual configuration files")
public class YamlConfigReader implements ConfigReader {

    private final String ACE_CONFIG_PROPERTY_GLOB = "repGlob";
    private final String ACE_CONFIG_PROPERTY_PERMISSION = "permission";
    private final String ACE_CONFIG_PROPERTY_PRIVILEGES = "privileges";
    private final String ACE_CONFIG_PROPERTY_ACTIONS = "actions";
    private final String ACE_CONFIG_PROPERTY_PATH = "path";
    private final String ACE_CONFIG_PROPERTY_FOR = "for";

    private final String GROUP_CONFIG_PROPERTY_MEMBER_OF = "memberOf";
    private final String GROUP_CONFIG_PROPERTY_PATH = "path";
    private final String GROUP_CONFIG_PROPERTY_IS_GROUP = "isGroup";
    private final String GROUP_CONFIG_PROPERTY_PASSWORD = "password";
    private final String GROUP_CONFIG_PROPERTY_NAME = "name";

    // FIXME: This class should not depend on Sling
    @Reference
    private SlingRepository repository;

    private final String ASSERTED_EXCEPTION = "assertedException";

    private final Logger LOG = LoggerFactory.getLogger(YamlConfigReader.class);

    /*
     * (non-Javadoc)
     * 
     * @see
     * biz.netcentric.cq.tools.actool.helper.ConfigReader#getAceConfigurationBeans
     * (javax.jcr.Session, java.util.List, java.util.Set,
     * biz.netcentric.cq.tools.actool.validators.AceBeanValidator)
     */
    @Override
    @SuppressWarnings("rawtypes")
    public Map<String, Set<AceBean>> getAceConfigurationBeans(
            final Collection<?> aceConfigData, Set<String> groupsFromConfig,
            AceBeanValidator aceBeanValidator) throws RepositoryException,
            AcConfigBeanValidationException {

        List<LinkedHashMap> aclList = (List<LinkedHashMap>) getConfigSection(
                Constants.ACE_CONFIGURATION_KEY, aceConfigData);

        if (aclList == null) {
            LOG.error("ACL configuration not found in YAML configuration file");
            return null;
        }

        Map<String, Set<AceBean>> aceMapFromConfig = getPreservedOrderdAceMap(
                aclList, groupsFromConfig, aceBeanValidator); // group based Map
                                                              // from config
                                                              // file
        return aceMapFromConfig;

    }

    /*
     * (non-Javadoc)
     * 
     * @see biz.netcentric.cq.tools.actool.helper.ConfigReader#
     * getAuthorizableConfigurationBeans(java.util.List,
     * biz.netcentric.cq.tools.actool.validators.AuthorizableValidator)
     */
    @Override
    public Map<String, LinkedHashSet<AuthorizableConfigBean>> getGroupConfigurationBeans(
            final Collection yamlList,
            AuthorizableValidator authorizableValidator)
            throws AcConfigBeanValidationException {

        List<LinkedHashMap> authorizableList = (List<LinkedHashMap>) getConfigSection(
                Constants.GROUP_CONFIGURATION_KEY, yamlList);

        if (authorizableList == null) {
            LOG.error("group configuration not found in YAML configuration file");
            return null;
        }

        Map<String, LinkedHashSet<AuthorizableConfigBean>> principalsMap = getAuthorizablesMap(
                authorizableList, authorizableValidator);
        return principalsMap;
    }

    private Collection getConfigSection(final String sectionName,
            final Collection yamlList) {
        List<LinkedHashMap<?, ?>> yamList = new ArrayList<LinkedHashMap<?, ?>>(
                yamlList);
        for (LinkedHashMap<?, ?> currMap : yamList) {
            if (sectionName.equals(currMap.keySet().iterator().next())) {
                return (List<LinkedHashMap>) currMap.get(sectionName);
            }
        }
        return null;
    }

    @Override
    public Map<String, LinkedHashSet<AuthorizableConfigBean>> getUserConfigurationBeans(
            Collection<?> userConfigData,
            AuthorizableValidator authorizableValidator)
            throws AcConfigBeanValidationException {
        // TODO Auto-generated method stub
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * biz.netcentric.cq.tools.actool.helper.ConfigReader#getAuthorizablesMap
     * (java.util.List,
     * biz.netcentric.cq.tools.actool.validators.AuthorizableValidator)
     */

    private Map<String, LinkedHashSet<AuthorizableConfigBean>> getAuthorizablesMap(
            final List<LinkedHashMap> yamlMap,
            AuthorizableValidator authorizableValidator)
            throws AcConfigBeanValidationException {
        Set<String> alreadyProcessedGroups = new HashSet<String>();
        Map<String, LinkedHashSet<AuthorizableConfigBean>> principalMap = new LinkedHashMap<String, LinkedHashSet<AuthorizableConfigBean>>();

        if (yamlMap == null) {
            return principalMap;
        }
        Session session = null;

        for (LinkedHashMap currentMap : yamlMap) {

            String currentPrincipal = (String) currentMap.keySet().iterator()
                    .next();

            if (!alreadyProcessedGroups.add(currentPrincipal)) {
                throw new IllegalArgumentException(
                        "There is more than one group definition for group: "
                                + currentPrincipal);
            }
            LOG.info("start reading group configuration");
            LOG.info("Found principal: {} in config", currentPrincipal);

            LinkedHashSet<AuthorizableConfigBean> tmpSet = new LinkedHashSet<AuthorizableConfigBean>();
            principalMap.put((String) currentPrincipal, tmpSet);

            List<Map<String, String>> currentPrincipalData = (List<Map<String, String>>) currentMap
                    .get(currentPrincipal);

            if (currentPrincipalData != null && !currentPrincipalData.isEmpty()) {

                for (Map<String, String> currentPrincipalDataMap : currentPrincipalData) {
                    AuthorizableConfigBean tmpPrincipalConfigBean = new AuthorizableConfigBean();
                    setupAuthorizableBean(tmpPrincipalConfigBean,
                            currentPrincipalDataMap, (String) currentPrincipal);
                    if (authorizableValidator != null) {
                        authorizableValidator.validate(tmpPrincipalConfigBean);
                    }
                    principalMap.get(currentPrincipal).add(
                            tmpPrincipalConfigBean);
                }
            }
        }
        return principalMap;

    }

    private Map<String, Set<AceBean>> getPreservedOrderdAceMap(
            final List<LinkedHashMap> aceYamlList,
            Set<String> groupsFromCurrentConfig,
            AceBeanValidator aceBeanValidator) throws RepositoryException,
            AcConfigBeanValidationException {

        // stores the names of groups for which the ACEs already have been
        // processed in order to detect doubled defined groups
        Set<String> alreadyProcessedGroups = new HashSet<String>();
        Map<String, Set<AceBean>> aceMap = new LinkedHashMap<String, Set<AceBean>>();

        if (aceYamlList == null) {
            return aceMap;
        }
        Session session = null;
        try {
            if (repository != null) {
                session = repository.loginAdministrative(null);
            }
            for (LinkedHashMap<?, ?> currentPrincipalAceMap : aceYamlList) {

                String principalName = (String) currentPrincipalAceMap.keySet()
                        .iterator().next();

                List<LinkedHashMap> aceDefinitions = (List<LinkedHashMap>) currentPrincipalAceMap
                        .get(principalName);
                LOG.info("start reading ACE configuration of authorizable: {}",
                        principalName);

                // if current principal is not yet in map, add new key and empty
                // set for storing the pricipals ACE beans
                if (aceMap.get(principalName) == null) {
                    Set<AceBean> tmpSet = new LinkedHashSet<AceBean>();
                    aceMap.put(principalName, tmpSet);
                }

                if (aceDefinitions == null || aceDefinitions.isEmpty()) {
                    LOG.warn("no ACE definition(s) found for autorizable: {}",
                            principalName);
                    continue;
                }

                // create ACE bean and populate it according to the properties
                // in the config

                if (aceBeanValidator != null) {
                    aceBeanValidator.setCurrentAuthorizableName(principalName);
                }

                for (LinkedHashMap<String, String> currentAceDefinition : aceDefinitions) {
                    if (currentAceDefinition.containsKey(ACE_CONFIG_PROPERTY_FOR)) {
                        List<AceBean> beans = unrollForLoop(currentAceDefinition, principalName);
                        for (AceBean bean : beans) {
                            aceMap.get(principalName).add(bean);
                        }
                    } else {
                        AceBean newAceBean = new AceBean();
                        setupAceBean(principalName, currentAceDefinition,
                                newAceBean);
                        if (aceBeanValidator != null) {
                            aceBeanValidator.validate(newAceBean);
                        }
    
                        // --- handle wildcards ---
    
                        if (newAceBean.getJcrPath() != null
                                && newAceBean.getJcrPath().contains("*")
                                && null != session) {
                            handleWildcards(session, aceMap, principalName,
                                    newAceBean);
                        } else {
                            aceMap.get(principalName).add(newAceBean);
                        }
                    }
                }
            }
            return aceMap;
        } finally {
            if (session != null) {
                session.logout();
            }
        }
    }

    private Pattern forLoopPattern = Pattern.compile("(\\w+) in \\[([,\\s\\w]+)\\]");
    
    protected List<AceBean> unrollForLoop(Map<String, String> config, String principalName) {
        List<AceBean> beans = new LinkedList<AceBean>();
        String forSpec = config.get(ACE_CONFIG_PROPERTY_FOR);
        Matcher matcher = forLoopPattern.matcher(forSpec);
        if (matcher.find()) {
            String var = matcher.group(1);
            String in = matcher.group(2);
            String[] values = in.split(",\\s+");
            for (String value : values) {
                AceBean newAceBean = new AceBean();
                // Replace variables in config
                Map<String, String> unrolledConfig = new HashMap<String, String>();
                String regex = "\\$\\{" + var + "}";
                for (String key : config.keySet()) {
                    LOG.info("{} => {}", key, config.get(key));
                    String val = config.get(key);
                    String newVal = val == null ? null : val.replaceAll(regex, value.trim());
                    unrolledConfig.put(key, newVal);
                }
                setupAceBean(principalName, unrolledConfig, newAceBean);
                beans.add(newAceBean);
            }
        } else {
            LOG.error("Incorrect for loop syntax: {}", forSpec);
        }
        return beans;
    }

    private void handleWildcards(final Session session,
            Map<String, Set<AceBean>> aceMap, String principal,
            AceBean tmpAclBean) throws InvalidQueryException,
            RepositoryException {
        // perform query using the path containing wildcards
        String query = "/jcr:root" + tmpAclBean.getJcrPath();
        Set<Node> result = QueryHelper.getNodes(session, query);

        // if there are nodes in repository matching the wildcard-query
        // replace the bean holding the wildcard path by beans having the found
        // paths
        Set<AceBean> replaceBeans = new HashSet<AceBean>();

        if (result.isEmpty()) {
            return;
        }
        for (Node node : result) {
            // ignore rep:policy nodes
            if (!node.getPath().contains("/rep:policy")) {
                AceBean replacementBean = new AceBean();
                replacementBean.setJcrPath(node.getPath());
                replacementBean.setActions(tmpAclBean.getActions());
                replacementBean.setAllow(tmpAclBean.isAllow());
                replacementBean.setPrincipal(tmpAclBean.getPrincipalName());
                replacementBean.setPrivilegesString(tmpAclBean
                        .getPrivilegesString());

                if (!aceMap.get(principal).add(replacementBean)) {
                    LOG.warn("wildcard replacement: replacing bean: "
                            + tmpAclBean + ", with bean " + replacementBean
                            + " failed!");
                } else {
                    LOG.info("wildcard replacement: replaced bean: "
                            + tmpAclBean + ", with bean " + replacementBean);
                }
            }
        }
    }

    private void setupAceBean(String principal,
            Map<String, String> currentAceDefinition, AceBean tmpAclBean) {
        tmpAclBean.setPrincipal(principal);
        tmpAclBean.setJcrPath(getMapValueAsString(currentAceDefinition,
                ACE_CONFIG_PROPERTY_PATH));
        tmpAclBean.setActionsStringFromConfig(getMapValueAsString(
                currentAceDefinition, ACE_CONFIG_PROPERTY_ACTIONS));
        tmpAclBean.setPrivilegesString(getMapValueAsString(
                currentAceDefinition, ACE_CONFIG_PROPERTY_PRIVILEGES));
        tmpAclBean.setPermissionString(getMapValueAsString(
                currentAceDefinition, ACE_CONFIG_PROPERTY_PERMISSION));
        tmpAclBean.setRepGlob(getMapValueAsString(currentAceDefinition,
                ACE_CONFIG_PROPERTY_GLOB));
        tmpAclBean.setAssertedExceptionString(getMapValueAsString(
                currentAceDefinition, ASSERTED_EXCEPTION));
    }

    private void setupAuthorizableBean(
            AuthorizableConfigBean authorizableConfigBean,
            Map<String, String> currentPrincipalDataMap,
            final String authorizableId) {
        authorizableConfigBean.setPrincipalID(authorizableId);
        authorizableConfigBean.setAuthorizableName(getMapValueAsString(
                currentPrincipalDataMap, GROUP_CONFIG_PROPERTY_NAME));
        authorizableConfigBean.setMemberOfString(getMapValueAsString(
                currentPrincipalDataMap, GROUP_CONFIG_PROPERTY_MEMBER_OF));
        authorizableConfigBean.setPath(getMapValueAsString(
                currentPrincipalDataMap, GROUP_CONFIG_PROPERTY_PATH));
        authorizableConfigBean.setIsGroup(true);
        authorizableConfigBean.setPassword(getMapValueAsString(
                currentPrincipalDataMap, GROUP_CONFIG_PROPERTY_PASSWORD));
        authorizableConfigBean.setAssertedExceptionString(getMapValueAsString(
                currentPrincipalDataMap, ASSERTED_EXCEPTION));
    }

    private String getMapValueAsString(
            final Map<String, String> currentAceDefinition,
            final String propertyName) {
        if (currentAceDefinition.get(propertyName) != null) {
            return currentAceDefinition.get(propertyName);
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getTemplateConfiguration(Collection<?> configData) {
        List<Map<?, ?>> templates = (List<Map<?, ?>>) getConfigSection(Constants.TEMPLATE_CONFIGURATION_KEY, configData);
        if (templates == null) {
            LOG.info("Template section missing from configuration file.");
            return null;
        }
        Map<String, String> templateMappings = new HashMap<String, String>();
        for (Map<?,  ?> entry : templates) {
            if (entry.get("path") == null) {
                LOG.warn("Template configuration entry {} is missing value for path.", entry);
            }
            if (entry.get("template") == null) {
                LOG.warn("Template configuration entry {} is missing value for template.", entry);
            }
            templateMappings.put(entry.get("path").toString(), entry.get("template").toString());
        }
        return templateMappings;
    }

}
