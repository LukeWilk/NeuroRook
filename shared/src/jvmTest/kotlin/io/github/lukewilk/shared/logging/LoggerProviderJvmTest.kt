package io.github.lukewilk.shared.logging

import co.touchlab.kermit.Logger
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * JVM coverage tests for logger configuration lookup.
 */
class LoggerProviderJvmTest {
    @Test
    fun `read log level from config returns null when no config file exists`() {
        // Confirms the JVM lookup falls back cleanly when the working directory has no NeuroRook.conf.
        withTemporaryWorkingDirectory {
            assertNull(runProbe(this, mode = "read").ifBlank { null })
        }
    }

    @Test
    fun `read log level from config trims the configured value`() {
        // Verifies the JVM lookup accepts the config-file path when LOG_LEVEL is not provided by the environment.
        withTemporaryWorkingDirectory {
            resolve("NeuroRook.conf").writeText("LOG_LEVEL= debug \n")
            assertEquals("debug", runProbe(this, mode = "read"))
        }
    }

    @Test
    fun `logger provider creates a logger when config contains an unknown severity`() {
        // Exercises the lazy loggerConfig fallback branch that coerces invalid severities back to Info.
        withTemporaryWorkingDirectory {
            resolve("NeuroRook.conf").writeText("LOG_LEVEL=NOT_A_REAL_LEVEL\n")
            assertEquals("ok", runProbe(this, mode = "logger"))
        }
    }

    /**
     * Runs the probe in an isolated working directory and extracts the explicit result marker from stdout.
     */
    private fun runProbe(directory: java.nio.file.Path, mode: String): String {
        val javaBin = java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val result = ProcessBuilder(
            javaBin,
            "-cp",
            System.getProperty("java.class.path"),
            "io.github.lukewilk.shared.logging.LoggerProviderJvmProbe",
            mode
        )
            .redirectErrorStream(true)
            .directory(directory.toFile())
            .apply { environment().remove("LOG_LEVEL") }
            .start()

        val output = result.inputStream.bufferedReader().readText().trim()
        assertEquals(0, result.waitFor(), "Expected probe process to exit successfully, output was: $output")
        return output.substringAfterLast("RESULT:")
    }

    /**
     * Creates a temporary directory so each logger-config test owns its own `NeuroRook.conf` fixture.
     */
    private fun withTemporaryWorkingDirectory(block: java.nio.file.Path.() -> Unit) {
        val tempDir = Files.createTempDirectory("logger-provider-test")
        try {
            block(tempDir)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}

/** Small subprocess probe so file-based logger config branches can run without inheriting the parent LOG_LEVEL environment. */
object LoggerProviderJvmProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        when (args[0]) {
            "read" -> print("RESULT:${readLogLevelFromConfig().orEmpty()}")
            "logger" -> {
                val logger: Logger = LoggerProvider.getLogger("LoggerProviderJvmTest")
                logger.i { "Probe logger initialized" }
                print("RESULT:ok")
            }
        }
    }
}

