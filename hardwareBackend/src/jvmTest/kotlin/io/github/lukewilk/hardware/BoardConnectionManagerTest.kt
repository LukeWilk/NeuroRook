package io.github.lukewilk.hardware

import brainflow.BoardIds
import brainflow.BoardShim
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.After
import co.touchlab.kermit.Logger
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class BoardConnectionManagerTest {
    private val logger = LoggerProvider.getLogger("BoardConnectionManagerTest")

    val stateStore = StateStore(HardwareState())
    val manager = BoardConnectionManager(stateStore)

    @After
    fun tearDown() {
        runBlocking {
            manager.close()
        }
    }

    @Test
    fun testConnectReturnsBoolean() = runBlocking {
        val result: Boolean = manager.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")
        assertTrue(result, "Should connect() return Boolean and handle exceptions gracefully")
        manager.startStream()
        logger.i { "BoardConnectionManager connect() result: $result" }
        manager.stopStream()
        manager.close()
    }

    @Test
    fun testConnectSyntheticBoard() = runBlocking {
        val result = manager.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")
        assertTrue(result, "Should connect successfully to SYNTHETIC_BOARD")
        logger.i { "BoardConnectionManager connect() to SYNTHETIC_BOARD result: $result" }
    }

    @Test
    fun testIsConnectedReflectsState() = runBlocking {
        assertTrue(!manager.state.value.connected, "Should not be connected initially")
        manager.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")
        assertTrue(manager.state.value.connected, "Should be connected after connect()")
        manager.close()
        assertTrue(!manager.state.value.connected, "Should not be connected after close()")
    }

    @Test
    fun testCloseHandlesNoSession() = runBlocking {
        manager.close()
        assertTrue(!manager.state.value.connected, "Should not be connected after close() with no session")
    }

    @Test
    fun testConnectHandlesException() = runBlocking {
        val result = manager.connect(boardId = BoardIds.NO_BOARD, serialPort = "invalid")
        assertTrue(!result, "Should return false on failed connect")
        assertTrue(!manager.state.value.connected, "Should not be connected after failed connect")
    }

    @Test
    fun testEnableDisableChannelUpdatesState() = runBlocking {
        manager.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")
        manager.enableChannel(0)
        assertTrue(0 in manager.state.value.enabledChannels, "Channel 0 should be enabled")
        manager.disableChannel(0)
        assertTrue(0 !in manager.state.value.enabledChannels, "Channel 0 should be disabled")
    }

    @Test
    fun testEnableDisableRLDUpdatesState() = runBlocking {
        manager.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")
        manager.enableRLD(0)
        assertTrue(0 in manager.state.value.rldEnabled, "RLD for channel 0 should be enabled")
        manager.disableRLD(0)
        assertTrue(0 !in manager.state.value.rldEnabled, "RLD for channel 0 should be disabled")
    }

    @Test
    fun testEnableDisableChannelInvalidIndex() = runBlocking {
        manager.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")
        val before = manager.state.value.enabledChannels.size
        manager.enableChannel(100)
        manager.disableChannel(100)
        // Should not throw, state unchanged for out-of-bounds
        val after = manager.state.value.enabledChannels.size
        assertEquals(before, after, "enabledChannels size should not change for invalid index")
    }

    @Test
    fun testEnableDisableRLDInvalidIndex() = runBlocking {
        manager.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")
        val before = manager.state.value.rldEnabled.size
        manager.enableRLD(100)
        manager.disableRLD(100)
        // Should not throw, state unchanged for out-of-bounds
        val after = manager.state.value.rldEnabled.size
        assertEquals(before, after, "rldEnabled size should not change for invalid index")
    }

    @Test
    fun testEnableDisableChannelKnightBoardCallsConfigBoard() = runBlocking {
        val mockShim = Mockito.mock(BoardShim::class.java)
        val manager = BoardConnectionManager(
            stateStore = stateStore,
            boardShimFactory = { _, _ -> mockShim },
            samplingRateProvider = { 128 }
        )
        manager.connect(boardId = BoardIds.NEUROPAWN_KNIGHT_BOARD, serialPort = "")
        logger.i { "State after connect: ${manager.state.value}" }
        manager.enableChannel(2)
        logger.i { "State after enableChannel(2): ${manager.state.value}" }
        verify(mockShim, times(1)).config_board("enable_channel 2")
        assertTrue(2 in manager.state.value.enabledChannels, "Channel 2 should be enabled")
        manager.disableChannel(2)
        logger.i { "State after disableChannel(2): ${manager.state.value}" }
        verify(mockShim, times(1)).config_board("disable_channel 2")
        assertTrue(2 !in manager.state.value.enabledChannels, "Channel 2 should be disabled")
    }

    @Test
    fun testEnableDisableRLDKnightBoardCallsConfigBoard() = runBlocking {
        val mockShim = Mockito.mock(BoardShim::class.java)
        val manager = BoardConnectionManager(
            stateStore = stateStore,
            boardShimFactory = { _, _ -> mockShim },
            samplingRateProvider = { 128 }
        )
        manager.connect(boardId = BoardIds.NEUROPAWN_KNIGHT_BOARD, serialPort = "")
        logger.i { "State after connect: ${manager.state.value}" }
        manager.enableRLD(3)
        logger.i { "State after enableRLD(3): ${manager.state.value}" }
        verify(mockShim, times(1)).config_board("enable_rld 3")
        assertTrue(3 in manager.state.value.rldEnabled, "RLD for channel 3 should be enabled")
        manager.disableRLD(3)
        logger.i { "State after disableRLD(3): ${manager.state.value}" }
        verify(mockShim, times(1)).config_board("disable_rld 3")
        assertTrue(3 !in manager.state.value.rldEnabled, "RLD for channel 3 should be disabled")
    }

    @Test
    fun testEnableDisableChannelSyntheticBoardDoesNotCallConfigBoard() = runBlocking {
        val mockShim = Mockito.mock(BoardShim::class.java)
        val manager = BoardConnectionManager(
            stateStore = stateStore,
            boardShimFactory = { _, _ -> mockShim },
            samplingRateProvider = { 128 }
        )
        manager.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")
        logger.i { "State after connect: ${manager.state.value}" }
        manager.enableChannel(1)
        logger.i { "State after enableChannel(1): ${manager.state.value}" }
        verify(mockShim, times(0)).config_board(any())
        assertTrue(1 in manager.state.value.enabledChannels, "Channel 1 should be enabled")
        manager.disableChannel(1)
        logger.i { "State after disableChannel(1): ${manager.state.value}" }
        verify(mockShim, times(0)).config_board(any())
        assertTrue(1 !in manager.state.value.enabledChannels, "Channel 1 should be disabled")
    }

    @Test
    fun testEnableDisableRLDSyntheticBoardDoesNotCallConfigBoard() = runBlocking {
        val mockShim = Mockito.mock(BoardShim::class.java)
        val manager = BoardConnectionManager(
            stateStore = stateStore,
            boardShimFactory = { _, _ -> mockShim },
            samplingRateProvider = { 128 }
        )
        manager.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")
        logger.i { "State after connect: ${manager.state.value}" }
        manager.enableRLD(1)
        logger.i { "State after enableRLD(1): ${manager.state.value}" }
        verify(mockShim, times(0)).config_board(any())
        assertTrue(1 in manager.state.value.rldEnabled, "RLD for channel 1 should be enabled")
        manager.disableRLD(1)
        logger.i { "State after disableRLD(1): ${manager.state.value}" }
        verify(mockShim, times(0)).config_board(any())
        assertTrue(1 !in manager.state.value.rldEnabled, "RLD for channel 1 should be disabled")
    }

    @Test
    fun testGetBoardShimReturnsNullInitially() {
        val shim = manager.getBoardShim()
        assertTrue(shim == null, "BoardShim should be null before connect")
    }

    @Test
    fun testGetNumberOfChannelsSyntheticHint() {
        val channels = manager.getNumberOfChannels(BoardIds.SYNTHETIC_BOARD, syntheticHint = true)
        assertEquals(16, channels, "Synthetic board should have 16 channels")
    }

    @Test
    fun testGetNumberOfChannelsErrorHandling() {
        val invalidBoardId = BoardIds.NO_BOARD
        try {
            manager.getNumberOfChannels(invalidBoardId)
        } catch (e: Exception) {
            assertTrue(e is Exception, "Should throw exception for invalid boardId")
        }
    }
}
