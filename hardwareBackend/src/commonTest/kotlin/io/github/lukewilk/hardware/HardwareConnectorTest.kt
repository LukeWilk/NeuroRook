package io.github.lukewilk.hardware

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withTimeout
import kotlin.test.*

class HardwareConnectorTest {
    @Test
    fun stubEmitsFrame() = runBlocking {
        val connector = StubHardwareConnector(frameIntervalMs = 10)
        val frame = connector.streamRawFrames().first()
        assertNotNull(frame)
        assertTrue(frame.data.isNotEmpty())
        connector.close()
    }

    @Test
    fun stubIsConnectedReflectsState() = runBlocking {
        val connector = StubHardwareConnector()
        assertTrue(connector.isConnected(), "Should be connected initially")
        connector.close()
        assertFalse(connector.isConnected(), "Should not be connected after close()")
    }

    @Test
    fun stubStreamRawFramesEmitsMultipleFrames() = runBlocking {
        val connector = StubHardwareConnector(frameIntervalMs = 1)
        val frames = mutableListOf<RawFrame>()
        withTimeout(1000) {
            connector.streamRawFrames().take(3).collect { frames.add(it) }
        }
        connector.close()
        assertEquals(3, frames.size, "Should emit 3 frames before close")
    }
}


class StubHardwareConnector(private val frameIntervalMs: Long = 50L) : HardwareConnector {
    private var connected: Boolean = true

    override fun streamRawFrames(): Flow<RawFrame> = flow {
        while (connected) {
            val payload = DoubleArray(8) { Random.nextDouble(-1.0, 1.0) }
            // Use a placeholder timestamp in common code; platform implementations can provide real timestamps.
            emit(RawFrame(0L, 0,payload))
            delay(frameIntervalMs)
        }
    }

    override suspend fun isConnected(): Boolean = connected

    override suspend fun close() {
        connected = false
    }
}


