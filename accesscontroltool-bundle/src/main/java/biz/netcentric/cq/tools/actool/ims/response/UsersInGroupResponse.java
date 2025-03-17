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

import java.util.List;

import org.apache.http.client.methods.HttpRequestBase;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UsersInGroupResponse {

    @JsonProperty("lastPage")
    public boolean isLastPage;
    
    @JsonProperty("result")
    public String result;
    
    @JsonProperty("groupName")
    public String groupName;
    
    @JsonProperty(value = "users", required = true)
    public List<IMSUser> users;
    
    @JsonIgnore
    public HttpRequestBase associatedRequest;
}
