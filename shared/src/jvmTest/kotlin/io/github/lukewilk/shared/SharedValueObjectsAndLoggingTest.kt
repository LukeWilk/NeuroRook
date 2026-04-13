package io.github.lukewilk.shared

import co.touchlab.kermit.Severity
import io.github.lukewilk.shared.logging.LoggerProvider
import io.github.lukewilk.shared.logging.readLogLevelFromConfigFile
import io.github.lukewilk.shared.logging.resolvedLogSeverity
import io.github.lukewilk.shared.model.BandPower
import io.github.lukewilk.shared.model.SerialPortSuggestion
import io.github.lukewilk.shared.model.SystemLogEntry
import io.github.lukewilk.shared.model.SystemLogSeverity
import io.github.lukewilk.ui.ChannelState
import java.io.File
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * JVM coverage tests for shared value objects and logging helpers.
 */
class SharedValueObjectsAndLoggingTest {
    @Test
    fun `shared value objects preserve constructor values and support copies`() {
        // Documents the simple immutable contracts that higher-level modules rely on when copying shared state.
        val bandPower = BandPower(name = "Alpha", power = 2.5)
        val serialSuggestion = SerialPortSuggestion(path = "/dev/ttyUSB0")
        val logEntry = SystemLogEntry(
            timestampEpochMillis = 123L,
            severity = SystemLogSeverity.WARN,
            message = "Cable disconnected"
        )
        val channelState = ChannelState(id = 1, name = "Channel 2", enabled = true, rld = false, status = "Configured")

        assertEquals("Alpha", bandPower.name)
        assertEquals(2.5, bandPower.power)
        assertEquals("/dev/ttyUSB0", serialSuggestion.displayName)
        assertEquals("", serialSuggestion.details)
        assertEquals(false, serialSuggestion.isUsbDevice)
        assertEquals(false, serialSuggestion.isRecommended)
        assertEquals(SystemLogSeverity.WARN, logEntry.severity)
        assertEquals("Cable disconnected", logEntry.message)
        assertEquals("Not configured", channelState.copy(enabled = false, status = "Not configured").status)
    }

    @Test
    fun `resolved log severity uppercases known values and falls back to info`() {
        // Verifies shared logger configuration stays deterministic for valid, missing, and invalid severity values.
        assertEquals(Severity.Warn, resolvedLogSeverity("warn"))
        assertEquals(Severity.Info, resolvedLogSeverity(null))
        assertEquals(Severity.Info, resolvedLogSeverity("not-a-level"))
    }

    @Test
    fun `jvm log level lookup prefers env values before reading config files`() {
        // Confirms the JVM helper trims config entries yet lets an explicit environment override win.
        val configPath = createTempFile(prefix = "logger-provider", suffix = ".conf")
        configPath.writeText("LOG_LEVEL= debug \n")

        val configFile = configPath.toFile()
        try {
            assertEquals("TRACE", readLogLevelFromConfigFile(configFile, envLogLevel = "TRACE"))
            assertEquals("debug", readLogLevelFromConfigFile(configFile, envLogLevel = null))
            assertEquals(null, readLogLevelFromConfigFile(File(configFile.parentFile, "missing.conf"), envLogLevel = null))
        } finally {
            configFile.delete()
        }
    }

    @Test
    fun `logger provider returns a logger instance for caller tags`() {
        // Exercises the shared logger-provider entry point directly inside the instrumented JVM process for coverage.
        assertNotNull(LoggerProvider.getLogger("SharedValueObjectsAndLoggingTest"))
    }
}

