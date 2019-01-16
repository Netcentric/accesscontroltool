/*
 * (C) Copyright 2019 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.aem;

import java.security.Principal;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.security.util.CqActions;

@Component(service = AemCqActionsSupport.class)
public class AemCqActionsSupportImpl implements AemCqActionsSupport {

    private static final Logger LOG = LoggerFactory.getLogger(AemCqActionsSupportImpl.class);

    public static class CqActionsWrapper implements AemCqActions {
        
        private final CqActions cqActions;

        public CqActionsWrapper(com.day.cq.security.util.CqActions cqActions) {
            this.cqActions = cqActions;
        }

        public Collection<String> getAllowedActions(String nodePath, Set<Principal> principals) throws RepositoryException {
            return cqActions.getAllowedActions(nodePath, principals);
        }

        public void installActions(String nodePath, Principal principal, Map<String, Boolean> actionMap, Collection<String> inheritedAllows) throws RepositoryException {
            cqActions.installActions(nodePath, principal, actionMap, inheritedAllows);
        }

    }
    
    public AemCqActions getCqActions(Session session) {
    
        CqActions cqActions;
        try {
            cqActions = new CqActions(session);
            final CqActionsWrapper cqActionWrapper = new CqActionsWrapper(cqActions);
            return cqActionWrapper;
        } catch (Exception e) {
            throw new IllegalStateException("Could not instantiate CqActions: "+e, e);
        }
    }
    
    
    public boolean definesContent(Node node) throws RepositoryException {
        return CqActions.definesContent(node);
    }
    
}
