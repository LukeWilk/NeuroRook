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
import co.touchlab.kermit.Logger
import io.github.lukewilk.hardware.BoardConnectionManager
import io.github.lukewilk.hardware.main
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

internal fun runnerBandPowerSummaryMessage(bandPowers: List<BandPower>): RunnerLogMessage =
    if (hasNonZeroBandPowers(bandPowers)) {
        RunnerLogMessage(
            RunnerLogLevel.INFO,
            nonZeroBandPowerSummaryText(bandPowers)
        )
    } else {
        RunnerLogMessage(RunnerLogLevel.WARN, "[BandPowers] All zeroes!")
    }

internal fun hasNonZeroBandPowers(bandPowers: List<BandPower>): Boolean =
    bandPowers.any { it.power != 0.0 }

internal fun nonZeroBandPowerSummaryText(bandPowers: List<BandPower>): String =
    "[BandPowers-NonZero] min=${minimumBandPowerValue(bandPowers)}, max=${maximumBandPowerValue(bandPowers)}"

internal fun minimumBandPowerValue(bandPowers: List<BandPower>): Double = bandPowers.minOf { it.power }

internal fun maximumBandPowerValue(bandPowers: List<BandPower>): Double = bandPowers.maxOf { it.power }

internal fun bandPowerLogMessages(bandPowers: List<BandPower>): List<RunnerLogMessage> = listOf(
    RunnerLogMessage(RunnerLogLevel.INFO, "[BandPowers] ${bandPowers.joinToString(", ") { bp -> "${bp.name}: ${"%.3f".format(bp.power)}" }}"),
    runnerBandPowerSummaryMessage(bandPowers)
)

internal fun emitRunnerLogs(logger: Logger, messages: List<RunnerLogMessage>) {
    messages.forEach { message ->
        when (message.level) {
            RunnerLogLevel.VERBOSE -> logger.v { message.message }
            RunnerLogLevel.INFO -> logger.i { message.message }
            RunnerLogLevel.WARN -> logger.w { message.message }
        }
    }
}

internal fun defaultRunnerLogger(name: String): Logger = LoggerProvider.getLogger(name)

internal suspend fun invokeRunnerHardwareMain(
    args: Array<String>,
    onFiltered: (DoubleArray) -> Unit,
    onFFTResult: (Array<Pair<Double, Double>>) -> Unit,
    onBandPowers: (List<BandPower>) -> Unit,
    stateStore: StateStore<HardwareState>,
    manager: BoardConnectionManager
) {
    main(_args = args, onFiltered = onFiltered, onFFTResult = onFFTResult, onBandPowers = onBandPowers, stateStore = stateStore, manager = manager)
}

internal object RunnerRuntimeHooks {
    private val defaultRawHardwareMainInvoker: suspend (Array<String>, (DoubleArray) -> Unit, (Array<Pair<Double, Double>>) -> Unit, (List<BandPower>) -> Unit, StateStore<HardwareState>, BoardConnectionManager) -> Unit =
        { args, onFiltered, onFFTResult, onBandPowers, stateStore, manager -> rawHardwareMainFallbackInvoker(args, onFiltered, onFFTResult, onBandPowers, stateStore, manager) }

    var loggerFactoryOverride: ((String) -> Logger)? = null
    var connectOverride: ((BoardConnectionManager, BoardIds, String) -> Unit)? = null
    var startStreamOverride: ((BoardConnectionManager) -> Unit)? = null
    var enableChannelOverride: ((BoardConnectionManager, Int) -> Unit)? = null
    var stopStreamOverride: ((BoardConnectionManager) -> Unit)? = null
    var closeOverride: ((BoardConnectionManager) -> Unit)? = null
    var loggerFactoryFallback: (String) -> Logger = ::defaultRunnerLogger
    var connectFallback: (BoardConnectionManager, BoardIds, String) -> Unit = { manager, boardId, serialPort -> manager.connect(boardId, serialPort) }
    var startStreamFallback: (BoardConnectionManager) -> Unit = { manager -> manager.startStream() }
    var enableChannelFallback: (BoardConnectionManager, Int) -> Unit = { manager, channelId -> manager.enableChannel(channelId) }
    var stopStreamFallback: (BoardConnectionManager) -> Unit = { manager -> manager.stopStream() }
    var closeFallback: (BoardConnectionManager) -> Unit = { manager -> manager.close() }
    var backendMainOverride: (suspend (Array<String>, Logger, StateStore<HardwareState>, BoardConnectionManager) -> Unit)? = null
    var backendMainFallbackOverride: (suspend (Array<String>, Logger, StateStore<HardwareState>, BoardConnectionManager) -> Unit)? = null
    var rawHardwareMainFallbackInvoker: suspend (Array<String>, (DoubleArray) -> Unit, (Array<Pair<Double, Double>>) -> Unit, (List<BandPower>) -> Unit, StateStore<HardwareState>, BoardConnectionManager) -> Unit =
        ::invokeRunnerHardwareMain
    var rawHardwareMainInvoker: suspend (Array<String>, (DoubleArray) -> Unit, (Array<Pair<Double, Double>>) -> Unit, (List<BandPower>) -> Unit, StateStore<HardwareState>, BoardConnectionManager) -> Unit = defaultRawHardwareMainInvoker

