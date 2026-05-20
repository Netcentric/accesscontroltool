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

import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

class PersistableInstallationLoggerTest {

    private PersistableInstallationLogger logger;

    @BeforeEach
    void setUp() {
        logger = new PersistableInstallationLogger();
    }

    @Test
    void testGetVerboseMessageHistoryContainsAllMessageTypes() {
        logger.addMessage(NOPLogger.NOP_LOGGER, "regular message");
        logger.addWarning(NOPLogger.NOP_LOGGER, "a warning");
        logger.addVerboseMessage(NOPLogger.NOP_LOGGER, "verbose detail");
        logger.addError(NOPLogger.NOP_LOGGER, "an error", null);

        String verbose = logger.getVerboseMessageHistory();

        assertTrue(verbose.contains("regular message"), "verbose history should contain regular messages");
        assertTrue(verbose.contains("a warning"), "verbose history should contain warnings");
        assertTrue(verbose.contains("verbose detail"), "verbose history should contain verbose messages");
        assertTrue(verbose.contains("an error"), "verbose history should contain errors");
    }

    @Test
    void testGetVerboseMessageHistoryContainsVerboseMessagesAbsentFromMessageHistory() {
        logger.addMessage(NOPLogger.NOP_LOGGER, "regular message");
        logger.addVerboseMessage(NOPLogger.NOP_LOGGER, "verbose only detail");

        String messageHistory = logger.getMessageHistory();
        String verboseHistory = logger.getVerboseMessageHistory();

        assertFalse(messageHistory.contains("verbose only detail"),
                "non-verbose message history should NOT contain verbose messages");
        assertTrue(verboseHistory.contains("verbose only detail"),
                "verbose message history should contain verbose messages");
        assertTrue(verboseHistory.contains("regular message"),
                "verbose message history should also contain regular messages");
    }

    @Test
    void testGetVerboseMessageHistoryWithExceptionIncludesStackTrace() {
        RuntimeException ex = new RuntimeException("something went wrong");
        logger.addError(NOPLogger.NOP_LOGGER, "error with exception", ex);

        String verboseHistory = logger.getVerboseMessageHistory();

        assertTrue(verboseHistory.contains("error with exception"),
                "verbose history should contain the error message");
        assertTrue(verboseHistory.contains("RuntimeException"),
                "verbose history should contain the exception stack trace");
    }

    @Test
    void testGetVerboseMessageHistoryEmptyWhenNoMessages() {
        String verboseHistory = logger.getVerboseMessageHistory();
        assertTrue(verboseHistory.isEmpty(), "verbose history should be empty when no messages have been added");
    }

    @Test
    void testGetVerboseMessageHistoryTimestampPrefix() {
        logger.addMessage(NOPLogger.NOP_LOGGER, "timed message");
        logger.addVerboseMessage(NOPLogger.NOP_LOGGER, "timed verbose");

        String verboseHistory = logger.getVerboseMessageHistory();

        // Each entry is formatted as: \nHH:mm:ss.SSS: <message>
        Pattern timestampPattern = Pattern.compile("\\d{2}:\\d{2}:\\d{2}\\.\\d{3}: ");
        assertTrue(timestampPattern.matcher(verboseHistory).find(),
                "each entry should be prefixed with a timestamp in HH:mm:ss.SSS format");

        // Verify every line that contains a known message has the timestamp prefix
        for (String line : verboseHistory.split(PersistableInstallationLogger.EOL)) {
            if (line.isEmpty()) {
                continue;
            }
            assertTrue(timestampPattern.matcher(line).find(),
                    "line should start with HH:mm:ss.SSS timestamp: " + line);
        }
    }

    @Test
    void testGetVerboseMessageHistoryMessagesAreOrderedByIndex() {
        logger.addMessage(NOPLogger.NOP_LOGGER, "first");
        logger.addVerboseMessage(NOPLogger.NOP_LOGGER, "second");
        logger.addMessage(NOPLogger.NOP_LOGGER, "third");

        String verboseHistory = logger.getVerboseMessageHistory();

        int firstPos = verboseHistory.indexOf("first");
        int secondPos = verboseHistory.indexOf("second");
        int thirdPos = verboseHistory.indexOf("third");

        assertTrue(firstPos < secondPos, "first message should appear before second");
        assertTrue(secondPos < thirdPos, "second message should appear before third");
    }
}
