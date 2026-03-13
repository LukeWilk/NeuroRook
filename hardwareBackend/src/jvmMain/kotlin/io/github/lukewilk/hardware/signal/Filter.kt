package io.github.lukewilk.hardware.signal

import com.github.psambit9791.jdsp.filter.Butterworth

/**
 * High-pass (detrend) filter configuration and application.
 *
 * A high-pass filter removes slow drifts and DC offset by attenuating low frequencies.
 * Standard choice: 0.5 Hz cutoff for EEG.
 */
data class HighPassConfig(
    val cutoffHz: Double = 0.5,      // Cutoff frequency in Hz
    val order: Int = 2,                // Filter order (higher = steeper rolloff)
    val samplingRateHz: Double = 250.0 // Sampling rate in Hz
)

/**
 * Apply a high-pass (detrend) filter to a signal.
 *
 * @param signal input signal (time-domain samples)
 * @param config high-pass filter configuration
 * @return filtered signal
 */
fun applyHighPassFilter(signal: DoubleArray, config: HighPassConfig): DoubleArray {
    require(signal.isNotEmpty()) { "Signal cannot be empty" }
    require(config.cutoffHz > 0.0) { "Cutoff frequency must be > 0" }
    require(config.cutoffHz < config.samplingRateHz / 2.0) { "Cutoff must be less than Nyquist frequency" }
    require(config.order > 0) { "Filter order must be > 0" }

    val nyquistFreq = config.samplingRateHz / 2.0
    val normalizedCutoff = config.cutoffHz / nyquistFreq

    val butterworth = Butterworth(config.samplingRateHz)
    butterworth.highPassFilter(signal, config.order, normalizedCutoff)
    return signal
}

/**
 * Notch (bandstop) filter configuration.
 *
 * A notch filter removes narrow-band noise (e.g., 50/60 Hz power line).
 */
data class NotchFilterConfig(
    val centerHz: Double = 60.0,        // Center frequency (e.g., 60 Hz for US power line)
    val bandwidthHz: Double = 2.0,      // Bandwidth around center (±1 Hz typical)
    val order: Int = 2,                 // Filter order
    val samplingRateHz: Double = 250.0  // Sampling rate in Hz
)

/**
 * Apply a notch (bandstop) filter to a signal using JDSP Butterworth bandStopFilter.
 *
 * This implementation delegates to the JDSP Butterworth filter which returns a new
 * filtered array. We compute normalized low/high cutoffs from the requested center
 * and bandwidth and pass them to the library.
 */
fun applyNotchFilter(signal: DoubleArray, config: NotchFilterConfig): DoubleArray {
    require(signal.isNotEmpty()) { "Signal cannot be empty" }
    require(config.centerHz > 0.0) { "Center frequency must be > 0" }
    require(config.bandwidthHz > 0.0) { "Bandwidth must be > 0" }
    require(config.centerHz < config.samplingRateHz / 2.0) { "Center must be less than Nyquist" }
    require(config.order > 0) { "Filter order must be > 0" }

    val nyquistFreq = config.samplingRateHz / 2.0
    val lowCut = (config.centerHz - config.bandwidthHz / 2.0).coerceAtLeast(0.1)
    val highCut = (config.centerHz + config.bandwidthHz / 2.0).coerceAtMost(nyquistFreq - 0.1)
    val normalizedLow = lowCut / nyquistFreq
    val normalizedHigh = highCut / nyquistFreq

    val butterworth = Butterworth(config.samplingRateHz)
    // JDSP's bandStopFilter returns a new filtered array
    return butterworth.bandStopFilter(signal, config.order, normalizedLow, normalizedHigh)
}

/**
 * Bandpass filter configuration.
 *
 * A bandpass filter isolates a frequency of interest (e.g., alpha 8–12 Hz).
 */
data class BandpassFilterConfig(
    val lowCutHz: Double = 8.0,         // Low cutoff in Hz
    val highCutHz: Double = 12.0,       // High cutoff in Hz
    val order: Int = 2,                 // Filter order
    val samplingRateHz: Double = 250.0  // Sampling rate in Hz
)

/**
 * Apply a bandpass filter to a signal.
 *
 * @param signal input signal
 * @param config bandpass filter configuration
 * @return filtered signal containing only frequencies in [lowCutHz, highCutHz]
 */
fun applyBandpassFilter(signal: DoubleArray, config: BandpassFilterConfig): DoubleArray {
    require(signal.isNotEmpty()) { "Signal cannot be empty" }
    require(config.lowCutHz > 0.0) { "Low cutoff must be > 0" }
    require(config.highCutHz > config.lowCutHz) { "High cutoff must be > low cutoff" }
    require(config.highCutHz < config.samplingRateHz / 2.0) { "High cutoff must be less than Nyquist" }
    require(config.order > 0) { "Filter order must be > 0" }

    val nyquistFreq = config.samplingRateHz / 2.0
    val normalizedLow = config.lowCutHz / nyquistFreq
    val normalizedHigh = config.highCutHz / nyquistFreq

    val butterworth = Butterworth(config.samplingRateHz)
    butterworth.bandPassFilter(signal, config.order, normalizedLow, normalizedHigh)
    return signal
}
