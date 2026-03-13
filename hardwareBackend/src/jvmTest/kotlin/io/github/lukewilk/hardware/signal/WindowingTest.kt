package io.github.lukewilk.hardware.signal

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WindowingTest {
    /** Window length must be > 0. */
    @Test
    fun testCreateWindowLengthZeroThrows() {
        assertFailsWith<IllegalArgumentException> { createWindow(0) }
    }

    /** Window coefficients are produced and lie in sensible ranges. */
    @Test
    fun testCreateWindowsAreNormalized() {
        val length = 16
        for (type in WindowType.values()) {
            val w = createWindow(length, type)
            assertEquals(length, w.size, "window length should match requested length")
            if (type == WindowType.BLACKMAN) {
                // Blackman window may have small negative sidelobes; allow a safe lower bound
                assertTrue(w.all { it >= -0.2 && it <= 1.0 }, "All coefficients should be in [-0.2,1] for type=$type")
            } else {
                assertTrue(w.all { it >= 0.0 && it <= 1.0 }, "All coefficients should be in [0,1] for type=$type")
            }
        }
    }

    /** Applying a window of ones should return the same coefficients when signal is ones. */
    @Test
    fun testApplyWindowOnOnesReturnsWindowCoefficients() {
        val length = 32
        val ones = DoubleArray(length) { 1.0 }
        val w = createWindow(length, WindowType.HAMMING)
        val out = applyWindow(ones, w)
        for (i in 0 until length) {
            assertTrue(abs(out[i] - w[i]) < 1e-12, "Mismatch at $i: ${out[i]} vs ${w[i]}")
        }
    }

    /** Mismatched lengths between signal and window should throw. */
    @Test
    fun testApplyWindowMismatchedLengthThrows() {
        val signal = DoubleArray(8) { 1.0 }
        val window = DoubleArray(4) { 1.0 }
        assertFailsWith<IllegalArgumentException> { applyWindow(signal, window) }
    }

    /** Coherent gain equals sum(window)/length and power sum equals sum(window^2). */
    @Test
    fun testWindowCoherentGainAndPowerSum() {
        val w = createWindow(25, WindowType.HANN)
        val cg = windowCoherentGain(w)
        val expectedCg = w.sum() / w.size
        assertTrue(abs(cg - expectedCg) < 1e-12, "Coherent gain mismatch: $cg vs $expectedCg")
        val ps = windowPowerSum(w)
        val expectedPs = w.map { it * it }.sum()
        assertTrue(abs(ps - expectedPs) < 1e-12, "Power sum mismatch: $ps vs $expectedPs")
    }

    /** Convenience: applyWindow(signal, windowType) should match applying explicit window. */
    @Test
    fun testApplyWindowConvenienceMatchesExplicit() {
        val length = 12
        val signal = DoubleArray(length) { it.toDouble() }
        val outConvenience = applyWindow(signal, WindowType.BLACKMAN)
        val explicit = createWindow(length, WindowType.BLACKMAN)
        val outExplicit = applyWindow(signal, explicit)
        for (i in 0 until length) {
            assertTrue(abs(outConvenience[i] - outExplicit[i]) < 1e-12, "Convenience mismatch at $i")
        }
    }
}

