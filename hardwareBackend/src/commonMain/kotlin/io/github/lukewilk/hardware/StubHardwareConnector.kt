package io.github.lukewilk.hardware

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

class StubHardwareConnector(private val frameIntervalMs: Long = 50L) : HardwareConnector {
    private var connected: Boolean = true

    override fun streamRawFrames(): Flow<RawFrame> = flow {
        while (connected) {
            val payload = ByteArray(8) { Random.nextInt(0, 256).toByte() }
            // Use a placeholder timestamp in common code; platform implementations can provide real timestamps.
            emit(RawFrame(0L, payload))
            delay(frameIntervalMs)
        }
    }

    override suspend fun isConnected(): Boolean = connected

    override suspend fun close() {
        connected = false
    }
}
