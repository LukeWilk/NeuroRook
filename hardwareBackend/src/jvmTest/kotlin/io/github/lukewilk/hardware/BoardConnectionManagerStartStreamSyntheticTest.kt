package io.github.lukewilk.hardware

import brainflow.BoardIds
import brainflow.BoardShim
import brainflow.BrainFlowInputParams
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import kotlin.test.Test
import kotlin.test.assertTrue
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.Mockito

class BoardConnectionManagerStartStreamSyntheticTest {
    private val stateStore = StateStore(HardwareState())

    @Test
    fun testStartStreamDoesNotCallNativeWhenSynthetic() {
        val mockShim = Mockito.mock(BoardShim::class.java)
        val manager = BoardConnectionManager(
            stateStore = stateStore,
            boardShimFactory = { _, _ -> mockShim },
            samplingRateProvider = { 128 }
        )

        // Connect as synthetic board
        val connected = manager.connect(BoardIds.SYNTHETIC_BOARD, serialPort = "")
        assertTrue(connected)

        // Starting stream in synthetic mode should not call native start_stream
        manager.startStream()
        assertTrue(manager.isStreaming())
        verify(mockShim, times(0)).start_stream(any(), any())

        manager.stopStream()
        // Ensure any registered streaming coroutine is fully cleaned up before the test exits
        manager.awaitRegisteredStreamingJobForTests()
    }
}

