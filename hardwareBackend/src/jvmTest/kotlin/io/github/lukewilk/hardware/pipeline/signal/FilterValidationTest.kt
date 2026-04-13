package io.github.lukewilk.hardware.pipeline.signal

import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Argument-validation tests for the filter helpers in `Filter.kt`.
 */
class FilterValidationTest {
    /** Verifies high-pass filtering rejects empty input, invalid cutoff values, and invalid order values. */
    @Test
    fun `apply high pass filter rejects invalid arguments`() {
        val signal = DoubleArray(100) { 1.0 }

        assertFailsWith<IllegalArgumentException> {
            applyHighPassFilter(DoubleArray(0), HighPassConfig())
        }
        assertFailsWith<IllegalArgumentException> {
            applyHighPassFilter(signal, HighPassConfig(cutoffHz = 0.0))
        }
        assertFailsWith<IllegalArgumentException> {
            applyHighPassFilter(signal, HighPassConfig(cutoffHz = 200.0, samplingRateHz = 100.0))
        }
        assertFailsWith<IllegalArgumentException> {
            applyHighPassFilter(signal, HighPassConfig(order = 0))
        }
    }

    /** Verifies notch filtering rejects empty input, invalid center/bandwidth values, and invalid order values. */
    @Test
    fun `apply notch filter rejects invalid arguments`() {
        val signal = DoubleArray(100) { 1.0 }

        assertFailsWith<IllegalArgumentException> {
            applyNotchFilter(DoubleArray(0), NotchFilterConfig())
        }
        assertFailsWith<IllegalArgumentException> {
            applyNotchFilter(signal, NotchFilterConfig(centerHz = 0.0))
        }
        assertFailsWith<IllegalArgumentException> {
            applyNotchFilter(signal, NotchFilterConfig(bandwidthHz = 0.0))
        }
        assertFailsWith<IllegalArgumentException> {
            applyNotchFilter(signal, NotchFilterConfig(centerHz = 200.0, samplingRateHz = 100.0))
        }
        assertFailsWith<IllegalArgumentException> {
            applyNotchFilter(signal, NotchFilterConfig(order = 0))
        }
    }

    /** Verifies bandpass filtering rejects empty input, invalid cutoffs, and invalid order values. */
    @Test
    fun `apply bandpass filter rejects invalid arguments`() {
        val signal = DoubleArray(100) { 1.0 }

        assertFailsWith<IllegalArgumentException> {
            applyBandpassFilter(DoubleArray(0), BandpassFilterConfig())
        }
        assertFailsWith<IllegalArgumentException> {
            applyBandpassFilter(signal, BandpassFilterConfig(lowCutHz = 0.0))
        }
        assertFailsWith<IllegalArgumentException> {
            applyBandpassFilter(signal, BandpassFilterConfig(lowCutHz = 10.0, highCutHz = 5.0))
        }
        assertFailsWith<IllegalArgumentException> {
            applyBandpassFilter(signal, BandpassFilterConfig(highCutHz = 200.0, samplingRateHz = 100.0))
        }
        assertFailsWith<IllegalArgumentException> {
            applyBandpassFilter(signal, BandpassFilterConfig(order = 0))
        }
    }
}

