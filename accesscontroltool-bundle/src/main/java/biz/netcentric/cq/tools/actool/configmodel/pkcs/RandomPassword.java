package biz.netcentric.cq.tools.actool.configmodel.pkcs;

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

import java.security.SecureRandom;
import java.util.Random;

public class RandomPassword {

    static final char[] ALLOWED_CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGJKLMNPRSTUVWXYZ0123456789".toCharArray();

    private RandomPassword() {
    }

    public static char[] generate(int length) {
        Random random = new SecureRandom();
        char[] password = new char[length];
        for (int i = 0; i < length; i++) {
            password[i] = ALLOWED_CHARACTERS[random.nextInt(ALLOWED_CHARACTERS.length)];
        }
        return password;
    }

}

