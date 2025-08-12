package biz.netcentric.cq.tools.actool.user;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2024 Cognizant Netcentric
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
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
