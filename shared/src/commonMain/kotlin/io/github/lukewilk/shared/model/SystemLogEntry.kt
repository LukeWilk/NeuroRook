package io.github.lukewilk.shared.model

enum class SystemLogSeverity {
    INFO,
    WARN,
    ERROR
}

data class SystemLogEntry(
    val timestampEpochMillis: Long,
    val severity: SystemLogSeverity,
    val message: String
)

