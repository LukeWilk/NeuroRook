package io.github.lukewilk.hardware

import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import io.github.lukewilk.shared.SyntheticMode
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.WaveType

/**
 * Shared synthetic-board fixtures for `BoardConnectionManager` JVM tests.
 */
internal abstract class BoardConnectionManagerSyntheticTestSupport : BoardConnectionManagerTestSupport() {
    /** Builds a synthetic state with one enabled sine wave so synthetic-path tests can stay concise. */
    protected fun syntheticState(
        channels: Int,
        samplingRateHz: Int = 128,
        enabledChannels: List<Int> = listOf(0),
        syntheticMode: SyntheticMode = SyntheticMode.WAVE_GENERATOR,
        waveSpecs: List<WaveSpec> = listOf(
            WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 8.0)
        )
    ): HardwareState = HardwareState(
        synthetic = true,
        channels = channels,
        samplingRateHz = samplingRateHz,
        enabledChannels = enabledChannels,
        syntheticMode = syntheticMode,
        waveSpecs = waveSpecs
    )

    /** Creates a manager from a synthetic state fixture so tests share one setup style. */
    protected fun syntheticManager(state: HardwareState): BoardConnectionManager =
        BoardConnectionManager(StateStore(state))
}

