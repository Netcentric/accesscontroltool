package biz.netcentric.cq.tools.actool.ims.request;

import java.util.Collection;
import java.util.LinkedList;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ActionCommand {

    @JsonProperty("do")
    final Collection<Step> steps;

    public ActionCommand() {
        steps = new LinkedList<>();
    }

    public boolean addStep(Step step) {
        return steps.add(step);
    }
}
