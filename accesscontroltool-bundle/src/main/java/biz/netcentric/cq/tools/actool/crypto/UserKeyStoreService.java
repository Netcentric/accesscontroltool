package biz.netcentric.cq.tools.actool.crypto;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2026 Cognizant Netcentric
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.Certificate;

import org.apache.sling.api.resource.ResourceResolver;

/** Interface for managing user's key stores. 
 * This allows to decouple from a concrete (AEM-specific) interface like {@link com.adobe.granite.keystore.KeyStoreService} */
public interface UserKeyStoreService {

    boolean keyStoreExists(ResourceResolver resourceResolver, String userId);

    void addKeyStoreKeyEntry(ResourceResolver resourceResolver, String userId, String key, PrivateKey privateKey,
            Certificate[] certificates);

    void addKeyStoreKeyPair(ResourceResolver resourceResolver, String userId, KeyPair keyPair, String key);

    void createKeyStore(ResourceResolver resourceResolver, String userId, char[] keyStorePasswordCharArray);

}
