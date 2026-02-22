package io.github.lukewilk.hardware

import brainflow.BoardIds
import brainflow.BoardShim
import brainflow.BrainFlowInputParams
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * JVM/Android BrainFlow connector: for now, just connects to the board using BrainFlow Java bindings.
 * This is a skeleton; real data streaming will be added later.
 */
class BrainFlowHardwareConnector : HardwareConnector {
    private val logger = Logger.withTag("BrainFlowHardwareConnector")
    private var connected = false
    private var boardShim: BoardShim? = null

    override fun streamRawFrames(): Flow<RawFrame> = emptyFlow() // Not implemented yet

    override suspend fun isConnected(): Boolean = connected

    override suspend fun close() {
        try {
            boardShim?.release_session()
        } catch (_: Exception) {}
        connected = false
    }

    /**
     * Connect to the board using BrainFlow Java API.
     * @param boardId The BrainFlow board ID (e.g., 0 for synthetic)
     * @param serialPort Serial port or other connection param (see BrainFlow docs)
     */
    fun connect(boardId: BoardIds, serialPort: String): Boolean {
        try {
            val params = BrainFlowInputParams()
            params.serial_port = serialPort
            logger.i { "Attempting to connect: boardId=$boardId, serialPort=$serialPort, params=$params" }

            boardShim = BoardShim(boardId, params)
            boardShim?.prepare_session()
            connected = true
            return true
        } catch (e: Exception) {
            logger.e(e) { "BrainFlow connection error on $serialPort: ${e.message}" }
            connected = false
        }
        return false
    }
}
