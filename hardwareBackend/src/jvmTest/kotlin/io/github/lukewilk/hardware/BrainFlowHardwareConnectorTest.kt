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
}
