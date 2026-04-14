package io.github.lukewilk.hardware

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.take
import kotlin.test.*

class HardwareConnectorTest {
    @Test
    fun `stub connector emits a deterministic first frame`() = runBlocking {
        // Verifies the stream starts with a predictable payload so downstream tests can assert exact values.
        val connector = StubHardwareConnector(frameIntervalMs = 10)
        val frame = connector.streamRawFrames().first()
        assertNotNull(frame)
        assertEquals(0L, frame.timestampMs)
        assertEquals(0, frame.channel)
        assertTrue(frame.data.contentEquals(doubleArrayOf(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0)))
        connector.close()
    }

    @Test
    fun `stub connector reports connection state before and after close`() = runBlocking {
        // Documents the lifecycle contract expected by callers that manage connector shutdown.
        val connector = StubHardwareConnector()
        assertTrue(connector.isConnected(), "Should be connected initially")
        connector.close()
        assertFalse(connector.isConnected(), "Should not be connected after close()")
    }

    @Test
    fun `stub connector emits successive frames with stable ordering`() = runBlocking {
        // Confirms collectors receive frames in sequence without relying on random payload generation.
        val connector = StubHardwareConnector(frameIntervalMs = 1)
        val frames = mutableListOf<RawFrame>()
        connector.streamRawFrames().take(3).collect { frames.add(it) }
        connector.close()
        assertEquals(3, frames.size, "Should emit 3 frames before close")
        assertEquals(listOf(0L, 1L, 2L), frames.map { it.timestampMs })
        assertEquals(listOf(0, 1, 2), frames.map { it.channel })
        assertTrue(frames[2].data.contentEquals(doubleArrayOf(2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0)))
    }
}


class StubHardwareConnector(private val frameIntervalMs: Long = 50L) : HardwareConnector {
    private var connected: Boolean = true
    private var emittedFrames: Long = 0

    override fun streamRawFrames(): Flow<RawFrame> = flow {
        while (connected) {
            // Keeps the test stub deterministic so failures point to behavior changes instead of random data.
            val payload = DoubleArray(8) { index -> emittedFrames.toDouble() + index }
            emit(RawFrame(emittedFrames, (emittedFrames % 8).toInt(), payload))
            emittedFrames += 1
            delay(frameIntervalMs)
        }
    }

    override suspend fun isConnected(): Boolean = connected

    override suspend fun close() {
        connected = false
    }
}


