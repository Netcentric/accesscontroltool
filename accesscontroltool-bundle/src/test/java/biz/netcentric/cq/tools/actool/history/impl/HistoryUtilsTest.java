package biz.netcentric.cq.tools.actool.history.impl;

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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HistoryUtilsTest {

    @Test
    void testGetIdFromPath() {
        assertEquals("test", HistoryUtils.getIdFromPath("/base/history_test"));
        assertEquals("test", HistoryUtils.getIdFromPath("/some/deeply/nested/history_test"));
    }

    @Test
    void testGetPathFromId() {
        assertEquals("/base/history_test", HistoryUtils.getPathFromId("test", "/base"));
        assertEquals("/deeply/nested/base/history_test", HistoryUtils.getPathFromId("test", "/deeply/nested/base"));
    }
}
