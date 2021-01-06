package biz.netcentric.cq.tools.actool.authorizableinstaller.impl;

import static biz.netcentric.cq.tools.actool.history.PersistableInstallationLogger.msHumanReadable;

import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.jcr.ValueFactory;

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

import com.google.common.annotations.VisibleForTesting;

import biz.netcentric.cq.tools.actool.helper.Constants;
import biz.netcentric.cq.tools.actool.history.InstallationLogger;

/** Prefetches a) all groups and system users (but not regular users) and b) all memberships between these authorizables in constructor.  */
class PrefetchingUserManager implements UserManager {

    private static final Logger LOG = LoggerFactory.getLogger(PrefetchingUserManager.class);

    private final UserManager delegate;
    private final Map<String, Authorizable> authorizableCache = new HashMap<>();

    private final Map<String, Set<String>> nonRegularUserMembersByAuthorizableId = new HashMap<>();
    private final Map<String, Set<String>> isMemberOfByAuthorizableId = new HashMap<>();

    public PrefetchingUserManager(UserManager delegate, final ValueFactory valueFactory, InstallationLogger installLog)
            throws RepositoryException {
        this.delegate = delegate;

        long startPrefetch = System.currentTimeMillis();
        Iterator<Authorizable> allAuthorizables = delegate.findAuthorizables(new Query() {
            public <T> void build(QueryBuilder<T> builder) {
                builder.setCondition(
                        builder.or( //
                                builder.neq("@" + JcrConstants.JCR_PRIMARYTYPE, valueFactory.createValue(UserConstants.NT_REP_USER)),
                                builder.eq("@" + UserConstants.REP_AUTHORIZABLE_ID, valueFactory.createValue(Constants.USER_ANONYMOUS))) //
                );
            }
        });
        while (allAuthorizables.hasNext()) {
            Authorizable auth = allAuthorizables.next();
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

    @Override
    public Authorizable getAuthorizable(String id) throws RepositoryException {
        Authorizable authorizable = authorizableCache.get(id);
        if (authorizable == null) {
            authorizable = delegate.getAuthorizable(id);
            authorizableCache.put(id, authorizable);
        }
        return authorizable;
    }

    public Set<String> getDeclaredIsMemberOf(String id) throws RepositoryException {
        
        if(isMemberOfByAuthorizableId.containsKey(id)) {
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
        return nonRegularUserMembersByAuthorizableId.containsKey(id) ? nonRegularUserMembersByAuthorizableId.get(id): Collections.<String>emptySet();
    }

    public int getCacheSize() {
        return authorizableCache.size();
    }

    // --- delegated methods

    public Authorizable getAuthorizable(Principal principal) throws RepositoryException {
        return delegate.getAuthorizable(principal);
    }

    public Authorizable getAuthorizableByPath(String path) throws RepositoryException {
        return delegate.getAuthorizableByPath(path);
    }

    public Iterator<Authorizable> findAuthorizables(String relPath, String value) throws RepositoryException {
        return delegate.findAuthorizables(relPath, value);
    }

    public Iterator<Authorizable> findAuthorizables(String relPath, String value, int searchType) throws RepositoryException {
        return delegate.findAuthorizables(relPath, value, searchType);
    }

    public Iterator<Authorizable> findAuthorizables(Query query) throws RepositoryException {
        return delegate.findAuthorizables(query);
    }

    public User createUser(String userID, String password) throws RepositoryException {
        return delegate.createUser(userID, password);
    }

    public User createUser(String userID, String password, Principal principal, String intermediatePath) throws RepositoryException {
        return delegate.createUser(userID, password, principal, intermediatePath);
    }

    public User createSystemUser(String userID, String intermediatePath) throws RepositoryException {
        return delegate.createSystemUser(userID, intermediatePath);
    }

    public Group createGroup(String groupID) throws RepositoryException {
        return delegate.createGroup(groupID);
    }

    public Group createGroup(Principal principal) throws RepositoryException {
        return delegate.createGroup(principal);
    }

    public Group createGroup(Principal principal, String intermediatePath) throws RepositoryException {
        return delegate.createGroup(principal, intermediatePath);
    }

    public Group createGroup(String groupID, Principal principal, String intermediatePath) throws RepositoryException {
        return delegate.createGroup(groupID, principal, intermediatePath);
    }

    public boolean isAutoSave() {
        return delegate.isAutoSave();
    }

    public void autoSave(boolean enable) throws RepositoryException {
        delegate.autoSave(enable);
    }

}
