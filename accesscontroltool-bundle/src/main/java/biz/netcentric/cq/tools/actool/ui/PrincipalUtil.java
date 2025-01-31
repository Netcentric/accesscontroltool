package biz.netcentric.cq.tools.actool.ui;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2024 Cognizant Netcentric
 * %%
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * #L%
 */

import java.security.Principal;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.AuthorizableTypeException;
import org.apache.jackrabbit.api.security.user.Group;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Encapsulates all principals bound to a given session.
 * Natively exposed from Oak 1.40 onwards (see OAK-8611).
 */
class BoundPrincipals {

    private static final Logger log = LoggerFactory.getLogger(BoundPrincipals.class);

    private Set<Principal> boundPrincipals;

    BoundPrincipals(@NotNull JackrabbitSession session) throws RepositoryException {
        final String userId = session.getUserID();
        // newer Oak versions expose bound principals via session attribute (https://issues.apache.org/jira/browse/OAK-9415)
        boundPrincipals = (Set<Principal>)session.getAttribute("oak.bound-principals");
        if (boundPrincipals == null) {
            boundPrincipals = new HashSet<>();
            Authorizable authorizable = session.getUserManager().getAuthorizable(userId);
            if (authorizable == null) {
                throw new AuthorizableTypeException("Could not find authorizable for session's user ID " + userId);
            }
            boundPrincipals.add(authorizable.getPrincipal());
            
            Iterator<Group> groupIterator = authorizable.memberOf();
            while (groupIterator.hasNext()) {
                boundPrincipals.add(groupIterator.next().getPrincipal());
            }
            log.debug("Bound principals calculated from user manager");
        } else {
            log.debug("Bound principals found in session attribute");
        }
        if (log.isDebugEnabled()) {
            log.debug("Bound principals for session associated with user id {}: {}", userId, boundPrincipals.stream().map(Principal::getName).collect(Collectors.joining(", ")));
        }
    }

    public boolean containsOneOf(@NotNull Collection<String> principalNames) {
        for (Principal principal : boundPrincipals) {
            if (principalNames.contains(principal.getName())) {
                return true;
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("None of the bound principals {} match any of the provided names: {}",
                    boundPrincipals.stream().map(Principal::getName).collect(Collectors.joining(", ")), principalNames);
        }
        return false;
    }

}
