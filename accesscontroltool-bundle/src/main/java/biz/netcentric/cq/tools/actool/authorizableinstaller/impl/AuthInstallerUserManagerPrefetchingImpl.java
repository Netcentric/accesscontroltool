package biz.netcentric.cq.tools.actool.authorizableinstaller.impl;

import static biz.netcentric.cq.tools.actool.history.impl.PersistableInstallationLogger.msHumanReadable;

import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.jcr.ValueFactory;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.Query;
import org.apache.jackrabbit.api.security.user.QueryBuilder;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.oak.spi.security.user.UserConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.helper.Constants;
import biz.netcentric.cq.tools.actool.history.InstallationLogger;

/**
 * <p>
 * Special user manager that prefetches
 * <ul>
 * <li>all groups, system users and anonymous (but not regular users)</li>
 * <li>all memberships between the preloaded authorizables</li>
 * <ul>
 * upon creation.
 * </p>
 * <p>
 * Authorizables as well as membership relationships are cached by their case-insensitive IDs in alignment
 * with {@link org.apache.jackrabbit.oak.security.user.AuthorizableBaseProvider#getContentID(String authorizableId)}.
 * </p>
 * <p>
 * The membership relationships are cached in lookup maps to quickly be able to query the repository state for both
 * authorizable.declaredMemberOf() (this we call in this class also) and group.getDeclaredMembers(). Having called declaredMemberOf() for
 * all groups of the repository has also retrieved all memberships (expect regular user memberships), hence the method getDeclaredMembers()
 * does not have to be called anymore. This is particularly useful because that way the AC tool does not have to iterate over a potentially
 * huge number of users in production to then only filter out a few relevant groups in the end (the AC Tool does not touch user memberships
 * to groups).
 * </p>
 */
class AuthInstallerUserManagerPrefetchingImpl implements AuthInstallerUserManager {

    private static final Logger LOG = LoggerFactory.getLogger(AuthInstallerUserManagerPrefetchingImpl.class);

    private final UserManager delegate;
    private final Map<String, Authorizable> authorizableCache = new CaseInsensitiveMap();

    private final Map<String, Set<String>> nonRegularUserMembersByAuthorizableId = new CaseInsensitiveMap();
    private final Map<String, Set<String>> isMemberOfByAuthorizableId = new CaseInsensitiveMap();

    public AuthInstallerUserManagerPrefetchingImpl(UserManager delegate, final ValueFactory valueFactory, InstallationLogger installLog)
            throws RepositoryException {
        this.delegate = delegate;

        long startPrefetch = System.currentTimeMillis();
        Iterator<Authorizable> authorizablesToPrefetchIt = delegate.findAuthorizables(new Query() {
            public <T> void build(QueryBuilder<T> builder) {
                builder.setCondition(
                        builder.or( //
                                builder.neq("@" + JcrConstants.JCR_PRIMARYTYPE, valueFactory.createValue(UserConstants.NT_REP_USER)),
                                builder.eq("@" + UserConstants.REP_AUTHORIZABLE_ID, valueFactory.createValue(Constants.USER_ANONYMOUS))) //
                );
            }
        });
        while (authorizablesToPrefetchIt.hasNext()) {
            Authorizable auth = authorizablesToPrefetchIt.next();
            String authId = auth.getID();
            authorizableCache.put(authId, auth);

            // init lists
            nonRegularUserMembersByAuthorizableId.put(authId, new HashSet<String>());
            isMemberOfByAuthorizableId.put(authId, new HashSet<String>());
        }

        installLog.addMessage(LOG, "Prefetched " + authorizableCache.size() + " authorizables in "
                + msHumanReadable(System.currentTimeMillis() - startPrefetch));

        long startPrefetchMemberships = System.currentTimeMillis();
        int membershipCount = 0;
        for (Authorizable authorizable : authorizableCache.values()) {
            String authId = authorizable.getID();
            Iterator<Group> declaredMemberOf = authorizable.declaredMemberOf();
            while (declaredMemberOf.hasNext()) {
                Group memberOfGroup = declaredMemberOf.next();
                String memberOfGroupId = memberOfGroup.getID();
                isMemberOfByAuthorizableId.get(authId).add(memberOfGroupId);
                nonRegularUserMembersByAuthorizableId.get(memberOfGroupId).add(authId);
                membershipCount++;
            }
        }

        installLog.addMessage(LOG, "Prefetched " + membershipCount + " memberships in "
                + msHumanReadable(System.currentTimeMillis() - startPrefetchMemberships));
    }

