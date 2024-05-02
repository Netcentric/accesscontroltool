package biz.netcentric.cq.tools.actool.user.impl;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Query;
import org.apache.jackrabbit.api.security.user.QueryBuilder;
import org.apache.jackrabbit.api.security.user.QueryBuilder.Direction;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;

import biz.netcentric.cq.tools.actool.user.UserProcessor;

@Component
public class UserProcessorImpl implements UserProcessor {

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private SlingRepository repository;

    @Override
    public void forEachNonSystemUser(Consumer<User> userConsumer) throws RepositoryException {
        Session session = null;
        try { 
            session = repository.loginService(null, null);
            if (!(session instanceof JackrabbitSession)) {
                throw new IllegalStateException("Session is no instance of JackrabbitSession");
            }
            JackrabbitSession jrSession = JackrabbitSession.class.cast(session);
            Iterator<Authorizable> iterator = jrSession.getUserManager().findAuthorizables(new Query() {
                public <T> void build(QueryBuilder<T> builder) {
                    // only enabled users
                    builder.setCondition(builder.not(builder.exists("@rep:disabled")));
                    // is is not possible to add a condition for jcr:primaryType with value type name
                    builder.setSortOrder("@name", Direction.ASCENDING);
                    builder.setSelector(User.class);
                }
            });
            StreamSupport.stream(
                            Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED),
                            false)
                .map(User.class::cast)
                .filter(u -> !u.isSystemUser())
                .forEach(userConsumer);
        } finally {
            if (session != null) {
                session.logout();
            }
        }
    }
}
