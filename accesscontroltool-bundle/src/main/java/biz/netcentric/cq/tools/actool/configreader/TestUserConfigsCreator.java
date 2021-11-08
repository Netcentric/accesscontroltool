/*
 * (C) Copyright 2017 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configreader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.configmodel.AcConfiguration;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizablesConfig;
import biz.netcentric.cq.tools.actool.configmodel.AutoCreateTestUsersConfig;
import biz.netcentric.cq.tools.actool.configmodel.GlobalConfiguration;
import biz.netcentric.cq.tools.actool.crypto.DecryptionService;
import biz.netcentric.cq.tools.actool.history.InstallationLogger;
import biz.netcentric.cq.tools.actool.slingsettings.ExtendedSlingSettingsService;

@Component(service=TestUserConfigsCreator.class)
public class TestUserConfigsCreator {

    private static final Logger LOG = LoggerFactory.getLogger(TestUserConfigsCreator.class);

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    ExtendedSlingSettingsService slingSettingsService;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    DecryptionService decryptionService;

    YamlMacroElEvaluator elEvaluator = null;
    
    public boolean isSkippedForRunmode(List<String> skipForRunmodes) {
        return slingSettingsService != null && !CollectionUtils.intersection(slingSettingsService.getRunModes(), skipForRunmodes).isEmpty();
    }

    void createTestUserConfigs(AcConfiguration acConfiguration, InstallationLogger logger) {

        AutoCreateTestUsersConfig autoCreateTestUsersConf = acConfiguration.getGlobalConfiguration().getAutoCreateTestUsersConfig();
        if (autoCreateTestUsersConf == null) {
            return;
        }

        if (isSkippedForRunmode(autoCreateTestUsersConf.getSkipForRunmodes())) {
            return;
        }
        
        List<AuthorizableConfigBean> testUserConfigBeansToAdd = new ArrayList<>();
        AuthorizablesConfig authorizablesConfig = acConfiguration.getAuthorizablesConfig();
        for (AuthorizableConfigBean groupAuthConfigBean : authorizablesConfig) {
            if(!groupAuthConfigBean.isGroup()) {
                continue;
            }
            String groupId = groupAuthConfigBean.getAuthorizableId();
            if (groupId.matches(autoCreateTestUsersConf.getCreateForGroupNamesRegEx())) {
                
                Map<String, Object> vars = getVarsForAuthConfigBean(groupAuthConfigBean);
                
                AuthorizableConfigBean testUserConfigBean = new AuthorizableConfigBean();
                testUserConfigBean.setIsGroup(false);
                String testUserAuthId = autoCreateTestUsersConf.getPrefix() + groupId;
                testUserConfigBean.setAuthorizableId(testUserAuthId);
                testUserConfigBean.setPath(autoCreateTestUsersConf.getPath());
                testUserConfigBean.setIsMemberOf(new String[] { groupId });

                String name = StringUtils.defaultIfEmpty(autoCreateTestUsersConf.getName(), "Test User %{group.name}");
                testUserConfigBean.setName(processValue(name, vars));

                if(StringUtils.isNotBlank(autoCreateTestUsersConf.getEmail())) {
                    testUserConfigBean.setEmail(processValue(autoCreateTestUsersConf.getEmail(), vars));
                }
                if(StringUtils.isNotBlank(autoCreateTestUsersConf.getDescription())) {
                    testUserConfigBean.setDescription(processValue(autoCreateTestUsersConf.getDescription(), vars));
                }
                
                String password = autoCreateTestUsersConf.getPassword();
                if(StringUtils.isNotBlank(password)) {
                    password = processValue(password, vars); // allow for pws ala "pw%{group.id}"
                } else {
                    password = testUserAuthId;
                }

                try {
                    password = decryptionService.decrypt(password);
                } catch (UnsupportedOperationException e) {
                        throw new IllegalArgumentException("Could not unprotect password " + password + " as given in "
                                + GlobalConfiguration.KEY_AUTOCREATE_TEST_USERS);
                }
                testUserConfigBean.setPassword(password);

                testUserConfigBeansToAdd.add(testUserConfigBean);
            }
        }

        authorizablesConfig.addAll(testUserConfigBeansToAdd);

        logger.addMessage(LOG,
                "Created  " + testUserConfigBeansToAdd.size() + " test user configs at path " + autoCreateTestUsersConf.getPath()
                        + " (for groups matching " + autoCreateTestUsersConf.getCreateForGroupNamesRegEx() + ")");

    }

    Map<String, Object> getVarsForAuthConfigBean(AuthorizableConfigBean groupAuthConfigBean) {
        Map<String,Object> vars = new HashMap<>();
        Map<String,String> groupVar = new HashMap<>();
        String groupId = groupAuthConfigBean.getAuthorizableId();
        groupVar.put("id", groupId);
        groupVar.put("name", StringUtils.defaultIfEmpty(groupAuthConfigBean.getName(), groupId));
        groupVar.put("path", groupAuthConfigBean.getPath());
        vars.put("group", groupVar);
        return vars;
    }

    String processValue(String value, Map<? extends Object, ? extends Object> variables) {

        String elWithDollarExpressions = value.replaceAll("%\\{([^\\}]+)\\}", "\\${$1}");
        if(elEvaluator==null) {
            elEvaluator = new YamlMacroElEvaluator();
        }
        
        String interpolatedValue = elEvaluator.evaluateEl(elWithDollarExpressions, String.class, variables);
        
        return interpolatedValue;
    }



}
