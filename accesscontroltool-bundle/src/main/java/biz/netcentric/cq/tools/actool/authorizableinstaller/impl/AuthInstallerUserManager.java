package biz.netcentric.cq.tools.actool.authorizableinstaller.impl;

import java.security.Principal;
import java.util.Set;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;

/** Replicates the methods from {@link UserManager} but does not extend this interface because {@link UserManager} is a provider type that
 * may receive additional methods in the future (and hence it would break this code). See {@link AuthInstallerUserManagerPrefetchingImpl}
 * for the motivation to use this special user manager (performance). */
interface AuthInstallerUserManager {

    // -- AC Tool special methods
    Set<String> getDeclaredIsMemberOf(String id) throws RepositoryException;

    Set<String> getDeclaredMembersWithoutRegularUsers(String id);

    UserManager getOakUserManager();

    void removeAuthorizable(Authorizable authorizable) throws RepositoryException;

    // -- methods from oak UserManager that the AC tool uses (delegated, only the relevant methods are listed here)

    Authorizable getAuthorizable(String id) throws RepositoryException;

    User createUser(String userID, String password, Principal principal, String intermediatePath) throws RepositoryException;

    User createSystemUser(String userID, String intermediatePath) throws RepositoryException;

    Group createGroup(Principal principal) throws RepositoryException;

    Group createGroup(Principal principal, String intermediatePath) throws RepositoryException;

}
