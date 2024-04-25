package biz.netcentric.cq.tools.actool.ims.response;

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
