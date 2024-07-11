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

@JsonIgnoreProperties(ignoreUnknown = true)
public class ActionCommandIssue {

    @JsonProperty(required = false)
    String requestID;
    @JsonProperty
    int index;
    @JsonProperty
    int step;
    @JsonProperty
    String message;
    @JsonProperty
    String user;

    @Override
    public String toString() {
        return "ActionCommandIssue [requestID=" + requestID + ", index=" + index + ", step=" + step + ", message=" + message + ", user="
                + user + "]";
    }
}
