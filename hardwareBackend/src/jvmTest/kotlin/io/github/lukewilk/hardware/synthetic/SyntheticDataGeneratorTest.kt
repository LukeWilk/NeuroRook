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
import kotlin.math.abs

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

    @Test
    fun `generate returns zeros when all wave specs are disabled`() {
        val state = HardwareState(
            channels = 2,
            samplingRateHz = 128,
            synthetic = true,
            enabledChannels = listOf(0, 1),
            waveSpecs = listOf(
                WaveSpec(enabled = false, type = SharedWaveType.SINE, amplitude = 1.0, frequencyHz = 6.0, phaseShiftRad = 0.0),
                WaveSpec(enabled = false, type = SharedWaveType.SQUARE, amplitude = 0.8, frequencyHz = 4.0, phaseShiftRad = 0.1)
            )
        )

        SyntheticDataGenerator.resetPhases()
        val output = SyntheticDataGenerator.generate(state, 32)

        // All outputs should be zeros because no spec is enabled
        assertTrue(output.all { row -> row.all { it == 0.0 } })
    }

    @Test
    fun `generate does not emit to channels that are not enabled even when specs target them`() {
        val state = HardwareState(
            channels = 3,
            samplingRateHz = 128,
            synthetic = true,
            enabledChannels = listOf(0), // only channel 0 enabled
            waveSpecs = listOf(
                WaveSpec(enabled = true, type = SharedWaveType.SINE, amplitude = 1.0, frequencyHz = 6.0, phaseShiftRad = 0.0, channels = listOf(1,2))
            )
        )

        SyntheticDataGenerator.resetPhases()
        val output = SyntheticDataGenerator.generate(state, 32)

        // Per-wave channel assignments should be authoritative: even though global enabledChannels
        // contains only channel 0, this spec targets channels 1 and 2 and should therefore
        // produce non-zero output on those channels.
        assertTrue(output[1].any { it != 0.0 }, "Spec-targeted channel 1 should produce non-zero samples")
        assertTrue(output[2].any { it != 0.0 }, "Spec-targeted channel 2 should produce non-zero samples")
        // Channel 0 should remain zero because it was not targeted by the spec.
        assertTrue(output[0].all { it == 0.0 }, "Channel 0 was not targeted and should remain zero-filled")
    }

    @Test
    fun `single sine amplitude respects specified amplitude bound`() {
        val state = HardwareState(
            channels = 1,
            samplingRateHz = 256,
            synthetic = true,
            enabledChannels = listOf(0),
            waveSpecs = listOf(
                WaveSpec(enabled = true, type = SharedWaveType.SINE, amplitude = 1.0, frequencyHz = 10.0, phaseShiftRad = 0.0, channels = listOf(0))
            )
        )

        SyntheticDataGenerator.resetPhases()
        val output = SyntheticDataGenerator.generate(state, 1024)

        // For a single sine with amplitude 1.0 the absolute sample values should not exceed 1.0
        val maxAbs = output[0].maxOfOrNull { v -> abs(v) } ?: 0.0
        assertTrue(maxAbs <= 1.0001, "Expected max abs <= 1.0, got $maxAbs")
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

    /** Verifies appending a disabled second wave mid-stream does not reset phase continuity for the existing wave. */
    @Test
    fun `adding disabled second wave mid stream preserves existing wave continuity`() {
        val samplesBeforeAdd = 25
        val samplesAfterAdd = 20
        val singleWaveState = HardwareState(
            channels = 1,
            samplingRateHz = 100,
            synthetic = true,
            enabledChannels = listOf(0),
            waveSpecs = listOf(
                WaveSpec(enabled = true, type = SharedWaveType.SINE, amplitude = 1.0, frequencyHz = 5.0, phaseShiftRad = 0.0)
            )
        )
        val expandedState = singleWaveState.copy(
            waveSpecs = singleWaveState.waveSpecs + WaveSpec(
                enabled = false,
                type = SharedWaveType.SINE,
                amplitude = 1.0,
                frequencyHz = 5.0,
                phaseShiftRad = 0.0
            )
        )

        SyntheticDataGenerator.resetPhases()
        val expectedCombined = SyntheticDataGenerator.generate(singleWaveState, samplesBeforeAdd + samplesAfterAdd)[0]

        SyntheticDataGenerator.resetPhases()
        val firstBlock = SyntheticDataGenerator.generate(singleWaveState, samplesBeforeAdd)[0]
        val secondBlock = SyntheticDataGenerator.generate(expandedState, samplesAfterAdd)[0]
        val concatenated = DoubleArray(samplesBeforeAdd + samplesAfterAdd).also { combined ->
            System.arraycopy(firstBlock, 0, combined, 0, samplesBeforeAdd)
            System.arraycopy(secondBlock, 0, combined, samplesBeforeAdd, samplesAfterAdd)
        }

        assertSamplesMatch(concatenated, expectedCombined, label = "Post-add sample")
    }

    /** Verifies enabling a second identical wave mid-stream keeps it phase-aligned with the already-running wave. */
    @Test
    fun `enabling second identical wave mid stream doubles the live waveform instead of restarting phase`() {
        val samplesBeforeEnable = 25
        val samplesAfterEnable = 20
        val initiallySingleWaveState = HardwareState(
            channels = 1,
            samplingRateHz = 100,
            synthetic = true,
            enabledChannels = listOf(0),
            waveSpecs = listOf(
                WaveSpec(enabled = true, type = SharedWaveType.SINE, amplitude = 1.0, frequencyHz = 5.0, phaseShiftRad = 0.0),
                WaveSpec(enabled = false, type = SharedWaveType.SINE, amplitude = 1.0, frequencyHz = 5.0, phaseShiftRad = 0.0)
            )
        )
        val bothWavesEnabledState = initiallySingleWaveState.copy(
            waveSpecs = initiallySingleWaveState.waveSpecs.mapIndexed { index, wave ->
                if (index == 1) wave.copy(enabled = true) else wave
            }
        )

        SyntheticDataGenerator.resetPhases()
        SyntheticDataGenerator.generate(initiallySingleWaveState, samplesBeforeEnable)
        val enabledBlock = SyntheticDataGenerator.generate(bothWavesEnabledState, samplesAfterEnable)[0]

        SyntheticDataGenerator.resetPhases()
        SyntheticDataGenerator.generate(initiallySingleWaveState, samplesBeforeEnable)
        val continuingSingleWaveBlock = SyntheticDataGenerator.generate(initiallySingleWaveState, samplesAfterEnable)[0]
        val expectedDoubledBlock = DoubleArray(samplesAfterEnable) { index -> continuingSingleWaveBlock[index] * 2.0 }

        assertSamplesMatch(enabledBlock, expectedDoubledBlock, label = "Enabled-wave sample")
    }

    /** Verifies unrelated synthetic configurations do not steal phase continuity from each other. */
    @Test
    fun `phase continuity stays isolated per signal configuration`() {
        val baseState = singleSineSyntheticState(
            channels = 1,
            samplingRateHz = 100,
            enabledChannels = listOf(0),
            frequencyHz = 5.0
        )
        val interferingState = singleSineSyntheticState(
            channels = 16,
            samplingRateHz = 100,
            enabledChannels = listOf(0),
            frequencyHz = 11.0
        )
        val samplesPerBlock = 40

        SyntheticDataGenerator.resetPhases()
        val combined = SyntheticDataGenerator.generate(baseState, samplesPerBlock * 2)

        SyntheticDataGenerator.resetPhases()
        val first = SyntheticDataGenerator.generate(baseState, samplesPerBlock)
        SyntheticDataGenerator.generate(interferingState, samplesPerBlock)
        val second = SyntheticDataGenerator.generate(baseState, samplesPerBlock)

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

