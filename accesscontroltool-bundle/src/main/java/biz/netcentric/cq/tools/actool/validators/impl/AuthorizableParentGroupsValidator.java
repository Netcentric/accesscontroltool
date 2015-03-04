package biz.netcentric.cq.tools.actool.validators.impl;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.validators.exceptions.InvalidGroupNameException;

/** Iterates over a list of groups and adjusts the memberOf values with the parent values.
 * 
 * @author Roland Gruber */
public class AuthorizableParentGroupsValidator {

    /** Checks if the provided parent groups exist and updates their memberOf variable.
     * 
     * @param authorizables groups
     * @throws InvalidGroupNameException error validating parent groups */
    public void validate(final Map<String, Set<AuthorizableConfigBean>> authorizables) throws InvalidGroupNameException {
        // iterate over all groups
        for (final Entry<String, Set<AuthorizableConfigBean>> entry : authorizables.entrySet()) {
            final String groupName = entry.getKey();
            final AuthorizableConfigBean group = entry.getValue().iterator().next();
            final String[] parents = group.getParents();
            if ((parents == null) || (parents.length == 0)) {
                continue;
            }
            for (final String parent : parents) {
                if (!authorizables.containsKey(parent)) {
                    throw new InvalidGroupNameException(String.format("Group %s defined as parent in group %s is not specified.", parent, groupName));
                }
                authorizables.get(parent).iterator().next().addMemberOf(groupName);
            }
        }
    }

}
