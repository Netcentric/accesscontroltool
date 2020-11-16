/*
 * (C) Copyright 2019 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.aem;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.granite.crypto.CryptoException;
import com.adobe.granite.crypto.CryptoSupport;

@Component(service=AemCryptoSupport.class)
public class AemCryptoSupportImpl extends AemCryptoSupport {

    private static final Logger LOG = LoggerFactory.getLogger(AemCryptoSupportImpl.class);

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private CryptoSupport cryptoSupport;

    @Override
    public String unprotect(String password) {
        String abbreviatedPasswordHint = password.substring(0, 4)+"..";
        try {
            String unprotected = cryptoSupport.unprotect(password);
            LOG.debug("Unprotected {} to password with {} chars", abbreviatedPasswordHint, password.length());
            return unprotected;
        } catch (CryptoException e) {
            throw new IllegalArgumentException("Invalid password string starting with '"+abbreviatedPasswordHint+"' (cannot be decrypted)", e);
        }
    }

}
