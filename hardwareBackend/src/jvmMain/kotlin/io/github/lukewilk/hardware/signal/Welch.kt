package io.github.lukewilk.hardware.signal

import com.github.psambit9791.jdsp.transform.FastFourier
import kotlin.math.sqrt

/**
 * Configuration for Welch power spectral density (PSD) estimation.
 *
 * Welch's method divides a signal into overlapping segments, computes the FFT of each,
 * and averages the resulting power spectra for noise reduction.
 */
data class WelchConfig(
    val samplingRateHz: Double = 250.0,
    val windowType: WindowType = WindowType.HAMMING,
    val padToNextPowerOfTwo: Boolean = true
)

/**
 * Compute the power spectral density (PSD) of a windowed signal using FFT.
 *
 * Uses real FFT via JDSP library. Returns one-sided PSD with proper normalization.
 *
 * @param windowedSignal pre-windowed signal segment (real-valued)
 * @param config Welch configuration (sampling rate, window type, padding)
 * @return array of (frequency_hz, power_linear) pairs where power is in linear units
 */
fun computeWelchPSD(windowedSignal: DoubleArray, config: WelchConfig): Array<Pair<Double, Double>> {
    require(windowedSignal.isNotEmpty()) { "Signal cannot be empty" }

    // Pad to next power of two if requested
    val nfft = if (config.padToNextPowerOfTwo) {
        nextPowerOfTwo(windowedSignal.size)
    } else {
        windowedSignal.size
    }

    // Pad signal with zeros
    val paddedSignal = DoubleArray(nfft)
    for (i in windowedSignal.indices) {
        paddedSignal[i] = windowedSignal[i]
    }

    // Compute window normalization factors
    val window = createWindow(windowedSignal.size, config.windowType)
    val windowSum = windowPowerSum(window)  // Sum of window squared

    // Compute real FFT using JDSP FastFourier
    val fft = FastFourier(paddedSignal)
    fft.transform()  // Transforms in place
    val fftComplex = fft.getComplex(true)  // Get one-sided FFT result (onlyPositive=true)

    // Extract one-sided power spectrum
    val numFreqs = fftComplex.size
    val psd = DoubleArray(numFreqs)
    val frequencies = DoubleArray(numFreqs)

    // Frequency resolution
    val freqResolution = config.samplingRateHz / nfft

    // Compute one-sided PSD
    // For k=0 (DC): power = |X[0]|^2 / (windowSum * samplingRate)
    // For 0<k<nfft/2: power = 2 * |X[k]|^2 / (windowSum * samplingRate)  [multiply by 2 to account for negative freqs]
    // For k=nfft/2 (Nyquist, only if nfft is even): power = |X[nfft/2]|^2 / (windowSum * samplingRate)
    val norm = windowSum * config.samplingRateHz

    for (k in 0 until numFreqs) {
        frequencies[k] = k.toDouble() * freqResolution

        val real = fftComplex[k].real
        val imag = fftComplex[k].imaginary

        val magnitude = sqrt(real * real + imag * imag)
        val power = magnitude * magnitude

        // Apply one-sided conversion
        // Note: getComplex(true) already gives us one-sided spectrum, but we still need proper normalization
        psd[k] = when {
            k == 0 || k == nfft / 2 -> power / norm  // DC and Nyquist: no doubling
            else -> 2.0 * power / norm  // Other bins: double to account for negative frequencies
        }
    }

    return Array(numFreqs) { k ->
        val freqHz = frequencies[k]
        val powerLinear = psd[k].coerceAtLeast(1e-12)  // Clamp to avoid log of zero
        Pair(freqHz, powerLinear)
    }
}

/**
 * Estimate band power by integrating PSD over a frequency band.
 *
 * @param psd power spectral density (frequency_hz, power_linear pairs) where power is linear units
 * @param lowHz low frequency of band (Hz)
 * @param highHz high frequency of band (Hz)
 * @return integrated power in the band (linear units, same as PSD)
 */
fun bandPower(psd: Array<Pair<Double, Double>>, lowHz: Double, highHz: Double): Double {
    require(lowHz >= 0.0 && highHz > lowHz) { "Invalid frequency bounds: lowHz=$lowHz, highHz=$highHz" }
    require(psd.isNotEmpty()) { "PSD cannot be empty" }

    val inBand = psd.filter { (freq, _) -> freq in lowHz..highHz }
    if (inBand.isEmpty()) return 0.0

    // Trapezoidal integration over frequency
    var power = 0.0
    for (i in 0 until inBand.size - 1) {
        val (f1, p1) = inBand[i]
        val (f2, p2) = inBand[i + 1]
        val df = f2 - f1
        power += (p1 + p2) / 2.0 * df
    }
    return power
}

/**
 * Round up to the next power of two.
 */
private fun nextPowerOfTwo(n: Int): Int {
    if (n <= 1) return 1
    var power = 1
    while (power < n) {
        power *= 2
    }
    return power
}






