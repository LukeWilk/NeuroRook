package io.github.lukewilk.hardware.pipeline.signal

/**
 * Exponential moving average (EMA).
 * Useful for smoothing band power estimates in real-time feedback.
 */
class ExponentialMovingAverage(val alpha: Double = 0.3) {
    init {
        require(alpha in 0.0..1.0) { "Alpha must be in [0.0, 1.0]" }
    }

    private var lastValue: Double? = null

    /**
     * Update with a new value and return the smoothed EMA.
     *
     * @param newValue new input value
     * @return smoothed value
     */
    fun update(newValue: Double): Double {
        val result = if (lastValue == null) {
            newValue
        } else {
            alpha * newValue + (1.0 - alpha) * lastValue!!
        }
        lastValue = result
        return result
    }

    /**
     * Reset the EMA state.
     */
    fun reset() {
        lastValue = null
    }
}

/**
 * Apply exponential moving average smoothing to a signal.
 *
 * @param signal input signal
 * @param alpha smoothing factor [0..1]; higher = faster response, lower = smoother
 * @return smoothed signal
 */
fun applyExponentialMovingAverage(signal: DoubleArray, alpha: Double = 0.3): DoubleArray {
    require(signal.isNotEmpty()) { "Signal cannot be empty" }
    require(alpha in 0.0..1.0) { "Alpha must be in [0.0, 1.0]" }

    val ema = ExponentialMovingAverage(alpha)
    return DoubleArray(signal.size) { i -> ema.update(signal[i]) }
}

/**
 * Apply median filter smoothing to a signal.
 * Useful for removing outliers and noise spikes.
 *
 * @param signal input signal
 * @param windowSize size of median window (should be odd for symmetry)
 * @return median-filtered signal
 */
fun applyMedianFilter(signal: DoubleArray, windowSize: Int = 5): DoubleArray {
    require(signal.isNotEmpty()) { "Signal cannot be empty" }
    require(windowSize > 0 && windowSize % 2 == 1) { "Window size must be odd and > 0" }

    val half = windowSize / 2
    val result = DoubleArray(signal.size)

    for (i in signal.indices) {
        val window = mutableListOf<Double>()

        // Collect samples in window
        for (j in -half..half) {
            val idx = i + j
            if (idx in signal.indices) {
                window.add(signal[idx])
            }
        }

        // Sort and take median
        window.sort()
        result[i] = if (window.size % 2 == 1) {
            window[window.size / 2]
        } else {
            (window[window.size / 2 - 1] + window[window.size / 2]) / 2.0
        }
    }

    return result
}


