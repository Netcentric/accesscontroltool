package biz.netcentric.cq.tools.actool.ims.request;

import java.util.Objects;

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

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

/** Maintains members of groups, to be used with {@link UserGroupActionCommand}.
 * For maintaining group administrators use {@link AddGroupMembership}.
 */
@JsonTypeName("add")
@JsonInclude(Include.NON_EMPTY) // neither empty strings nor null values are allowed for the fields
public class AddGroupMembers implements Step {

    @JsonProperty("user")
    public Set<String> userIds;
    
    @JsonProperty("productConfiguration")
    public Set<String> productProfileIds;

    @Override
    public String toString() {
        return "AddGroupMembers [userIds=" + userIds + ", productProfileIds=" + productProfileIds + "]";
    }

    @Override
    public int hashCode() {
        return Objects.hash(productProfileIds, userIds);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AddGroupMembers other = (AddGroupMembers) obj;
        return Objects.equals(productProfileIds, other.productProfileIds) && Objects.equals(userIds, other.userIds);
    }
}