    internal fun reset() {
        loggerFactoryOverride = null
        connectOverride = null
        startStreamOverride = null
        enableChannelOverride = null
        stopStreamOverride = null
        closeOverride = null
        loggerFactoryFallback = ::defaultRunnerLogger
        connectFallback = { manager, boardId, serialPort -> manager.connect(boardId, serialPort) }
        startStreamFallback = { manager -> manager.startStream() }
        enableChannelFallback = { manager, channelId -> manager.enableChannel(channelId) }
        stopStreamFallback = { manager -> manager.stopStream() }
        closeFallback = { manager -> manager.close() }
        backendMainOverride = null
        backendMainFallbackOverride = null
        rawHardwareMainFallbackInvoker = ::invokeRunnerHardwareMain
        rawHardwareMainInvoker = defaultRawHardwareMainInvoker
    }
}

internal fun defaultRunnerLoggerFactory(name: String): Logger {
    val override = RunnerRuntimeHooks.loggerFactoryOverride
    return if (override != null) {
        override(name)
    } else {
        RunnerRuntimeHooks.loggerFactoryFallback(name)
    }
}

internal fun defaultRunnerConnect(manager: BoardConnectionManager, boardId: BoardIds, serialPort: String) {
    RunnerRuntimeHooks.connectOverride?.invoke(manager, boardId, serialPort) ?: RunnerRuntimeHooks.connectFallback(manager, boardId, serialPort)
}

internal fun defaultRunnerStartStream(manager: BoardConnectionManager) {
    RunnerRuntimeHooks.startStreamOverride?.invoke(manager) ?: RunnerRuntimeHooks.startStreamFallback(manager)
}

internal fun defaultRunnerEnableChannel(manager: BoardConnectionManager, channelId: Int) {
    RunnerRuntimeHooks.enableChannelOverride?.invoke(manager, channelId) ?: RunnerRuntimeHooks.enableChannelFallback(manager, channelId)
}

internal fun defaultRunnerStopStream(manager: BoardConnectionManager) {
    RunnerRuntimeHooks.stopStreamOverride?.invoke(manager) ?: RunnerRuntimeHooks.stopStreamFallback(manager)
}

internal fun defaultRunnerClose(manager: BoardConnectionManager) {
    RunnerRuntimeHooks.closeOverride?.invoke(manager) ?: RunnerRuntimeHooks.closeFallback(manager)
}

internal suspend fun defaultRunnerBackendMain(
    args: Array<String>,
    logger: Logger,
    stateStore: StateStore<HardwareState>,
    manager: BoardConnectionManager
) = (RunnerRuntimeHooks.backendMainOverride
    ?: RunnerRuntimeHooks.backendMainFallbackOverride
    ?: { forwardedArgs, forwardedLogger, forwardedStateStore, forwardedManager ->
        RunnerRuntimeHooks.rawHardwareMainInvoker.invoke(
            forwardedArgs,
            { filtered: DoubleArray -> emitRunnerLogs(forwardedLogger, filteredLogMessages(filtered)) },
            { fftResult: Array<Pair<Double, Double>> -> emitRunnerLogs(forwardedLogger, fftLogMessages(fftResult)) },
            { bandPowers: List<BandPower> -> emitRunnerLogs(forwardedLogger, bandPowerLogMessages(bandPowers)) },
            forwardedStateStore,
            forwardedManager
        )
    }).invoke(args, logger, stateStore, manager)

internal data class RunnerRuntime(
    val stateStore: StateStore<HardwareState> = StateStore(HardwareState()),
    val managerFactory: (StateStore<HardwareState>) -> BoardConnectionManager = ::BoardConnectionManager,
    val loggerFactory: (String) -> Logger = ::defaultRunnerLoggerFactory,
    val connect: (BoardConnectionManager, BoardIds, String) -> Unit = ::defaultRunnerConnect,
    val startStream: (BoardConnectionManager) -> Unit = ::defaultRunnerStartStream,
    val enableChannel: (BoardConnectionManager, Int) -> Unit = ::defaultRunnerEnableChannel,
    val stopStream: (BoardConnectionManager) -> Unit = ::defaultRunnerStopStream,
    val close: (BoardConnectionManager) -> Unit = ::defaultRunnerClose,
    val backendMain: suspend (Array<String>, Logger, StateStore<HardwareState>, BoardConnectionManager) -> Unit =
        ::defaultRunnerBackendMain
)

internal fun runRunner(args: Array<String>, runtime: RunnerRuntime = RunnerRuntime()) {
    val stateStore = runtime.stateStore
    val manager = runtime.managerFactory(stateStore)
    configureRunnerSyntheticDefaults(stateStore)
    val boardId = resolveBoardId(args)
    val serialPort = args.getOrNull(1) ?: ""
    runtime.connect(manager, boardId, serialPort)
    runtime.startStream(manager)
    val logger = runtime.loggerFactory("hardwareRunner.Main")
    runtime.enableChannel(manager, 0)
    runBlocking {
        runtime.backendMain(args, logger, stateStore, manager)
    }
    runtime.stopStream(manager)
    runtime.close(manager)
}

fun main(args: Array<String>) {
    runRunner(args)
}
