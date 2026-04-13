package io.github.lukewilk.hardware.pipeline.signal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavior tests for smoothing helpers in `Smoothing.kt`.
 */
class SmoothingBehaviorTest {
    /** Verifies EMA reset plus alpha boundary values keep the expected running value semantics. */
    @Test
    fun `exponential moving average supports reset and alpha boundary values`() {
        val zeroAlpha = ExponentialMovingAverage(alpha = 0.0)
        assertEquals(5.0, zeroAlpha.update(5.0), 1e-9)
        assertEquals(5.0, zeroAlpha.update(10.0), 1e-9)
        zeroAlpha.reset()
        assertEquals(20.0, zeroAlpha.update(20.0), 1e-9)

        val oneAlpha = ExponentialMovingAverage(alpha = 1.0)
        assertEquals(2.0, oneAlpha.update(2.0), 1e-9)
        assertEquals(9.0, oneAlpha.update(9.0), 1e-9)
    }

    /** Verifies EMA smoothing preserves size, anchors on the first value, and smooths later values. */
    @Test
    fun `apply exponential moving average smooths the signal`() {
        val input = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0)

        val result = applyExponentialMovingAverage(input, alpha = 0.5)

        assertEquals(input.size, result.size)
        assertEquals(1.0, result[0], 1e-9)
        assertTrue(result[1] > 1.0 && result[1] < 2.0)
        assertTrue(result[4] < 5.0)
    }

    /** Verifies median filtering suppresses spikes while keeping the expected center median. */
    @Test
    fun `apply median filter smooths isolated spikes`() {
        val input = doubleArrayOf(1.0, 100.0, 2.0, 3.0, 4.0, 5.0)

        val result = applyMedianFilter(input, windowSize = 3)

        assertEquals(input.size, result.size)
        assertTrue(result[1] < 100.0)
        assertEquals(3.0, result[2], 1e-9)
    }

    /** Verifies median filtering handles edge windows with the expected partial-window medians. */
    @Test
    fun `apply median filter handles window edges`() {
        val input = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0)

        val result = applyMedianFilter(input, windowSize = 3)

        assertEquals(1.5, result[0], 1e-9)
        assertEquals(2.0, result[1], 1e-9)
        assertEquals(3.0, result[2], 1e-9)
        assertEquals(4.0, result[3], 1e-9)
        assertEquals(4.5, result[4], 1e-9)
    }

    /** Verifies the default overloads still produce sensible smoothing results. */
    @Test
    fun `default smoothing overloads produce expected output`() {
        val input = doubleArrayOf(1.0, 10.0, 2.0, 3.0, 4.0)

        val ema = applyExponentialMovingAverage(input)
        assertEquals(input.size, ema.size)
        assertEquals(1.0, ema.first(), 1e-9)

        val median = applyMedianFilter(input)
        assertEquals(input.size, median.size)
        assertTrue(median[1] < input[1], "Default median filter should smooth the spike")
    }

    /** Verifies a median window larger than the signal still produces finite values. */
    @Test
    fun `median filter with window larger than the signal still produces finite medians`() {
        val input = doubleArrayOf(9.0, 1.0, 5.0)

        val result = applyMedianFilter(input, windowSize = 5)

        assertEquals(input.size, result.size)
        assertTrue(result.all { it.isFinite() })
    }

    /** Verifies a median window of one leaves the input unchanged. */
    @Test
    fun `median filter with window size one returns the original signal`() {
        val input = doubleArrayOf(9.0, 1.0, 5.0)

        val result = applyMedianFilter(input, windowSize = 1)

        assertTrue(result.contentEquals(input), "A window size of one should leave the signal unchanged")
    }

    /** Verifies a single-sample signal remains unchanged even with a larger median window. */
    @Test
    fun `median filter single sample with large window keeps the only in-range value`() {
        val input = doubleArrayOf(42.0)

        val result = applyMedianFilter(input, windowSize = 5)

        assertEquals(1, result.size)
        assertEquals(42.0, result.single(), 1e-9)
    }
}

