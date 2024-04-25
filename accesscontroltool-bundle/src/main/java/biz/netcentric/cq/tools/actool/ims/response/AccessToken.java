package biz.netcentric.cq.tools.actool.ims.response;

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
