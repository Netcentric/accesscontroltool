/*
 * (C) Copyright 2016 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configmodel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Root class of the configuration model as it is constructed from the multiple yaml files, it is a fully merged configuration. All loops
 * and variables have been processed. */
public class AcConfiguration {

    public static final Logger LOG = LoggerFactory.getLogger(AcConfiguration.class);

    private GlobalConfiguration globalConfiguration;

    // Authorizables configuration beans in order as they appear in configuration
    private AuthorizablesConfig authorizablesConfig;

    // ACE configuration beans in order as they appear in configuration
    private AcesConfig aceBeansConfig;

    private Set<String> obsoleteAuthorizables = new HashSet<String>();

    private List<AuthorizableConfigBean> virtualGroups = new ArrayList<AuthorizableConfigBean>();

    public GlobalConfiguration getGlobalConfiguration() {
        return globalConfiguration;
    }

    public void setGlobalConfiguration(GlobalConfiguration globalConfiguration) {
        this.globalConfiguration = globalConfiguration;
    }

    public AuthorizablesConfig getAuthorizablesConfig() {
        return authorizablesConfig;
    }

    public void setAuthorizablesConfig(AuthorizablesConfig authorizablesSet) {
        this.authorizablesConfig = authorizablesSet;
    }

    public AcesConfig getAceConfig() {
        return aceBeansConfig;
    }

    public void setAceConfig(AcesConfig aceBeansSet) {
        this.aceBeansConfig = aceBeansSet;
        ensureAceBeansHaveCorrectPrincipalNameSet();
    }

    // this is required as the configuration contains authorizableIds, but the JCR contains principal names. The mapping is available via
    // authorizablesConfig
    public void ensureAceBeansHaveCorrectPrincipalNameSet() {
        if (authorizablesConfig == null) {
            throw new IllegalStateException("authorizablesConfig must be set before setAceConfig() is called");
        }
        LOG.debug("Ensuring ACE Beans have correct principal name set...");

        for (AceBean aceBean : aceBeansConfig) {
            String authorizableId = aceBean.getAuthorizableId();
            String principalName = authorizablesConfig.getPrincipalNameForAuthorizableId(authorizableId);
            if(StringUtils.isNotBlank(principalName)) {
                aceBean.setPrincipalName(principalName);
            } else {
                LOG.debug(
                        "Setting principal name for ACE at {} to authorizable id '{}' as principal name cannot be mapped from authorizable bean",
                        aceBean.getJcrPath(),
                        authorizableId);
                aceBean.setPrincipalName(authorizableId);
            }
           
        }
    }

    public Set<String> getObsoleteAuthorizables() {
        return obsoleteAuthorizables;
    }

    public void setObsoleteAuthorizables(Set<String> obsoleteAuthorizables) {
        this.obsoleteAuthorizables = obsoleteAuthorizables;
    }

    public List<AuthorizableConfigBean> getVirtualGroups() {
        return virtualGroups;
    }

    public void setVirtualGroups(List<AuthorizableConfigBean> virtualGroups) {
        this.virtualGroups = virtualGroups;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("globalConfiguration", globalConfiguration)
                .append("authorizablesConfig", authorizablesConfig)
                .append("aceBeansConfig", aceBeansConfig)
                .append("obsoleteAuthorizables", obsoleteAuthorizables)
                .append("virtualGroups", virtualGroups)
                .toString();
    }
}
