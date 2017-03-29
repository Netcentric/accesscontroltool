package biz.netcentric.cq.tools.actool.session.impl;

import biz.netcentric.cq.tools.actool.session.SessionManager;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.HashMap;
import java.util.Map;

@Component
@Service
public class SessionManagerImpl implements SessionManager {

    private static final Logger LOG = LoggerFactory.getLogger(SessionManagerImpl.class);

    ThreadLocal<Session> sessionHolder;

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Activate
    public void activate() {
        sessionHolder = new ThreadLocal<>();
    }

    @Override
    public Session getSession() throws RepositoryException {
        Session session = getCurrentUserSession();
        if(session != null) {
            return session;
        }

        return getServiceUserSession(session);
    }

    @Override
    public void close(Session session) {
        if(session.equals(sessionHolder.get())) {
            LOG.info("Session is from current user. No need to close it.");
            return;
        }

        LOG.info("actool trying to close session.");
        if (session != null && session.isLive()) {
            session.logout();
        }

    }

    private Session getCurrentUserSession() {
        Session session = sessionHolder.get();
        if(session != null && session.isLive()) {
            LOG.debug("Using current user session");
            return session;
        }
        return getThreadSession();
    }

    private Session getThreadSession() {

        ResourceResolver resolver = resourceResolverFactory.getThreadResourceResolver();
        if(resolver != null) {
            Session session = resolver.adaptTo(Session.class);
            if(session != null) {
                LOG.debug("Thread session user id: [{}]", session.getUserID());
                sessionHolder.set(session);
                return session;
            } else {
                LOG.debug("Thread user session is null");
            }
        } else {
            LOG.debug("Thread resource resolver is null");
        }
        return null;
    }

    private Session getServiceUserSession(Session session) throws RepositoryException {
        LOG.debug("Trying to get actool system user session");
        try {
            Map<String, Object> map = new HashMap<>();
            map.put(ResourceResolverFactory.SUBSERVICE, "actool");
            ResourceResolver resourceResolver = resourceResolverFactory.getServiceResourceResolver(map);

            if (resourceResolver != null) {
                session = resourceResolver.adaptTo(Session.class);
                LOG.debug("actool session has been retrieved successfully");
            }
        } catch (LoginException ex) {
            LOG.error("AC Tool User not available.");
            throw new RepositoryException("Cannot get session for actool", ex);
        }
        return session;
    }

}
