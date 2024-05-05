/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configreader;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.InvalidQueryException;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.util.ISO9075;
import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.configmodel.AcesConfig;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizablesConfig;
import biz.netcentric.cq.tools.actool.configmodel.GlobalConfiguration;
import biz.netcentric.cq.tools.actool.configmodel.pkcs.Key;
import biz.netcentric.cq.tools.actool.configmodel.pkcs.PrivateKeyDecryptor;
import biz.netcentric.cq.tools.actool.crypto.DecryptionService;
import biz.netcentric.cq.tools.actool.helper.Constants;
import biz.netcentric.cq.tools.actool.helper.QueryHelper;
import biz.netcentric.cq.tools.actool.validators.AceBeanValidator;
import biz.netcentric.cq.tools.actool.validators.AuthorizableValidator;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;
import biz.netcentric.cq.tools.actool.validators.exceptions.InvalidAuthorizableException;

@Component()
public class YamlConfigReader implements ConfigReader {
    private static final Logger LOG = LoggerFactory.getLogger(YamlConfigReader.class);

    protected static final String ACE_CONFIG_PROPERTY_GLOB = "repGlob";
    protected static final String ACE_CONFIG_PROPERTY_RESTRICTIONS = "restrictions";

    protected static final String ACE_CONFIG_PROPERTY_PERMISSION = "permission";
    protected static final String ACE_CONFIG_PROPERTY_PRIVILEGES = "privileges";
    protected static final String ACE_CONFIG_PROPERTY_ACTIONS = "actions";
    protected static final String ACE_CONFIG_PROPERTY_PATH = "path";
    protected static final String ACE_CONFIG_PROPERTY_KEEP_ORDER = "keepOrder";
    protected static final String ACE_CONFIG_INITIAL_CONTENT = "initialContent";

    private static final String GROUP_CONFIG_PROPERTY_IS_MEMBER_OF = "isMemberOf";
    private static final String GROUP_CONFIG_PROPERTY_MEMBER_OF_LEGACY = "memberOf";
    private static final String GROUP_CONFIG_PROPERTY_MEMBERS = "members";
    private static final String GROUP_CONFIG_PROPERTY_PATH = "path";
    private static final String GROUP_CONFIG_PROPERTY_PASSWORD = "password";
    protected static final String GROUP_CONFIG_PROPERTY_NAME = "name";
    private static final String GROUP_CONFIG_PROPERTY_DESCRIPTION = "description";
    private static final String GROUP_CONFIG_PROPERTY_EXTERNAL_ID = "externalId";

    private static final String GROUP_CONFIG_PROPERTY_MIGRATE_FROM = "migrateFrom";

    private static final String GROUP_CONFIG_PROPERTY_UNMANAGED_ACE_PATHS_REGEX = "unmanagedAcePathsRegex";
    private static final String GROUP_CONFIG_PROPERTY_UNMANAGED_EXTERNAL_ISMEMBEROF_REGEX = "unmanagedExternalIsMemberOfRegex";
    private static final String GROUP_CONFIG_PROPERTY_UNMANAGED_EXTERNAL_MEMBERS_REGEX = "unmanagedExternalMembersRegex";

    private static final String GROUP_CONFIG_IS_VIRTUAL = "virtual";

    private static final String USER_CONFIG_PROPERTY_IS_SYSTEM_USER = "isSystemUser";

    private static final String USER_CONFIG_PROFILE_CONTENT = "profileContent";
    private static final String USER_CONFIG_PREFERENCES_CONTENT = "preferencesContent";
    private static final String USER_CONFIG_SOCIAL_CONTENT = "socialContent";

    private static final String USER_CONFIG_PROPERTY_EMAIL = "email";
    
    
    private static final String USER_CONFIG_DISABLED = "disabled";
    private static final String USER_CONFIG_KEYS = "keys";
    private static final String USER_CONFIG_APPEND_TO_KEYSTORE = "appendToKeyStore";
    private static final String USER_CONFIG_KEYSTORE_PASSWORD = "keyStorePassword";
    private static final String USER_CONFIG_KEY_PUBLIC = "public";
    private static final String USER_CONFIG_KEY_PRIVATE_PASSWORD = "privatePassword";
    private static final String USER_CONFIG_KEY_PRIVATE = "private";
    private static final String USER_CONFIG_KEY_CERTIFICATE = "certificate";

