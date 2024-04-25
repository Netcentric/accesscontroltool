package biz.netcentric.cq.tools.actool.ims.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME, 
        include = As.WRAPPER_OBJECT, visible = true)
@JsonSubTypes({@Type(CreateGroupStep.class)})
public interface Step {

}
