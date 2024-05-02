package biz.netcentric.cq.tools.actool.user;

import java.util.function.Consumer;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.api.security.user.User;

public interface UserProcessor {

    /**
     * Calls the provided functional interface for each non-system enabled user on the system.
     * @param userConsumer the functional interface to call for each user
     * @throws RepositoryException
     */
    void forEachNonSystemUser(Consumer<User> userConsumer) throws RepositoryException;

}