    private static final String USER_CONFIG_IMPERSONATION_ALLOWED_FOR = "impersonationAllowedFor";
    
    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private SlingRepository repository;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    DecryptionService decryptionService;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    PrivateKeyDecryptor privateKeyDecryptor;

    @Override
    @SuppressWarnings("rawtypes")
    public AcesConfig getAceConfigurationBeans(final Collection<?> aceConfigData,
            final AceBeanValidator aceBeanValidator, Session session, String sourceFile)
            throws RepositoryException, AcConfigBeanValidationException {

        final List<LinkedHashMap> aclList = (List<LinkedHashMap>) getConfigSection(Constants.ACE_CONFIGURATION_KEY, aceConfigData);

        if (aclList == null) {
            LOG.debug("ACL configuration not found in this YAML configuration file");
            return null;
        }

        // group based Map from config file
        AcesConfig aceMapFromConfig = getPreservedOrderdAceSet(aclList, aceBeanValidator, session, sourceFile);
        return aceMapFromConfig;

    }

    @Override
    public AuthorizablesConfig getGroupConfigurationBeans(final Collection yamlList,
            final AuthorizableValidator authorizableValidator) throws AcConfigBeanValidationException {

        final List<LinkedHashMap> authorizableList = (List<LinkedHashMap>) getConfigSection(Constants.GROUP_CONFIGURATION_KEY, yamlList);

        if (authorizableList == null) {
            LOG.debug("Group configuration not found in this YAML configuration file");
            return null;
        }

        AuthorizablesConfig authorizableBeans = getAuthorizableBeans(authorizableList, authorizableValidator, true);
        return authorizableBeans;
    }

    @Override
    public AuthorizablesConfig getUserConfigurationBeans(final Collection yamlList,
            final AuthorizableValidator authorizableValidator) throws AcConfigBeanValidationException {

        List<LinkedHashMap> authorizableList = (List<LinkedHashMap>) getConfigSection(Constants.USER_CONFIGURATION_KEY, yamlList);

        AuthorizablesConfig authorizableBeans = getAuthorizableBeans(authorizableList, authorizableValidator, false);
        return authorizableBeans;
    }

    @Override
    public GlobalConfiguration getGlobalConfiguration(final Collection yamlList) {

        Map globalConfigMap = (Map) getConfigSection(Constants.GLOBAL_CONFIGURATION_KEY, yamlList);

        GlobalConfiguration globalConfiguration = new GlobalConfiguration(globalConfigMap);

        return globalConfiguration;
    }

    @Override
    public Set<String> getObsoluteAuthorizables(final Collection yamlList) {

        List obsoleteAuthorizablesList = (List) getConfigSection(Constants.OBSOLETE_AUTHORIZABLES_KEY, yamlList);

        Set<String> obsoleteAuthorizables = new HashSet<String>();
        if (obsoleteAuthorizablesList != null) {
            for (Object obsoleteAuthorizable : obsoleteAuthorizablesList) {
                if (obsoleteAuthorizable instanceof String) {
                    obsoleteAuthorizables.add((String) obsoleteAuthorizable);
                } else if (obsoleteAuthorizable instanceof Map) {
                    // besides plain string also allow map
                    // (that way it is possible to copy sections one-to-one from group_config to obsolete_authorizables)
                    Map map = (Map) obsoleteAuthorizable;
                    obsoleteAuthorizables.add((String) map.keySet().iterator().next());
                }
            }
        }
        return obsoleteAuthorizables;
    }

    private Object getConfigSection(final String sectionName, final Collection yamlList) {
        final List<LinkedHashMap<?, ?>> yamList = new ArrayList<LinkedHashMap<?, ?>>(yamlList);
        for (final LinkedHashMap<?, ?> currMap : yamList) {
            Iterator<?> keyIt = currMap.keySet().iterator();
            if (keyIt.hasNext() && sectionName.equals(keyIt.next())) {
                return currMap.get(sectionName);
            }
        }
        return null;
    }

