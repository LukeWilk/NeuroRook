package io.github.lukewilk.hardware.pipeline.signal

import io.github.lukewilk.hardware.pipeline.signal.computeOptimalFFTWindow
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class FFTWindowTest {
    @Test
    fun `happy path single band`() {
        val cfg = computeOptimalFFTWindow(
            samplingRateHz = 1000.0,
            bandsHz = listOf(8.0 to 12.0),
            cycles = 4,
            minWindowMs = 50,
            maxWindowSec = 10.0,
            preferPowerOfTwo = true
        )
        // expect window captures 4 cycles of 8Hz => duration = 0.5s => 500 samples => next power of two = 512
        assertEquals(512, cfg.windowSamples)
        // hop = 512/8 = 64 => overlap = 1 - 64/512 = 0.875
        assertTrue(abs(cfg.overlap - 0.875) < 1e-9)
    }

    @Test
    fun `low sampling rate clamps to minWindowMs`() {
        val cfg = computeOptimalFFTWindow(
            samplingRateHz = 10.0,
            bandsHz = listOf(1.0 to 3.0),
            cycles = 4,
            minWindowMs = 100, // min 100 ms
            maxWindowSec = 10.0,
            preferPowerOfTwo = true
        )
        // desired duration = 4 / 1 = 4s -> but samplingRate low; ensure samples >= 1 and power of two
        assertTrue(cfg.windowSamples >= 1)
        assertTrue(cfg.overlap in 0.0..1.0)
    }

    @Test
    fun `very low fLow clamps to maxWindowSec`() {
        val cfg = computeOptimalFFTWindow(
            samplingRateHz = 100.0,
            bandsHz = listOf(0.05 to 0.2),
            cycles = 4,
            minWindowMs = 50,
            maxWindowSec = 2.0,
            preferPowerOfTwo = true
        )
        // desired = 4 / 0.05 = 80s -> clamped to 2s => samples = 200 -> next power of two = 256
        assertEquals(256, cfg.windowSamples)
        assertTrue(cfg.overlap in 0.0..1.0)
    }

    @Test
    fun `empty bands throws`() {
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

    @Test
    fun `preferred overlap close to achievable`() {
        val cfg = computeOptimalFFTWindow(
            samplingRateHz = 500.0,
            bandsHz = listOf(8.0 to 12.0),
            cycles = 4,
            preferPowerOfTwo = true,
            preferredOverlap = 0.9
        )
        // Expect a window capturing 4 cycles of 8Hz -> 0.5s -> 250 samples -> next pow2 = 256
        assertEquals(256, cfg.windowSamples)
        // requested overlap 0.9 => hop = 256 * 0.1 = 25.6 -> int hop = 25 -> overlap = 1 - 25/256
        val expectedOverlap = 1.0 - 25.0 / 256.0
        assertTrue(abs(cfg.overlap - expectedOverlap) < 1e-6)
    }

    @Test
    fun `preferred overlap boundary values`() {
        // preferredOverlap 0.0 (no overlap) should produce hop == windowSize
        val cfg0 = computeOptimalFFTWindow(
            samplingRateHz = 200.0,
            bandsHz = listOf(4.0 to 8.0),
            cycles = 4,
            preferPowerOfTwo = false,
            preferredOverlap = 0.0
        )
        // duration = 4 cycles / 4Hz = 1s -> 200 samples
        assertEquals(200, cfg0.windowSamples)
        assertEquals(0.0, cfg0.overlap) // hop == windowSize -> overlap 0

        // preferredOverlap extremely close to 1.0 should be capped by integer hop to at most (windowSize-1)/windowSize
        val cfgHigh = computeOptimalFFTWindow(
            samplingRateHz = 100.0,
            bandsHz = listOf(8.0 to 12.0),
            cycles = 4,
            preferPowerOfTwo = false,
            preferredOverlap = 0.999
        )
        // window: 4 cycles of 8Hz => 0.5s => 50 samples
        assertEquals(50, cfgHigh.windowSamples)
        // maximum overlap achievable is (windowSize-1)/windowSize
        assertEquals((50 - 1).toDouble() / 50.0, cfgHigh.overlap)
    }
}
