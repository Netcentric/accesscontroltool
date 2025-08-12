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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** OAuth 2.0 Access Token JSON Format as specified in <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-5.1">RFC6749</a>. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AccessToken {
    @JsonProperty("access_token")
    public String token;
    @JsonProperty("token_type")
    public String type;
    @JsonProperty("expires_in")
    public long lifeTimeInSeconds;
}