    private AuthorizablesConfig getAuthorizableBeans(
            List<LinkedHashMap> yamlMap, final AuthorizableValidator authorizableValidator, boolean isGroupSection)
            throws AcConfigBeanValidationException {
        final Set<String> alreadyProcessedGroups = new HashSet<String>();
        final AuthorizablesConfig authorizableBeans = new AuthorizablesConfig();

        if (yamlMap == null) {
            return authorizableBeans;
        }

        for (final LinkedHashMap currentMap : yamlMap) {

            final String currentAuthorizableIdFromYaml = (String) currentMap.keySet().iterator().next();

            if (!alreadyProcessedGroups.add(currentAuthorizableIdFromYaml)) {
                throw new IllegalArgumentException("There is more than one group definition for group: " + currentAuthorizableIdFromYaml);
            }
            LOG.trace("Found principal: {} in config", currentAuthorizableIdFromYaml);

            final List<Map<String, Object>> currentAuthorizableData = (List<Map<String, Object>>) currentMap.get(currentAuthorizableIdFromYaml);

            if ((currentAuthorizableData != null) && !currentAuthorizableData.isEmpty()) {

                if (currentAuthorizableData.size() > 1) {
                    throw new AcConfigBeanValidationException("Invalid authorizable " + currentAuthorizableIdFromYaml
                            + " - configuration needs to contain exactly one yaml list entry");
                }
                try {
                    Map<String, Object> currentPrincipalDataMap = currentAuthorizableData.get(0);
                    final AuthorizableConfigBean tmpPrincipalConfigBean = getNewAuthorizableConfigBean();
                    setupAuthorizableBean(tmpPrincipalConfigBean, currentPrincipalDataMap, currentAuthorizableIdFromYaml, isGroupSection);
                    if (authorizableValidator != null) {
                        authorizableValidator.validate(tmpPrincipalConfigBean);
                    }
                    authorizableBeans.add(tmpPrincipalConfigBean);
                } catch (AcConfigBeanValidationException e) {
                    throw new AcConfigBeanValidationException("Invalid authorizable " + currentAuthorizableIdFromYaml, e);
                }
            }

        }
        return authorizableBeans;

    }

    private AcesConfig getPreservedOrderdAceSet(List<LinkedHashMap> aceYamlList,
            AceBeanValidator aceBeanValidator, Session session, String sourceFile) throws RepositoryException,
            AcConfigBeanValidationException {

        final AcesConfig aceSet = new AcesConfig();

        if (aceYamlList == null) {
            return aceSet;
        }

        for (final Map<String, List<Map<String, ?>>> currentPrincipalAceMap : aceYamlList) {

            final String authorizableId = currentPrincipalAceMap.keySet().iterator().next();

            final List<Map<String, ?>> aceDefinitions = currentPrincipalAceMap.get(authorizableId);

            LOG.trace("Start reading ACE configuration of authorizable: {}", authorizableId);

            if ((aceDefinitions == null) || aceDefinitions.isEmpty()) {
                LOG.warn("No ACE definition(s) found for authorizable: {}", authorizableId);
                continue;
            }

            // create ACE bean and populate it according to the properties
            // in the config
            for (final Map<String, ?> currentAceDefinition : aceDefinitions) {
                AceBean newAceBean = getNewAceBean();
                setupAceBean(authorizableId, currentAceDefinition, newAceBean, sourceFile);
                if (aceBeanValidator != null) {
                    aceBeanValidator.validate(newAceBean, session.getAccessControlManager());
                }

                // --- handle wildcards ---

                if ((newAceBean.getJcrPath() != null)
                        && newAceBean.getJcrPath().contains("*")
                        && (null != session)) {
                    handleWildcards(session, aceSet, authorizableId, newAceBean);
                } else {
                    aceSet.add(newAceBean);
                }
            }

        }
        return aceSet;

    }

    protected void handleWildcards(final Session session,
            final Set<AceBean> aceSet, final String principal,
            final AceBean tmpAclBean) throws InvalidQueryException,
            RepositoryException {
        // perform query using the path containing wildcards
        final String query = createXPathQueryForPathWithWildcards(tmpAclBean.getJcrPath());
        final Set<String> result = QueryHelper.getNodePathsFromQuery(session, query);

        if (result.isEmpty()) {
            return;
        }
        for (final String path : result) {
            // ignore rep:policy nodes
            if (!path.contains("/rep:policy")) {
                final AceBean replacementBean = tmpAclBean.clone();
                replacementBean.setJcrPath(path);

                if (aceSet.add(replacementBean)) {
                    LOG.info("Wildcard replacement: Cloned " + tmpAclBean + " to " + replacementBean);
                } else {
                    LOG.warn("Wildcard replacement failed: Cloned "
                            + tmpAclBean + " to " + replacementBean
                            + " but bean was already in set");
                }
            }
        }
    }

