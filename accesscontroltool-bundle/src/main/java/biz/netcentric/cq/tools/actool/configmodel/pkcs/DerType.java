package biz.netcentric.cq.tools.actool.configmodel.pkcs;

import java.util.Base64;

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

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public enum DerType {

    PUBLIC_KEY("Public Key", DerData.PUBLIC_KEY_PATTERN),
    PRIVATE_KEY("PKCS#8 Private Key", DerData.PRIVATE_KEY_PATTERN),
    ENCRYPTED_PRIVATE_KEY("PKCS#8 Encrypted Private Key", DerData.ENCRYPTED_PRIVATE_KEY_PATTERN),
    CERTIFICATE("Certificate", DerData.CERTIFICATE_PATTERN);
    
    private final String label;
    private final Pattern pattern;
    
    private DerType(String label, Pattern pattern) {
        this.label = label;
        this.pattern = pattern;
    }

    public Pattern getPattern() {
        return pattern;
    }
    
    byte[] fromPem(String pem) {
        Matcher matcher = pattern.matcher(pem);
        if (!matcher.find()) {
            return null;
        }
        String base64 = matcher.group(1).replaceAll("\\s","");
        return Base64.getDecoder().decode(base64);
    }
}
