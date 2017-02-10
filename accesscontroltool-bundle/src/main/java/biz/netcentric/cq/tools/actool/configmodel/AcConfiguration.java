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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

/** Root class of the configuration model as it is constructed from the multiple yaml files, it is a fully merged configuration. All loops
 * and variables have been processed. */
public class AcConfiguration {

	private Map<String, SortedSet<String>> honorPrivilegePaths;
	
    private GlobalConfiguration globalConfiguration;

    // to be changed from map to PojoClass in future versions
    private Map<String, Set<AuthorizableConfigBean>> authorizablesMap;

    // to be changed from map to PojoClass in future versions
    private Map<String, Set<AceBean>> aceMap;

	private Set<String> obsoleteAuthorizables = new HashSet<String>();    
    
    public Map<String, SortedSet<String>> getHonorPrivilegePaths() {
		return honorPrivilegePaths;
	}

	public void setHonorPrivilegePaths(Map<String, SortedSet<String>> honorPrivilegePaths) {
		this.honorPrivilegePaths = honorPrivilegePaths;
	}

    public GlobalConfiguration getGlobalConfiguration() {
        return globalConfiguration;
    }

    public void setGlobalConfiguration(GlobalConfiguration globalConfiguration) {
        this.globalConfiguration = globalConfiguration;
    }

    public Map<String, Set<AuthorizableConfigBean>> getAuthorizablesConfig() {
        return authorizablesMap;
    }

    public void setAuthorizablesConfig(Map<String, Set<AuthorizableConfigBean>> authorizablesMap) {
        this.authorizablesMap = authorizablesMap;
    }

    public Map<String, Set<AceBean>> getAceConfig() {
        return aceMap;
    }

    public void setAceConfig(Map<String, Set<AceBean>> aceMap) {
        this.aceMap = aceMap;
    }

    public Set<String> getObsoleteAuthorizables() {
        return obsoleteAuthorizables;
    }

    public void setObsoleteAuthorizables(Set<String> obsoleteAuthorizables) {
        this.obsoleteAuthorizables = obsoleteAuthorizables;
    }

}
