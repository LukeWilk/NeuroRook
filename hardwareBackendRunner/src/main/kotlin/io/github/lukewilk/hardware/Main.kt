package io.github.lukewilk.hardware

import co.touchlab.kermit.Logger
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) = runBlocking {
    val logger = Logger.withTag("Main")
    val boardId = when (args.firstOrNull()?.lowercase()) {
        "neuropawn", "knight" -> brainflow.BoardIds.NEUROPAWN_KNIGHT_BOARD
        else -> brainflow.BoardIds.SYNTHETIC_BOARD
    }
    val port = "/dev/ttyACM0"
    val boardName = if (boardId == brainflow.BoardIds.NEUROPAWN_KNIGHT_BOARD) "NEUROPAWN_KNIGHT_BOARD" else "SYNTHETIC_BOARD"
    logger.i { "Using board: $boardName on port: $port" }
    val connector = BrainFlowHardwareConnector()
    val connected = connector.connect(boardId = boardId, serialPort = port)
    logger.i { "BrainFlow connect() result: $connected" }
    connector.close()
}
