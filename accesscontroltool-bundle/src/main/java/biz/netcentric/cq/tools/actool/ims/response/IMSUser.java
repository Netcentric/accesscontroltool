package biz.netcentric.cq.tools.actool.ims.response;

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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class IMSUser {

    @JsonProperty("type")
    public String type;

    @JsonProperty("email")
    public String email;
    
    @JsonProperty("status")
    public String status;

    @JsonProperty("groups")
    public List<String> groups;
    
    @JsonProperty("domain")
    public String domain;
    
    @JsonProperty("country")
    public String country;

    @JsonProperty("tags")
    public List<String> tags;
    
    @JsonProperty("username")
    public String username;

    @Override
    public String toString() {
        return "IMSUser [type=" + type + ", email=" + email + ", status=" + status + ", groups=" + groups + ", domain=" + domain
                + ", country=" + country + ", tags=" + tags + ", username=" + username + "]";
    }

    public String getType() {
        return type;
    }

    public String getEmail() {
        return email;
    }

    public String getStatus() {
        return status;
    }

    public List<String> getGroups() {
        return groups;
    }

    public String getDomain() {
        return domain;
    }

    public String getCountry() {
        return country;
    }

    public List<String> getTags() {
        return tags;
    }

    public String getUsername() {
        return username;
    }
    
    
}
