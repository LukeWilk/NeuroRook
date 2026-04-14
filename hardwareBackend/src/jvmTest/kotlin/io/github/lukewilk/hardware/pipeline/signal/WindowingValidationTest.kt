package io.github.lukewilk.hardware.pipeline.signal
import kotlin.test.Test
import kotlin.test.assertFailsWith
/**
 * Argument-validation tests for `Windowing.kt`.
 */
class WindowingValidationTest {
    /** Verifies creating a window with a non-positive length is rejected. */
    @Test
    fun `create window rejects zero length`() {
        assertFailsWith<IllegalArgumentException> { createWindow(0) }
    }
    /** Verifies applying a window rejects mismatched signal and window lengths. */
    @Test
    fun `apply window rejects mismatched lengths`() {
        val signal = DoubleArray(8) { 1.0 }
        val window = DoubleArray(4) { 1.0 }
        assertFailsWith<IllegalArgumentException> { applyWindow(signal, window) }
    }
    /** Verifies applying a window rejects empty signal or window inputs. */
    @Test
    fun `apply window rejects empty inputs`() {
        assertFailsWith<IllegalArgumentException> { applyWindow(doubleArrayOf(), doubleArrayOf(1.0)) }
        assertFailsWith<IllegalArgumentException> { applyWindow(doubleArrayOf(1.0), doubleArrayOf()) }
    }
    /** Verifies coherent gain and power sum reject empty windows. */
    @Test
    fun `window coherent gain and power sum reject empty windows`() {
        assertFailsWith<IllegalArgumentException> { windowCoherentGain(doubleArrayOf()) }
        assertFailsWith<IllegalArgumentException> { windowPowerSum(doubleArrayOf()) }
    }
}
