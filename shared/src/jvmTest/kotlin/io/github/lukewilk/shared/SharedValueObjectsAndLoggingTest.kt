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
import kotlin.test.assertNull

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
    fun `jvm log level lookup ignores blank env and blank config values`() {
        // Covers the blank-value guard branches so empty environment or file entries do not become effective log levels.
        val blankConfigPath = createTempFile(prefix = "logger-provider-blank", suffix = ".conf")
        val warnConfigPath = createTempFile(prefix = "logger-provider-warn", suffix = ".conf")
        blankConfigPath.writeText("LOG_LEVEL=   \n")
        warnConfigPath.writeText("LOG_LEVEL= warn \n")

        val blankConfigFile = blankConfigPath.toFile()
        val warnConfigFile = warnConfigPath.toFile()
        try {
            assertNull(readLogLevelFromConfigFile(blankConfigFile, envLogLevel = "   "))
            assertEquals("warn", readLogLevelFromConfigFile(warnConfigFile, envLogLevel = "   "))
        } finally {
            blankConfigFile.delete()
            warnConfigFile.delete()
        }
    }

    @Test
    fun `jvm log level lookup returns null when the config file has no log level entry`() {
        // Covers the existing-file/no-matching-line branch so unrelated config content does not produce a phantom level.
        val configPath = createTempFile(prefix = "logger-provider-missing-key", suffix = ".conf")
        configPath.writeText("SOME_OTHER_KEY=value\n")

        val configFile = configPath.toFile()
        try {
            assertNull(readLogLevelFromConfigFile(configFile, envLogLevel = null))
        } finally {
            configFile.delete()
        }
    }

    @Test
    fun `jvm log level lookup default argument matches the explicit environment-backed call`() {
        // Covers the generated default-argument wrapper without assuming anything about the process LOG_LEVEL environment.
        val configPath = createTempFile(prefix = "logger-provider-default-arg", suffix = ".conf")
        configPath.writeText("LOG_LEVEL= info \n")

        val configFile = configPath.toFile()
        try {
            assertEquals(
                readLogLevelFromConfigFile(configFile, envLogLevel = System.getenv("LOG_LEVEL")),
                readLogLevelFromConfigFile(configFile)
            )
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

