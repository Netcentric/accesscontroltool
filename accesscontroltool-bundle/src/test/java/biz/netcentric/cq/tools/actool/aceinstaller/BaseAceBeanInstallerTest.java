package biz.netcentric.cq.tools.actool.aceinstaller;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2025 Cognizant Netcentric
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.api.Test;

class BaseAceBeanInstallerTest {

    @Test
    void removePathsWithPrefixes() {
        assertEquals(Set.of("/apps/test", "/content2/other"), BaseAceBeanInstaller.removePathsWithPrefixes(Set.of("/content/test", "/content/test2", "/apps/test", "/content2/other"), new String[] { "/content" }));
    }

    @Test
    void removePathsWithoutPrefixes() {
        assertEquals(Set.of("/content/test", "/content/test2"), BaseAceBeanInstaller.removePathsWithoutPrefixes(Set.of("/content/test", "/content/test2", "/apps/test", "/content2/other"), new String[] { "/content" }));
    }
}
