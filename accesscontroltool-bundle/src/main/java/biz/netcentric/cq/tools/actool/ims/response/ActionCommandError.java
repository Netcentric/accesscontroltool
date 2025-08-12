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

/**
 * 
 * @see <a href="https://adobe-apiplatform.github.io/umapi-documentation/en/api/ActionsRef.html#responses">Action Response Format</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActionCommandError extends ActionCommandIssue {

    @JsonProperty
    String errorCode;

    @Override
    public String toString() {
        return "ActionCommandError [errorCode=" + errorCode + ", requestID=" + requestID + ", index=" + index + ", step=" + step
                + ", message=" + message + ", user=" + user + "]";
    }
}
