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

import java.util.Collections;
import java.util.List;

import org.apache.http.client.methods.HttpRequestBase;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** General response format for UMAPI action requests */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActionCommandResponse {

    public ActionCommandResponse() {
        errors = Collections.emptyList();
        warnings = Collections.emptyList();
    }

    @JsonProperty("completed")
    public int numCompletedActions;
    
    @JsonProperty("notCompleted")
    public int numNotCompletedActions;
    
    @JsonProperty("completedInTestMode")
    public int numCompletedActionsInTestMode;
    
    @JsonProperty("errors")
    public List<ActionCommandError> errors;
    
    @JsonProperty("warnings")
    public List<ActionCommandWarning> warnings;

    @JsonIgnore
    public HttpRequestBase associatedRequest;
}
