package io.github.lukewilk.hardware.pipeline.signal

import kotlin.math.PI
import kotlin.math.cos

/**
 * Window function types for spectral analysis.
 */
enum class WindowType {
    HAMMING,  // Hamming window (minimizes sidelobe leakage)
    HANN,     // Hann (Hanning) window (smooth transition)
    BLACKMAN  // Blackman window (reduced sidelobe levels)
}

/**
 * Create a window function of specified type and length.
 *
 * @param length number of samples in the window
 * @param type window type (HAMMING, HANN, BLACKMAN)
 * @return array of window coefficients [0..1]
 */
fun createWindow(length: Int, type: WindowType = WindowType.HAMMING): DoubleArray {
    require(length > 0) { "Window length must be > 0" }

    val window = DoubleArray(length)
    when (type) {
        WindowType.HAMMING -> {
            for (n in 0 until length) {
                window[n] = 0.54 - 0.46 * cos(2.0 * PI * n.toDouble() / (length - 1))
            }
        }
        WindowType.HANN -> {
            for (n in 0 until length) {
                window[n] = 0.5 * (1.0 - cos(2.0 * PI * n.toDouble() / (length - 1)))
            }
        }
        WindowType.BLACKMAN -> {
            for (n in 0 until length) {
                val x = 2.0 * PI * n.toDouble() / (length - 1)
                window[n] = 0.42 - 0.5 * cos(x) + 0.08 * cos(2.0 * x)
            }
        }
    }
    return window
}

/**
 * Apply a window function to a signal.
 *
 * @param signal input signal
 * @param window window coefficients (length must match or signal will be truncated/padded)
 * @return windowed signal
 */
fun applyWindow(signal: DoubleArray, window: DoubleArray): DoubleArray {
    require(signal.isNotEmpty()) { "Signal cannot be empty" }
    require(window.isNotEmpty()) { "Window cannot be empty" }
    require(window.size == signal.size) { "Window and signal must have same length" }

    return DoubleArray(signal.size) { i -> signal[i] * window[i] }
}

/**
 * Apply a window function to a signal (convenience method).
 *
 * @param signal input signal
 * @param windowType window type to apply
 * @return windowed signal
 */
fun applyWindow(signal: DoubleArray, windowType: WindowType = WindowType.HAMMING): DoubleArray {
    val window = createWindow(signal.size, windowType)
    return applyWindow(signal, window)
}

/**
 * Compute coherent gain of a window (normalization factor for power).
 *
 * @param window window coefficients
 * @return coherent gain (sum of window / window length)
 */
fun windowCoherentGain(window: DoubleArray): Double {
    require(window.isNotEmpty()) { "Window cannot be empty" }
    return window.sum() / window.size
}

/**
 * Compute the sum of window squared (for power normalization in Welch's method).
 *
 * @param window window coefficients
 * @return sum of squared window values
 */
fun windowPowerSum(window: DoubleArray): Double {
    require(window.isNotEmpty()) { "Window cannot be empty" }
    return window.map { it * it }.sum()
}