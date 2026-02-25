package io.github.lukewilk.hardware.synthetic

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WaveGeneratorUnitTest {
    @Test
    fun testGenerateSine() {
        val spec = WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 2.0, frequencyHz = 5.0, phaseShiftRad = 0.0)
        val arr = WaveGenerator.generateSampleArray(listOf(spec), 100, 100)
        assertEquals(100, arr.size)
        assertTrue(arr.any { it != 0.0 })
    }

    @Test
    fun testSuperposition() {
        val s1 = WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 5.0, phaseShiftRad = 0.0)
        val s2 = WaveSpec(enabled = true, type = WaveType.SQUARE, amplitude = 0.5, frequencyHz = 10.0, phaseShiftRad = 0.0)
        val arr = WaveGenerator.generateSampleArray(listOf(s1, s2), 100, 100)
        assertEquals(100, arr.size)
        assertTrue(arr.any { it != 0.0 })
    }

    @Test
    fun testGenerateSawtoothPattern() {
        // Setup: 1 Hz sawtooth sampled at 4 Hz -> 4 samples per period
        val spec = WaveSpec(enabled = true, type = WaveType.SAWTOOTH, amplitude = 1.0, frequencyHz = 1.0, phaseShiftRad = 0.0)
        val arr = WaveGenerator.generateSampleArray(listOf(spec), 4, 4)
        assertEquals(4, arr.size)
        // Expected fractional positions: 0, 1/4, 2/4, 3/4 -> values = 2*frac - 1
        val expected = doubleArrayOf(-1.0, -0.5, 0.0, 0.5)
        for (i in arr.indices) {
            assertTrue(abs(arr[i] - expected[i]) < 1e-12, "Sawtooth sample $i differs: ${arr[i]} vs ${expected[i]}")
        }
    }

    @Test
    fun testGenerateTrianglePattern() {
        // Setup: 1 Hz triangle sampled at 4 Hz -> 4 samples per period
        val spec = WaveSpec(enabled = true, type = WaveType.TRIANGLE, amplitude = 1.0, frequencyHz = 1.0, phaseShiftRad = 0.0)
        val arr = WaveGenerator.generateSampleArray(listOf(spec), 4, 4)
        assertEquals(4, arr.size)
        // Expected triangle values for frac=0,0.25,0.5,0.75 -> -1,0,1,0
        val expected = doubleArrayOf(-1.0, 0.0, 1.0, 0.0)
        for (i in arr.indices) {
            assertTrue(abs(arr[i] - expected[i]) < 1e-12, "Triangle sample $i differs: ${arr[i]} vs ${expected[i]}")
        }
    }

    @Test
    fun testGenerateNoiseBoundsAndVariation() {
        val spec = WaveSpec(enabled = true, type = WaveType.NOISE, amplitude = 0.8, frequencyHz = 1.0, phaseShiftRad = 0.0)
        val arr = WaveGenerator.generateSampleArray(listOf(spec), 100, 200)
        assertEquals(200, arr.size)
        // Values should be within [-amplitude, amplitude]
        assertTrue(arr.all { it >= -0.801 && it <= 0.801 }, "Noise samples must lie in [-amplitude, amplitude]")
        // And there should be some variation (not all equal)
        val first = arr[0]
        assertTrue(arr.any { abs(it - first) > 1e-12 }, "Noise should vary between samples")
    }

    @Test
    fun testSawtoothPhaseContinuityWithPhasesApi() {
        // Use generateSampleArrayWithPhases to verify continuity across blocks
        val spec = WaveSpec(
            enabled = true,
            type = WaveType.SAWTOOTH,
            amplitude = 1.0,
            frequencyHz = 1.0,
            phaseShiftRad = 0.0
        )
        val samplingRate = 4
        val block = 4
        val zeros = List(1) { 0.0 }

        // Generate combined
        val (combined, _) = WaveGenerator.generateSampleArrayWithPhases(
            listOf(spec),
            samplingRate,
            block * 2,
            zeros
        )

        // Generate two sequential blocks using updated phases
        val (first, updated) = WaveGenerator.generateSampleArrayWithPhases(
            listOf(spec),
            samplingRate,
            block,
            zeros
        )
        val (second, _) = WaveGenerator.generateSampleArrayWithPhases(
            listOf(spec),
            samplingRate,
            block,
            updated
        )

        // concat and compare
        val concat = DoubleArray(block * 2)
        for (i in 0 until block) concat[i] = first[i]
        for (i in 0 until block) concat[block + i] = second[i]

        for (i in concat.indices) {
            assertTrue(
                abs(concat[i] - combined[i]) < 1e-12,
                "Concatenated sample $i differs: ${concat[i]} vs ${combined[i]}"
            )
        }
    }

    @Test
    fun testSawtoothRespectsInitialPhaseOffset() {
        val spec = WaveSpec(enabled = true, type = WaveType.SAWTOOTH, amplitude = 1.0, frequencyHz = 1.0, phaseShiftRad = 0.0)
        val samplingRate = 4
        val samples = 4

        // No initial phase
        val (base, _) = WaveGenerator.generateSampleArrayWithPhases(listOf(spec), samplingRate, samples, listOf(0.0))

        // Initial phase of PI (half period) -> frac offset of 0.5
        val (offset, _) = WaveGenerator.generateSampleArrayWithPhases(listOf(spec), samplingRate, samples, listOf(PI))

        // With a 0.5 fractional offset, sawtooth values shift by 2*0.5 - 1 = 0 (i.e. sign flips relative)
        // Ensure outputs differ (phase offset applied)
        var different = false
        for (i in 0 until samples) {
            if (abs(base[i] - offset[i]) > 1e-12) {
                different = true
                break
            }
        }
        assertTrue(different, "Sawtooth with non-zero initial phase should differ from base output")
    }

    @Test
    fun testTriangleRespectsInitialPhaseOffset() {
        val spec = WaveSpec(enabled = true, type = WaveType.TRIANGLE, amplitude = 1.0, frequencyHz = 1.0, phaseShiftRad = 0.0)
        val samplingRate = 4
        val samples = 4

        val (base, _) = WaveGenerator.generateSampleArrayWithPhases(listOf(spec), samplingRate, samples, listOf(0.0))
        val (offset, _) = WaveGenerator.generateSampleArrayWithPhases(listOf(spec), samplingRate, samples, listOf(PI / 2.0))

        // With a quarter-period phase offset (PI/2), triangle waveform will be shifted; outputs should differ
        var different = false
        for (i in 0 until samples) {
            if (abs(base[i] - offset[i]) > 1e-12) {
                different = true
                break
            }
        }
        assertTrue(different, "Triangle with non-zero initial phase should differ from base output")
    }

    @Test
    fun testGenerateWithPhasesReturnsUpdatedPhases() {
        val spec = WaveSpec(enabled = true, type = WaveType.SAWTOOTH, amplitude = 1.0, frequencyHz = 2.0, phaseShiftRad = 0.0)
        val samplingRate = 8
        val samples = 8
        val initial = listOf(0.25) // some small initial phase in radians

        val (_, updated) = WaveGenerator.generateSampleArrayWithPhases(listOf(spec), samplingRate, samples, initial)

        // updated phases must be list of size specs and differ from initial by 2*pi*freq*(samples/samplingRate)
        assertEquals(1, updated.size)
        val expectedDelta = 2.0 * PI * spec.frequencyHz * (samples.toDouble() / samplingRate.toDouble())
        assertTrue(abs((updated[0] - initial[0]) - expectedDelta) < 1e-12, "Updated phase does not match expected delta")
    }

    @Test
    fun testEmptySpecsEchoInitialPhases() {
        val initial = listOf(3.1415)
        val (out, updated) = WaveGenerator.generateSampleArrayWithPhases(emptyList(), 100, 5, initial)
        // out length equals samples (maxOf(0, samples)) and is zero-filled
        assertEquals(5, out.size)
        assertTrue(out.all { it == 0.0 })
        // updated phases should be identical to initial when specs.isEmpty()
        assertEquals(initial, updated)
    }

    @Test
    fun testZeroSamplesEchoInitialPhases() {
        val spec = WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 2.0, phaseShiftRad = 0.0)
        val initial = listOf(0.5)
        val (out, updated) = WaveGenerator.generateSampleArrayWithPhases(listOf(spec), 100, 0, initial)
        assertEquals(0, out.size)
        // when samples==0 the function returns initialPhases unchanged
        assertEquals(initial, updated)
    }

    @Test
    fun testZeroSamplingRateEchoInitialPhases() {
        val spec = WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 2.0, phaseShiftRad = 0.0)
        val initial = listOf(1.23)
        val (out, updated) = WaveGenerator.generateSampleArrayWithPhases(listOf(spec), 0, 4, initial)
        // samplingRate <= 0 triggers early return with out length = samples
        assertEquals(4, out.size)
        assertTrue(out.all { it == 0.0 })
        assertEquals(initial, updated)
    }

    @Test
    fun testGetOrNullNullBranchMatchesZeroPhases() {
        val specs = listOf(
            WaveSpec(enabled = true, type = WaveType.SAWTOOTH, amplitude = 1.0, frequencyHz = 1.0, phaseShiftRad = 0.0),
            WaveSpec(enabled = true, type = WaveType.TRIANGLE, amplitude = 1.0, frequencyHz = 1.0, phaseShiftRad = 0.0),
            WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 1.0, phaseShiftRad = 0.0)
        )
        val sr = 4
        val samples = 4

        // call with empty initialPhases -> getOrNull should return null -> treated as 0.0
        val (outNull, updatedNull) = WaveGenerator.generateSampleArrayWithPhases(specs, sr, samples, listOf())

        // call with explicit zero phases
        val zeros = List(specs.size) { 0.0 }
        val (outZero, updatedZero) = WaveGenerator.generateSampleArrayWithPhases(specs, sr, samples, zeros)

        // outputs and updated phases should match
        for (i in outZero.indices) assertTrue(abs(outZero[i] - outNull[i]) < 1e-12)
        for (j in updatedZero.indices) assertTrue(abs(updatedZero[j] - updatedNull[j]) < 1e-12)
    }

    @Test
    fun testGetOrNullNonNullBranchAffectsOutput() {
        val specs = listOf(
            WaveSpec(enabled = true, type = WaveType.SAWTOOTH, amplitude = 1.0, frequencyHz = 1.0, phaseShiftRad = 0.0),
            WaveSpec(enabled = true, type = WaveType.TRIANGLE, amplitude = 1.0, frequencyHz = 1.0, phaseShiftRad = 0.0)
        )
        val sr = 4
        val samples = 4

        val zeros = List(specs.size) { 0.0 }
        val (outZero, _) = WaveGenerator.generateSampleArrayWithPhases(specs, sr, samples, zeros)

        val nonZero = listOf(PI, PI / 2.0)
        val (outPhase, _) = WaveGenerator.generateSampleArrayWithPhases(specs, sr, samples, nonZero)

        // With non-zero initial phases the generated outputs should differ
        var different = false
        for (i in 0 until samples) if (abs(outZero[i] - outPhase[i]) > 1e-12) { different = true; break }
        assertTrue(different, "Outputs should differ when initial phases are provided")
    }

    @Test
    fun testPhaseAccumulationUpdatedPhases() {
        val specs = listOf(
            WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 3.0, phaseShiftRad = 0.0),
            WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 5.0, phaseShiftRad = 0.0)
        )
        val sr = 10
        val samples = 20
        val initial = listOf(0.1, 0.2)
        val (_, updated) = WaveGenerator.generateSampleArrayWithPhases(specs, sr, samples, initial)

        // expected delta for each spec
        val expectedDeltas = specs.map { spec -> 2.0 * PI * spec.frequencyHz * (samples.toDouble() / sr.toDouble()) }
        for (idx in updated.indices) {
            val delta = updated[idx] - initial[idx]
            assertTrue(abs(delta - expectedDeltas[idx]) < 1e-12, "Updated phase delta mismatch at idx $idx")
        }
    }
}