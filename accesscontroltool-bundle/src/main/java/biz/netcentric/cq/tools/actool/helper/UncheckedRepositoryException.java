/*
 * (C) Copyright 2024 Cognizant Netcentric.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 */
package biz.netcentric.cq.tools.actool.helper;

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

import java.util.Objects;

import javax.jcr.RepositoryException;

/**
 * Wraps a {@link RepositoryException} with an unchecked exception.
 * This is useful for usage within lambdas.
 *
 */
public class UncheckedRepositoryException extends RuntimeException {

    private static final long serialVersionUID = 2727436608772501551L;

    /**
     * Constructs an instance of this class.
     *
     * @param   cause
     *          the {@code RepositoryException}
     *
     * @throws  NullPointerException
     *          if the cause is {@code null}
     */
    public UncheckedRepositoryException(RepositoryException cause) {
        super(Objects.requireNonNull(cause));
    }
    
    /**
     * Returns the cause of this exception.
     *
     * @return  the {@code RepositoryException} which is the cause of this exception.
     */
    @Override
    public synchronized RepositoryException getCause() {
        return (RepositoryException) super.getCause();
    }
}
