package io.github.lukewilk.hardware.pipeline

import brainflow.BrainFlowInputParams
import brainflow.BoardShim
import io.github.lukewilk.hardware.RawFrame
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Native and injected-shim acquisition tests for `DataAcquisition`.
 */
class DataAcquisitionNativeShimTest : DataAcquisitionTestSupport() {

    /** Verifies the native fetch loop exits immediately when a shim exists but streaming is already inactive. */
    @Test
    fun `native stream raw frames returns empty when streaming is inactive at entry`() = runBlocking {
        val fakeShim = object : BoardShim(0, BrainFlowInputParams()) {
            override fun get_board_data(num_datapoints: Int): Array<DoubleArray> = arrayOf(DoubleArray(num_datapoints) { 1.0 })
        }
        val localStateStore = StateStore(HardwareState(connected = true, synthetic = false, windowSize = 4, overlap = 2, channels = 1, enabledChannels = listOf(0)))
        val localManager = io.github.lukewilk.hardware.BoardConnectionManager(localStateStore)
        val localAcquisition = DataAcquisition(
            localManager,
            boardShimProvider = { fakeShim },
            stateProvider = { localStateStore.get() }
        )

        val items = localAcquisition.streamRawFrames().take(1).toList()

        assertTrue(items.isEmpty(), "Native acquisition should exit immediately when no active stream is running")
    }

    /** Verifies the injected native shim path preserves expected overlap between emitted windows. */
    @Test
    fun `stream raw frames native shim path preserves overlap`() = runBlocking {
        val fakeShim = object : BoardShim(0, BrainFlowInputParams()) {
            override fun get_board_data(num_datapoints: Int): Array<DoubleArray> {
                return arrayOf(
                    DoubleArray(num_datapoints) { it.toDouble() },
                    DoubleArray(num_datapoints) { (it + 100).toDouble() }
                )
            }
        }
        val localStateStore = StateStore(HardwareState(connected = true, windowSize = 4, overlap = 2, channels = 2, enabledChannels = listOf(0, 1)))
        val localManager = io.github.lukewilk.hardware.BoardConnectionManager(localStateStore)
        val localAcquisition = DataAcquisition(
            localManager,
            boardShimProvider = { fakeShim },
            stateProvider = { localStateStore.get() }
        )

        localManager.startStream()
        val frames = mutableListOf<RawFrame>()
        localAcquisition.streamRawFrames().take(3).toList(frames)
        localManager.stopStream()

        assertTrue(frames.isNotEmpty())
        assertTrue(frames.all { it.data.size == 4 })
        val framesByChannel = frames.groupBy { it.channel }
        val overlapSize = 2
        for ((channel, channelFrames) in framesByChannel) {
            if (channelFrames.size >= 2) {
                for (index in 0 until channelFrames.size - 1) {
                    val overlap = channelFrames[index].data.takeLast(overlapSize)
                    val nextStart = channelFrames[index + 1].data.take(overlapSize)
                    overlap.zip(nextStart).forEach { (a, b) ->
                        assertTrue(abs(a - b) < 1e-9, "Channel $channel overlap values should match: $a vs $b")
                    }
                }
            }
        }
    }

    /** Verifies acquisition tolerates channel-count changes between successive native fetches. */
    @Test
    fun `stream raw frames handles channel count changes`() = runBlocking {
        var callCount = 0
        val fakeShim = object : BoardShim(0, BrainFlowInputParams()) {
            override fun get_board_data(num_datapoints: Int): Array<DoubleArray> {
                callCount++
                return if (callCount == 1) {
                    arrayOf(DoubleArray(num_datapoints) { 1.0 })
                } else {
                    arrayOf(DoubleArray(num_datapoints) { 2.0 }, DoubleArray(num_datapoints) { 3.0 })
                }
            }
        }
        val localStateStore = StateStore(HardwareState(connected = true, windowSize = 2, overlap = 1, channels = 2, enabledChannels = listOf(0, 1)))
        val localManager = io.github.lukewilk.hardware.BoardConnectionManager(localStateStore)
        val localAcquisition = DataAcquisition(
            localManager,
            boardShimProvider = { fakeShim },
            stateProvider = { localStateStore.get() }
        )

        localManager.startStream()
        val frames = mutableListOf<RawFrame>()
        localAcquisition.streamRawFrames().take(4).toList(frames)
        localManager.stopStream()

        assertTrue(frames.isNotEmpty(), "Native acquisition should keep emitting even when the returned channel count changes")
    }

