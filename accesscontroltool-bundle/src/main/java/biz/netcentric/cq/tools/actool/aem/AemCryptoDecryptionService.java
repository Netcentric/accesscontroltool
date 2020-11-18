/*
 * (C) Copyright 2019 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.aem;

import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.granite.crypto.CryptoException;
import com.adobe.granite.crypto.CryptoSupport;

import biz.netcentric.cq.tools.actool.crypto.DecryptionService;

@Component(property = Constants.SERVICE_RANKING + ":Integer=1000")
public class AemCryptoDecryptionService implements DecryptionService {

    private static final Logger LOG = LoggerFactory.getLogger(AemCryptoDecryptionService.class);

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private CryptoSupport cryptoSupport;

    @Override
    public String decrypt(String text) {
        if (!cryptoSupport.isProtected(text)) {
            LOG.debug("Given text is not encrypted and therefore doesn't need decryption: {}", text);
            return text;
        }
        String abbreviatedPasswordHint = text.substring(0, 4)+"..";
        try {
            String unprotected = cryptoSupport.unprotect(text);
            LOG.debug("Decrypted {} to text with {} chars", abbreviatedPasswordHint, unprotected.length());
            return unprotected;
        } catch (CryptoException e) {
            throw new IllegalArgumentException("Invalid password string starting with '"+abbreviatedPasswordHint+"' (cannot be decrypted)", e);
        }
    }

}
