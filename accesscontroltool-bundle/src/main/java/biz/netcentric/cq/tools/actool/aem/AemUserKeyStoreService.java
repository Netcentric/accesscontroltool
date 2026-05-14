package biz.netcentric.cq.tools.actool.aem;

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
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;

import com.adobe.granite.keystore.KeyStoreService;

import biz.netcentric.cq.tools.actool.crypto.UserKeyStoreService;

@Component
public class AemUserKeyStoreService implements UserKeyStoreService {

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private KeyStoreService delegate;

    @Override
    public boolean keyStoreExists(ResourceResolver resourceResolver, String userId) {
        return delegate.keyStoreExists(resourceResolver, userId);
    }

    @Override
    public void addKeyStoreKeyEntry(ResourceResolver resourceResolver, String userId, String key, PrivateKey privateKey,
            Certificate[] certificates) {
        delegate.addKeyStoreKeyEntry(resourceResolver, userId, key, privateKey, certificates);
    }

    @Override
    public void addKeyStoreKeyPair(ResourceResolver resourceResolver, String userId, KeyPair keyPair, String key) {
        delegate.addKeyStoreKeyPair(resourceResolver, userId, keyPair, key);
    }

    @Override
    public void createKeyStore(ResourceResolver resourceResolver, String userId, char[] keyStorePasswordCharArray) {
        delegate.createKeyStore(resourceResolver, userId, keyStorePasswordCharArray);
    }

}
