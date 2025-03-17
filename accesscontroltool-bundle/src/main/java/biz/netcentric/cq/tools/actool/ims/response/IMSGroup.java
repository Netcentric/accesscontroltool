package biz.netcentric.cq.tools.actool.ims.response;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2024 Cognizant Netcentric
 * %%
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * #L%
 */

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Represents either a user group or product profile in IMS. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IMSGroup {

    @JsonProperty("type")
    public String type;

    @JsonProperty("memberCount")
    public int memberCount;
    
    @JsonProperty("adminGroupName")
    public String adminGroupName;

    @JsonProperty("groupName")
    public String groupName;
    
    @JsonProperty("groupId")
    public long groupId;
    
    @JsonProperty("userGroupName")
    public String userGroupName;

    @Override
    public String toString() {
        return "IMSGroup [type=" + type + ", memberCount=" + memberCount + ", adminGroupName=" + adminGroupName + ", groupName=" + groupName
                + ", groupId=" + groupId + ", userGroupName=" + userGroupName + "]";
    }

    public String getType() {
        return type;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public String getAdminGroupName() {
        return adminGroupName;
    }

    public String getGroupName() {
        return groupName;
    }

    public long getGroupId() {
        return groupId;
    }

    public String getUserGroupName() {
        return userGroupName;
    }
}
