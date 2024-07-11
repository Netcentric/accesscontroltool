package biz.netcentric.cq.tools.actool.user;

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
