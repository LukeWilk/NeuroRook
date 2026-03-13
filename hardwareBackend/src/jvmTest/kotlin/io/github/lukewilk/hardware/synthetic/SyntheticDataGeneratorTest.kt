package io.github.lukewilk.hardware.synthetic

import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.WaveType as SharedWaveType
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyntheticDataGeneratorTest {

    /** non-synthetic mode returns zero-filled arrays. */
    @Test
    fun testGenerateNonSyntheticReturnsZeros() {
        val st = HardwareState().copy(
            channels = 4,
            samplingRateHz = 100,
            synthetic = false,
            enabledChannels = (0 until 4).toList(),
            waveSpecs = listOf()
        )
        val samples = 10
        val out = SyntheticDataGenerator.generate(st, samples)
        assertEquals(4, out.size, "should produce one row per channel")
        assertEquals(samples, out[0].size, "each row should have requested samples")
        // all values should be zero since synthetic=false
        assertTrue(out.all { row -> row.all { v -> v == 0.0 } }, "All outputs should be zero when synthetic=false")
    }

    /** enabled channel yields signal; disabled channels are zeros. */
    @Test
    fun testGenerateEnabledChannelProducesSignal() {
        val st = HardwareState().copy(
            channels = 3,
            samplingRateHz = 100,
            synthetic = true,
            enabledChannels = listOf(1), // only channel 1 enabled
            waveSpecs = listOf(
                WaveSpec(enabled = true, type = SharedWaveType.SINE, amplitude = 1.0, frequencyHz = 5.0, phaseShiftRad = 0.0)
            )
        )
        val samples = 20
        // Ensure deterministic start
        SyntheticDataGenerator.resetPhases()
        val out = SyntheticDataGenerator.generate(st, samples)

        assertEquals(3, out.size)
        assertEquals(samples, out[0].size)

        // channel 1 should contain non-zero samples (signal)
        assertTrue(out[1].any { it != 0.0 }, "Enabled channel should produce non-zero samples")
        // other channels should be zero
        assertTrue(out[0].all { it == 0.0 }, "Disabled channel 0 should be all zeros")
        assertTrue(out[2].all { it == 0.0 }, "Disabled channel 2 should be all zeros")
    }

    /** phase reset ensures concatenated blocks match a combined block. */
    @Test
    fun testPhaseResetMakesConcatenationMatchCombined() {
        val st = HardwareState().copy(
            channels = 1,
            samplingRateHz = 100,
            synthetic = true,
            enabledChannels = listOf(0),
            waveSpecs = listOf(
                WaveSpec(enabled = true, type = SharedWaveType.SINE, amplitude = 1.0, frequencyHz = 5.0, phaseShiftRad = 0.0)
            )
        )

        val samplesPerBlock = 50
        // Combined generation after resetting phases
        SyntheticDataGenerator.resetPhases()
        val combined = SyntheticDataGenerator.generate(st, samplesPerBlock * 2)

        // Reset phases then generate two blocks sequentially and concatenate
        SyntheticDataGenerator.resetPhases()
        val first = SyntheticDataGenerator.generate(st, samplesPerBlock)
        val second = SyntheticDataGenerator.generate(st, samplesPerBlock)
        val concat = DoubleArray(samplesPerBlock * 2)
        for (i in 0 until samplesPerBlock) concat[i] = first[0][i]
        for (i in 0 until samplesPerBlock) concat[samplesPerBlock + i] = second[0][i]

        // Compare sample-by-sample for channel 0
        for (i in concat.indices) {
            val a = concat[i]
            val b = combined[0][i]
            val diff = abs(a - b)
            assertTrue(diff < 1e-8, "Sample $i differs: $a vs $b (diff=$diff)")
        }
    }
}

