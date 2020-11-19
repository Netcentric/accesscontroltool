/*
 * (C) Copyright 2019 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.crypto;

/** Interface for decrypting encrypted text.
 * This allows to decouple from a concrete (AEM-specific) interface like {@link com.adobe.granite.crypto.CryptoSupport} */
public interface DecryptionService {
    
    /**
     * Decrypts the given parameter in case it is encrypted. Otherwise returns the given parameter unmodified.
     * @param text the potentially encrypted text
     * @return the decrypted text
     */
    public String decrypt(String text);
}
