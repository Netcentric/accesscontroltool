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
import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicyOption;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.settings.SlingSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.granite.crypto.CryptoException;
import com.adobe.granite.crypto.CryptoSupport;

import biz.netcentric.cq.tools.actool.configmodel.AcConfiguration;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizablesConfig;
import biz.netcentric.cq.tools.actool.configmodel.AutoCreateTestUsersConfig;
import biz.netcentric.cq.tools.actool.configmodel.GlobalConfiguration;
import biz.netcentric.cq.tools.actool.history.InstallationLogger;

@Service(TestUserConfigsCreator.class)
@Component
public class TestUserConfigsCreator {

    private static final Logger LOG = LoggerFactory.getLogger(TestUserConfigsCreator.class);

    @Reference
    SlingSettingsService slingSettingsService;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL_UNARY, policyOption = ReferencePolicyOption.GREEDY)
    CryptoSupport cryptoSupport;

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
        for (AuthorizableConfigBean authConfigBean : authorizablesConfig) {
            if(!authConfigBean.isGroup()) {
                continue;
            }
            String groupId = authConfigBean.getAuthorizableId();
            if (groupId.matches(autoCreateTestUsersConf.getCreateForGroupNamesRegEx())) {
                AuthorizableConfigBean testUserConfigBean = new AuthorizableConfigBean();
                testUserConfigBean.setIsGroup(false);
                String testUserAuthId = autoCreateTestUsersConf.getPrefix() + groupId;
                testUserConfigBean.setAuthorizableId(testUserAuthId);
                testUserConfigBean.setPath(autoCreateTestUsersConf.getPath());
                testUserConfigBean.setIsMemberOf(new String[] { groupId });

                String password = StringUtils.defaultIfEmpty(autoCreateTestUsersConf.getPassword(), testUserAuthId);

                if (password.matches("\\{.+}") && cryptoSupport != null) {
                    try {
                        password = cryptoSupport.unprotect(password);
                    } catch (CryptoException e) {
                        throw new IllegalArgumentException("Could not unprotect password " + password + " as given in "
                                + GlobalConfiguration.KEY_AUTOCREATE_TEST_USERS);
                    }
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


}
