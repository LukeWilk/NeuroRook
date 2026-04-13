package io.github.lukewilk.hardware

import co.touchlab.kermit.Logger
import io.github.lukewilk.shared.logging.LoggerProvider
import kotlin.test.Test
import kotlin.test.assertNotNull

class LoggerProviderTest {
    @Test
    fun `logger provider returns a logger for the requested tag`() {
        // Confirms shared code can obtain a logger instance without platform-specific setup in tests.
        val logger: Logger = LoggerProvider.getLogger("LoggerProviderTest")
        assertNotNull(logger)
    }

    @Test
    fun `logger created by the provider accepts every severity`() {
        // Exercises the configured logger across all severity methods and only fails if logging throws.
        val logger = LoggerProvider.getLogger("LoggerProviderTest")
        logger.v { "Verbose log" }
        logger.d { "Debug log" }
        logger.i { "Info log" }
        logger.w { "Warn log" }
        logger.e { "Error log" }
        logger.a { "Assert log" }
        assertNotNull(logger)
    }
}
