package io.github.lukewilk.hardware.pipeline

import brainflow.BoardIds
import brainflow.BrainFlowInputParams
import brainflow.BoardShim
import io.github.lukewilk.hardware.BoardConnectionManager
import io.github.lukewilk.hardware.RawFrame
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.WaveType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Connection-state and synthetic-streaming tests for `DataAcquisition`.
 */
class DataAcquisitionTest : DataAcquisitionTestSupport() {

    /** Verifies acquisition emits nothing when the board is disconnected before collection starts. */
    @Test
    fun `stream raw frames returns an empty flow when disconnected`() = runBlocking {
        manager.close()

        val items = acquisition.streamRawFrames().take(1).toList()

        assertTrue(items.isEmpty(), "Flow should emit no items when not connected")
    }

    /** Verifies a connected non-synthetic acquisition exits quietly when no BoardShim is available. */
    @Test
    fun `stream raw frames returns empty when connected without a shim`() = runBlocking {
        val localStateStore = StateStore(HardwareState(connected = true, synthetic = false, windowSize = 4, overlap = 2, channels = 1))
        val localManager = BoardConnectionManager(localStateStore)
        val localAcquisition = DataAcquisition(
            localManager,
            boardShimProvider = { null },
            stateProvider = { localStateStore.get() }
        )

        val items = localAcquisition.streamRawFrames().take(1).toList()

        assertTrue(items.isEmpty(), "Connected non-synthetic acquisition should exit quietly when no BoardShim is available")
    }

    /** Verifies synthetic streaming emits windowed frames once the synthetic board is connected and started. */
    @Test
    fun `synthetic board stream raw frames emits frames`() = runBlocking {
        // Seed one enabled channel so the synthetic loop has deterministic output to emit.
        stateStore.update { st -> st.copy(channels = 1, samplingRateHz = 120, enabledChannels = listOf(0)) }
        manager.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")
        manager.startStream()

        logger.i { "Board connected: ${manager.state.value}" }
        val frames = mutableListOf<RawFrame>()
        withTimeout(10_000) {
            acquisition.streamRawFrames().take(3).toList(frames)
        }
        manager.stopStream()

        logger.i { "Frames collected: ${frames.size}" }
        frames.forEachIndexed { idx, frame ->
            logger.i { "Frame $idx data: ${frame.data.contentToString()}" }
        }

        assertEquals(3, frames.size, "Should emit 3 frames before close")
        frames.forEach { frame -> assertTrue(frame.data.isNotEmpty(), "Frame data should not be empty") }
    }

    /** Verifies synthetic acquisition defaults to all available channels when none are explicitly enabled. */
    @Test
    fun `synthetic board with empty enabled channels emits all channels`() = runBlocking {
        val localStateStore = StateStore(
            HardwareState(
                connected = true,
                synthetic = true,
                samplingRateHz = 120,
                channels = 2,
                enabledChannels = emptyList(),
                windowSize = 4,
                overlap = 2,
                waveSpecs = listOf(WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 8.0))
            )
        )
        val localManager = BoardConnectionManager(localStateStore)
        val localAcquisition = DataAcquisition(localManager)

        localManager.startStream()
        val frames = withTimeout(5_000) { localAcquisition.streamRawFrames().take(4).toList() }
        localManager.stopStream()

