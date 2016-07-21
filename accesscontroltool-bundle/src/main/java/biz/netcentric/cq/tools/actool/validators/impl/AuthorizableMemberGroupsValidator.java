package biz.netcentric.cq.tools.actool.validators.impl;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.helper.Constants;
import biz.netcentric.cq.tools.actool.validators.exceptions.InvalidGroupNameException;

/** Iterates over a list of groups and adjusts the isMemberOf values with the members values.
 * 
 * @author Roland Gruber */
public class AuthorizableMemberGroupsValidator {

    /** Checks if the provided member groups exist and updates their isMemberOf variable.
     * 
     * @param aceConfig groups
     * @throws InvalidGroupNameException error validating member groups */
    public void validate(final Map<String, Set<AuthorizableConfigBean>> aceConfig) throws InvalidGroupNameException {
        // iterate over all groups
        for (final Entry<String, Set<AuthorizableConfigBean>> aceConfigEntry : aceConfig.entrySet()) {
            final String groupName = aceConfigEntry.getKey();
            final AuthorizableConfigBean group = aceConfigEntry.getValue().iterator().next();
            final String[] members = group.getMembers();
            if ((members == null) || (members.length == 0)) {
                continue;
            }
            for (final String member : members) {

                boolean isAnonymousUser = Constants.USER_ANONYMOUS.equals(member);
                if (isAnonymousUser) {
                    continue; // special handling for anonymous necessary (as it can not be added to isMemberOf list as no aceConfigEntry
                              // exists for anonymous)
                }

                boolean memberContainedInConfig = aceConfig.containsKey(member);

                if (!memberContainedInConfig) {
                    throw new InvalidGroupNameException(String.format("Group %s defined as member in group %s is not specified.", member, groupName));
                }
                aceConfig.get(member).iterator().next().addMemberOf(groupName);
            }
        }
    }

}
