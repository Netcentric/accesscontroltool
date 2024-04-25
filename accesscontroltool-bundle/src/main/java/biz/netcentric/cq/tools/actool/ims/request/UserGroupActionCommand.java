package biz.netcentric.cq.tools.actool.ims.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UserGroupActionCommand extends ActionCommand {

    public UserGroupActionCommand(String userGroup) {
        this.userGroup = userGroup;
    }

    @JsonProperty("usergroup")
    String userGroup;
}
