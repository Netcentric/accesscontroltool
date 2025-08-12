package biz.netcentric.cq.tools.actool.validators;

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
