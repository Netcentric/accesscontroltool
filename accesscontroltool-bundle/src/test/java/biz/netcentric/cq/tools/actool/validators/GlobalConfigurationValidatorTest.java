/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.validators;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class GlobalConfigurationValidatorTest {

    @Test
    public void testVersionIsNewerOrEqualTo() {
        assertTrue(GlobalConfigurationValidator.versionIsNewerOrEqualTo("1.0.0", "1.0.0"));
        assertTrue(GlobalConfigurationValidator.versionIsNewerOrEqualTo("1.1.0", "1.0.0"));
        assertTrue(GlobalConfigurationValidator.versionIsNewerOrEqualTo("2.0.0", "1.0.0"));
        assertTrue(GlobalConfigurationValidator.versionIsNewerOrEqualTo("1.0.0.test", "1.0.0"));

        assertFalse(GlobalConfigurationValidator.versionIsNewerOrEqualTo("1.0.0", "1.1.0"));
    }

}
