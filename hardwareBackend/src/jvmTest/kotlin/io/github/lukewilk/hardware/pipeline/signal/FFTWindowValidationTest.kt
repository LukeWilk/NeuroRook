package io.github.lukewilk.hardware.pipeline.signal
import kotlin.test.Test
import kotlin.test.assertFailsWith
/**
 * Argument-validation tests for `computeOptimalFFTWindow`.
 */
class FFTWindowValidationTest {
    /** Verifies an empty band list is rejected. */
    @Test
    fun `empty bands throw`() {
        assertFailsWith<IllegalArgumentException> {
            computeOptimalFFTWindow(
                samplingRateHz = 100.0,
                bandsHz = emptyList(),
                cycles = 4,
                minWindowMs = 50,
                maxWindowSec = 10.0,
                preferPowerOfTwo = true
            )
        }
    }
    /** Verifies a non-positive sampling rate is rejected. */
    @Test
    fun `invalid sampling rate throws`() {
        assertFailsWith<IllegalArgumentException> {
            computeOptimalFFTWindow(
                samplingRateHz = 0.0,
                bandsHz = listOf(8.0 to 12.0),
                cycles = 4,
                minWindowMs = 50,
                maxWindowSec = 10.0,
                preferPowerOfTwo = true
            )
        }
    }
    /** Verifies a non-positive lower band bound is rejected. */
    @Test
    fun `invalid lower band bound throws`() {
        assertFailsWith<IllegalArgumentException> {
            computeOptimalFFTWindow(
                samplingRateHz = 100.0,
                bandsHz = listOf(0.0 to 12.0)
            )
        }
    }
    /** Verifies preferred overlap values outside the supported range are rejected. */
    @Test
    fun `invalid preferred overlap values throw`() {
        assertFailsWith<IllegalArgumentException> {
            computeOptimalFFTWindow(
                samplingRateHz = 250.0,
                bandsHz = listOf(8.0 to 12.0),
                preferredOverlap = -0.1
            )
        }
        assertFailsWith<IllegalArgumentException> {
            computeOptimalFFTWindow(
                samplingRateHz = 250.0,
                bandsHz = listOf(8.0 to 12.0),
                preferredOverlap = 1.0
            )
        }
    }
}
