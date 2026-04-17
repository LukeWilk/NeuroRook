package io.github.lukewilk.shared.logging

import co.touchlab.kermit.Logger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * JVM tests for logger configuration lookup.
 */
class LoggerProviderJvmTest {
    @Test
    fun `parsed config log level helper returns null for missing and blank lines and trims valid values`() {
        // Covers the extracted config-line parser directly so null, blank, and nonblank values all stay deterministic.
        assertNull(parsedConfigLogLevel(null))
        assertNull(parsedConfigLogLevel("OTHER_KEY=warn"))
        assertNull(parsedConfigLogLevel("LOG_LEVEL=   "))
        assertEquals("trace", parsedConfigLogLevel("  LOG_LEVEL= trace  "))
        assertEquals("warn", parsedConfigLogLevel("LOG_LEVEL= warn "))
    }

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
    fun `read log level wrapper uses the instrumented worker directory while respecting process env precedence`() {
        // Exercises the actual JVM wrapper in-process and verifies it stays aligned with the same env-over-file precedence as the file helper.
        withProcessWorkingDirectoryConfig {
            writeText("LOG_LEVEL= trace \n")
            assertEquals(
                readLogLevelFromConfigFile(toFile(), envLogLevel = System.getenv("LOG_LEVEL")),
                readLogLevelFromConfig()
            )
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

    @Test
    fun `logger provider default tag bridge creates a logger`() {
        // Targets the JVM default-argument bridge so Kover credits LoggerProvider.getLogger$default.
        val logger: Logger = LoggerProvider.getLogger()
        logger.i { "Default logger initialized" }
        assertNotNull(logger)
    }

    /**
     * Runs the probe in an isolated working directory and extracts the explicit result marker from stdout.
     */
    private fun runProbe(directory: Path, mode: String): String {
        val javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString()
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
    private fun withTemporaryWorkingDirectory(block: Path.() -> Unit) {
        val tempDir = Files.createTempDirectory("logger-provider-test")
        try {
            block(tempDir)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    /**
     * Temporarily swaps the `NeuroRook.conf` file in the already-started JVM worker directory so the actual wrapper can be covered in-process.
     */
    private fun withProcessWorkingDirectoryConfig(block: Path.() -> Unit) {
        synchronized(processWorkingDirectoryConfigLock) {
            val configPath = Path.of(System.getProperty("user.dir")).resolve("NeuroRook.conf")
            val hadOriginalFile = configPath.exists()
            val originalContents = if (hadOriginalFile) configPath.readText() else null

            try {
                block(configPath)
            } finally {
                if (hadOriginalFile) {
                    configPath.writeText(originalContents!!)
                } else {
                    configPath.deleteIfExists()
                }
            }
        }
    }

    private companion object {
        /** Serializes mutations to the shared worker-directory config file used by the in-process wrapper test. */
        val processWorkingDirectoryConfigLock = Any()
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

