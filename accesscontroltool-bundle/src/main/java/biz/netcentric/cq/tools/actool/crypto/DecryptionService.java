package biz.netcentric.cq.tools.actool.crypto;

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
