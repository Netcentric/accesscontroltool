package biz.netcentric.cq.tools.actool.ims.request;

import java.util.Objects;

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
@JsonInclude(Include.NON_NULL)
public class CreateGroupStep implements Step {

    // this cannot be a constant, but still needs to be serialized as literal
    @JsonProperty("option")
    final String defaultOption = "updateIfAlreadyExists";
    // unclear why name should be passed here as well: https://github.com/adobe-apiplatform/umapi-documentation/issues/87, doesn't seem necessary
    //@JsonProperty
    //String name; 
    @JsonProperty
    public String description; // this may be empty

    @Override
    public String toString() {
        return "CreateGroupStep [defaultOption=" + defaultOption + ", description=" + description + "]";
    }

    @Override
    public int hashCode() {
        return Objects.hash(defaultOption, description);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CreateGroupStep other = (CreateGroupStep) obj;
        return Objects.equals(defaultOption, other.defaultOption) && Objects.equals(description, other.description);
    }
}