    /**
     * Create an XPath query for the given JCR path (which may contain wildcards ({@code *}) which should be preserved).
     * @param jcrPathWithWildcard a valid JCR path where some names/elements might be wildcards.
     * 
     * @see <a href="https://jackrabbit.apache.org/archive/wiki/JCR/EncodingAndEscaping_115513396.html#EncodingAndEscaping-Encodingpathinqueries">Encoding Path In XPath Queries</a>
     * @see <a href="https://issues.apache.org/jira/browse/JCR-5051">JCR-5051</a>
     */
    static final String createXPathQueryForPathWithWildcards(String jcrPathWithWildcard) {
        final String query = "/jcr:root" + ISO9075.encodePath(jcrPathWithWildcard);
        // decode wildcards again (https://issues.apache.org/jira/browse/JCR-5051)
        return query.replace("_x002a_", "*");
    }

    protected AceBean getNewAceBean() {
        return new AceBean();
    }

    protected AuthorizableConfigBean getNewAuthorizableConfigBean() {
        return new AuthorizableConfigBean();
    }

    protected void setupAceBean(final String authorizableId, final Map<String, ?> currentAceDefinition, final AceBean aclBean, String sourceFile) {

        aclBean.setAuthorizableId(authorizableId);
        aclBean.setPrincipalName(authorizableId); // to ensure it is set, later corrected if necessary in
                                                  // AcConfiguration.ensureAceBeansHaveCorrectPrincipalNameSet()

        String jcrPath = getMapValueAsString(currentAceDefinition, ACE_CONFIG_PROPERTY_PATH).trim();
        // remove trailing slashes (but retain simple slashes)
        jcrPath = (!jcrPath.equals("/") && jcrPath.endsWith("/")) ? StringUtils.removeEnd(jcrPath, "/") : jcrPath;
        aclBean.setJcrPath(jcrPath);

        aclBean.setPrivilegesString(getMapValueAsString(currentAceDefinition, ACE_CONFIG_PROPERTY_PRIVILEGES));
        aclBean.setPermission(getMapValueAsString(currentAceDefinition, ACE_CONFIG_PROPERTY_PERMISSION));

        aclBean.setRestrictions(currentAceDefinition.get(ACE_CONFIG_PROPERTY_RESTRICTIONS),(String) currentAceDefinition.get(ACE_CONFIG_PROPERTY_GLOB));
        aclBean.setActions(parseActionsString(getMapValueAsString(currentAceDefinition, ACE_CONFIG_PROPERTY_ACTIONS)));

        aclBean.setKeepOrder(Boolean.valueOf(getMapValueAsString(currentAceDefinition, ACE_CONFIG_PROPERTY_KEEP_ORDER)));

        String initialContent = getMapValueAsString(currentAceDefinition, ACE_CONFIG_INITIAL_CONTENT);
        aclBean.setInitialContent(initialContent);
        
        aclBean.setConfigSource(sourceFile);
    }

    public static String[] parseActionsString(final String actionsStringFromConfig) {
        final String[] empty = {};
        return StringUtils.isNotBlank(actionsStringFromConfig) ? actionsStringFromConfig.split(",") : empty;
    }

