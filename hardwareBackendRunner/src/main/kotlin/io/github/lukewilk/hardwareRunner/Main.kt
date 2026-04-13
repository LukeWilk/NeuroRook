/**
 * Main entry point for running the hardware backend in development/testing mode.
 *
 * Usage:
 *   - By default, launches with a synthetic board generating a 10 Hz sine wave on channel 0.
 *   - Logs filtered signals, FFT results, and band powers for inspection.
 *   - Used for backend development, debugging, and pipeline validation.
 *
 * Example:
 *   ./gradlew :hardwareBackendRunner:run --args=SYNTHETIC_BOARD
 *
 */

package io.github.lukewilk.hardwareRunner

import brainflow.BoardIds
import io.github.lukewilk.hardware.main
import io.github.lukewilk.hardware.BoardConnectionManager
import io.github.lukewilk.shared.model.BandPower
import io.github.lukewilk.shared.StateStore
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.SyntheticMode
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.WaveType
import io.github.lukewilk.shared.logging.LoggerProvider
import kotlinx.coroutines.runBlocking

internal enum class RunnerLogLevel { VERBOSE, INFO, WARN }

internal data class RunnerLogMessage(
    val level: RunnerLogLevel,
    val message: String
)

/** Provides the deterministic runner wave used by the smoke-tested synthetic default path. */
internal fun defaultRunnerWaveSpec(): WaveSpec = WaveSpec(
    enabled = true,
    type = WaveType.SINE,
    amplitude = 1.0,
    frequencyHz = 10.0,
    phaseShiftRad = 0.0
)

/** Seeds the runner's synthetic defaults while tolerating an initially empty wave list. */
internal fun defaultRunnerState(state: HardwareState): HardwareState {
    val seededWaveSpecs = state.waveSpecs.toMutableList().apply {
        if (isEmpty()) {
            add(defaultRunnerWaveSpec())
        } else {
            this[0] = defaultRunnerWaveSpec()
        }
    }

    return state.copy(
        channels = 1,
        enabledChannels = listOf(0),
        syntheticMode = SyntheticMode.WAVE_GENERATOR,
        waveSpecs = seededWaveSpecs
    )
}

/** Applies the runner's deterministic synthetic defaults to the shared state store. */
internal fun configureRunnerSyntheticDefaults(stateStore: StateStore<HardwareState>) {
    stateStore.update(::defaultRunnerState)
}

/** Resolves the requested board id from CLI args and falls back to NO_BOARD for unknown names. */
internal fun resolveBoardId(args: Array<String>): BoardIds = BoardIds.entries.find {
    it.name.equals(args.getOrNull(0), ignoreCase = true)
} ?: BoardIds.NO_BOARD

internal fun filteredLogMessages(filtered: DoubleArray): List<RunnerLogMessage> = buildList {
    add(RunnerLogMessage(RunnerLogLevel.VERBOSE, "[Filtered] ${filtered.joinToString(", ", limit = 10, truncated = "...")}"))
    if (filtered.any { it != 0.0 }) {
        add(RunnerLogMessage(RunnerLogLevel.INFO, "[Filtered-NonZero] min=${filtered.minOrNull()}, max=${filtered.maxOrNull()}"))
    } else {
        add(RunnerLogMessage(RunnerLogLevel.WARN, "[Filtered] All zeroes!"))
    }
}

internal fun fftLogMessages(fftResult: Array<Pair<Double, Double>>): List<RunnerLogMessage> {
    val magnitudes = fftResult.map { it.second }
    return buildList {
        add(
            RunnerLogMessage(
                RunnerLogLevel.VERBOSE,
                "[FFT] ${fftResult.take(10).joinToString(", ") { pair -> "(${"%.3f".format(pair.first)}, ${"%.3f".format(pair.second)})" }}..."
            )
        )
        if (magnitudes.any { it != 0.0 }) {
            add(RunnerLogMessage(RunnerLogLevel.INFO, "[FFT-NonZero] min=${magnitudes.minOrNull()}, max=${magnitudes.maxOrNull()}"))
        } else {
            add(RunnerLogMessage(RunnerLogLevel.WARN, "[FFT] All zeroes!"))
        }
    }
}

internal fun bandPowerLogMessages(bandPowers: List<BandPower>): List<RunnerLogMessage> = buildList {
    add(RunnerLogMessage(RunnerLogLevel.INFO, "[BandPowers] ${bandPowers.joinToString(", ") { bp -> "${bp.name}: ${"%.3f".format(bp.power)}" }}"))
    if (bandPowers.any { it.power != 0.0 }) {
        add(RunnerLogMessage(RunnerLogLevel.INFO, "[BandPowers-NonZero] min=${bandPowers.minOf { it.power }}, max=${bandPowers.maxOf { it.power }}"))
    } else {
        add(RunnerLogMessage(RunnerLogLevel.WARN, "[BandPowers] All zeroes!"))
    }
}

internal fun emitRunnerLogs(logger: co.touchlab.kermit.Logger, messages: List<RunnerLogMessage>) {
    messages.forEach { message ->
        when (message.level) {
            RunnerLogLevel.VERBOSE -> logger.v { message.message }
            RunnerLogLevel.INFO -> logger.i { message.message }
            RunnerLogLevel.WARN -> logger.w { message.message }
        }
    }
}

fun main(args: Array<String>) {
    // Set up state store and connection manager
    val stateStore = StateStore(HardwareState())
    val manager = BoardConnectionManager(stateStore)
    // Configure synthetic board state for nonzero data
    configureRunnerSyntheticDefaults(stateStore)
    // Connect first
    val boardId = resolveBoardId(args)
    val serialPort = args.getOrNull(1) ?: ""
    manager.connect(boardId, serialPort)
    manager.startStream()
    val logger = LoggerProvider.getLogger("hardwareRunner.Main")
    manager.enableChannel(0)
    // Now start the main pipeline with the same stateStore and manager
    runBlocking {
        main(
            _args = args,
            onFiltered = { filtered: DoubleArray -> emitRunnerLogs(logger, filteredLogMessages(filtered)) },
            onFFTResult = { fftResult: Array<Pair<Double, Double>> -> emitRunnerLogs(logger, fftLogMessages(fftResult)) },
            onBandPowers = { bandPowers: List<BandPower> -> emitRunnerLogs(logger, bandPowerLogMessages(bandPowers)) },
            stateStore = stateStore,
            manager = manager
        )
    }
    manager.stopStream()
    manager.close()
}
