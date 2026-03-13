// Usage:
// Set the LOG_LEVEL environment variable to control runtime logging verbosity.
// Valid values correspond to the `Severity` enum names used by Kermit, for example:
//   DEBUG, INFO, WARN, ERROR (or Debug, Info, Warn, Error depending on platform mapping)
// Example (bash):
//   export LOG_LEVEL=DEBUG
//   ./gradlew :hardwareBackendRunner:run
// By default, if LOG_LEVEL is not set, the provider uses INFO level.
//
// Example: private val logger = LoggerProvider.getLogger("MyClass")

package io.github.lukewilk.hardware

import co.touchlab.kermit.Logger
import co.touchlab.kermit.loggerConfigInit
import co.touchlab.kermit.platformLogWriter
import co.touchlab.kermit.Severity

object LoggerProvider {
    private val loggerConfig by lazy {
        val logLevel = System.getenv("LOG_LEVEL")?.uppercase() ?: "INFO"
        val minSeverity = Severity.values().find { it.name == logLevel } ?: Severity.Info
        loggerConfigInit(
            platformLogWriter(),
            minSeverity = minSeverity
        )
    }

    fun getLogger(tag: String = "App"): Logger = Logger(loggerConfig).withTag(tag)
}
