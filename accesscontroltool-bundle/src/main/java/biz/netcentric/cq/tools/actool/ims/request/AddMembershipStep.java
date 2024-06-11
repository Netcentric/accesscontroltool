package biz.netcentric.cq.tools.actool.ims.request;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("add")
@JsonInclude(Include.NON_EMPTY) // neither empty strings nor null values are allowed for the fields
public class AddMembershipStep implements Step {

    @JsonProperty("user")
    public Set<String> userIds;
    
    @JsonProperty("productConfiguration")
    public Set<String> productProfileIds;
}
