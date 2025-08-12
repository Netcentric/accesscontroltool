package biz.netcentric.cq.tools.actool.ims.request;

import java.util.Objects;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2024 Cognizant Netcentric
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import com.fasterxml.jackson.annotation.JsonProperty;

public class UserGroupActionCommand extends ActionCommand {

    public UserGroupActionCommand(String userGroup) {
        this.userGroup = userGroup;
    }

    @JsonProperty("usergroup")
    String userGroup;

    @Override
    public String toString() {
        return "UserGroupActionCommand [userGroup=" + userGroup + ", steps=" + steps + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hash(userGroup);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        UserGroupActionCommand other = (UserGroupActionCommand) obj;
        return Objects.equals(userGroup, other.userGroup);
    }
}
