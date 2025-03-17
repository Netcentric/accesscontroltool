package biz.netcentric.cq.tools.actool.ims.request;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2024 Cognizant Netcentric
 * %%
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * #L%
 */

import java.util.Collection;
import java.util.LinkedList;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ActionCommand {

    @JsonProperty(value = "do", required = true)
    final Collection<Step> steps;

    public ActionCommand() {
        steps = new LinkedList<>();
    }

    public boolean addStep(Step step) {
        return steps.add(step);
    }

    @Override
    public String toString() {
        return "ActionCommand [steps=" + steps + "]";
    }

    @Override
    public int hashCode() {
        return Objects.hash(steps);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ActionCommand other = (ActionCommand) obj;
        return Objects.equals(steps, other.steps);
    }
}