    /** Verifies native acquisition skips iterations when the shim returns empty inner channel data. */
    @Test
    fun `native shim empty data branch emits no frames`() = runBlocking {
        val fakeShim = object : BoardShim(0, BrainFlowInputParams()) {
            override fun get_board_data(num_datapoints: Int): Array<DoubleArray> = arrayOf(DoubleArray(0))
        }
        val localStateStore = StateStore(HardwareState(connected = true, windowSize = 4, overlap = 2, channels = 1, enabledChannels = listOf(0)))
        val localManager = io.github.lukewilk.hardware.BoardConnectionManager(localStateStore)
        val localAcquisition = DataAcquisition(
            localManager,
            boardShimProvider = { fakeShim },
            stateProvider = { localStateStore.get() }
        )

        localManager.startStream()
        val frames = mutableListOf<RawFrame>()
        val job = launch { localAcquisition.streamRawFrames().collect { frames.add(it) } }
        delay(100)
        job.cancel()
        job.join()
        localManager.stopStream()

        assertTrue(frames.isEmpty(), "Should emit no frames when BoardShim returns empty data")
    }

    /** Verifies native acquisition skips iterations when the shim returns no channels at all. */
    @Test
    fun `native shim empty outer array branch emits no frames`() = runBlocking {
        val fakeShim = object : BoardShim(0, BrainFlowInputParams()) {
            override fun get_board_data(num_datapoints: Int): Array<DoubleArray> = emptyArray()
        }
        val localStateStore = StateStore(HardwareState(connected = true, windowSize = 4, overlap = 2, channels = 1, enabledChannels = listOf(0)))
        val localManager = io.github.lukewilk.hardware.BoardConnectionManager(localStateStore)
        val localAcquisition = DataAcquisition(
            localManager,
            boardShimProvider = { fakeShim },
            stateProvider = { localStateStore.get() }
        )

        localManager.startStream()
        val frames = mutableListOf<RawFrame>()
        val job = launch { localAcquisition.streamRawFrames().collect { frames.add(it) } }
        delay(100)
        job.cancel()
        job.join()
        localManager.stopStream()

        assertTrue(frames.isEmpty(), "Should emit no frames when BoardShim returns an empty outer array")
    }

    /** Verifies only valid native enabled-channel indexes are emitted from the returned data block. */
    @Test
    fun `native shim skips invalid enabled channels`() = runBlocking {
        val fakeShim = object : BoardShim(0, BrainFlowInputParams()) {
            override fun get_board_data(num_datapoints: Int): Array<DoubleArray> {
                return arrayOf(DoubleArray(num_datapoints) { 1.0 }, DoubleArray(num_datapoints) { 2.0 })
            }
        }
        val localStateStore = StateStore(HardwareState(connected = true, windowSize = 4, overlap = 2, channels = 2, enabledChannels = listOf(-1, 1, 5)))
        val localManager = io.github.lukewilk.hardware.BoardConnectionManager(localStateStore)
        val localAcquisition = DataAcquisition(
            localManager,
            boardShimProvider = { fakeShim },
            stateProvider = { localStateStore.get() }
        )

        localManager.startStream()
        val frames = localAcquisition.streamRawFrames().take(2).toList()
        localManager.stopStream()

        assertTrue(frames.isNotEmpty())
        assertTrue(frames.all { it.channel == 1 }, "Only valid enabled channels should produce frames")
    }

    /** Verifies native acquisition defaults to all returned channels when enabledChannels is empty. */
    @Test
    fun `native shim enabled channels default branch emits all channels`() = runBlocking {
        val fakeShim = object : BoardShim(0, BrainFlowInputParams()) {
            override fun get_board_data(num_datapoints: Int): Array<DoubleArray> {
                return arrayOf(
                    DoubleArray(num_datapoints) { 1.0 },
                    DoubleArray(num_datapoints) { 2.0 },
                    DoubleArray(num_datapoints) { 3.0 }
                )
            }
        }
        val localStateStore = StateStore(HardwareState(connected = true, windowSize = 4, overlap = 2, channels = 3, enabledChannels = emptyList()))
        val localManager = io.github.lukewilk.hardware.BoardConnectionManager(localStateStore)
        val localAcquisition = DataAcquisition(
            localManager,
            boardShimProvider = { fakeShim },
            stateProvider = { localStateStore.get() }
        )

        localManager.startStream()
        val frames = mutableListOf<RawFrame>()
        localAcquisition.streamRawFrames().take(3).toList(frames)
        localManager.stopStream()

        val channelsEmitted = frames.map { it.channel }.toSet()
        assertTrue(channelsEmitted.containsAll(setOf(0, 1, 2)), "Should emit frames for all channels when enabledChannels is empty")
    }

