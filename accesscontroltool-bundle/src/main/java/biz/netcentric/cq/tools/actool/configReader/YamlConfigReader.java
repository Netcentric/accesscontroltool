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

import org.apache.commons.lang.StringUtils;
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

    private static final String ACE_CONFIG_PROPERTY_GLOB = "repGlob";
    private static final String ACE_CONFIG_PROPERTY_PERMISSION = "permission";
    private static final String ACE_CONFIG_PROPERTY_PRIVILEGES = "privileges";
    private static final String ACE_CONFIG_PROPERTY_ACTIONS = "actions";
    private static final String ACE_CONFIG_PROPERTY_PATH = "path";
    private static final String ACE_CONFIG_PROPERTY_FOR = "for";

    private static final String GROUP_CONFIG_PROPERTY_MEMBER_OF = "isMemberOf";
    private static final String GROUP_CONFIG_PROPERTY_MEMBER_OF_LEGACY = "memberOf";
    private static final String GROUP_CONFIG_PROPERTY_MEMBERS = "members";
    private static final String GROUP_CONFIG_PROPERTY_PATH = "path";
    private static final String GROUP_CONFIG_PROPERTY_IS_GROUP = "isGroup";
    private static final String GROUP_CONFIG_PROPERTY_PASSWORD = "password";
    private static final String GROUP_CONFIG_PROPERTY_NAME = "name";

    // FIXME: This class should not depend on Sling
    @Reference
    private SlingRepository repository;

    private final String ASSERTED_EXCEPTION = "assertedException";

    private final Logger LOG = LoggerFactory.getLogger(YamlConfigReader.class);

    /*
     * (non-Javadoc)
     * 
     * @see biz.netcentric.cq.tools.actool.helper.ConfigReader#getAceConfigurationBeans (javax.jcr.Session, java.util.List, java.util.Set,
     * biz.netcentric.cq.tools.actool.validators.AceBeanValidator)
     */
    @Override
    @SuppressWarnings("rawtypes")
    public Map<String, Set<AceBean>> getAceConfigurationBeans(
            final Collection<?> aceConfigData, final Set<String> groupsFromConfig,
            final AceBeanValidator aceBeanValidator) throws RepositoryException,
            AcConfigBeanValidationException {

        final List<LinkedHashMap> aclList = (List<LinkedHashMap>) getConfigSection(
                Constants.ACE_CONFIGURATION_KEY, aceConfigData);

        if (aclList == null) {
            LOG.error("ACL configuration not found in YAML configuration file");
            return null;
        }

        final Map<String, Set<AceBean>> aceMapFromConfig = getPreservedOrderdAceMap(
                aclList, groupsFromConfig, aceBeanValidator); // group based Map
                                                              // from config
                                                              // file
        return aceMapFromConfig;

    }

    /*
     * (non-Javadoc)
     * 
     * @see biz.netcentric.cq.tools.actool.helper.ConfigReader# getAuthorizableConfigurationBeans(java.util.List,
     * biz.netcentric.cq.tools.actool.validators.AuthorizableValidator)
     */
    @Override
    public Map<String, Set<AuthorizableConfigBean>> getGroupConfigurationBeans(
            final Collection yamlList,
            final AuthorizableValidator authorizableValidator)
            throws AcConfigBeanValidationException {

        final List<LinkedHashMap> authorizableList = (List<LinkedHashMap>) getConfigSection(
                Constants.GROUP_CONFIGURATION_KEY, yamlList);

        if (authorizableList == null) {
            LOG.error("group configuration not found in YAML configuration file");
            return null;
        }

        final Map<String, Set<AuthorizableConfigBean>> principalsMap = getAuthorizablesMap(
                authorizableList, authorizableValidator);
        return principalsMap;
    }

    private Collection getConfigSection(final String sectionName,
            final Collection yamlList) {
        final List<LinkedHashMap<?, ?>> yamList = new ArrayList<LinkedHashMap<?, ?>>(
                yamlList);
        for (final LinkedHashMap<?, ?> currMap : yamList) {
            if (sectionName.equals(currMap.keySet().iterator().next())) {
                return (List<LinkedHashMap>) currMap.get(sectionName);
            }
        }
        return null;
    }

    @Override
    public Map<String, LinkedHashSet<AuthorizableConfigBean>> getUserConfigurationBeans(
            final Collection<?> userConfigData,
            final AuthorizableValidator authorizableValidator)
            throws AcConfigBeanValidationException {
        // TODO Auto-generated method stub
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see biz.netcentric.cq.tools.actool.helper.ConfigReader#getAuthorizablesMap (java.util.List,
     * biz.netcentric.cq.tools.actool.validators.AuthorizableValidator)
     */

    private Map<String, Set<AuthorizableConfigBean>> getAuthorizablesMap(
            final List<LinkedHashMap> yamlMap,
            final AuthorizableValidator authorizableValidator)
            throws AcConfigBeanValidationException {
        final Set<String> alreadyProcessedGroups = new HashSet<String>();
        final Map<String, Set<AuthorizableConfigBean>> principalMap = new LinkedHashMap<String, Set<AuthorizableConfigBean>>();

        if (yamlMap == null) {
            return principalMap;
        }
        final Session session = null;

        for (final LinkedHashMap currentMap : yamlMap) {

            final String currentPrincipal = (String) currentMap.keySet().iterator()
                    .next();

            final Matcher matcher = forLoopPattern.matcher(currentPrincipal);
            if (matcher.find()) {
                LOG.info("Principal name {} matches FOR loop pattern. Unrolling {}", currentPrincipal, currentMap.get(currentPrincipal));
                final List<AuthorizableConfigBean> groups = unrollGroupForLoop(currentPrincipal, (List<Map<String, ?>>) currentMap.get(currentPrincipal),
                        new HashMap<String, String>());
                for (final AuthorizableConfigBean group : groups) {
                    final String principal = group.getPrincipalID();
                    if (!alreadyProcessedGroups.add(principal)) {
                        throw new IllegalArgumentException(
                                "There is more than one group definition for group: "
                                        + principal);
                    }
                    if (authorizableValidator != null) {
                        authorizableValidator.validate(group);
                    }
                    final Set<AuthorizableConfigBean> tmpSet = new LinkedHashSet<AuthorizableConfigBean>();
                    tmpSet.add(group);
                    principalMap.put(principal, tmpSet);
                }
            } else {

                if (!alreadyProcessedGroups.add(currentPrincipal)) {
                    throw new IllegalArgumentException(
                            "There is more than one group definition for group: "
                                    + currentPrincipal);
                }
                LOG.info("start reading group configuration");
                LOG.info("Found principal: {} in config", currentPrincipal);

                final LinkedHashSet<AuthorizableConfigBean> tmpSet = new LinkedHashSet<AuthorizableConfigBean>();
                principalMap.put(currentPrincipal, tmpSet);

                final List<Map<String, String>> currentPrincipalData = (List<Map<String, String>>) currentMap
                        .get(currentPrincipal);

                if (currentPrincipalData != null && !currentPrincipalData.isEmpty()) {

                    for (final Map<String, String> currentPrincipalDataMap : currentPrincipalData) {
                        final AuthorizableConfigBean tmpPrincipalConfigBean = new AuthorizableConfigBean();
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
        }
        return principalMap;

    }

    private Map<String, Set<AceBean>> getPreservedOrderdAceMap(
            final List<LinkedHashMap> aceYamlList,
            final Set<String> groupsFromCurrentConfig,
            final AceBeanValidator aceBeanValidator) throws RepositoryException,
            AcConfigBeanValidationException {

        // stores the names of groups for which the ACEs already have been
        // processed in order to detect doubled defined groups
        final Set<String> alreadyProcessedGroups = new HashSet<String>();
        final Map<String, Set<AceBean>> aceMap = new LinkedHashMap<String, Set<AceBean>>();

        if (aceYamlList == null) {
            return aceMap;
        }
        Session session = null;
        try {
            if (repository != null) {
                session = repository.loginAdministrative(null);
            }
            for (final Map<String, List<Map<String, ?>>> currentPrincipalAceMap : aceYamlList) {

                final String principalName = (String) currentPrincipalAceMap.keySet()
                        .iterator().next();

                final Matcher matcher = forLoopPattern.matcher(principalName);
                if (matcher.find()) {
                    LOG.info("Principal name {} matches FOR loop pattern. Unrolling {}", principalName, currentPrincipalAceMap.get(principalName));
                    final List<AceBean> aces = unrollAceForLoop(principalName, currentPrincipalAceMap.get(principalName), new HashMap<String, String>());
                    for (final AceBean ace : aces) {
                        if (aceMap.get(ace.getPrincipalName()) == null) {
                            final Set<AceBean> tmpSet = new LinkedHashSet<AceBean>();
                            aceMap.put(ace.getPrincipalName(), tmpSet);
                        }
                        aceMap.get(ace.getPrincipalName()).add(ace);
                    }
                } else {
                    final List<Map<String, ?>> aceDefinitions = currentPrincipalAceMap.get(principalName);

                    LOG.info("start reading ACE configuration of authorizable: {}",
                            principalName);

                    // if current principal is not yet in map, add new key and empty
                    // set for storing the pricipals ACE beans
                    if (aceMap.get(principalName) == null) {
                        final Set<AceBean> tmpSet = new LinkedHashSet<AceBean>();
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

                    for (final Map<String, ?> currentAceDefinition : aceDefinitions) {
                        final AceBean newAceBean = new AceBean();
                        setupAceBean(principalName, currentAceDefinition,
                                newAceBean);
                        if (aceBeanValidator != null) {
                            aceBeanValidator.validate(newAceBean, session.getAccessControlManager());
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

    private final Pattern forLoopPattern = Pattern.compile("for (\\w+) in \\[([,/\\s\\w]+)\\]", Pattern.CASE_INSENSITIVE);

    protected List<AceBean> unrollAceForLoop(final String forSpec, final List<Map<String, ?>> groups, final Map<String, String> substitutions) {
        final List<AceBean> beans = new LinkedList<AceBean>();
        final Matcher matcher = forLoopPattern.matcher(forSpec);
        if (matcher.find()) {
            final String var = matcher.group(1);
            final String in = matcher.group(2);
            final String[] values = in.split(",\\s+");
            // Looping over values in FOR statement
            for (final String value : values) {
                // Replace variables in config
                final String regex = "\\$\\{" + var + "}";
                // Looping over groups
                for (final Map<String, ?> group : groups) {
                    final String key = group.keySet().iterator().next();
                    final Matcher matcher2 = forLoopPattern.matcher(key);
                    if (matcher2.find()) {
                        substitutions.put(regex, value);
                        LOG.info("Detected nested loop {}. Unrolling with {}", key, substitutions);
                        final List<AceBean> nestedAces = unrollAceForLoop(key, (List<Map<String, ?>>) group.get(key), substitutions);
                        beans.addAll(nestedAces);
                    } else {
                        String principalName = key.replaceAll(regex, value.trim());
                        for (final String sub : substitutions.keySet()) {
                            principalName = principalName.replaceAll(sub, substitutions.get(sub).trim());
                        }
                        final List<Map<String, ?>> aces = (List<Map<String, ?>>) group.get(key);
                        // Looping over ACEs for group
                        for (final Map<String, ?> ace : aces) {
                            final Map<String, String> unrolledGroup = new HashMap<String, String>();
                            final AceBean newAceBean = new AceBean();
                            // Looping over property values and child objects
                            for (final String key2 : ace.keySet()) {
                                final Object val = ace.get(key2);
                                if (val == null) {
                                    unrolledGroup.put(key2, null);
                                } else if (val instanceof String) {
                                    String newVal = ((String) val).replaceAll(regex, value.trim());
                                    for (final String sub : substitutions.keySet()) {
                                        newVal = newVal.replaceAll(sub, substitutions.get(sub).trim());
                                    }
                                    unrolledGroup.put(key2, newVal);
                                }
                            }
                            setupAceBean(principalName, unrolledGroup, newAceBean);
                            beans.add(newAceBean);
                        }
                    }
                }
            }
        } else {
            LOG.error("Incorrect for loop syntax: {}", forSpec);
        }
        return beans;
    }

    protected List<AuthorizableConfigBean> unrollGroupForLoop(final String forSpec, final List<Map<String, ?>> groups, final Map<String, String> substitutions) {
        final List<AuthorizableConfigBean> beans = new LinkedList<AuthorizableConfigBean>();
        final Matcher matcher = forLoopPattern.matcher(forSpec);
        if (matcher.find()) {
            final String var = matcher.group(1);
            final String in = matcher.group(2);
            final String[] values = in.split(",\\s+");
            // Looping over values in FOR statement
            for (final String value : values) {
                // Replace variables in config
                final String regex = "\\$\\{" + var + "}";
                // Looping over groups
                for (final Map<String, ?> group : groups) {
                    final String key = group.keySet().iterator().next();
                    final Matcher matcher2 = forLoopPattern.matcher(key);
                    if (matcher2.find()) {
                        substitutions.put(regex, value);
                        LOG.info("Detected nested loop {}. Unrolling with {}", key, substitutions);
                        final List<AuthorizableConfigBean> nestedGroups = unrollGroupForLoop(key, (List<Map<String, ?>>) group.get(key), substitutions);
                        beans.addAll(nestedGroups);
                    } else {
                        String principalName = key.replaceAll(regex, value.trim());
                        for (final String sub : substitutions.keySet()) {
                            principalName = principalName.replaceAll(sub, substitutions.get(sub).trim());
                        }
                        final List<Map<String, ?>> groupMaps = (List<Map<String, ?>>) group.get(key);
                        // Looping over ACEs for group
                        for (final Map<String, ?> props : groupMaps) {
                            final Map<String, String> unrolledGroup = new HashMap<String, String>();
                            final AuthorizableConfigBean newGroupBean = new AuthorizableConfigBean();
                            // Looping over property values and child objects
                            for (final String key2 : props.keySet()) {
                                final Object val = props.get(key2);
                                if (val == null) {
                                    unrolledGroup.put(key2, null);
                                } else if (val instanceof String) {
                                    String newVal = ((String) val).replaceAll(regex, value.trim());
                                    for (final String sub : substitutions.keySet()) {
                                        newVal = newVal.replaceAll(sub, substitutions.get(sub).trim());
                                    }
                                    unrolledGroup.put(key2, newVal);
                                }
                            }
                            setupAuthorizableBean(newGroupBean, unrolledGroup, principalName);
                            beans.add(newGroupBean);
                        }
                    }
                }
            }
        } else {
            LOG.error("Incorrect for loop syntax: {}", forSpec);
        }
        return beans;
    }

    private void handleWildcards(final Session session,
            final Map<String, Set<AceBean>> aceMap, final String principal,
            final AceBean tmpAclBean) throws InvalidQueryException,
            RepositoryException {
        // perform query using the path containing wildcards
        final String query = "/jcr:root" + tmpAclBean.getJcrPath();
        final Set<Node> result = QueryHelper.getNodes(session, query);

        // if there are nodes in repository matching the wildcard-query
        // replace the bean holding the wildcard path by beans having the found
        // paths
        final Set<AceBean> replaceBeans = new HashSet<AceBean>();

        if (result.isEmpty()) {
            return;
        }
        for (final Node node : result) {
            // ignore rep:policy nodes
            if (!node.getPath().contains("/rep:policy")) {
                final AceBean replacementBean = new AceBean();
                replacementBean.setJcrPath(node.getPath());
                replacementBean.setActions(tmpAclBean.getActions());
                replacementBean.setPermission(tmpAclBean.getPermission());
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

    private void setupAceBean(final String principal,
            final Map<String, ?> currentAceDefinition, final AceBean tmpAclBean) {
        tmpAclBean.setPrincipal(principal);
        tmpAclBean.setJcrPath(getMapValueAsString(currentAceDefinition,
                ACE_CONFIG_PROPERTY_PATH));
        tmpAclBean.setActionsStringFromConfig(getMapValueAsString(
                currentAceDefinition, ACE_CONFIG_PROPERTY_ACTIONS));
        tmpAclBean.setPrivilegesString(getMapValueAsString(
                currentAceDefinition, ACE_CONFIG_PROPERTY_PRIVILEGES));
        tmpAclBean.setPermission(getMapValueAsString(
                currentAceDefinition, ACE_CONFIG_PROPERTY_PERMISSION));
        tmpAclBean.setRepGlob((String) currentAceDefinition.get(ACE_CONFIG_PROPERTY_GLOB));
        tmpAclBean.setAssertedExceptionString(getMapValueAsString(
                currentAceDefinition, ASSERTED_EXCEPTION));
        tmpAclBean.setActions(parseActionsString(getMapValueAsString(currentAceDefinition,
                ACE_CONFIG_PROPERTY_ACTIONS)));
    }

    private String[] parseActionsString(final String actionsStringFromConfig) {
        final String[] empty = {};
        return StringUtils.isNotBlank(actionsStringFromConfig) ? actionsStringFromConfig.split(",") : empty;
    }

    private void setupAuthorizableBean(
            final AuthorizableConfigBean authorizableConfigBean,
            final Map<String, String> currentPrincipalDataMap,
            final String authorizableId) {
        authorizableConfigBean.setPrincipalID(authorizableId);
        authorizableConfigBean.setAuthorizableName(getMapValueAsString(
                currentPrincipalDataMap, GROUP_CONFIG_PROPERTY_NAME));
        authorizableConfigBean.setMemberOfString(getMapValueAsString(
                currentPrincipalDataMap, GROUP_CONFIG_PROPERTY_MEMBER_OF));
        // read also memberOf property from legacy scripts
        if (!StringUtils.isEmpty(getMapValueAsString(currentPrincipalDataMap, GROUP_CONFIG_PROPERTY_MEMBER_OF_LEGACY))) {
            authorizableConfigBean.setMemberOfString(getMapValueAsString(
                    currentPrincipalDataMap, GROUP_CONFIG_PROPERTY_MEMBER_OF_LEGACY));
        }
        authorizableConfigBean.setMembersString(getMapValueAsString(
                currentPrincipalDataMap, GROUP_CONFIG_PROPERTY_MEMBERS));
        authorizableConfigBean.setPath(getMapValueAsString(
                currentPrincipalDataMap, GROUP_CONFIG_PROPERTY_PATH));
        authorizableConfigBean.setIsGroup(true);
        authorizableConfigBean.setPassword(getMapValueAsString(
                currentPrincipalDataMap, GROUP_CONFIG_PROPERTY_PASSWORD));
        authorizableConfigBean.setAssertedExceptionString(getMapValueAsString(
                currentPrincipalDataMap, ASSERTED_EXCEPTION));
    }

    private String getMapValueAsString(
            final Map<String, ?> currentAceDefinition,
            final String propertyName) {
        if (currentAceDefinition.get(propertyName) != null) {
            return currentAceDefinition.get(propertyName).toString();
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getTemplateConfiguration(final Collection<?> configData) {
        final List<Map<?, ?>> templates = (List<Map<?, ?>>) getConfigSection(Constants.TEMPLATE_CONFIGURATION_KEY, configData);
        if (templates == null) {
            LOG.info("Template section missing from configuration file.");
            return null;
        }
        final Map<String, String> templateMappings = new HashMap<String, String>();
        for (final Map<?, ?> entry : templates) {
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