    public Authorizable getAuthorizable(String id) throws RepositoryException {
        Authorizable authorizable = authorizableCache.get(id);
        if (authorizable == null) {
            authorizable = delegate.getAuthorizable(id);
            authorizableCache.put(id, authorizable);
        }
        return authorizable;
    }

    public Set<String> getDeclaredIsMemberOf(String id) throws RepositoryException {

        if (isMemberOfByAuthorizableId.containsKey(id)) {
            return isMemberOfByAuthorizableId.get(id);
        } else {
            // for users fall back to retrieve on demand
            Set<String> memberOfSet = new HashSet<String>();
            Iterator<Group> memberOfIt = getAuthorizable(id).declaredMemberOf();
            while (memberOfIt.hasNext()) {
                Authorizable memberOfGroup = memberOfIt.next();
                memberOfSet.add(memberOfGroup.getID());
            }
            return memberOfSet;
        }
    }

    public Set<String> getDeclaredMembersWithoutRegularUsers(String id) {
        return nonRegularUserMembersByAuthorizableId.containsKey(id) ? nonRegularUserMembersByAuthorizableId.get(id)
                : Collections.<String>emptySet();
    }

    public int getCacheSize() {
        return authorizableCache.size();
    }

    private void refreshAuthorizableCacheIfNeeded(final String id, final Authorizable authorizable) {
        if (authorizableCache.containsKey(id)) {
            authorizableCache.put(id, authorizable);
        }
    }

    @Override
    public void removeAuthorizable(final Authorizable authorizable) throws RepositoryException {
        Objects.requireNonNull(authorizable);

        // remove auth from cache
        authorizableCache.remove(authorizable.getID());

        // if auth is a group, remove auth from the cache which lists groups of a given user
        if (authorizable instanceof Group) {
            final Group group = (Group) authorizable;
            removeGroupFromCache(group);
        }
        authorizable.remove();
    }

    private void removeGroupFromCache(final Group group) throws RepositoryException {
        final String groupID = group.getID();
        final Iterator<Authorizable> membersIt = group.getDeclaredMembers();
        while (membersIt.hasNext()) {
            final Authorizable member = membersIt.next();
            final String memberID = member.getID();
            if (isMemberOfByAuthorizableId.containsKey(memberID)) {
                isMemberOfByAuthorizableId.get(memberID).remove(groupID);
            }
        }
    }

    // --- delegated methods

    public UserManager getOakUserManager() {
        return delegate;
    }

    public User createUser(String userID, String password, Principal principal, String intermediatePath) throws RepositoryException {
        final User user = delegate.createUser(userID, password, principal, intermediatePath);
        refreshAuthorizableCacheIfNeeded(userID, user);
        return user;
    }

    public User createSystemUser(String userID, String intermediatePath) throws RepositoryException {
        final User user = delegate.createSystemUser(userID, intermediatePath);
        refreshAuthorizableCacheIfNeeded(userID, user);
        return user;
    }

    public Group createGroup(Principal principal) throws RepositoryException {
        final Group group = delegate.createGroup(principal);
        refreshAuthorizableCacheIfNeeded(principal.getName(), group);
        return group;
    }

    public Group createGroup(Principal principal, String intermediatePath) throws RepositoryException {
        final Group group = delegate.createGroup(principal, intermediatePath);
        refreshAuthorizableCacheIfNeeded(principal.getName(), group);
        return group;
    }

}