    protected void setupAuthorizableBean(
            final AuthorizableConfigBean authorizableConfigBean,
            final Map<String, Object> currentPrincipalDataMap,
            final String authorizableId,
            boolean isGroupSection) throws AcConfigBeanValidationException {
        authorizableConfigBean.setAuthorizableId(authorizableId);

        authorizableConfigBean.setName(getMapValueAsString(currentPrincipalDataMap, GROUP_CONFIG_PROPERTY_NAME));

        authorizableConfigBean.setEmail(getMapValueAsString(
                currentPrincipalDataMap, USER_CONFIG_PROPERTY_EMAIL));
        authorizableConfigBean.setDescription(getMapValueAsString(
                currentPrincipalDataMap, GROUP_CONFIG_PROPERTY_DESCRIPTION));
        
        String externalIdVal = getMapValueAsString(currentPrincipalDataMap, GROUP_CONFIG_PROPERTY_EXTERNAL_ID);
        if (StringUtils.isNotBlank(externalIdVal)) {
            authorizableConfigBean.setExternalId(externalIdVal);
            // if an externalId is used, the principalName differs from authorizableId
            String principalName = StringUtils.substringBeforeLast(externalIdVal, ";");
            authorizableConfigBean.setPrincipalName(principalName);
        } else {
            // default: rep:authorizableId and rep:principalName are equal
            authorizableConfigBean.setPrincipalName(authorizableId);
        }

        Object isMemberOfVal = currentPrincipalDataMap.containsKey(GROUP_CONFIG_PROPERTY_IS_MEMBER_OF) ? 
                currentPrincipalDataMap.get(GROUP_CONFIG_PROPERTY_IS_MEMBER_OF) : 
                    currentPrincipalDataMap.get(GROUP_CONFIG_PROPERTY_MEMBER_OF_LEGACY);
        if(isMemberOfVal instanceof String) {
            authorizableConfigBean.setIsMemberOf(((String) isMemberOfVal).trim().split("\\s*,\\s*"));
        } else if(isMemberOfVal instanceof List) {
            authorizableConfigBean.setIsMemberOf((List<String>) isMemberOfVal);
        }

        Object membersVal = currentPrincipalDataMap.get(GROUP_CONFIG_PROPERTY_MEMBERS);
        if(membersVal instanceof String) {
            authorizableConfigBean.setMembers(((String) membersVal).trim().split("\\s*,\\s*"));
        } else if(membersVal instanceof List) {
            List<String> membersList = (List<String>) membersVal;
            authorizableConfigBean.setMembers(membersList.toArray(new String[membersList.size()]));
        }
        
        
        authorizableConfigBean.setPath(normalizePath(getMapValueAsString(
                currentPrincipalDataMap, GROUP_CONFIG_PROPERTY_PATH)));

        authorizableConfigBean.setMigrateFrom(getMapValueAsString(currentPrincipalDataMap,
                GROUP_CONFIG_PROPERTY_MIGRATE_FROM));

        authorizableConfigBean.setUnmanagedAcePathsRegex(getMapValueAsString(currentPrincipalDataMap,
                GROUP_CONFIG_PROPERTY_UNMANAGED_ACE_PATHS_REGEX));
        authorizableConfigBean.setUnmanagedExternalIsMemberOfRegex(getMapValueAsString(currentPrincipalDataMap,
                GROUP_CONFIG_PROPERTY_UNMANAGED_EXTERNAL_ISMEMBEROF_REGEX));
        authorizableConfigBean.setUnmanagedExternalMembersRegex(getMapValueAsString(currentPrincipalDataMap,
                GROUP_CONFIG_PROPERTY_UNMANAGED_EXTERNAL_MEMBERS_REGEX));

        authorizableConfigBean.setVirtual(Boolean.valueOf(getMapValueAsString(currentPrincipalDataMap,
                GROUP_CONFIG_IS_VIRTUAL)));

        authorizableConfigBean.setIsGroup(isGroupSection);
        authorizableConfigBean.setIsSystemUser(Boolean.valueOf(getMapValueAsString(currentPrincipalDataMap,
                USER_CONFIG_PROPERTY_IS_SYSTEM_USER)));

        if(currentPrincipalDataMap.containsKey(GROUP_CONFIG_PROPERTY_PASSWORD)) {
            authorizableConfigBean.setPassword(getMapValueAsString(
                    currentPrincipalDataMap, GROUP_CONFIG_PROPERTY_PASSWORD));
        }

        authorizableConfigBean.setProfileContent(getMapValueAsString(
                currentPrincipalDataMap, USER_CONFIG_PROFILE_CONTENT));
        authorizableConfigBean.setPreferencesContent(getMapValueAsString(
                currentPrincipalDataMap, USER_CONFIG_PREFERENCES_CONTENT));
        authorizableConfigBean.setSocialContent(getMapValueAsString(
                currentPrincipalDataMap, USER_CONFIG_SOCIAL_CONTENT));

        if (currentPrincipalDataMap.containsKey(USER_CONFIG_DISABLED)) {
            authorizableConfigBean.setDisabled(getMapValueAsString(currentPrincipalDataMap, USER_CONFIG_DISABLED));
        }

        authorizableConfigBean.setAppendToKeyStore(Boolean.valueOf(getMapValueAsString(currentPrincipalDataMap, USER_CONFIG_APPEND_TO_KEYSTORE)));
        if (currentPrincipalDataMap.containsKey(USER_CONFIG_KEYS)) {
            Object configObjUserConfigKeys = currentPrincipalDataMap.get(USER_CONFIG_KEYS);
            if (!(configObjUserConfigKeys instanceof Map)) {
                throw new InvalidAuthorizableException("Field '" + USER_CONFIG_KEYS + "' must be a map but is a " + configObjUserConfigKeys.getClass());
            }
            try {
                setupAuthorizableKeys(authorizableConfigBean, (Map<String, Object>) configObjUserConfigKeys);
            } catch (GeneralSecurityException|IOException e) {
                throw new InvalidAuthorizableException("Invalid key format given", e);
            }
        }

        if (currentPrincipalDataMap.containsKey(USER_CONFIG_IMPERSONATION_ALLOWED_FOR)) {
            Object configObjImpersonationAllowedFor = currentPrincipalDataMap.get(USER_CONFIG_IMPERSONATION_ALLOWED_FOR);
            if(configObjImpersonationAllowedFor instanceof String) {
                authorizableConfigBean.setImpersonationAllowedFor(Arrays.asList(((String) configObjImpersonationAllowedFor).trim().split("\\s*,\\s*")));
            } else if (configObjImpersonationAllowedFor instanceof List) {
                authorizableConfigBean.setImpersonationAllowedFor((List<String>) configObjImpersonationAllowedFor);
            } else {
                throw new InvalidAuthorizableException("Field '" + USER_CONFIG_IMPERSONATION_ALLOWED_FOR + "' must be a list (yaml list or comma-separated string) but is a " + configObjImpersonationAllowedFor.getClass());
            }
        }
        String keystorePassword = getMapValueAsString(currentPrincipalDataMap, USER_CONFIG_KEYSTORE_PASSWORD);
        if (!keystorePassword.isEmpty()) {
            authorizableConfigBean.setKeyStorePassword(keystorePassword);
        }
    }

