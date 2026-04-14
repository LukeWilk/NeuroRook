package io.github.lukewilk.hardware.pipeline.signal

import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Argument-validation tests for smoothing helpers in `Smoothing.kt`.
 */
class SmoothingValidationTest {
    /** Verifies EMA rejects alpha values outside the supported inclusive range. */
    @Test
    fun `exponential moving average rejects invalid alpha values`() {
        assertFailsWith<IllegalArgumentException> { ExponentialMovingAverage(-0.01) }
        assertFailsWith<IllegalArgumentException> { ExponentialMovingAverage(1.01) }
    }

    /** Verifies EMA application rejects empty input and invalid alpha values. */
    @Test
    fun `apply exponential moving average rejects invalid arguments`() {
        assertFailsWith<IllegalArgumentException> {
            applyExponentialMovingAverage(doubleArrayOf(), 0.5)
        }
        assertFailsWith<IllegalArgumentException> {
            applyExponentialMovingAverage(doubleArrayOf(1.0), -0.1)
        }
        assertFailsWith<IllegalArgumentException> {
            applyExponentialMovingAverage(doubleArrayOf(1.0), 1.1)
        }
    }

    /** Verifies median filtering rejects empty input and invalid window sizes. */
    @Test
    fun `apply median filter rejects invalid arguments`() {
        assertFailsWith<IllegalArgumentException> { applyMedianFilter(doubleArrayOf(), 3) }
        assertFailsWith<IllegalArgumentException> { applyMedianFilter(doubleArrayOf(1.0), 0) }
        assertFailsWith<IllegalArgumentException> { applyMedianFilter(doubleArrayOf(1.0), 2) }
    }
}

