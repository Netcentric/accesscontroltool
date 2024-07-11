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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("createUserGroup")
@JsonInclude(Include.NON_EMPTY) // neither empty strings nor null values are allowed for "description", compare with https://github.com/Netcentric/accesscontroltool/issues/724
public class CreateGroupStep implements Step {

    // this cannot be a constant, but still needs to be serialized as literal
    @JsonProperty("option")
    final String defaultOption = "updateIfAlreadyExists";
    // unclear why name should be passed here as well: https://github.com/adobe-apiplatform/umapi-documentation/issues/87, doesn't seem necessary
    //@JsonProperty
    //String name; 
    @JsonProperty
    public String description; // this may be null
}
