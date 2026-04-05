package io.github.lukewilk.hardware

import java.io.File

actual fun readLogLevelFromConfig(): String? {
    val configFile = File("NeuroRook.conf")
    if (!configFile.exists()) return null
    val lines = configFile.readLines()
    val logLevelLine = lines.firstOrNull { it.trim().startsWith("LOG_LEVEL=") }
    return logLevelLine?.substringAfter("LOG_LEVEL=")?.trim()
}
