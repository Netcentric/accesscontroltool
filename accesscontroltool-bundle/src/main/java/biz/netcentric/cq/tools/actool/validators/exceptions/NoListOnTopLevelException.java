package biz.netcentric.cq.tools.actool.validators.exceptions;

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

public class NoListOnTopLevelException extends AcConfigBeanValidationException {

    public NoListOnTopLevelException(String message, Throwable cause) {
        super(message, cause);
    }

}
