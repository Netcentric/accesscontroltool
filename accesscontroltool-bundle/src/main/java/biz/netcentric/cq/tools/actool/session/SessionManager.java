package biz.netcentric.cq.tools.actool.session;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

/**
 * Session manager has to be used to manage sessions.
 */
public interface SessionManager {

    /**
     * Gets session. Either local thread or system user.
     *
     * @return the session
     * @throws RepositoryException the repository exception
     */
    Session getSession() throws RepositoryException;

    /**
     * Close.
     *
     * @param session the session
     */
    void close(Session session);
}
