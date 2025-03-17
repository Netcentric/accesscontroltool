package biz.netcentric.cq.tools.actool.ims.request;

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
}