    private void setupAuthorizableKeys(final AuthorizableConfigBean authorizableConfigBean, Map<String, Object> keys) throws InvalidAuthorizableException, IOException, GeneralSecurityException {
        Map<String, Key> parsedKeys = new HashMap<>();
        for (Entry<String, Object> entry : keys.entrySet()) {
            // ensure correct format
            if (!(entry.getValue() instanceof Map)) {
                throw new InvalidAuthorizableException("Field '" + entry.getKey() + "' must be a map but is a " + entry.getValue().getClass());
            }
            // map is nested
            Map<String, String> keyFields = (Map<String, String>)entry.getValue();
            // TODO: make sure that all fields are strings
            final Key key;
            if (StringUtils.isNotBlank(keyFields.get(USER_CONFIG_KEY_CERTIFICATE))) {
                key = Key.createFromPrivateKeyAndCertificate(decryptionService, keyFields.get(USER_CONFIG_KEY_PRIVATE), keyFields.get(USER_CONFIG_KEY_PRIVATE_PASSWORD), keyFields.get(USER_CONFIG_KEY_CERTIFICATE), privateKeyDecryptor);
            } else {
                key = Key.createFromKeyPair(decryptionService, keyFields.get(USER_CONFIG_KEY_PRIVATE), keyFields.get(USER_CONFIG_KEY_PRIVATE_PASSWORD), keyFields.get(USER_CONFIG_KEY_PUBLIC), privateKeyDecryptor);
            }
            parsedKeys.put(entry.getKey(), key);
        }
        authorizableConfigBean.setKeys(parsedKeys);
    }

    protected String getMapValueAsString(
            final Map<String, ?> currentAceDefinition,
            final String propertyName) {
        if (currentAceDefinition.get(propertyName) != null) {
            return currentAceDefinition.get(propertyName).toString();
        }
        return "";
    }
    
    /**
     * Normalize paths by removing superfluous "/" at the end to prevent unnecessary change detection
     * @param path
     * @return the normalized path
     */
    private static String normalizePath(String path) {
        if (path.endsWith("/") && path.length() > 1) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }

}
