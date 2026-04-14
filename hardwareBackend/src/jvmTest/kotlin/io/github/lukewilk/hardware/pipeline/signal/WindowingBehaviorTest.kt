package io.github.lukewilk.hardware.pipeline.signal

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Window-construction and application behavior tests for `Windowing.kt`.
 */
class WindowingBehaviorTest {
    /** Verifies each supported window type produces coefficients in the expected normalized range. */
    @Test
    fun `create window produces normalized coefficients for each window type`() {
        val length = 16

        for (type in WindowType.entries) {
            val window = createWindow(length, type)
            assertEquals(length, window.size, "window length should match requested length")
            if (type == WindowType.BLACKMAN) {
                assertTrue(window.all { it >= -0.2 && it <= 1.0 }, "All coefficients should be in [-0.2,1] for type=$type")
            } else {
                assertTrue(window.all { it >= 0.0 && it <= 1.0 }, "All coefficients should be in [0,1] for type=$type")
            }
        }
    }

    /** Verifies applying a window to an all-ones signal returns the window coefficients unchanged. */
    @Test
    fun `apply window on ones returns the window coefficients`() {
        val length = 32
        val ones = DoubleArray(length) { 1.0 }
        val window = createWindow(length, WindowType.HAMMING)

        val output = applyWindow(ones, window)

        for (index in 0 until length) {
            assertTrue(abs(output[index] - window[index]) < 1e-12, "Mismatch at $index: ${output[index]} vs ${window[index]}")
        }
    }

    /** Verifies coherent gain and power sum match their direct mathematical definitions. */
    @Test
    fun `window coherent gain and power sum match direct calculations`() {
        val window = createWindow(25, WindowType.HANN)

        val coherentGain = windowCoherentGain(window)
        val expectedCoherentGain = window.sum() / window.size
        assertTrue(abs(coherentGain - expectedCoherentGain) < 1e-12, "Coherent gain mismatch: $coherentGain vs $expectedCoherentGain")

        val powerSum = windowPowerSum(window)
        val expectedPowerSum = window.map { it * it }.sum()
        assertTrue(abs(powerSum - expectedPowerSum) < 1e-12, "Power sum mismatch: $powerSum vs $expectedPowerSum")
    }

    /** Verifies the window-type convenience overload matches explicit window creation and application. */
    @Test
    fun `apply window convenience overload matches explicit window application`() {
        val length = 12
        val signal = DoubleArray(length) { it.toDouble() }

        val convenienceOutput = applyWindow(signal, WindowType.BLACKMAN)
        val explicitWindow = createWindow(length, WindowType.BLACKMAN)
        val explicitOutput = applyWindow(signal, explicitWindow)

        for (index in 0 until length) {
            assertTrue(abs(convenienceOutput[index] - explicitOutput[index]) < 1e-12, "Convenience mismatch at $index")
        }
    }

    /** Verifies the default convenience overload matches explicit HAMMING window application. */
    @Test
    fun `apply window default convenience overload matches hamming application`() {
        val signal = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val expected = applyWindow(signal, createWindow(signal.size, WindowType.HAMMING))
        val actual = applyWindow(signal)

        actual.indices.forEach { index ->
            assertTrue(abs(actual[index] - expected[index]) < 1e-12, "Default convenience mismatch at $index")
        }
    }
}

