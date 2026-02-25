package io.github.lukewilk.hardware

import co.touchlab.kermit.Logger
import kotlin.test.Test
import kotlin.test.assertNotNull

class LoggerProviderTest {
    @Test
    fun testLoggerCreation() {
        val logger: Logger = LoggerProvider.getLogger("LoggerProviderTest")
        assertNotNull(logger)
    }
    @Test
    fun testLoggerLogsAtAllLevels() {
        val logger = LoggerProvider.getLogger("LoggerProviderTest")
        logger.v { "Verbose log" }
        logger.d { "Debug log" }
        logger.i { "Info log" }
        logger.w { "Warn log" }
        logger.e { "Error log" }
        logger.a { "Assert log" }
        // No assertion, just ensure no exceptions
    }
}