    /** Verifies native acquisition skips a ready buffered window once streaming stops after a fetch. */
    @Test
    fun `native streaming stops before buffered window emission`() = runBlocking {
        lateinit var localManager: io.github.lukewilk.hardware.BoardConnectionManager
        val fakeShim = object : BoardShim(0, BrainFlowInputParams()) {
            override fun get_board_data(num_datapoints: Int): Array<DoubleArray> {
                localManager.stopStream()
                return arrayOf(DoubleArray(num_datapoints) { 1.0 })
            }
        }
        val localStateStore = StateStore(HardwareState(connected = true, synthetic = false, windowSize = 4, overlap = 2, channels = 1, enabledChannels = listOf(0)))
        localManager = io.github.lukewilk.hardware.BoardConnectionManager(localStateStore)
        val localAcquisition = DataAcquisition(
            localManager,
            boardShimProvider = { fakeShim },
            stateProvider = { localStateStore.get() }
        )

        localManager.startStream()
        val frames = localAcquisition.streamRawFrames().take(1).toList()

        assertTrue(frames.isEmpty(), "Native acquisition should skip buffered window emission once streaming has already been stopped")
    }

    /** Verifies native streaming loop errors are swallowed after at most one emitted frame. */
    @Test
    fun `native shim streaming loop error branch exits quietly`() = runBlocking {
        val fakeShim = object : BoardShim(0, BrainFlowInputParams()) {
            override fun get_board_data(num_datapoints: Int): Array<DoubleArray> = arrayOf(DoubleArray(num_datapoints) { 1.0 })
        }
        var callCount = 0
        val localStateStore = StateStore(HardwareState(connected = true, windowSize = 4, overlap = 2, channels = 1, enabledChannels = listOf(0)))
        val throwingStateProvider = {
            callCount++
            if (callCount > 1) throw RuntimeException("Simulated streaming loop error")
            localStateStore.get()
        }
        val localManager = io.github.lukewilk.hardware.BoardConnectionManager(localStateStore)
        val localAcquisition = DataAcquisition(
            localManager,
            boardShimProvider = { fakeShim },
            stateProvider = throwingStateProvider
        )

        localManager.startStream()
        val frames = mutableListOf<RawFrame>()
        localAcquisition.streamRawFrames().take(1).toList(frames)
        localManager.stopStream()

        assertTrue(frames.size <= 1, "Should emit at most one frame before streaming loop error")
    }

    /** Verifies cancelling the collector during a blocking native fetch exits without leaking frames or exceptions. */
    @Test
    fun `stream raw frames cancellation during fetch exits cleanly`() = runBlocking {
        val blockingShim = object : BoardShim(0, BrainFlowInputParams()) {
            override fun get_board_data(num_datapoints: Int): Array<DoubleArray> {
                try {
                    Thread.sleep(1000)
                } catch (_: InterruptedException) {
                }
                return arrayOf(DoubleArray(num_datapoints) { 0.0 })
            }
        }
        val localStateStore = StateStore(HardwareState(connected = true, windowSize = 8, overlap = 4, channels = 1, enabledChannels = listOf(0)))
        val localManager = io.github.lukewilk.hardware.BoardConnectionManager(localStateStore)
        val localAcquisition = DataAcquisition(
            localManager,
            boardShimProvider = { blockingShim },
            stateProvider = { localStateStore.get() }
        )

        localManager.startStream()
        val frames = mutableListOf<RawFrame>()
        val job = launch {
            try {
                localAcquisition.streamRawFrames().collect { frames.add(it) }
            } catch (_: Exception) {
                // The collector may be cancelled; the test only cares that cancellation stays contained.
            }
        }
        delay(100)
        job.cancel()
        job.join()

        localManager.stopStream()
        localManager.close()

        assertTrue(frames.isEmpty(), "No frames should be emitted when fetch is cancelled")
    }
}
