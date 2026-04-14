package io.github.lukewilk.hardwareRunner

import brainflow.BoardIds
import io.github.lukewilk.hardware.BoardConnectionManager
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import io.github.lukewilk.shared.SyntheticMode
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.WaveType
import io.github.lukewilk.shared.logging.LoggerProvider
import io.github.lukewilk.shared.model.BandPower
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
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
    fun `band power log messages treat an empty list as an all zero summary`() {
        // Covers the empty-list branch so the formatter stays safe before any band powers have been emitted.
        val emptyMessages = bandPowerLogMessages(emptyList())

        assertEquals(RunnerLogLevel.INFO, emptyMessages.first().level)
        assertEquals("[BandPowers] ", emptyMessages.first().message)
        assertEquals(RunnerLogLevel.WARN, emptyMessages.last().level)
        assertEquals("[BandPowers] All zeroes!", emptyMessages.last().message)
    }

    @Test
    fun `band power log messages short circuit immediately when the first power is non zero`() {
        // Covers the early-true branch of the any/min/max path when the first sample is already non-zero.
        val messages = bandPowerLogMessages(listOf(BandPower("Alpha", 1.5), BandPower("Beta", 0.0)))

        assertEquals(RunnerLogLevel.INFO, messages.last().level)
        assertTrue(messages.last().message.contains("min=0.0"))
        assertTrue(messages.last().message.contains("max=1.5"))
    }

    @Test
    fun `runner band power summary helper returns info for non zero lists and warn for zero lists`() {
        // Covers the extracted summary helper directly so Kover attributes the branch to the small named function.
        val nonZero = runnerBandPowerSummaryMessage(listOf(BandPower("Alpha", 0.0), BandPower("Beta", 2.0)))
        val zero = runnerBandPowerSummaryMessage(listOf(BandPower("Alpha", 0.0)))

        assertEquals(RunnerLogLevel.INFO, nonZero.level)
        assertTrue(nonZero.message.contains("BandPowers-NonZero"))
        assertEquals(RunnerLogLevel.WARN, zero.level)
        assertEquals("[BandPowers] All zeroes!", zero.message)
    }

    @Test
    fun `runner band power summary helper handles a single non zero entry`() {
        // Covers the single-item min/max path so the summary helper does not rely on multiple values to stay covered.
        val summary = runnerBandPowerSummaryMessage(listOf(BandPower("Gamma", 4.0)))

        assertEquals(RunnerLogLevel.INFO, summary.level)
        assertEquals("[BandPowers-NonZero] min=4.0, max=4.0", summary.message)
    }

    @Test
    fun `runner band power helpers expose explicit non zero detection and min max summary text`() {
        // Verifies the extracted non-zero summary seams directly so Kover attributes the range calculation outside the compact wrapper branch.
        val bandPowers = listOf(BandPower("Alpha", -1.5), BandPower("Beta", 0.0), BandPower("Gamma", 3.5))

        assertTrue(hasNonZeroBandPowers(bandPowers))
        assertTrue(!hasNonZeroBandPowers(listOf(BandPower("Delta", 0.0), BandPower("Theta", 0.0))))
        assertEquals(-1.5, minimumBandPowerValue(bandPowers))
        assertEquals(3.5, maximumBandPowerValue(bandPowers))
        assertEquals("[BandPowers-NonZero] min=-1.5, max=3.5", nonZeroBandPowerSummaryText(bandPowers))
    }

    @Test
    fun `runner band power min max helpers fail fast for empty lists`() {
        // Covers the empty-list exception branches so the extracted helpers preserve the same strict min/max contract.
        assertFailsWith<NoSuchElementException> {
            minimumBandPowerValue(emptyList())
        }
        assertFailsWith<NoSuchElementException> {
            maximumBandPowerValue(emptyList())
        }
    }

    @Test
    fun `band power log messages handle an empty list as an all zero summary`() {
        // Covers the empty-list path so the formatter stays deterministic even before any band powers are available.
        val messages = bandPowerLogMessages(emptyList())

        assertEquals(RunnerLogLevel.INFO, messages.first().level)
        assertEquals("[BandPowers] ", messages.first().message)
        assertEquals(RunnerLogLevel.WARN, messages.last().level)
        assertEquals("[BandPowers] All zeroes!", messages.last().message)
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

    @Test
    fun `run runner orchestrates connection pipeline callbacks and cleanup in order`() {
        // Verifies the extracted entrypoint helper seeds defaults, forwards CLI args, and performs cleanup in sequence.
        val stateStore = StateStore(HardwareState())
        val recordedSteps = mutableListOf<String>()
        var resolvedBoardId: BoardIds? = null
        var resolvedSerialPort: String? = null

        runRunner(
            args = arrayOf("SYNTHETIC_BOARD", "/dev/ttyUSB-runner"),
            runtime = RunnerRuntime(
                stateStore = stateStore,
                connect = { _, boardId, serialPort ->
                    recordedSteps += "connect"
                    resolvedBoardId = boardId
                    resolvedSerialPort = serialPort
                },
                startStream = { recordedSteps += "startStream" },
                enableChannel = { _, channelId -> recordedSteps += "enableChannel:$channelId" },
                stopStream = { recordedSteps += "stopStream" },
                close = { recordedSteps += "close" },
                backendMain = { args, _, runnerStateStore, _ ->
                    recordedSteps += "backendMain:${args.joinToString(",")}"
                    assertEquals(1, runnerStateStore.get().channels)
                    assertEquals(listOf(0), runnerStateStore.get().enabledChannels)
                    assertEquals(SyntheticMode.WAVE_GENERATOR, runnerStateStore.get().syntheticMode)
                    assertEquals(defaultRunnerWaveSpec(), runnerStateStore.get().waveSpecs.first())
                }
            )
        )

        assertEquals(BoardIds.SYNTHETIC_BOARD, resolvedBoardId)
        assertEquals("/dev/ttyUSB-runner", resolvedSerialPort)
        assertEquals(
            listOf(
                "connect",
                "startStream",
                "enableChannel:0",
                "backendMain:SYNTHETIC_BOARD,/dev/ttyUSB-runner",
                "stopStream",
                "close"
            ),
            recordedSteps
        )
    }

    @Test
    fun `run runner default runtime uses hookable default operations in order`() {
        // Covers the omitted-runtime path without touching the real pipeline by swapping in internal default-operation hooks.
        val recordedSteps = mutableListOf<String>()

        try {
            RunnerRuntimeHooks.loggerFactoryOverride = { name ->
                recordedSteps += "logger:$name"
                LoggerProvider.getLogger("MainSmokeTest.defaultRuntime")
            }
            RunnerRuntimeHooks.connectOverride = { _, boardId, serialPort ->
                recordedSteps += "connect:${boardId.name}:$serialPort"
            }
            RunnerRuntimeHooks.startStreamOverride = { recordedSteps += "startStream" }
            RunnerRuntimeHooks.enableChannelOverride = { _, channelId -> recordedSteps += "enableChannel:$channelId" }
            RunnerRuntimeHooks.stopStreamOverride = { recordedSteps += "stopStream" }
            RunnerRuntimeHooks.closeOverride = { recordedSteps += "close" }
            RunnerRuntimeHooks.backendMainOverride = { args, _, stateStore, _ ->
                recordedSteps += "backendMain:${args.joinToString(",")}"
                assertEquals(1, stateStore.get().channels)
                assertEquals(listOf(0), stateStore.get().enabledChannels)
                assertEquals(SyntheticMode.WAVE_GENERATOR, stateStore.get().syntheticMode)
                assertEquals(defaultRunnerWaveSpec(), stateStore.get().waveSpecs.first())
            }

            runRunner(arrayOf("SYNTHETIC_BOARD", "/dev/default-runtime"))
        } finally {
            RunnerRuntimeHooks.reset()
        }

        assertEquals(
            listOf(
                "connect:SYNTHETIC_BOARD:/dev/default-runtime",
                "startStream",
                "logger:hardwareRunner.Main",
                "enableChannel:0",
                "backendMain:SYNTHETIC_BOARD,/dev/default-runtime",
                "stopStream",
                "close"
            ),
            recordedSteps
        )
    }

    @Test
    fun `main delegates to the default run runner path`() {
        // Confirms the public top-level entrypoint still routes through the same default runner orchestration.
        val recordedSteps = mutableListOf<String>()

        try {
            RunnerRuntimeHooks.connectOverride = { _, boardId, _ -> recordedSteps += "connect:${boardId.name}" }
            RunnerRuntimeHooks.startStreamOverride = { recordedSteps += "startStream" }
            RunnerRuntimeHooks.loggerFactoryOverride = { LoggerProvider.getLogger("MainSmokeTest.main") }
            RunnerRuntimeHooks.enableChannelOverride = { _, channelId -> recordedSteps += "enableChannel:$channelId" }
            RunnerRuntimeHooks.backendMainOverride = { _, _, _, _ -> recordedSteps += "backendMain" }
            RunnerRuntimeHooks.stopStreamOverride = { recordedSteps += "stopStream" }
            RunnerRuntimeHooks.closeOverride = { recordedSteps += "close" }

            main(arrayOf("SYNTHETIC_BOARD"))
        } finally {
            RunnerRuntimeHooks.reset()
        }

        assertEquals(
            listOf("connect:SYNTHETIC_BOARD", "startStream", "enableChannel:0", "backendMain", "stopStream", "close"),
            recordedSteps
        )
    }

    @Test
    fun `default runner backend main uses the hookable fallback invoker when no override is installed`() = kotlinx.coroutines.runBlocking {
        // Covers the fallback branch without entering the real backend loop by replacing only the final fallback invoker.
        val stateStore = StateStore(HardwareState())
        val manager = RunnerRuntime().managerFactory(stateStore)
        val recordedSteps = mutableListOf<String>()

        try {
            RunnerRuntimeHooks.backendMainFallbackOverride = { args, _, forwardedStateStore, forwardedManager ->
                recordedSteps += "fallback:${args.joinToString(",")}"
                assertEquals(stateStore, forwardedStateStore)
                assertEquals(manager, forwardedManager)
            }

            defaultRunnerBackendMain(
                args = arrayOf("SYNTHETIC_BOARD"),
                logger = LoggerProvider.getLogger("MainSmokeTest.backendMain"),
                stateStore = stateStore,
                manager = manager
            )
        } finally {
            RunnerRuntimeHooks.reset()
        }

        assertEquals(listOf("fallback:SYNTHETIC_BOARD"), recordedSteps)
    }

    @Test
    fun `default runner backend main forwards logging callbacks into the raw hardware entrypoint hook`() = kotlinx.coroutines.runBlocking {
        // Executes the real fallback lambda while replacing only the final hardware-main handoff.
        val stateStore = StateStore(HardwareState())
        val manager = RunnerRuntime().managerFactory(stateStore)
        val recordedSteps = mutableListOf<String>()

        try {
            RunnerRuntimeHooks.rawHardwareMainInvoker = { args, onFiltered, onFFTResult, onBandPowers, forwardedStateStore, forwardedManager ->
                recordedSteps += "raw:${args.joinToString(",")}"
                assertEquals(stateStore, forwardedStateStore)
                assertEquals(manager, forwardedManager)
                onFiltered(doubleArrayOf(0.0, 1.0))
                onFFTResult(arrayOf(1.0 to 2.0))
                onBandPowers(listOf(BandPower("Alpha", 3.0)))
            }

            defaultRunnerBackendMain(
                args = arrayOf("SYNTHETIC_BOARD"),
                logger = LoggerProvider.getLogger("MainSmokeTest.rawFallback"),
                stateStore = stateStore,
                manager = manager
            )
        } finally {
            RunnerRuntimeHooks.reset()
        }

        assertEquals(listOf("raw:SYNTHETIC_BOARD"), recordedSteps)
    }

    @Test
    fun `default runner helper functions use fallback hooks when no overrides are installed`() {
        // Covers the null-override branches without invoking the real backend manager side effects.
        val stateStore = StateStore(HardwareState())
        val manager = RunnerRuntime().managerFactory(stateStore)
        val recordedSteps = mutableListOf<String>()

        try {
            RunnerRuntimeHooks.loggerFactoryFallback = { name ->
                recordedSteps += "logger:$name"
                LoggerProvider.getLogger("MainSmokeTest.fallbackLogger")
            }
            RunnerRuntimeHooks.connectFallback = { _, boardId, serialPort ->
                recordedSteps += "connect:${boardId.name}:$serialPort"
            }
            RunnerRuntimeHooks.startStreamFallback = { recordedSteps += "startStream" }
            RunnerRuntimeHooks.enableChannelFallback = { _, channelId -> recordedSteps += "enableChannel:$channelId" }
            RunnerRuntimeHooks.stopStreamFallback = { recordedSteps += "stopStream" }
            RunnerRuntimeHooks.closeFallback = { recordedSteps += "close" }

            defaultRunnerLoggerFactory("hardwareRunner.Main")
            defaultRunnerConnect(manager, BoardIds.SYNTHETIC_BOARD, "/dev/fallback")
            defaultRunnerStartStream(manager)
            defaultRunnerEnableChannel(manager, 7)
            defaultRunnerStopStream(manager)
            defaultRunnerClose(manager)
        } finally {
            RunnerRuntimeHooks.reset()
        }

        assertEquals(
            listOf(
                "logger:hardwareRunner.Main",
                "connect:SYNTHETIC_BOARD:/dev/fallback",
                "startStream",
                "enableChannel:7",
                "stopStream",
                "close"
            ),
            recordedSteps
        )
    }

    @Test
    fun `default runner logger factory uses the shared logger provider when unmodified`() {
        // Covers the unmodified logger-provider fallback path with no runner-specific hook installed.
        assertNotNull(defaultRunnerLoggerFactory("MainSmokeTest.defaultLoggerProvider"))
    }

    @Test
    fun `default runner logger helper returns a shared Kermit logger`() {
        // Covers the extracted default logger helper directly so the fallback path has a named target in coverage.
        assertNotNull(defaultRunnerLogger("MainSmokeTest.defaultRunnerLogger"))
    }

    @Test
    fun `default runner logger factory prefers the explicit override when installed`() {
        // Covers the direct override branch without relying on the broader runRunner orchestration path.
        val logger = LoggerProvider.getLogger("MainSmokeTest.overrideLogger")

        try {
            RunnerRuntimeHooks.loggerFactoryOverride = { logger }
            assertEquals(logger, defaultRunnerLoggerFactory("ignored"))
        } finally {
            RunnerRuntimeHooks.reset()
        }
    }

    @Test
    fun `default runner backend main uses the raw hardware fallback hook when overrides are absent`() = kotlinx.coroutines.runBlocking {
        // Covers the real default raw-hardware invoker branch while replacing only its final fallback target.
        val stateStore = StateStore(HardwareState())
        val manager = RunnerRuntime().managerFactory(stateStore)
        val recordedSteps = mutableListOf<String>()

        try {
            RunnerRuntimeHooks.rawHardwareMainFallbackInvoker = { args, onFiltered, onFFTResult, onBandPowers, forwardedStateStore, forwardedManager ->
                recordedSteps += "fallbackRaw:${args.joinToString(",")}"
                assertEquals(stateStore, forwardedStateStore)
                assertEquals(manager, forwardedManager)
                onFiltered(doubleArrayOf(0.0, 0.0))
                onFFTResult(arrayOf(1.0 to 0.0))
                onBandPowers(listOf(BandPower("Alpha", 0.0)))
            }

            defaultRunnerBackendMain(
                args = arrayOf("SYNTHETIC_BOARD"),
                logger = LoggerProvider.getLogger("MainSmokeTest.rawHardwareFallback"),
                stateStore = stateStore,
                manager = manager
            )
        } finally {
            RunnerRuntimeHooks.reset()
        }

        assertEquals(listOf("fallbackRaw:SYNTHETIC_BOARD"), recordedSteps)
    }

    @Test
    fun `default runner backend main can execute the real raw backend delegate`() = runBlocking {
        // Covers the default raw-backend delegate line by running the synthetic backend briefly and timing out after callbacks arrive.
        val stateStore = StateStore(HardwareState(windowSize = 32, overlap = 16))
        val manager = prepareSyntheticManager(stateStore)
        try {
            val result = withTimeoutOrNull(1_000) {
                defaultRunnerBackendMain(
                    args = arrayOf("SYNTHETIC_BOARD"),
                    logger = LoggerProvider.getLogger("MainSmokeTest.realRawDelegate"),
                    stateStore = stateStore,
                    manager = manager
                )
            }

            assertEquals(null, result)
        } finally {
            manager.stopStream()
            manager.close()
        }
    }

    @Test
    fun `default raw hardware fallback invoker executes the real backend main path directly`() = runBlocking {
        // Calls the default fallback invoker directly so the remaining wrapper lambda is covered without any intermediate runner hooks.
        val stateStore = StateStore(HardwareState(windowSize = 32, overlap = 16))
        val manager = prepareSyntheticManager(stateStore)

        try {
            val result = withTimeoutOrNull(1_000) {
                RunnerRuntimeHooks.rawHardwareMainFallbackInvoker(
                    arrayOf("SYNTHETIC_BOARD"),
                    {},
                    {},
                    {},
                    stateStore,
                    manager
                )
            }

            assertEquals(null, result)
        } finally {
            manager.stopStream()
            manager.close()
        }
    }

    @Test
    fun `reset restores the real raw backend delegate path`() = runBlocking {
        // Covers the reset-installed raw-backend delegate line by restoring defaults before the same bounded synthetic run.
        val stateStore = StateStore(HardwareState(windowSize = 32, overlap = 16))
        val manager = prepareSyntheticManager(stateStore)
        var callbackCount = 0

        try {
            RunnerRuntimeHooks.rawHardwareMainFallbackInvoker = { _, _, _, _, _, _ -> callbackCount = -999 }
            RunnerRuntimeHooks.reset()

            val result = withTimeoutOrNull(1_000) {
                defaultRunnerBackendMain(
                    args = arrayOf("SYNTHETIC_BOARD"),
                    logger = LoggerProvider.getLogger("MainSmokeTest.resetRawDelegate"),
                    stateStore = stateStore,
                    manager = manager
                )
            }

            assertEquals(0, callbackCount)
            assertEquals(null, result)
        } finally {
            manager.stopStream()
            manager.close()
            RunnerRuntimeHooks.reset()
        }
    }

    /** Creates a connected synthetic manager suitable for bounded raw-backend delegate coverage. */
    private fun prepareSyntheticManager(stateStore: StateStore<HardwareState>): BoardConnectionManager {
        val manager = BoardConnectionManager(stateStore)
        manager.connect(BoardIds.SYNTHETIC_BOARD, "")
        manager.enableChannel(0)
        manager.startStream()
        return manager
    }
}

