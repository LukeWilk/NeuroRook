package io.github.lukewilk.hardware

import brainflow.BoardIds
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
        assertTrue(result, "Should connect() return Boolean and handle exceptions gracefully")
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
        assertTrue(!connector.isConnected(), "Should not be connected initially")
        // Connect
        connector.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")
        assertTrue(connector.isConnected(), "Should be connected after connect()")
        // Close
        connector.close()
        assertTrue(!connector.isConnected(), "Should not be connected after close()")
    }

    @Test
    fun testCloseHandlesNoSession() = runBlocking {
        // Should not throw if close is called before connect
        connector.close()
        assertTrue(!connector.isConnected(), "Should not be connected after close() with no session")
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
        assertTrue(!result, "Should return false on failed connect")
        assertTrue(!connector.isConnected(), "Should not be connected after failed connect")
    }

    @Test
    fun testConnectHandlesNullBoardShim() = runBlocking {
        // Simulate boardShim being null and ensure no exception is thrown
        // This is already the default state before connect, so just call close
        connector.close() // Should not throw, should set connected = false
        assertTrue(!connector.isConnected(), "Should not be connected if boardShim is null and close() is called")
    }
}