        assertTrue(frames.map { it.channel }.toSet().containsAll(setOf(0, 1)))
    }

    /** Verifies synthetic acquisition ignores enabled-channel indexes that fall outside the generated data set. */
    @Test
    fun `synthetic board skips invalid enabled channels`() = runBlocking {
        val localStateStore = StateStore(
            HardwareState(
                connected = true,
                synthetic = true,
                samplingRateHz = 120,
                channels = 2,
                enabledChannels = listOf(-1, 1, 3),
                windowSize = 4,
                overlap = 2,
                waveSpecs = listOf(WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 8.0))
            )
        )
        val localManager = BoardConnectionManager(localStateStore)
        val localAcquisition = DataAcquisition(localManager)

        localManager.startStream()
        val frames = withTimeout(5_000) { localAcquisition.streamRawFrames().take(2).toList() }
        localManager.stopStream()

        assertTrue(frames.isNotEmpty())
        assertTrue(frames.all { it.channel == 1 }, "Synthetic acquisition should skip invalid enabled channel indexes")
    }

    /** Verifies acquisition reports connection state consistently across close boundaries. */
    @Test
    fun `is connected and close reflect manager state`() = runBlocking {
        manager.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")

        assertTrue(acquisition.isConnected(), "Should be connected after connect")

        acquisition.close()

        assertTrue(!acquisition.isConnected(), "Should not be connected after close")
    }

    /** Verifies a throwing injected shim produces no frames and exits quietly. */
    @Test
    fun `stream raw frames handles board shim errors`() = runBlocking {
        manager.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")
        manager.startStream()
        val errorState = HardwareState(connected = true, synthetic = true, samplingRateHz = 120, enabledChannels = listOf(0))
        val errorBoardShim = object : BoardShim(BoardIds.SYNTHETIC_BOARD, BrainFlowInputParams()) {
            override fun get_board_data(num_datapoints: Int): Array<DoubleArray> {
                throw RuntimeException("Simulated error")
            }
        }
        val localAcquisition = DataAcquisition(
            manager,
            boardShimProvider = { errorBoardShim },
            stateProvider = { errorState }
        )

        val frames = localAcquisition.streamRawFrames().take(1).toList()
        manager.stopStream()

        assertTrue(frames.isEmpty(), "Should emit no frames on error")
    }

    /** Verifies synthetic streaming loop errors are swallowed after at most one emitted frame. */
    @Test
    fun `synthetic streaming error path exits quietly`() = runBlocking {
        val localStateStore = StateStore(HardwareState(connected = true, synthetic = true, channels = 1, enabledChannels = listOf(0), windowSize = 4, samplingRateHz = 100))
        val localManager = BoardConnectionManager(localStateStore)
        var callCount = 0
        val throwingStateProvider = {
            callCount++
            if (callCount > 1) throw RuntimeException("Simulated synthetic streaming error")
            localStateStore.get()
        }
        val localAcquisition = DataAcquisition(localManager, stateProvider = throwingStateProvider)

        localManager.startStream()
        val frames = mutableListOf<RawFrame>()
        localAcquisition.streamRawFrames().take(1).toList(frames)
        localManager.stopStream()

        assertTrue(frames.size <= 1, "Should emit at most one frame before synthetic streaming error")
    }

    /** Verifies synthetic acquisition skips emitting a ready window once streaming stops during generation. */
    @Test
    fun `synthetic streaming stops before buffered window emission`() = runBlocking {
        val localStateStore = StateStore(
            HardwareState(
                connected = true,
                synthetic = true,
                channels = 1,
                enabledChannels = listOf(0),
                windowSize = 4,
                overlap = 2,
                samplingRateHz = 128
            )
        )
        val localManager = object : BoardConnectionManager(localStateStore) {
            override fun generateSyntheticData(samples: Int): Array<DoubleArray> {
                stopStream()
                return arrayOf(DoubleArray(samples) { 1.0 })
            }
        }
        var timeIndex = 0
        val scriptedTimes = listOf(0L, 1_000L, 1_001L)
        val localAcquisition = DataAcquisition(
            localManager,
            stateProvider = { localStateStore.get() },
            timeProvider = { scriptedTimes.getOrElse(timeIndex++) { scriptedTimes.last() } }
        )

        localManager.startStream()
        val frames = withTimeout(2_000) { localAcquisition.streamRawFrames().take(1).toList() }

        assertTrue(frames.isEmpty(), "Synthetic acquisition should skip buffered window emission once streaming has already been stopped")
    }

    /** Verifies synthetic acquisition caps large catch-up bursts to at most two seconds of data. */
    @Test
    fun `synthetic streaming caps large catch up bursts`() = runBlocking {
        val localStateStore = StateStore(
            HardwareState(
                connected = true,
                synthetic = true,
                samplingRateHz = 100,
                channels = 1,
                enabledChannels = listOf(0),
                windowSize = 4,
                overlap = 2
            )
        )
        var requestedSamples = -1
        val localManager = object : BoardConnectionManager(localStateStore) {
            override fun generateSyntheticData(samples: Int): Array<DoubleArray> {
                requestedSamples = samples
                return Array(1) { DoubleArray(samples) { 1.0 } }
            }
        }
        var timeIndex = 0
        val scriptedTimes = listOf(0L, 10_000L, 10_001L, 10_002L)
        val localAcquisition = DataAcquisition(
            localManager,
            stateProvider = { localStateStore.get() },
            timeProvider = { scriptedTimes.getOrElse(timeIndex++) { scriptedTimes.last() } }
        )

        localManager.startStream()
        val frames = withTimeout(2_000) { localAcquisition.streamRawFrames().take(1).toList() }
        localManager.stopStream()

        assertEquals(200, requestedSamples, "Synthetic acquisition should cap bursts to samplingRate * 2 samples")
        assertEquals(1, frames.size)
        assertEquals(4, frames.single().data.size)
    }

    /** Verifies synthetic acquisition skips frames and exits cleanly when generation fails every iteration. */
    @Test
    fun `synthetic streaming skips frames when generation fails`() = runBlocking {
        val localStateStore = StateStore(
            HardwareState(
                connected = true,
                synthetic = true,
                samplingRateHz = 128,
                channels = 1,
                enabledChannels = listOf(0),
                windowSize = 4,
                overlap = 2
            )
        )
        val localManager = object : BoardConnectionManager(localStateStore) {
            override fun generateSyntheticData(samples: Int): Array<DoubleArray> {
                throw RuntimeException("synthetic generation failed")
            }
        }
        val localAcquisition = DataAcquisition(
            localManager,
            stateProvider = { localStateStore.get() },
            timeProvider = { System.currentTimeMillis() + 1_000L }
        )

        localManager.startStream()
        val frames = mutableListOf<RawFrame>()
        val collector = launch {
            localAcquisition.streamRawFrames().collect { frames.add(it) }
        }

        delay(75)
        localManager.stopStream()
        collector.join()

        assertTrue(frames.isEmpty(), "No frames should be emitted when synthetic data generation fails for every iteration")
    }

    /** Verifies the outer streaming-loop helper returns true only while streaming, connected, and active. */
    @Test
    fun `should continue streaming loop helper covers all exit conditions`() {
        assertTrue(shouldContinueStreamingLoop(streaming = true, connected = true, isActive = true))
        assertTrue(!shouldContinueStreamingLoop(streaming = false, connected = true, isActive = true))
        assertTrue(!shouldContinueStreamingLoop(streaming = true, connected = false, isActive = true))
        assertTrue(!shouldContinueStreamingLoop(streaming = true, connected = true, isActive = false))
    }

    /** Verifies the per-window helper requires both an active stream and an active coroutine context. */
    @Test
    fun `should emit current window helper covers streaming and context checks`() {
        assertTrue(shouldEmitCurrentWindow(streaming = true, isActive = true))
        assertTrue(!shouldEmitCurrentWindow(streaming = false, isActive = true))
        assertTrue(!shouldEmitCurrentWindow(streaming = true, isActive = false))
    }

    /** Verifies the drain-loop helper blocks undersized, stopped, and inactive buffer states. */
    @Test
    fun `should drain channel buffer helper covers all branches`() {
        assertTrue(!shouldDrainChannelBuffer(bufferedSamples = 3, windowSize = 4, streaming = true, isActive = true))
        assertTrue(shouldDrainChannelBuffer(bufferedSamples = 4, windowSize = 4, streaming = true, isActive = true))
        assertTrue(!shouldDrainChannelBuffer(bufferedSamples = 4, windowSize = 4, streaming = false, isActive = true))
        assertTrue(!shouldDrainChannelBuffer(bufferedSamples = 4, windowSize = 4, streaming = true, isActive = false))
    }
}
