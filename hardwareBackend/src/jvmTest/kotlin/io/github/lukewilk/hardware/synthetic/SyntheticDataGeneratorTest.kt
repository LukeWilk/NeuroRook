package io.github.lukewilk.hardware.synthetic

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import co.touchlab.kermit.platformLogWriter
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.WaveType as SharedWaveType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Output-shape, enabled-channel, and phase-reset tests for `SyntheticDataGenerator`.
 */
internal class SyntheticDataGeneratorTest : SyntheticSignalTestSupport() {

    /** Verifies invalid channel, sampling-rate, and sample-count inputs return safe empty or zero-filled shapes. */
    @Test
    fun `generate returns safe empty or zero filled shapes for invalid dimensions`() {
        SyntheticDataGenerator.resetPhases()

        val invalidChannels = singleSineSyntheticState(channels = 0, samplingRateHz = 100, enabledChannels = listOf(0))
        assertEquals(0, SyntheticDataGenerator.generate(invalidChannels, 16).size)

        val invalidSampling = invalidChannels.copy(channels = 2, samplingRateHz = 0)
        val invalidSamplingOutput = SyntheticDataGenerator.generate(invalidSampling, 16)
        assertEquals(2, invalidSamplingOutput.size)
        assertTrue(invalidSamplingOutput.all { row -> row.all { it == 0.0 } })

        val invalidSampleCountOutput = SyntheticDataGenerator.generate(invalidChannels.copy(channels = 2, samplingRateHz = 100), 0)
        assertEquals(2, invalidSampleCountOutput.size)
        assertTrue(invalidSampleCountOutput.all { row -> row.isEmpty() })
    }

    /** Verifies non-synthetic states return zero-filled arrays for every channel. */
    @Test
    fun `generate returns zeros when synthetic mode is disabled`() {
        val state = HardwareState(
            channels = 4,
            samplingRateHz = 100,
            synthetic = false,
            enabledChannels = (0 until 4).toList(),
            waveSpecs = emptyList()
        )

        val output = SyntheticDataGenerator.generate(state, 10)

        assertEquals(4, output.size, "Expected one row per channel")
        assertEquals(10, output[0].size, "Expected each row to contain the requested number of samples")
        assertTrue(output.all { row -> row.all { value -> value == 0.0 } }, "All outputs should be zero when synthetic=false")
    }

    /** Verifies only enabled channels carry signal samples while disabled channels stay zero-filled. */
    @Test
    fun `generate produces signal only for enabled channels`() {
        val state = singleSineSyntheticState(channels = 3, samplingRateHz = 100, enabledChannels = listOf(1))

        SyntheticDataGenerator.resetPhases()
        val output = SyntheticDataGenerator.generate(state, 20)

        assertEquals(3, output.size)
        assertEquals(20, output[0].size)
        assertTrue(output[1].any { it != 0.0 }, "Enabled channel should produce non-zero samples")
        assertTrue(output[0].all { it == 0.0 }, "Disabled channel 0 should remain zero-filled")
        assertTrue(output[2].all { it == 0.0 }, "Disabled channel 2 should remain zero-filled")
    }

    /** Verifies empty enabled-channel selections produce only zeros even when synthetic waves are configured. */
    @Test
    fun `generate returns only zeros when no channels are enabled`() {
        val state = singleSineSyntheticState(channels = 2, samplingRateHz = 100, enabledChannels = emptyList())

        SyntheticDataGenerator.resetPhases()
        val output = SyntheticDataGenerator.generate(state, 16)

        assertTrue(output.all { row -> row.all { it == 0.0 } })
    }

    /** Verifies every shared wave-type mapping can contribute to one generated synthetic signal. */
    @Test
    fun `generate supports all shared wave type mappings`() {
        val state = HardwareState(
            channels = 1,
            samplingRateHz = 128,
            synthetic = true,
            enabledChannels = listOf(0),
            waveSpecs = listOf(
                WaveSpec(enabled = true, type = SharedWaveType.SINE, amplitude = 1.0, frequencyHz = 6.0, phaseShiftRad = 0.0),
                WaveSpec(enabled = true, type = SharedWaveType.SQUARE, amplitude = 0.8, frequencyHz = 4.0, phaseShiftRad = 0.1),
                WaveSpec(enabled = true, type = SharedWaveType.SAWTOOTH, amplitude = 0.6, frequencyHz = 3.0, phaseShiftRad = 0.2),
                WaveSpec(enabled = true, type = SharedWaveType.TRIANGLE, amplitude = 0.4, frequencyHz = 2.0, phaseShiftRad = 0.3),
                WaveSpec(enabled = true, type = SharedWaveType.NOISE, amplitude = 0.2, frequencyHz = 1.0, phaseShiftRad = 0.0)
            )
        )

        SyntheticDataGenerator.resetPhases()
        val output = SyntheticDataGenerator.generate(state, 64)

        assertEquals(1, output.size)
        assertEquals(64, output[0].size)
        assertTrue(output[0].all { it.isFinite() })
        assertTrue(output[0].any { it != 0.0 }, "Combined enabled wave types should generate a non-zero signal")
    }

    /** Verifies resetting phases makes two sequential blocks match one combined generation exactly. */
    @Test
    fun `phase reset makes concatenated sequential blocks match a combined block`() {
        val state = singleSineSyntheticState(channels = 1, samplingRateHz = 100, enabledChannels = listOf(0))
        val samplesPerBlock = 50

        SyntheticDataGenerator.resetPhases()
        val combined = SyntheticDataGenerator.generate(state, samplesPerBlock * 2)

        SyntheticDataGenerator.resetPhases()
        val first = SyntheticDataGenerator.generate(state, samplesPerBlock)
        val second = SyntheticDataGenerator.generate(state, samplesPerBlock)
        val concatenated = DoubleArray(samplesPerBlock * 2)
        for (index in 0 until samplesPerBlock) concatenated[index] = first[0][index]
        for (index in 0 until samplesPerBlock) concatenated[samplesPerBlock + index] = second[0][index]

        assertSamplesMatch(concatenated, combined[0])
    }

    /** Verifies the debug logging lambda in generate() is evaluated when the logger allows debug severity. */
    @Test
    fun `generate evaluates debug logging when configured logger allows it`() {
        val originalLoggerFactory = SyntheticDataGenerator.loggerFactory
        SyntheticDataGenerator.loggerFactory = {
            Logger(
                loggerConfigInit(
                    platformLogWriter(),
                    minSeverity = Severity.Debug
                )
            ).withTag("SyntheticDataGeneratorTest")
        }

        try {
            val state = singleSineSyntheticState(channels = 1, samplingRateHz = 100, enabledChannels = listOf(0))
            SyntheticDataGenerator.resetPhases()

            val output = SyntheticDataGenerator.generate(state, 8)

            assertEquals(1, output.size)
            assertTrue(output[0].any { it != 0.0 })
        } finally {
            SyntheticDataGenerator.loggerFactory = originalLoggerFactory
        }
    }
}

