package io.github.lukewilk.hardware.pipeline.signal

import kotlin.math.ceil
import kotlin.math.log2

/**
 * Configuration for an FFT window: number of samples in the window and the overlap fraction (0.0..1.0).
 */
class FFTWindowConfig(val windowSamples: Int, val overlap: Double)

/**
 * Compute an optimal FFT window size (in samples) and overlap fraction for band power estimation.
 *
 * Strategy:
 * - Determine the lowest frequency among the provided bands and target `cycles` of that frequency.
 * - Clamp the duration to the provided [minWindowMs] and [maxWindowSec] bounds.
 * - Convert duration to samples using [samplingRateHz].
 * - Optionally round up to the next power of two when [preferPowerOfTwo] is true.
 * - Choose a hop size equal to 1/8 of the window (87.5% overlap) to prioritize smooth feedback,
 *   unless [preferredOverlap] is provided in which case we attempt to honor it while respecting
 *   integer hop sizes.
 *
 * Parameters:
 * @param samplingRateHz sampling rate of the signal in Hz (must be > 0).
 * @param bandsHz list of frequency bands as pairs (lowHz to highHz). Lower bounds must be > 0.
 * @param cycles number of cycles of the lowest band to capture (default 6).
 * @param minWindowMs minimum window length in milliseconds (default 250 ms).
 * @param maxWindowSec maximum window length in seconds (default 5.0 s).
 * @param preferPowerOfTwo if true, round window length up to the next power of two (default true).
 * @param preferredOverlap optional requested overlap fraction (0.0 .. <1.0). If provided the
 * function will choose the closest achievable overlap given integer hop sizes; if null, a default
 * 87.5% overlap is used.
 *
 * @return FFTWindowConfig containing the chosen window size in samples and overlap fraction.
 */
fun computeOptimalFFTWindow(
    samplingRateHz: Double,
    bandsHz: List<Pair<Double, Double>>, // list of (lowHz to highHz)
    cycles: Int = 6,
    minWindowMs: Long = 250,
    maxWindowSec: Double = 5.0,
    preferPowerOfTwo: Boolean = true,
    preferredOverlap: Double? = null
): FFTWindowConfig {
    require(samplingRateHz > 0.0) { "samplingRateHz must be > 0" }
    require(bandsHz.isNotEmpty()) { "bandsHz must not be empty" }

    val lows = bandsHz.map { it.first }
    require(lows.all { it > 0.0 }) { "band lower bounds must be > 0" }

    var fLow = lows.first()
    for (index in 1 until lows.size) {
        val candidate = lows[index]
        if (candidate < fLow) {
            fLow = candidate
        }
    }

    // desired duration in seconds to capture `cycles` of fLow
    val minWindowSec = minWindowMs.toDouble() / 1000.0
    var desiredSec = cycles.toDouble() / fLow
    if (!desiredSec.isFinite()) desiredSec = maxWindowSec
    val durSec = desiredSec.coerceIn(minWindowSec, maxWindowSec)

    val samplesD = durSec * samplingRateHz
    var samples = samplesD.toInt().coerceAtLeast(1)

    if (preferPowerOfTwo) samples = nextPowerOfTwo(samples)

    // If a preferred overlap was provided, validate and compute the closest achievable overlap
    // given integer hop sizes. Otherwise use default hop = samples/8 (approx 87.5% overlap).
    val overlap = if (preferredOverlap != null) {
        require(preferredOverlap >= 0.0 && preferredOverlap < 1.0) { "preferredOverlap must be in [0.0, 1.0)" }
        // compute hop (integer) from requested overlap, ensure at least 1 sample hop
        val requestedHop = (samples * (1.0 - preferredOverlap)).toInt().coerceAtLeast(1)
        1.0 - requestedHop.toDouble() / samples.toDouble()
    } else {
        val hop = (samples / 8).coerceAtLeast(1)
        1.0 - hop.toDouble() / samples.toDouble()
    }

    return FFTWindowConfig(windowSamples = samples, overlap = overlap)
}

/**
 * Returns the smallest power of two greater than or equal to [n].
 * For n <= 1, returns 1.
 */
private fun nextPowerOfTwo(n: Int): Int {
    if (n <= 1) return 1
    val exponent = ceil(log2(n.toDouble())).toInt()
    val result = 1 shl exponent
    return if (result <= 0) Int.MAX_VALUE else result
}
