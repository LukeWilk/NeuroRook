package io.github.lukewilk.shared.model

/**
 * Describes a currently available serial device that can be proposed to the user.
 */
data class SerialPortSuggestion(
    val path: String,
    val displayName: String = path,
    val details: String = "",
    val isUsbDevice: Boolean = false,
    val isRecommended: Boolean = false
)

