package io.github.lukewilk.hardware.synthetic
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
/**
 * Phase continuity, initial-phase, and updated-phase tests for `WaveGenerator`.
 */
internal class WaveGeneratorPhaseStateTest : WaveGeneratorTestSupport() {
    /** Verifies sequential sawtooth blocks match one combined generation when updated phases are reused. */
    @Test
    fun `generate sample array with phases keeps sawtooth blocks continuous`() {
        val spec = WaveSpec(enabled = true, type = WaveType.SAWTOOTH, amplitude = 1.0, frequencyHz = 1.0, phaseShiftRad = 0.0)
        val samplingRate = 4
        val blockSize = 4
        val zeroPhases = listOf(0.0)
        val (combined, _) = WaveGenerator.generateSampleArrayWithPhases(listOf(spec), samplingRate, blockSize * 2, zeroPhases)
        val (first, updatedPhases) = WaveGenerator.generateSampleArrayWithPhases(listOf(spec), samplingRate, blockSize, zeroPhases)
        val (secondBlock, _) = WaveGenerator.generateSampleArrayWithPhases(listOf(spec), samplingRate, blockSize, updatedPhases)
        assertSamplesClose(concatenateBlocks(first, secondBlock), combined, label = "Concatenated sawtooth output")
    }
    /** Verifies a non-zero initial sawtooth phase changes the generated output. */
    @Test
    fun `generate sample array with phases applies an initial sawtooth phase offset`() {
        val spec = WaveSpec(enabled = true, type = WaveType.SAWTOOTH, amplitude = 1.0, frequencyHz = 1.0, phaseShiftRad = 0.0)
        val (base, _) = WaveGenerator.generateSampleArrayWithPhases(listOf(spec), 4, 4, listOf(0.0))
        val (offset, _) = WaveGenerator.generateSampleArrayWithPhases(listOf(spec), 4, 4, listOf(PI))
        assertTrue(base.indices.any { abs(base[it] - offset[it]) > 1e-12 }, "Sawtooth with non-zero initial phase should differ from base output")
    }
    /** Verifies a non-zero initial triangle phase changes the generated output. */
    @Test
    fun `generate sample array with phases applies an initial triangle phase offset`() {
        val spec = WaveSpec(enabled = true, type = WaveType.TRIANGLE, amplitude = 1.0, frequencyHz = 1.0, phaseShiftRad = 0.0)
        val (base, _) = WaveGenerator.generateSampleArrayWithPhases(listOf(spec), 4, 4, listOf(0.0))
        val (offset, _) = WaveGenerator.generateSampleArrayWithPhases(listOf(spec), 4, 4, listOf(PI / 2.0))
        assertTrue(base.indices.any { abs(base[it] - offset[it]) > 1e-12 }, "Triangle with non-zero initial phase should differ from base output")
    }
    /** Verifies updated phases advance by the expected amount for a single waveform. */
    @Test
    fun `generate sample array with phases returns the expected updated phase`() {
        val spec = WaveSpec(enabled = true, type = WaveType.SAWTOOTH, amplitude = 1.0, frequencyHz = 2.0, phaseShiftRad = 0.0)
        val initialPhases = listOf(0.25)
        val (_, updatedPhases) = WaveGenerator.generateSampleArrayWithPhases(listOf(spec), 8, 8, initialPhases)
        assertEquals(1, updatedPhases.size)
        val expectedDelta = 2.0 * PI * spec.frequencyHz * (8.0 / 8.0)
        assertTrue(abs((updatedPhases[0] - initialPhases[0]) - expectedDelta) < 1e-12, "Updated phase does not match expected delta")
    }
    /** Verifies empty specs preserve the provided phase vector while returning a zero-filled output. */
    @Test
    fun `generate sample array with phases echoes initial phases when specs are empty`() {
        val initialPhases = listOf(3.1415)
        val (output, updatedPhases) = WaveGenerator.generateSampleArrayWithPhases(emptyList(), 100, 5, initialPhases)
        assertEquals(5, output.size)
        assertTrue(output.all { it == 0.0 })
        assertEquals(initialPhases, updatedPhases)
    }
    /** Verifies zero requested samples keep the provided phases unchanged. */
    @Test
    fun `generate sample array with phases echoes initial phases when sample count is zero`() {
        val spec = WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 2.0, phaseShiftRad = 0.0)
        val initialPhases = listOf(0.5)
        val (output, updatedPhases) = WaveGenerator.generateSampleArrayWithPhases(listOf(spec), 100, 0, initialPhases)
        assertEquals(0, output.size)
        assertEquals(initialPhases, updatedPhases)
    }
    /** Verifies non-positive sampling rates keep the provided phases unchanged and emit zeros. */
    @Test
    fun `generate sample array with phases echoes initial phases when sampling rate is zero`() {
        val spec = WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 2.0, phaseShiftRad = 0.0)
        val initialPhases = listOf(1.23)
        val (output, updatedPhases) = WaveGenerator.generateSampleArrayWithPhases(listOf(spec), 0, 4, initialPhases)
        assertEquals(4, output.size)
        assertTrue(output.all { it == 0.0 })
        assertEquals(initialPhases, updatedPhases)
    }
    /** Verifies missing phase entries behave the same as explicitly provided zero phases. */
    @Test
    fun `generate sample array with phases treats missing initial phases as zeros`() {
        val specs = listOf(
            WaveSpec(enabled = true, type = WaveType.SAWTOOTH, amplitude = 1.0, frequencyHz = 1.0, phaseShiftRad = 0.0),
            WaveSpec(enabled = true, type = WaveType.TRIANGLE, amplitude = 1.0, frequencyHz = 1.0, phaseShiftRad = 0.0),
            WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 1.0, phaseShiftRad = 0.0)
        )
        val zeroPhases = List(specs.size) { 0.0 }
        val (implicitZeroOutput, implicitZeroUpdated) = WaveGenerator.generateSampleArrayWithPhases(specs, 4, 4, emptyList())
        val (explicitZeroOutput, explicitZeroUpdated) = WaveGenerator.generateSampleArrayWithPhases(specs, 4, 4, zeroPhases)
        assertSamplesClose(implicitZeroOutput, explicitZeroOutput)
        assertSamplesClose(
            implicitZeroUpdated.toDoubleArray(),
            explicitZeroUpdated.toDoubleArray(),
            label = "Updated phase output"
        )
    }
    /** Verifies explicit non-zero initial phases affect the generated output. */
    @Test
    fun `generate sample array with phases changes output when explicit phases are provided`() {
        val specs = listOf(
            WaveSpec(enabled = true, type = WaveType.SAWTOOTH, amplitude = 1.0, frequencyHz = 1.0, phaseShiftRad = 0.0),
            WaveSpec(enabled = true, type = WaveType.TRIANGLE, amplitude = 1.0, frequencyHz = 1.0, phaseShiftRad = 0.0)
        )
        val (zeroPhaseOutput, _) = WaveGenerator.generateSampleArrayWithPhases(specs, 4, 4, List(specs.size) { 0.0 })
        val (offsetPhaseOutput, _) = WaveGenerator.generateSampleArrayWithPhases(specs, 4, 4, listOf(PI, PI / 2.0))
        assertTrue(
            zeroPhaseOutput.indices.any { abs(zeroPhaseOutput[it] - offsetPhaseOutput[it]) > 1e-12 },
            "Outputs should differ when initial phases are provided"
        )
    }
    /** Verifies updated phases accumulate independently for each waveform in the list. */
    @Test
    fun `generate sample array with phases accumulates updated phases for multiple waveforms`() {
        val specs = listOf(
            WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 3.0, phaseShiftRad = 0.0),
            WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 5.0, phaseShiftRad = 0.0)
        )
        val initialPhases = listOf(0.1, 0.2)
        val (_, updatedPhases) = WaveGenerator.generateSampleArrayWithPhases(specs, 10, 20, initialPhases)
        val expectedDeltas = specs.map { spec -> 2.0 * PI * spec.frequencyHz * (20.0 / 10.0) }
        for (index in updatedPhases.indices) {
            val delta = updatedPhases[index] - initialPhases[index]
            assertTrue(abs(delta - expectedDeltas[index]) < 1e-12, "Updated phase delta mismatch at index $index")
        }
    }
}
