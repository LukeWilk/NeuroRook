package io.github.lukewilk.hardware.pipeline.signal
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
/**
 * Window-size and overlap-selection tests for `computeOptimalFFTWindow`.
 */
class FFTWindowSizingTest {
    /** Verifies a simple single-band request chooses the expected power-of-two window and overlap. */
    @Test
    fun `single band sizing chooses the expected power of two window`() {
        val cfg = computeOptimalFFTWindow(
            samplingRateHz = 1000.0,
            bandsHz = listOf(8.0 to 12.0),
            cycles = 4,
            minWindowMs = 50,
            maxWindowSec = 10.0,
            preferPowerOfTwo = true
        )
        assertEquals(512, cfg.windowSamples)
        assertTrue(abs(cfg.overlap - 0.875) < 1e-9)
    }
    /** Verifies low sampling rates still produce a valid clamped configuration. */
    @Test
    fun `low sampling rate clamps to the minimum window duration`() {
        val cfg = computeOptimalFFTWindow(
            samplingRateHz = 10.0,
            bandsHz = listOf(1.0 to 3.0),
            cycles = 4,
            minWindowMs = 100,
            maxWindowSec = 10.0,
            preferPowerOfTwo = true
        )
        assertTrue(cfg.windowSamples >= 1)
        assertTrue(cfg.overlap in 0.0..1.0)
    }
    /** Verifies very low lower-band bounds clamp the requested duration to the configured maximum. */
    @Test
    fun `very low lower band clamps to the maximum window duration`() {
        val cfg = computeOptimalFFTWindow(
            samplingRateHz = 100.0,
            bandsHz = listOf(0.05 to 0.2),
            cycles = 4,
            minWindowMs = 50,
            maxWindowSec = 2.0,
            preferPowerOfTwo = true
        )
        assertEquals(256, cfg.windowSamples)
        assertTrue(cfg.overlap in 0.0..1.0)
    }
    /** Verifies preferred overlap is rounded to the nearest achievable integer-hop overlap. */
    @Test
    fun `preferred overlap close to achievable produces the expected rounded overlap`() {
        val cfg = computeOptimalFFTWindow(
            samplingRateHz = 500.0,
            bandsHz = listOf(8.0 to 12.0),
            cycles = 4,
            preferPowerOfTwo = true,
            preferredOverlap = 0.9
        )
        assertEquals(256, cfg.windowSamples)
        val expectedOverlap = 1.0 - 25.0 / 256.0
        assertTrue(abs(cfg.overlap - expectedOverlap) < 1e-6)
    }
    /** Verifies preferred overlap boundary values map to the expected no-overlap and near-total-overlap cases. */
    @Test
    fun `preferred overlap boundary values produce the expected overlaps`() {
        val cfg0 = computeOptimalFFTWindow(
            samplingRateHz = 200.0,
            bandsHz = listOf(4.0 to 8.0),
            cycles = 4,
            preferPowerOfTwo = false,
            preferredOverlap = 0.0
        )
        assertEquals(200, cfg0.windowSamples)
        assertEquals(0.0, cfg0.overlap)
        val cfgHigh = computeOptimalFFTWindow(
            samplingRateHz = 100.0,
            bandsHz = listOf(8.0 to 12.0),
            cycles = 4,
            preferPowerOfTwo = false,
            preferredOverlap = 0.999
        )
        assertEquals(50, cfgHigh.windowSamples)
        assertEquals((50 - 1).toDouble() / 50.0, cfgHigh.overlap)
    }
    /** Verifies extremely large requests clamp to the maximum integer sample count safely. */
    @Test
    fun `extremely large window request clamps to int max value`() {
        val cfg = computeOptimalFFTWindow(
            samplingRateHz = 2_000_000_000.0,
            bandsHz = listOf(1.0 to 2.0),
            cycles = 6,
            minWindowMs = 250,
            maxWindowSec = 5.0,
            preferPowerOfTwo = true
        )
        assertEquals(Int.MAX_VALUE, cfg.windowSamples)
        assertTrue(cfg.overlap in 0.0..1.0)
    }
    /** Verifies non-finite desired durations fall back to the configured maximum window seconds. */
    @Test
    fun `non finite desired duration falls back to max window seconds`() {
        val cfg = computeOptimalFFTWindow(
            samplingRateHz = 100.0,
            bandsHz = listOf(Double.MIN_VALUE to 1.0),
            cycles = 1,
            minWindowMs = 50,
            maxWindowSec = 2.0,
            preferPowerOfTwo = false
        )
        assertEquals(200, cfg.windowSamples)
        assertEquals(0.875, cfg.overlap, 1e-9)
    }
    /** Verifies the smallest lower bound is selected even when the band list is unsorted. */
    @Test
    fun `lowest lower bound is selected from unsorted bands`() {
        val cfg = computeOptimalFFTWindow(
            samplingRateHz = 100.0,
            bandsHz = listOf(8.0 to 12.0, 2.0 to 4.0, 4.0 to 7.0),
            cycles = 2,
            minWindowMs = 50,
            maxWindowSec = 5.0,
            preferPowerOfTwo = false
        )
        assertEquals(100, cfg.windowSamples)
        assertEquals(0.88, cfg.overlap, 1e-9)
    }
}
