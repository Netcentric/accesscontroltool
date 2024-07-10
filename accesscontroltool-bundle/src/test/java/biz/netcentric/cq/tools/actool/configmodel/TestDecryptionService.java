package biz.netcentric.cq.tools.actool.configmodel;

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

import biz.netcentric.cq.tools.actool.crypto.DecryptionService;

public final class TestDecryptionService implements DecryptionService {

    @Override
    public String decrypt(String text) {
        return text.substring(1, text.length()-1);
    }
}
