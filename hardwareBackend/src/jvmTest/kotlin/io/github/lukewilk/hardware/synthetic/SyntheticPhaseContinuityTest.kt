package io.github.lukewilk.hardware.synthetic

import io.github.lukewilk.hardware.BoardConnectionManager
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import io.github.lukewilk.shared.SyntheticMode
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.WaveType
import kotlin.test.Test

/**
 * Phase-continuity tests for synthetic signals produced through `BoardConnectionManager`.
 */
internal class SyntheticPhaseContinuityTest : SyntheticSignalTestSupport() {
    private val stateStore = StateStore(HardwareState())

    /** Configures a manager to generate one enabled sine channel through the wave-generator synthetic mode. */
    private fun configuredSyntheticManager(): BoardConnectionManager = BoardConnectionManager(stateStore).also { manager ->
        manager.stateStore.update { state ->
            state.copy(
                connected = true,
                synthetic = true,
                samplingRateHz = 100,
                channels = 4,
                enabledChannels = listOf(0),
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
    }

    /** Verifies two sequential synthetic blocks match one combined generation after a phase reset. */
    @Test
    fun `synthetic manager keeps phase continuity across two blocks`() {
        val manager = configuredSyntheticManager()
        val samplesPerBlock = 50

        SyntheticDataGenerator.resetPhases()
        val first = manager.generateSyntheticData(samplesPerBlock)
        val second = manager.generateSyntheticData(samplesPerBlock)
        val concatenated = DoubleArray(samplesPerBlock * 2)
        for (index in 0 until samplesPerBlock) concatenated[index] = first[0][index]
        for (index in 0 until samplesPerBlock) concatenated[samplesPerBlock + index] = second[0][index]

        SyntheticDataGenerator.resetPhases()
        val combined = manager.generateSyntheticData(samplesPerBlock * 2)

        assertSamplesMatch(concatenated, combined[0])
    }

    /** Verifies many sequential synthetic blocks still match one combined generation after a phase reset. */
    @Test
    fun `synthetic manager keeps phase continuity across streaming style generation`() {
        val manager = configuredSyntheticManager()
        val blocks = 10
        val samplesPerBlock = 50
        val concatenated = DoubleArray(blocks * samplesPerBlock)

        SyntheticDataGenerator.resetPhases()
        for (blockIndex in 0 until blocks) {
            val block = manager.generateSyntheticData(samplesPerBlock)
            System.arraycopy(block[0], 0, concatenated, blockIndex * samplesPerBlock, samplesPerBlock)
        }

        SyntheticDataGenerator.resetPhases()
        val combined = manager.generateSyntheticData(blocks * samplesPerBlock)

        assertSamplesMatch(concatenated, combined[0])
    }
}