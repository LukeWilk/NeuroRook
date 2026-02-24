package io.github.lukewilk.shared

/**
 * Represents the state of the hardware connection.
 */
data class HardwareState(
    val connected: Boolean = false,
    val synthetic: Boolean = true, // true if connected to synthetic board, false for hardware
    val samplingRateHz: Int = 0 // sampling rate in Hz
)
