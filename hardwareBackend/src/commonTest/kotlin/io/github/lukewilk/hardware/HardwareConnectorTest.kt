package io.github.lukewilk.hardware

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
