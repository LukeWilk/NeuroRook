package io.github.lukewilk.hardwareRunner

import brainflow.BoardIds
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import io.github.lukewilk.shared.SyntheticMode
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.WaveType
import io.github.lukewilk.shared.logging.LoggerProvider
import io.github.lukewilk.shared.model.BandPower
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Smoke tests for the development runner's bounded helper logic.
 */
class MainSmokeTest {
    @Test
    fun `resolve board id accepts known enum names and falls back for unknown values`() {
        // Keeps CLI parsing deterministic without launching the long-running pipeline.
        assertEquals(BoardIds.SYNTHETIC_BOARD, resolveBoardId(arrayOf("synthetic_board")))
        assertEquals(BoardIds.NO_BOARD, resolveBoardId(arrayOf("unknown-board")))
        assertEquals(BoardIds.NO_BOARD, resolveBoardId(emptyArray()))
    }

    @Test
    fun `default runner state seeds a first synthetic sine wave when none exist`() {
        // Documents the smoke-path defaults used by the runner when starting from a blank HardwareState.
        val updated = defaultRunnerState(HardwareState())

        assertEquals(1, updated.channels)
        assertEquals(listOf(0), updated.enabledChannels)
        assertEquals(SyntheticMode.WAVE_GENERATOR, updated.syntheticMode)
        assertTrue(updated.waveSpecs.isNotEmpty(), "Expected the runner to seed at least one wave spec")
        assertEquals(WaveType.SINE, updated.waveSpecs.first().type)
        assertTrue(updated.waveSpecs.first().enabled, "Expected the seeded wave to be enabled")
        assertEquals(10.0, updated.waveSpecs.first().frequencyHz)
    }

    @Test
    fun `default runner state replaces the first existing wave instead of appending another one`() {
        // Verifies the helper updates an existing synthetic wave in place when the store already has wave specs.
        val initialState = HardwareState(
            waveSpecs = listOf(
                WaveSpec(enabled = false, type = WaveType.NOISE, amplitude = 0.5, frequencyHz = 3.0),
                WaveSpec(enabled = true, type = WaveType.SQUARE, amplitude = 2.0, frequencyHz = 20.0)
            )
        )

        val updated = defaultRunnerState(initialState)

        assertEquals(2, updated.waveSpecs.size)
        assertEquals(defaultRunnerWaveSpec(), updated.waveSpecs.first())
        assertEquals(WaveType.SQUARE, updated.waveSpecs[1].type)
    }

    @Test
    fun `configure runner synthetic defaults updates the shared state store`() {
        // Confirms the store-level helper applies the same deterministic runner defaults used by the CLI entrypoint.
        val stateStore = StateStore(HardwareState())

        configureRunnerSyntheticDefaults(stateStore)

        val updated = stateStore.get()
        assertEquals(1, updated.channels)
        assertEquals(SyntheticMode.WAVE_GENERATOR, updated.syntheticMode)
        assertEquals(defaultRunnerWaveSpec(), updated.waveSpecs.first())
    }

    @Test
    fun `filtered log messages cover zero and non zero branches`() {
        // Verifies the extracted filtered-data formatter emits both verbose output and the correct summary branch.
        val nonZeroMessages = filteredLogMessages(doubleArrayOf(0.0, 1.5, -2.0))
        val zeroMessages = filteredLogMessages(doubleArrayOf(0.0, 0.0))

        assertEquals(RunnerLogLevel.VERBOSE, nonZeroMessages.first().level)
        assertTrue(nonZeroMessages.first().message.contains("[Filtered]"))
        assertEquals(RunnerLogLevel.INFO, nonZeroMessages.last().level)
        assertTrue(nonZeroMessages.last().message.contains("Filtered-NonZero"))

        assertEquals(RunnerLogLevel.WARN, zeroMessages.last().level)
        assertEquals("[Filtered] All zeroes!", zeroMessages.last().message)
    }

    @Test
    fun `fft log messages cover zero and non zero magnitudes`() {
        // Confirms the FFT formatter reports either non-zero ranges or the all-zero warning.
        val nonZeroMessages = fftLogMessages(arrayOf(1.0 to 0.0, 2.0 to 3.5))
        val zeroMessages = fftLogMessages(arrayOf(1.0 to 0.0, 2.0 to 0.0))

        assertEquals(RunnerLogLevel.VERBOSE, nonZeroMessages.first().level)
        assertTrue(nonZeroMessages.first().message.contains("[FFT]"))
        assertEquals(RunnerLogLevel.INFO, nonZeroMessages.last().level)
        assertTrue(nonZeroMessages.last().message.contains("FFT-NonZero"))

        assertEquals(RunnerLogLevel.WARN, zeroMessages.last().level)
        assertEquals("[FFT] All zeroes!", zeroMessages.last().message)
    }

    @Test
    fun `band power log messages cover zero and non zero power summaries`() {
        // Verifies the band-power formatter emits a summary line and chooses the correct follow-up branch.
        val nonZeroMessages = bandPowerLogMessages(listOf(BandPower("Alpha", 0.0), BandPower("Beta", 2.0)))
        val zeroMessages = bandPowerLogMessages(listOf(BandPower("Alpha", 0.0), BandPower("Beta", 0.0)))

        assertEquals(RunnerLogLevel.INFO, nonZeroMessages.first().level)
        assertTrue(nonZeroMessages.first().message.contains("BandPowers"))
        assertEquals(RunnerLogLevel.INFO, nonZeroMessages.last().level)
        assertTrue(nonZeroMessages.last().message.contains("BandPowers-NonZero"))

        assertEquals(RunnerLogLevel.WARN, zeroMessages.last().level)
        assertEquals("[BandPowers] All zeroes!", zeroMessages.last().message)
    }

    @Test
    fun `emit runner logs accepts every extracted log level`() {
        // Exercises the logger-emission helper for verbose, info, and warn message routing.
        val logger = LoggerProvider.getLogger("MainSmokeTest")

        emitRunnerLogs(
            logger,
            listOf(
                RunnerLogMessage(RunnerLogLevel.VERBOSE, "verbose"),
                RunnerLogMessage(RunnerLogLevel.INFO, "info"),
                RunnerLogMessage(RunnerLogLevel.WARN, "warn")
            )
        )

        assertTrue(true, "Expected emitRunnerLogs to accept all runner log levels without throwing")
    }
}

