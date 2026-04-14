package io.github.lukewilk.hardware.synthetic

import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.WaveType as SharedWaveType
import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * Shared synthetic-signal fixtures and sample comparison helpers for generator-oriented tests.
 */
internal abstract class SyntheticSignalTestSupport {
    /** Builds a simple synthetic-wave state used by generator and continuity tests. */
    protected fun singleSineSyntheticState(
        channels: Int,
        samplingRateHz: Int,
        enabledChannels: List<Int>,
        amplitude: Double = 1.0,
        frequencyHz: Double = 5.0
    ): HardwareState = HardwareState(
        channels = channels,
        samplingRateHz = samplingRateHz,
        synthetic = true,
        enabledChannels = enabledChannels,
        waveSpecs = listOf(
            WaveSpec(
                enabled = true,
                type = SharedWaveType.SINE,
                amplitude = amplitude,
                frequencyHz = frequencyHz,
                phaseShiftRad = 0.0
            )
        )
    )

    /** Asserts that two sample arrays match within the tolerance used by synthetic generator tests. */
    protected fun assertSamplesMatch(actual: DoubleArray, expected: DoubleArray, tolerance: Double = 1e-8, label: String = "Sample") {
        assertTrue(actual.size == expected.size, "$label size differs: ${actual.size} vs ${expected.size}")
        for (index in actual.indices) {
            val diff = abs(actual[index] - expected[index])
            assertTrue(diff < tolerance, "$label $index differs: ${actual[index]} vs ${expected[index]} (diff=$diff)")
        }
    }
}

