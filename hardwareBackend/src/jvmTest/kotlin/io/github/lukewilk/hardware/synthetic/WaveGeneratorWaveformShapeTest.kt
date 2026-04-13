package io.github.lukewilk.hardware.synthetic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
/**
 * Waveform-shape and superposition tests for `WaveGenerator`.
 */
internal class WaveGeneratorWaveformShapeTest : WaveGeneratorTestSupport() {
    /** Verifies a basic sine-wave spec produces a non-zero signal with the requested sample count. */
    @Test
    fun `generate sample array produces a sine wave`() {
        val spec = WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 2.0, frequencyHz = 5.0, phaseShiftRad = 0.0)
        val samples = WaveGenerator.generateSampleArray(listOf(spec), 100, 100)
        assertEquals(100, samples.size)
        assertTrue(samples.any { it != 0.0 })
    }
    /** Verifies multiple enabled specs superpose into one non-zero combined signal. */
    @Test
    fun `generate sample array superposes enabled waveforms`() {
        val sine = WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 5.0, phaseShiftRad = 0.0)
        val square = WaveSpec(enabled = true, type = WaveType.SQUARE, amplitude = 0.5, frequencyHz = 10.0, phaseShiftRad = 0.0)
        val samples = WaveGenerator.generateSampleArray(listOf(sine, square), 100, 100)
        assertEquals(100, samples.size)
        assertTrue(samples.any { it != 0.0 })
    }
    /** Verifies a one-hertz sawtooth sampled four times per period matches the expected pattern exactly. */
    @Test
    fun `generate sample array produces the expected sawtooth pattern`() {
        val spec = WaveSpec(enabled = true, type = WaveType.SAWTOOTH, amplitude = 1.0, frequencyHz = 1.0, phaseShiftRad = 0.0)
        val samples = WaveGenerator.generateSampleArray(listOf(spec), 4, 4)
        assertEquals(4, samples.size)
        assertSamplesClose(samples, doubleArrayOf(-1.0, -0.5, 0.0, 0.5))
    }
    /** Verifies a one-hertz triangle sampled four times per period matches the expected pattern exactly. */
    @Test
    fun `generate sample array produces the expected triangle pattern`() {
        val spec = WaveSpec(enabled = true, type = WaveType.TRIANGLE, amplitude = 1.0, frequencyHz = 1.0, phaseShiftRad = 0.0)
        val samples = WaveGenerator.generateSampleArray(listOf(spec), 4, 4)
        assertEquals(4, samples.size)
        assertSamplesClose(samples, doubleArrayOf(-1.0, 0.0, 1.0, 0.0))
    }
    /** Verifies noise stays within amplitude bounds and varies from sample to sample. */
    @Test
    fun `generate sample array keeps noise within amplitude bounds with visible variation`() {
        val spec = WaveSpec(enabled = true, type = WaveType.NOISE, amplitude = 0.8, frequencyHz = 1.0, phaseShiftRad = 0.0)
        val samples = WaveGenerator.generateSampleArray(listOf(spec), 100, 200)
        assertEquals(200, samples.size)
        assertTrue(samples.all { it >= -0.801 && it <= 0.801 }, "Noise samples must lie in [-amplitude, amplitude]")
        val first = samples[0]
        assertTrue(samples.any { kotlin.math.abs(it - first) > 1e-12 }, "Noise should vary between samples")
    }
}
