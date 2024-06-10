package biz.netcentric.cq.tools.actool.ims.response;

import java.util.Collections;
import java.util.List;

import org.apache.http.client.methods.HttpRequestBase;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** General response format for UMAPI requests */
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
