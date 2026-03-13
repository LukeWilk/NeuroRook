package io.github.lukewilk.shared

/**
 * Represents the state of the hardware connection.
 * Holds config state
 */
enum class SyntheticMode { SYNTHETIC_EEG_SIGNAL, WAVE_GENERATOR }

data class WaveSpec(
    val enabled: Boolean = false,
    val type: WaveType = WaveType.SINE,
    val amplitude: Double = 1.0,
    val frequencyHz: Double = 1.0,
    val phaseShiftRad: Double = 0.0 // phase shift in radians
)

enum class WaveType { SINE, SQUARE, SAWTOOTH, TRIANGLE, NOISE }

/**
 * User-defined frequency band for PSD/banding calculations.
 * Up to 10 bands are supported by convention; two or more bands may overlap.
 */
data class Band(
    val name: String,
    val lowHz: Double,
    val highHz: Double
)

private fun defaultBands(): List<Band> = listOf(
    Band("Gamma", 30.0, 100.0),
    Band("High Beta", 20.0, 30.0),
    Band("Low Beta (SMR)", 12.0, 16.0),  // Widened from 13.0-15.0 to better capture 14 Hz with FFT bins
    Band("Alpha", 8.0, 12.0),
    Band("Theta", 4.0, 8.0),
    Band("Delta", 0.5, 4.0)
)

data class HardwareState(
    val connected: Boolean = false,
    val synthetic: Boolean = true, // true if connected to synthetic board, false for hardware
    val samplingRateHz: Int = 0, // sampling rate in Hz
    val channels: Int = 0, // number of channels
    val enabledChannels: List<Int> = emptyList(), // enabled/disabled per channel
    val rldEnabled: List<Int> = emptyList(), // RLD enabled/disabled per channel
    val filterConfig: FilterConfig = FilterConfig(null, emptyList()), // filter config for acquisition

    // New fields for synthetic waveform generator
    val syntheticMode: SyntheticMode = SyntheticMode.SYNTHETIC_EEG_SIGNAL,
    val waveSpecs: List<WaveSpec> = List(5) { WaveSpec() },

    // User-defined PSD bands (up to 10). By default include common EEG bands.
    val bands: List<Band> = defaultBands(),

    // Buffering configuration for streaming analysis
    val windowSize: Int = 256,
    val overlap: Int = 128,

    // Optional: request a preferred overlap fraction for FFT windowing (0.0 .. <1.0).
    // When set, pipeline components may compute an achievable overlap close to this value.
    val preferredOverlap: Double? = null
)
