package biz.netcentric.cq.tools.actool.validators;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2024 Cognizant Netcentric
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.util.regex.Pattern;

import biz.netcentric.cq.tools.actool.configmodel.AcConfiguration;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizablesConfig;

public class UnmangedExternalMemberRelationshipChecker {

    public static void validate(AcConfiguration acConfiguration) {

    	
    	AuthorizablesConfig authorizablesConfig = acConfiguration.getAuthorizablesConfig();
    	for (AuthorizableConfigBean authorizableConfigBean : authorizablesConfig) {
    		checkIsMemberOf(acConfiguration, authorizablesConfig, authorizableConfigBean);
    		checkMembers(acConfiguration, authorizablesConfig, authorizableConfigBean);
		}

    }

	private static void checkIsMemberOf(AcConfiguration acConfiguration, AuthorizablesConfig authorizablesConfig,
			AuthorizableConfigBean authorizableConfigBean) {
		
		Pattern unmanagedExternalIsMemberOfRegex = authorizableConfigBean.getUnmanagedExternalIsMemberOfRegex();
		if(unmanagedExternalIsMemberOfRegex==null) {
			unmanagedExternalIsMemberOfRegex = acConfiguration.getGlobalConfiguration().getDefaultUnmanagedExternalIsMemberOfRegex();
		}
		if(unmanagedExternalIsMemberOfRegex!=null) {
			String[] isMemberOfArr = authorizableConfigBean.getIsMemberOf();
			if(isMemberOfArr!=null) {
				for(String isMemberOfId: isMemberOfArr) {
					if(authorizablesConfig.getAuthorizableConfig(isMemberOfId)!=null) {
						continue; // group is not external but internal, nothing to check
					}
				
					if(unmanagedExternalIsMemberOfRegex.matcher(isMemberOfId).matches()) {
						throw new IllegalArgumentException("Group '"+authorizableConfigBean.getAuthorizableId() + "' references external group '" + isMemberOfId + "' via isMemberOf but relationships to this external group is explicitly unmanged by regex "+unmanagedExternalIsMemberOfRegex);
					}
				}    			
			}
		}
	}
	
	private static void checkMembers(AcConfiguration acConfiguration, AuthorizablesConfig authorizablesConfig,
			AuthorizableConfigBean authorizableConfigBean) {
		
		Pattern unmanagedExternalMembersRegex = authorizableConfigBean.getUnmanagedExternalMembersRegex();
		if(unmanagedExternalMembersRegex==null) {
			unmanagedExternalMembersRegex = acConfiguration.getGlobalConfiguration().getDefaultUnmanagedExternalMembersRegex();
		}
		if(unmanagedExternalMembersRegex!=null) {
			String[] membersArr = authorizableConfigBean.getMembers();
			if(membersArr!=null) {
				for(String memberId: membersArr) {
					if(authorizablesConfig.getAuthorizableConfig(memberId)!=null) {
						continue; // group or user is not external but internal, nothing to check
					}
				
					if(unmanagedExternalMembersRegex.matcher(memberId).matches()) {
						throw new IllegalArgumentException("Group '"+authorizableConfigBean.getAuthorizableId() + "' references external group group or user '" + memberId + "' via members but relationships to this external group is explicitly unmanged by regex "+unmanagedExternalMembersRegex);
					}
				}    			
			}
		}
	}


	
}
