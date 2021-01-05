package biz.netcentric.cq.tools.actool.authorizableinstaller.impl;

import static biz.netcentric.cq.tools.actool.history.PersistableInstallationLogger.msHumanReadable;

import java.security.Principal;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.Query;
import org.apache.jackrabbit.api.security.user.QueryBuilder;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.history.InstallationLogger;

/** Prefetches all authorizables in constructor to be able to answer getAuthorizable(userId) requests much quicker than the ootb oak
 * UserManager itself. */
class PrefetchedAuthorizablesUserManager implements UserManager {

    private static final Logger LOG = LoggerFactory.getLogger(PrefetchedAuthorizablesUserManager.class);

    private final UserManager delegate;
    private final Map<String, Authorizable> authorizableCache;

    public PrefetchedAuthorizablesUserManager(UserManager delegate, InstallationLogger installLog) throws RepositoryException {
        this.delegate = delegate;
        this.authorizableCache = new HashMap<>();

        long startPrefetch = System.currentTimeMillis();
        Iterator<Authorizable> allAuthorizables = delegate.findAuthorizables(new Query() {
            public <T> void build(QueryBuilder<T> builder) {
                // fetch all authorizables
            }
        });

        while (allAuthorizables.hasNext()) {
            Authorizable auth = allAuthorizables.next();
            authorizableCache.put(auth.getID(), auth);
        }

        installLog.addMessage(LOG, "Prefetched " + authorizableCache.size() + " authorizables in "
                + msHumanReadable(System.currentTimeMillis() - startPrefetch));
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
