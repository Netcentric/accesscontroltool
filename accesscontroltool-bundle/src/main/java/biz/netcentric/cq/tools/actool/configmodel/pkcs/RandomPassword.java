package biz.netcentric.cq.tools.actool.configmodel.pkcs;

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

