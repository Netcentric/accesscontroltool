/*
 * (C) Copyright 2016 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configmodel;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

/** Root class of the configuration model as it is constructed from the multiple yaml files, it is a fully merged configuration. All loops
 * and variables have been processed. */
public class AcConfiguration {

    private GlobalConfiguration globalConfiguration;

    // Authorizables configuration beans in order as they appear in configuration
    private AuthorizablesConfig authorizablesConfig;

    // ACE configuration beans in order as they appear in configuration
    private AcesConfig aceBeansConfig;

	private Set<String> obsoleteAuthorizables = new HashSet<String>();


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
        ensureAceBeansHaveCorrectPrincipalNameSet(aceBeansSet);
    }

    // this is required as the configuration contains authorizableIds, but the JCR contains principal names. The mapping is available via
    // authorizablesConfig
    private void ensureAceBeansHaveCorrectPrincipalNameSet(AcesConfig aceBeansSet) {
        if (authorizablesConfig == null) {
            throw new IllegalStateException("authorizablesConfig must be set before setAceConfig() is called");
        }
        for (AceBean aceBean : aceBeansSet) {
            String authorizableId = aceBean.getAuthorizableId();
            String principalName = authorizablesConfig.getPrincipalNameForAuthorizableId(authorizableId);
            aceBean.setPrincipalName(principalName);
        }
    }

    public Set<String> getObsoleteAuthorizables() {
        return obsoleteAuthorizables;
    }

    public void setObsoleteAuthorizables(Set<String> obsoleteAuthorizables) {
        this.obsoleteAuthorizables = obsoleteAuthorizables;
    }

}
