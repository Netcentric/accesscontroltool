package biz.netcentric.cq.tools.actool.slingsettings;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

public class ExtendedSlingSettingsServiceImplTest {

    @Test
    public void testGetBestRunModeMatchCountFromSpec() {
        assertEquals(0, ExtendedSlingSettingsServiceImpl.getBestRunModeMatchCountFromSpec("test1.test2,-test3.test4", Collections.singleton("test5")));
        assertEquals(0, ExtendedSlingSettingsServiceImpl.getBestRunModeMatchCountFromSpec("test1.test2,-test3.test4", ImmutableSet.of("test1", "test3")));
        assertEquals(0, ExtendedSlingSettingsServiceImpl.getBestRunModeMatchCountFromSpec("test1.test2,-test3.test4", ImmutableSet.of("test2", "test3")));
        assertEquals(2, ExtendedSlingSettingsServiceImpl.getBestRunModeMatchCountFromSpec("test1.test2,-test3.test4", ImmutableSet.of("test1", "test2")));
        assertEquals(2, ExtendedSlingSettingsServiceImpl.getBestRunModeMatchCountFromSpec("test1.test2,-test3.test4", ImmutableSet.of("test2", "test4")));
        assertEquals(3, ExtendedSlingSettingsServiceImpl.getBestRunModeMatchCountFromSpec("test1.test2,-test3.test4,test5.test6.test7", ImmutableSet.of("test1", "test2", "test4", "test5", "test6", "test7")));
    }
}
