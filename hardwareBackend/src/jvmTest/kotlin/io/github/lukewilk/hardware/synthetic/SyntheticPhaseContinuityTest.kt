package io.github.lukewilk.hardware.synthetic

import io.github.lukewilk.hardware.BoardConnectionManager
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import io.github.lukewilk.shared.SyntheticMode
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.WaveType
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests verifying phase continuity of generated synthetic signals.
 *
 * The project's synthetic data generator should produce continuous-phase
 * waveforms when data is generated in multiple sequential blocks. These
 * tests exercise two usage patterns:
 *  - generating two consecutive blocks and concatenating them, and
 *  - generating many sequential blocks in a streaming-style loop;
 *
 * both are compared against a single-call generation of the same total
 * duration (after resetting internal phases) to assert sample-level
 * equivalence within floating-point tolerance.
 */
class SyntheticPhaseContinuityTest {

    val stateStore = StateStore(HardwareState())

    /**
     * Verify that two sequential calls to `generateSyntheticData` and
     * concatenating their outputs is equivalent to a single call that
     * generates the same total number of samples. The test configures the
     * manager to produce a single enabled sine wave and compares sample-by-
     * sample values for channel 0.
     */
    @Test
    fun testPhaseContinuityAcrossBlocks() {
        val manager = BoardConnectionManager(stateStore)
        // configure state: synthetic, wave generator, sampling 100Hz, 4 channels, enable channel 0
        manager.stateStore.update { st ->
            st.copy(
                connected = true,
                synthetic = true,
                samplingRateHz = 100,
                channels = 4,
                enabledChannels = listOf(0), // Enable only channel 0 by index
                syntheticMode = SyntheticMode.WAVE_GENERATOR,
                waveSpecs = listOf(
                    WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 5.0, phaseShiftRad = 0.0),
                    WaveSpec(),
                    WaveSpec(),
                    WaveSpec(),
                    WaveSpec()
                )
            )
        }
        // reset any internal phases
        SyntheticDataGenerator.resetPhases()

        val samples = 50 // half-second blocks at 100Hz
        val first = manager.generateSyntheticData(samples)
        val second = manager.generateSyntheticData(samples)

        // concatenate channel 0 samples
        val concat = DoubleArray(samples * 2) { 0.0 }
        for (i in 0 until samples) concat[i] = first[0][i]
        for (i in 0 until samples) concat[samples + i] = second[0][i]

        // now generate the same duration in one call
        // reset phases and regenerate
        SyntheticDataGenerator.resetPhases()
        val combined = manager.generateSyntheticData(samples * 2)

        // compare sample-by-sample for channel 0 within small tolerance
        for (i in concat.indices) {
            val a = concat[i]
            val b = combined[0][i]
            val diff = abs(a - b)
            assertTrue(diff < 1e-8, "Sample $i differs: $a vs $b (diff=$diff)")
        }
    }

    /**
     * Simulates streaming generation: produce `blocks` sequential blocks of
     * synthetic samples and concatenate them, then compare against a single
     * generation of the same total duration to assert phase continuity.
     */
    @Test
    fun testPhaseContinuityStreaming() {
        val manager = BoardConnectionManager(stateStore)
        manager.stateStore.update { st ->
            st.copy(
                connected = true,
                synthetic = true,
                samplingRateHz = 100,
                channels = 4,
                enabledChannels = listOf(0), // Enable only channel 0 by index
                syntheticMode = SyntheticMode.WAVE_GENERATOR,
                waveSpecs = listOf(
                    WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 5.0, phaseShiftRad = 0.0),
                    WaveSpec(),
                    WaveSpec(),
                    WaveSpec(),
                    WaveSpec()
                )
            )
        }

        // reset phases
        SyntheticDataGenerator.resetPhases()

        val blocks = 10
        val samplesPerBlock = 50
        val concatenated = DoubleArray(blocks * samplesPerBlock)

        for (b in 0 until blocks) {
            val block = manager.generateSyntheticData(samplesPerBlock)
            val ch = block[0]
            System.arraycopy(ch, 0, concatenated, b * samplesPerBlock, samplesPerBlock)
        }

        // reset phases and generate combined
        SyntheticDataGenerator.resetPhases()
        val combined = manager.generateSyntheticData(blocks * samplesPerBlock)

        // compare sample-by-sample for channel 0 within small tolerance
        for (i in concatenated.indices) {
            val a = concatenated[i]
            val b = combined[0][i]
            val diff = abs(a - b)
            assertTrue(diff < 1e-8, "Sample $i differs: $a vs $b (diff=$diff)")
        }
    }
}