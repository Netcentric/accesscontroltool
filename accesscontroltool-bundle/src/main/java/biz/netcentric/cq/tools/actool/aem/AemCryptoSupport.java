/*
 * (C) Copyright 2019 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.aem;

/** OSGi service to encapulate AEM crypto support (to make it optional) */
public abstract class AemCryptoSupport {

    public abstract String unprotect(String password);

    /**
     * Copied from CryptoImpl, need it separately to be able to check if decryption is necessary before CryptoSupport is loaded
     */
    public static boolean isProtected(String text) {
        return text != null && text.length() > 2 && text.charAt(0) == '{' && text.charAt(text.length() - 1) == '}' && text.length() % 2 == 0 && text.lastIndexOf('{') == 0;
    }

}
