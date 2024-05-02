/*
 * (C) Copyright 2024 Cognizant Netcentric.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.helper;

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
