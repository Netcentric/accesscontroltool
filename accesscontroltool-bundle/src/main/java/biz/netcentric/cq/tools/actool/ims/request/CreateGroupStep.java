package biz.netcentric.cq.tools.actool.ims.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("createUserGroup")
public class CreateGroupStep implements Step {

    // this cannot be a constant, but still needs to be serialized as literal
    @JsonProperty("option")
    final String defaultOption = "ignoreIfAlreadyExists"; // the option "updateIfAlreadyExists" fails in case the group does already exist with a letter case, tracked in https://adminconsole.adobe.com/FA907D44536A3C2B0A490D4D@AdobeOrg/support/support-cases/E-001223901
    // unclear why name should be passed here as well: https://github.com/adobe-apiplatform/umapi-documentation/issues/87, doesn't seem necessary
    //@JsonProperty
    //String name; 
    @JsonProperty
    public
    String description;
}
