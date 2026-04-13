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

    return logLevelLine?.substringAfter("LOG_LEVEL=")?.trim()?.takeIf { it.isNotBlank() }
}

actual fun readLogLevelFromConfig(): String? = readLogLevelFromConfigFile(File("NeuroRook.conf"))

