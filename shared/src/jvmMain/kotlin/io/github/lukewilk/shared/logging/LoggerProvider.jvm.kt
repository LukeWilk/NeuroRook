package io.github.lukewilk.shared.logging

import java.io.File

internal fun readLogLevelFromConfigFile(
    configFile: File,
    envLogLevel: String? = System.getenv("LOG_LEVEL")
): String? {
    val env = envLogLevel?.takeIf { it.isNotBlank() }
    if (env != null) return env

    if (!configFile.exists()) return null

    val logLevelLine = configFile.readLines()
        .firstOrNull { it.trim().startsWith("LOG_LEVEL=") }

    return parsedConfigLogLevel(logLevelLine)
}

internal fun parsedConfigLogLevel(logLevelLine: String?): String? {
    if (logLevelLine == null) return null

    val normalizedLine = logLevelLine.trim()
    if (!normalizedLine.startsWith("LOG_LEVEL=")) return null

    val trimmedValue = normalizedLine.removePrefix("LOG_LEVEL=").trim()
    if (trimmedValue.isBlank()) return null
    return trimmedValue
}

actual fun readLogLevelFromConfig(): String? = readLogLevelFromConfigFile(File("NeuroRook.conf"))

