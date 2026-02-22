package io.github.lukewilk.hardware

import brainflow.BoardIds
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val connector = BrainFlowHardwareConnector()
    val connected = connector.connect(boardId = BoardIds.NO_BOARD, serialPort = "")
    println("BrainFlow connect() result: $connected")
    connector.close()
}

