package io.github.lukewilk.hardware

import brainflow.BoardIds
import brainflow.BoardShim
import brainflow.BrainFlowInputParams
import co.touchlab.kermit.Logger
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * HardwareConnector implementation using BrainFlow Java API.
 * This class manages the connection to a BrainFlow-compatible board and updates the hardware state accordingly.
 */
class BrainFlowHardwareConnector(
    private val boardShimFactory: (BoardIds, BrainFlowInputParams) -> BoardShim =
        { boardId, params -> BoardShim(boardId, params) },
    private val samplingRateProvider: (BoardIds) -> Int =
        { boardId -> BoardShim.get_sampling_rate(boardId) }
) : HardwareConnector {
    private val logger = Logger.withTag("BrainFlowHardwareConnector")
    private var boardShim: BoardShim? = null
    val stateStore = StateStore(HardwareState())
    val state = stateStore.state

    override fun streamRawFrames(): Flow<RawFrame> = emptyFlow() // Not implemented yet

    override suspend fun isConnected(): Boolean = state.value.connected
    override suspend fun close() {
        try {
            boardShim?.release_session()
        } catch (_: Exception) {}
        stateStore.update { it.copy(connected = false, synthetic = false, samplingRateHz = 0) }
    }

    /**
     * Connect to the board using BrainFlow Java API.
     * @param boardId Board ID from BrainFlow's BoardIds enum
     * @param serialPort Serial port or other connection param (see BrainFlow docs)
     */
    fun connect(boardId: BoardIds, serialPort: String): Boolean {
        try {
            val params = BrainFlowInputParams()
            params.serial_port = serialPort
            logger.i { "Attempting to connect: " +
                    "boardId=$boardId, " +
                    "serialPort=$serialPort, " +
                    "params=[serial_port=${params.serial_port}," +
                    " ip_address=${params.ip_address}," +
                    " mac_address=${params.mac_address}]" }
            boardShim = boardShimFactory(boardId, params)
            boardShim?.prepare_session()
            val isSynthetic = boardId == BoardIds.SYNTHETIC_BOARD
            val samplingRate = samplingRateProvider(boardId)
            stateStore.update { it.copy(
                connected = true,
                synthetic = isSynthetic,
                samplingRateHz = samplingRate
            ) }
            return true
        } catch (e: Exception) {
            logger.e(e) { "BrainFlow connection error on $serialPort: ${e.message}" }
            stateStore.update { it.copy(connected = false, synthetic = false, samplingRateHz = 0) }
        }
        return false
    }
}
