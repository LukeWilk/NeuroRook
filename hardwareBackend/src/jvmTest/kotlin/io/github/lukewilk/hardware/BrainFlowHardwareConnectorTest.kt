package io.github.lukewilk.hardware

import brainflow.BoardIds
import brainflow.BoardShim
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import co.touchlab.kermit.Logger

class BrainFlowHardwareConnectorTest {
    private val logger = Logger.withTag("BrainFlowHardwareConnectorTest")
    val connector = BrainFlowHardwareConnector()

    // Runs after each test for teardown logic
    @After
    fun tearDown() {
        runBlocking {
            connector.close()
        }
    }

    @Test
    fun testConnectReturnsBoolean() = runBlocking {
        val result: Boolean = connector.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")
        assertTrue(
            actual = result,
            message = "Should connect() return Boolean and handle exceptions gracefully"
        )
        logger.i { "BrainFlow connect() result: $result" }
    }

    @Test
    fun testConnectSyntheticBoard() = runBlocking {
        val result = connector.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")
        assertTrue(result, "Should connect successfully to SYNTHETIC_BOARD")
        logger.i { "BrainFlow connect() to SYNTHETIC_BOARD result: $result" }
    }

    @Test
    fun testIsConnectedReflectsState() = runBlocking {
        // Initially not connected
        assertTrue(
            actual = !connector.isConnected(),
            message = "Should not be connected initially")
        // Connect
        connector.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")
        assertTrue(
            actual = connector.isConnected(),
            message = "Should be connected after connect()"
        )
        // Close
        connector.close()
        assertTrue(
            actual = !connector.isConnected(),
            message = "Should not be connected after close()"
        )
    }

    @Test
    fun testCloseHandlesNoSession() = runBlocking {
        // Should not throw if close is called before connect
        connector.close()
        assertTrue(
            actual = !connector.isConnected(),
            message = "Should not be connected after close() with no session"
        )
    }

    @Test
    fun testStreamRawFramesReturnsEmptyFlow() {
        val flow = connector.streamRawFrames()
        // Should be empty
        assert(flow === kotlinx.coroutines.flow.emptyFlow<RawFrame>())
    }

    @Test
    fun testConnectHandlesException() = runBlocking {
        // Use an invalid board id to force an exception
        val result = connector.connect(boardId = BoardIds.NO_BOARD, serialPort = "invalid")
        assertTrue(
            actual = !result,
            message = "Should return false on failed connect")
        assertTrue(
            actual = !connector.isConnected(),
            message = "Should not be connected after failed connect"
        )
    }

    @Test
    fun testConnectHandlesNullBoardShim() = runBlocking {
        // Simulate boardShim being null and ensure no exception is thrown
        // This is already the default state before connect, so just call close
        connector.close() // Should not throw, should set connected = false
        assertTrue(
            actual = !connector.isConnected(),
            message = "Should not be connected if boardShim is null and close() is called"
        )
    }

    @Test
    fun testSyntheticBoardReflectsState() = runBlocking {
        val mockConnector = BrainFlowHardwareConnector(
            boardShimFactory = { boardId, params -> BoardShim(boardId, params) },
            samplingRateProvider = {
                boardId -> if (boardId == BoardIds.SYNTHETIC_BOARD)
                    BoardShim.get_sampling_rate(boardId) else 256
            }
        )
        connector.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")
        assertTrue(
            actual = connector.state.value.synthetic,
            message = "State should reflect synthetic board connection"
        )
        connector.close()
    }

    @Test
    fun testHardwareBoardReflectsState() = runBlocking {
        val mockConnector = BrainFlowHardwareConnector(
            boardShimFactory = { boardId, params -> BoardShim(boardId, params) },
            samplingRateProvider = {
                boardId -> if (boardId == BoardIds.SYNTHETIC_BOARD)
                    BoardShim.get_sampling_rate(boardId) else 256
            }
        )
        mockConnector.connect(boardId = BoardIds.NEUROPAWN_KNIGHT_BOARD, serialPort = "")
        assertTrue(
            actual = !mockConnector.state.value.synthetic,
            message = "State should reflect hardware board connection"
        )
        mockConnector.close()
    }

    @Test
    fun testSyntheticBoardSamplingRate() = runBlocking {
        connector.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")
        assertTrue(
            actual = connector.state.value.samplingRateHz == 250,
            message = "Synthetic board should have 250Hz sampling rate"
        )
        connector.close()
    }

    @Test
    fun testHardwareBoardSamplingRateMocked() = runBlocking {
        class MockBoardShim : BoardShim(BoardIds.NEUROPAWN_KNIGHT_BOARD, brainflow.BrainFlowInputParams()) {
            override fun prepare_session() { /* no-op */ }
        }
        val mockConnector = BrainFlowHardwareConnector(
            boardShimFactory = { boardId, params ->
                if (boardId == BoardIds.NEUROPAWN_KNIGHT_BOARD)
                    MockBoardShim()
                else
                    BoardShim(boardId, params) },
            samplingRateProvider = {
                boardId ->
                if (boardId == BoardIds.SYNTHETIC_BOARD)
                    BoardShim.get_sampling_rate(boardId)
                else 256
            }
        )
        mockConnector.connect(boardId = BoardIds.NEUROPAWN_KNIGHT_BOARD, serialPort = "")
        val hardwareRate = mockConnector.state.value.samplingRateHz
        assertTrue(
            actual = hardwareRate == 256,
            message = "Hardware board should have mocked 256Hz sampling rate, got $hardwareRate"
        )
        mockConnector.close()
    }
}
