package io.github.lukewilk.hardware

import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val connector = BrainFlowHardwareConnector()
    val connected = connector.connect(boardId = brainflow.BoardIds.NEUROPAWN_KNIGHT_BOARD, serialPort = "/dev/ttyACM0")
    println("BrainFlow connect() result: $connected")
    connector.close()
}

