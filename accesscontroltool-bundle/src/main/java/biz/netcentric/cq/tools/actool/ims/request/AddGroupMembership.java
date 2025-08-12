package biz.netcentric.cq.tools.actool.ims.request;

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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

/** 
 * Maintains memberships of users in (admin) groups, to be used with {@link UserActionCommand}.
 * @see AddGroupMembers
 */
@JsonTypeName("add")
@JsonInclude(Include.NON_EMPTY) // neither empty strings nor null values are allowed for the fields
public class AddGroupMembership implements Step {

    public AddGroupMembership(Collection<String> group) {
        this.group = new LinkedHashSet<>(group);
    }

    @JsonProperty(value = "group", required = true)
    public Set<String> group;

    @Override
    public String toString() {
        return "AddGroupMembership [group=" + group + "]";
    }

    @Override
    public int hashCode() {
        return Objects.hash(group);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AddGroupMembership other = (AddGroupMembership) obj;
        return Objects.equals(group, other.group);
    }
}
