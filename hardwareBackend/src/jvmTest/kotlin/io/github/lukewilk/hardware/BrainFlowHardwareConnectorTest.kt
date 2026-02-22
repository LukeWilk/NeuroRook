package io.github.lukewilk.hardware

import brainflow.BoardIds
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class BrainFlowHardwareConnectorTest {
    @Test
    fun testConnectReturnsBoolean() = runBlocking {
        val connector = BrainFlowHardwareConnector()
        val result: Boolean = connector.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")
        assertTrue(result, "Should connect() return Boolean and handle exceptions gracefully")
        println("BrainFlow connect() result: $result")
    }

    @Test
    fun testConnectSyntheticBoard() = runBlocking {
        val connector = BrainFlowHardwareConnector()
        val result = connector.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")
        assertTrue(result, "Should connect successfully to SYNTHETIC_BOARD")
        println("BrainFlow connect() to SYNTHETIC_BOARD result: $result")
    }
}
