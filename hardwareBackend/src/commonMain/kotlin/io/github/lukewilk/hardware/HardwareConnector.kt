package io.github.lukewilk.hardware
import kotlinx.coroutines.flow.Flow
/**
 * RawFrame represents a single raw sample collected from the board.
 */
data class RawFrame(val timestampMs: Long, val data: ByteArray)
/**
 * High-level interface for a hardware backend that emits raw frames.
 */
interface HardwareConnector {
    fun streamRawFrames(): Flow<RawFrame>
    suspend fun isConnected(): Boolean
    suspend fun close()
}
