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

import java.util.Date;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import biz.netcentric.cq.tools.actool.history.AcToolExecution;

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

    @Test
    void testExecutionsWithSameTimestampAreBothRetained() {
        Date sameTimestamp = new Date();
        Set<AcToolExecution> executions = new TreeSet<>();
        executions.add(new AcToolExecutionImpl("100_via_jmx",
                "/var/statistics/achistory/history_100_via_jmx", sameTimestamp, true, "/apps/test", 0, 0));
        executions.add(new AcToolExecutionImpl("101_via_jmx",
                "/var/statistics/achistory/history_101_via_jmx", sameTimestamp, true, "/apps/test", 0, 0));

        assertEquals(2, executions.size());
        assertEquals("100_via_jmx", executions.iterator().next().getId());
    }
}
