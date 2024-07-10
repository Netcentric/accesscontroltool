package biz.netcentric.cq.tools.actool.validators.exceptions;

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

public class TooManyActionsException extends AcConfigBeanValidationException {
    public TooManyActionsException(String message) {
        super(message);
    }
}
