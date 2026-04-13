package io.github.lukewilk.shared.logging

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import co.touchlab.kermit.platformLogWriter

expect fun readLogLevelFromConfig(): String?

internal fun resolvedLogSeverity(logLevel: String?): Severity {
    val normalizedLogLevel = logLevel?.trim()
    return Severity.entries.find { severity ->
        severity.name.equals(normalizedLogLevel, ignoreCase = true)
    } ?: Severity.Info
}

object LoggerProvider {
    private val loggerConfig by lazy {
        loggerConfigInit(
            platformLogWriter(),
            minSeverity = resolvedLogSeverity(readLogLevelFromConfig())
        )
    }

    fun getLogger(tag: String = "App"): Logger = Logger(loggerConfig).withTag(tag)
}

