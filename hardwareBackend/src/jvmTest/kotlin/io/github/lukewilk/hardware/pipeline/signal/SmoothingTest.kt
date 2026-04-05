package io.github.lukewilk.hardware.pipeline.signal

import io.github.lukewilk.hardware.pipeline.signal.applyExponentialMovingAverage
import io.github.lukewilk.hardware.pipeline.signal.applyMedianFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class SmoothingTest {
    @Test
    fun testApplyExponentialMovingAverage_basic() {
        val input = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val result = applyExponentialMovingAverage(input, alpha = 0.5)
        // Should start with first value and smooth toward input
        assertEquals(input.size, result.size)
        assertEquals(1.0, result[0], 1e-9)
        assertTrue(result[1] > 1.0 && result[1] < 2.0)
        assertTrue(result[4] < 5.0)
    }

    @Test
    fun testApplyExponentialMovingAverage_invalid() {
        assertFailsWith<IllegalArgumentException> {
            applyExponentialMovingAverage(
                doubleArrayOf(),
                0.5
            )
        }
        assertFailsWith<IllegalArgumentException> {
            applyExponentialMovingAverage(
                doubleArrayOf(1.0),
                -0.1
            )
        }
        assertFailsWith<IllegalArgumentException> {
            applyExponentialMovingAverage(
                doubleArrayOf(1.0),
                1.1
            )
        }
    }

    @Test
    fun testApplyMedianFilter_basic() {
        val input = doubleArrayOf(1.0, 100.0, 2.0, 3.0, 4.0, 5.0)
        val result = applyMedianFilter(input, windowSize = 3)
        assertEquals(input.size, result.size)
        // The spike at index 1 should be smoothed out
        assertTrue(result[1] < 100.0)
        // Median at index 2 should be 3.0 (window: [100.0, 2.0, 3.0] -> sorted: [2.0, 3.0, 100.0])
        assertEquals(3.0, result[2], 1e-9)
    }

    @Test
    fun testApplyMedianFilter_invalid() {
        assertFailsWith<IllegalArgumentException> { applyMedianFilter(doubleArrayOf(), 3) }
        assertFailsWith<IllegalArgumentException> { applyMedianFilter(doubleArrayOf(1.0), 0) }
        assertFailsWith<IllegalArgumentException> { applyMedianFilter(doubleArrayOf(1.0), 2) }
    }

    @Test
    fun testApplyMedianFilter_windowEdges() {
        val input = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val result = applyMedianFilter(input, windowSize = 3)
        // At index 0, window is [1.0, 2.0], median is (1.0+2.0)/2 = 1.5
        assertEquals(1.5, result[0], 1e-9)
        // At index 1, window is [1.0, 2.0, 3.0], median is 2.0
        assertEquals(2.0, result[1], 1e-9)
        // At index 2, window is [2.0, 3.0, 4.0], median is 3.0
        assertEquals(3.0, result[2], 1e-9)
        // At index 3, window is [3.0, 4.0, 5.0], median is 4.0
        assertEquals(4.0, result[3], 1e-9)
        // At index 4, window is [4.0, 5.0], median is (4.0+5.0)/2 = 4.5
        assertEquals(4.5, result[4], 1e-9)
    }
}
