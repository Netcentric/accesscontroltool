package biz.netcentric.cq.tools.actool.ims.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 
 * @see <a href="https://adobe-apiplatform.github.io/umapi-documentation/en/api/ActionsRef.html#responses">Action Response Format</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActionCommandWarning extends ActionCommandIssue {

    @JsonProperty
    String warningCode;

    @Override
    public String toString() {
        return "ActionCommandWarning [warningCode=" + warningCode + ", requestID=" + requestID + ", index=" + index + ", step=" + step
                + ", message=" + message + ", user=" + user + "]";
    }
}