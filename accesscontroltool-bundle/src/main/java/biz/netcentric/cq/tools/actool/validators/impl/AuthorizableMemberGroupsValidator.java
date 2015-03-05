package biz.netcentric.cq.tools.actool.validators.impl;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.validators.exceptions.InvalidGroupNameException;

/** Iterates over a list of groups and adjusts the isMemberOf values with the members values.
 * 
 * @author Roland Gruber */
public class AuthorizableMemberGroupsValidator {

    /** Checks if the provided member groups exist and updates their isMemberOf variable.
     * 
     * @param authorizables groups
     * @throws InvalidGroupNameException error validating member groups */
    public void validate(final Map<String, Set<AuthorizableConfigBean>> authorizables) throws InvalidGroupNameException {
        // iterate over all groups
        for (final Entry<String, Set<AuthorizableConfigBean>> entry : authorizables.entrySet()) {
            final String groupName = entry.getKey();
            final AuthorizableConfigBean group = entry.getValue().iterator().next();
            final String[] members = group.getMembers();
            if ((members == null) || (members.length == 0)) {
                continue;
            }
            for (final String member : members) {
                if (!authorizables.containsKey(member)) {
                    throw new InvalidGroupNameException(String.format("Group %s defined as member in group %s is not specified.", member, groupName));
                }
                authorizables.get(member).iterator().next().addMemberOf(groupName);
            }
        }
    }

}
