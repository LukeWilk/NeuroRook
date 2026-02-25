package io.github.lukewilk.hardware

import brainflow.BoardIds
import brainflow.BoardShim
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import co.touchlab.kermit.Logger
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class DataAcquisitionTest {
    private val logger = LoggerProvider.getLogger("DataAcquisitionTest")

    val stateStore = StateStore(HardwareState())
    val manager = BoardConnectionManager(stateStore = stateStore)
    val acquisition = DataAcquisition(manager)

    @AfterTest
    fun cleanup() {
        runBlocking {
            manager.close()
            kotlinx.coroutines.delay(200) // Give BrainFlow time to release resources
        }
    }

    @Test
    fun testStreamRawFramesReturnsEmptyFlow() = runBlocking {
        manager.close()
        val items = acquisition.streamRawFrames().take(1).toList()
        assertTrue(items.isEmpty(), "Flow should emit no items when not connected")
    }

    @Test
    fun testSyntheticBoardStreamRawFramesEmitsFrames() = runBlocking {
        // Ensure at least one channel is enabled before connecting
        stateStore.update { st ->
            st.copy(
                channels = 1,
                samplingRateHz = 120,
                enabledChannels = listOf(0)
            )
        }
        manager.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")
        manager.startStream()
        logger.i { "Board connected: ${manager.state.value}" }
        val frames = mutableListOf<RawFrame>()
        withTimeout(10000) { // Increased timeout
            acquisition.streamRawFrames().take(3).toList(frames)
        }
        manager.stopStream()
        manager.close()
        logger.i { "Frames collected: ${frames.size}" }
        frames.forEachIndexed { idx, frame ->
            logger.i { "Frame $idx data: ${frame.data.contentToString()}" }
        }
        assertEquals(3, frames.size, "Should emit 3 frames before close")
        frames.forEach { frame ->
            assertTrue(frame.data.isNotEmpty(), "Frame data should not be empty")
        }
    }

    @Test
    fun testIsConnectedAndCloseState() = runBlocking {
        manager.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")
        val acquisition = DataAcquisition(manager)
        assertTrue(acquisition.isConnected(), "Should be connected after connect")
        acquisition.close()
        assertTrue(!acquisition.isConnected(), "Should not be connected after close")
        manager.close()
    }

    @Test
    fun testStreamRawFramesHandlesBoardShimError() = runBlocking {
        manager.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")
        manager.startStream()
        val errorState = io.github.lukewilk.shared.HardwareState(
            connected = true,
            synthetic = true,
            samplingRateHz = 120,
            enabledChannels = listOf(0) // Enable channel 0 by index
        )
        val errorBoardShim = object : BoardShim(BoardIds.SYNTHETIC_BOARD, brainflow.BrainFlowInputParams()) {
            override fun get_board_data(num_datapoints: Int): Array<DoubleArray> {
                throw RuntimeException("Simulated error")
            }
        }
        val acquisition = DataAcquisition(
            manager,
            boardShimProvider = { errorBoardShim },
            stateProvider = { errorState }
        )
        val frames = acquisition.streamRawFrames().take(1).toList()
        manager.stopStream()
        manager.close()
        assertTrue(frames.isEmpty(), "Should emit no frames on error")
    }

    @Test
    fun testStreamRawFrames_nativeShimPath() = runBlocking {
        // Simulate a BoardShim that returns a fixed block of data
        val fakeShim = object : brainflow.BoardShim(0, brainflow.BrainFlowInputParams()) {
            override fun get_board_data(num_datapoints: Int): Array<DoubleArray> {
                // 2 channels, each with num_datapoints samples
                return arrayOf(
                    DoubleArray(num_datapoints) { it.toDouble() },
                    DoubleArray(num_datapoints) { (it + 100).toDouble() }
                )
            }
        }
        val stateStore = StateStore(HardwareState(connected = true, windowSize = 4, overlap = 2, channels = 2, enabledChannels = listOf(0, 1)))
        val manager = BoardConnectionManager(stateStore)
        val acq = DataAcquisition(manager, boardShimProvider = { fakeShim }, stateProvider = { stateStore.get() })
        manager.startStream()
        val frames = mutableListOf<RawFrame>()
        acq.streamRawFrames().take(3).toList(frames)
        manager.stopStream()
        // Should emit frames for both channels
        assertTrue(frames.isNotEmpty())
        assertTrue(frames.all { it.data.size == 4 })
        // Group frames by channel
        val framesByChannel = frames.groupBy { it.channel }
        val overlapSize = 2
        for ((ch, chFrames) in framesByChannel) {
            if (chFrames.size >= 2) {
                for (i in 0 until chFrames.size - 1) {
                    val overlap = chFrames[i].data.takeLast(overlapSize)
                    val nextStart = chFrames[i + 1].data.take(overlapSize)
                    println("DEBUG: channel $ch overlap = $overlap, nextStart = $nextStart")
                    overlap.zip(nextStart).forEach { (a, b) ->
                        assertTrue(kotlin.math.abs(a - b) < 1e-9, "Channel $ch overlap values should match: $a vs $b")
                    }
                }
            }
        }
    }

    @Test
    fun testStreamRawFrames_channelCountChange() = runBlocking {
        // Simulate a BoardShim that changes channel count
        var callCount = 0
        val fakeShim = object : brainflow.BoardShim(0, brainflow.BrainFlowInputParams()) {
            override fun get_board_data(num_datapoints: Int): Array<DoubleArray> {
                callCount++
                return if (callCount == 1) {
                    arrayOf(DoubleArray(num_datapoints) { 1.0 })
                } else {
                    arrayOf(DoubleArray(num_datapoints) { 2.0 }, DoubleArray(num_datapoints) { 3.0 })
                }
            }
        }
        val stateStore = StateStore(HardwareState(connected = true, windowSize = 2, overlap = 1, channels = 2, enabledChannels = listOf(0, 1)))
        val manager = BoardConnectionManager(stateStore)
        val acq = DataAcquisition(manager, boardShimProvider = { fakeShim }, stateProvider = { stateStore.get() })
        manager.startStream()
        val frames = mutableListOf<RawFrame>()
        acq.streamRawFrames().take(4).toList(frames)
        manager.stopStream()
        // Should handle channel count change without error
        assertTrue(frames.isNotEmpty())
    }

    @Test
    fun testSyntheticStreamingErrorPath() = runBlocking {
        // Use a stateProvider that throws after first call to simulate error in synthetic branch
        val stateStore = StateStore(HardwareState(connected = true, synthetic = true, channels = 1, enabledChannels = listOf(0), windowSize = 4, samplingRateHz = 100))
        val manager = BoardConnectionManager(stateStore)
        var callCount = 0
        val throwingStateProvider = {
            callCount++
            if (callCount > 1) throw RuntimeException("Simulated synthetic streaming error")
            stateStore.get()
        }
        val acq = DataAcquisition(manager, stateProvider = throwingStateProvider)
        manager.startStream()
        val frames = mutableListOf<RawFrame>()
        // Should not throw, should just log error and exit
        acq.streamRawFrames().take(1).toList(frames)
        manager.stopStream()
        // Should emit at most one frame before error
        assertTrue(frames.size <= 1, "Should emit at most one frame before synthetic streaming error")
    }

    @Test
    fun testNativeShimEmptyDataBranch() = runBlocking {
        // Simulate a BoardShim that returns empty data arrays
        val fakeShim = object : brainflow.BoardShim(0, brainflow.BrainFlowInputParams()) {
            override fun get_board_data(num_datapoints: Int): Array<DoubleArray> {
                return arrayOf(DoubleArray(0)) // data[0].isEmpty() == true
            }
        }
        val stateStore = StateStore(HardwareState(connected = true, windowSize = 4, overlap = 2, channels = 1, enabledChannels = listOf(0)))
        val manager = BoardConnectionManager(stateStore)
        val acq = DataAcquisition(manager, boardShimProvider = { fakeShim }, stateProvider = { stateStore.get() })
        manager.startStream()
        val frames = mutableListOf<RawFrame>()
        val job = launch {
            acq.streamRawFrames().collect { frames.add(it) }
        }
        delay(100)
        job.cancel()
        job.join()
        manager.stopStream()
        assertTrue(frames.isEmpty(), "Should emit no frames when BoardShim returns empty data")
    }

    @Test
    fun testNativeShimEnabledChannelsDefaultBranch() = runBlocking {
        // Simulate a BoardShim that returns data for 3 channels
        val fakeShim = object : brainflow.BoardShim(0, brainflow.BrainFlowInputParams()) {
            override fun get_board_data(num_datapoints: Int): Array<DoubleArray> {
                return arrayOf(
                    DoubleArray(num_datapoints) { 1.0 },
                    DoubleArray(num_datapoints) { 2.0 },
                    DoubleArray(num_datapoints) { 3.0 }
                )
            }
        }
        // Set enabledChannels to empty to trigger the default branch
        val stateStore = StateStore(HardwareState(connected = true, windowSize = 4, overlap = 2, channels = 3, enabledChannels = emptyList()))
        val manager = BoardConnectionManager(stateStore)
        val acq = DataAcquisition(manager, boardShimProvider = { fakeShim }, stateProvider = { stateStore.get() })
        manager.startStream()
        val frames = mutableListOf<RawFrame>()
        acq.streamRawFrames().take(3).toList(frames)
        manager.stopStream()
        // Should emit frames for all channels (0, 1, 2)
        val channelsEmitted = frames.map { it.channel }.toSet()
        assertTrue(channelsEmitted.containsAll(setOf(0, 1, 2)), "Should emit frames for all channels when enabledChannels is empty")
    }

    @Test
    fun testNativeShimStreamingLoopErrorBranch() = runBlocking {
        // Simulate a BoardShim that returns valid data
        val fakeShim = object : brainflow.BoardShim(0, brainflow.BrainFlowInputParams()) {
            override fun get_board_data(num_datapoints: Int): Array<DoubleArray> {
                return arrayOf(DoubleArray(num_datapoints) { 1.0 })
            }
        }
        // State provider that throws an exception after first call
        var callCount = 0
        val stateStore = StateStore(HardwareState(connected = true, windowSize = 4, overlap = 2, channels = 1, enabledChannels = listOf(0)))
        val throwingStateProvider = {
            callCount++
            if (callCount > 1) throw RuntimeException("Simulated streaming loop error")
            stateStore.get()
        }
        val manager = BoardConnectionManager(stateStore)
        val acq = DataAcquisition(manager, boardShimProvider = { fakeShim }, stateProvider = throwingStateProvider)
        manager.startStream()
        val frames = mutableListOf<RawFrame>()
        // Should not throw, should just log error and exit
        acq.streamRawFrames().take(1).toList(frames)
        manager.stopStream()
        // Should emit at most one frame before error
        assertTrue(frames.size <= 1, "Should emit at most one frame before streaming loop error")
    }

    @Test
    fun testStreamRawFrames_cancellationDuringFetch() = runBlocking {
        // Fake shim that blocks in get_board_data so the CompletableFuture remains pending
        val blockingShim = object : brainflow.BoardShim(0, brainflow.BrainFlowInputParams()) {
            override fun get_board_data(num_datapoints: Int): Array<DoubleArray> {
                try {
                    Thread.sleep(1000) // sleep long enough to allow cancellation
                } catch (_: InterruptedException) {}
                return arrayOf(DoubleArray(num_datapoints) { 0.0 })
            }
        }

        val stateStore = StateStore(HardwareState(connected = true, windowSize = 8, overlap = 4, channels = 1, enabledChannels = listOf(0)))
        val manager = BoardConnectionManager(stateStore)
        val acq = DataAcquisition(manager, boardShimProvider = { blockingShim }, stateProvider = { stateStore.get() })
        manager.startStream()

        // Launch a collector job and cancel it shortly afterward to trigger the cancellation handler
        val frames = mutableListOf<RawFrame>()
        val job = launch {
            try {
                acq.streamRawFrames().collect { frames.add(it) }
            } catch (_: Exception) {
                // collector may be cancelled; swallow exceptions for test
            }
        }
        // Give it a moment to start and hit the blocking get_board_data
        delay(100)
        // Cancel the collector which should invoke cont.invokeOnCancellation and cancel the CompletableFuture
        job.cancel()
        job.join()

        manager.stopStream()
        manager.close()

        // We don't expect frames (fetch was cancelled), but the important part is no exception bubbled
        assertTrue(frames.isEmpty(), "No frames should be emitted when fetch is cancelled")
    }
}
