package biz.netcentric.cq.tools.actool.slingsettings;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.api.Test;

public class ExtendedSlingSettingsServiceImplTest {

    @Test
    public void testGetBestRunModeMatchCountFromSpec() {
        assertEquals(0, ExtendedSlingSettingsServiceImpl.getBestRunModeMatchCountFromSpec("test1.test2,-test3.test4", Set.of("test5")));
        assertEquals(0, ExtendedSlingSettingsServiceImpl.getBestRunModeMatchCountFromSpec("test1.test2,-test3.test4", Set.of("test1", "test3")));
        assertEquals(0, ExtendedSlingSettingsServiceImpl.getBestRunModeMatchCountFromSpec("test1.test2,-test3.test4", Set.of("test2", "test3")));
        assertEquals(2, ExtendedSlingSettingsServiceImpl.getBestRunModeMatchCountFromSpec("test1.test2,-test3.test4", Set.of("test1", "test2")));
        assertEquals(2, ExtendedSlingSettingsServiceImpl.getBestRunModeMatchCountFromSpec("test1.test2,-test3.test4", Set.of("test2", "test4")));
        assertEquals(3, ExtendedSlingSettingsServiceImpl.getBestRunModeMatchCountFromSpec("test1.test2,-test3.test4,test5.test6.test7", Set.of("test1", "test2", "test4", "test5", "test6", "test7")));
    }
}
