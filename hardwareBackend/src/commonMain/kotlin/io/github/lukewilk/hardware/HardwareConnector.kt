package io.github.lukewilk.hardware

import io.github.lukewilk.hardware.RawFrame
import kotlinx.coroutines.flow.Flow

/**
 * High-level interface for a hardware backend that emits raw frames.
 */
interface HardwareConnector {
    fun streamRawFrames(): Flow<RawFrame>
    suspend fun isConnected(): Boolean
    suspend fun close()
}
