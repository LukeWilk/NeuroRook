package io.github.lukewilk.hardware
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
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
}
