/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.authorizableinstaller;

public class AuthorizableCreatorException extends Exception {
    public AuthorizableCreatorException(String message) {
        super(message);
    }

    public AuthorizableCreatorException(Throwable e) {
        super(e);
    }
    
    public AuthorizableCreatorException(String message, Throwable e) {
        super(message, e);
    }
}
