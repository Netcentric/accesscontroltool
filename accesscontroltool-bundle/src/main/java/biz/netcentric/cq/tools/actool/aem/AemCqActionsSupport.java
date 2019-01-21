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
import javax.jcr.security.Privilege;

/** OSGi service that encapsulates the usage of class CqAction in AC Tool. If not available (e.g. for the Sling use case), "actions" will not be supported but only "privileges". */
public interface AemCqActionsSupport {

    public static interface AemCqActions {

        public Collection<String> getAllowedActions(String nodePath, Set<Principal> principals) throws RepositoryException;

        public void installActions(String nodePath, Principal principal, Map<String, Boolean> actionMap, Collection<String> inheritedAllows) throws RepositoryException;

        public Set<Privilege> getPrivileges(String action);
    }
    
    public AemCqActions getCqActions(Session session);
    
    
    public boolean definesContent(Node node) throws RepositoryException;
}
