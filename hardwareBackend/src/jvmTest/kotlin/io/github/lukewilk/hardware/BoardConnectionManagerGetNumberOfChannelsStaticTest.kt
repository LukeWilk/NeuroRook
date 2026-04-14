package io.github.lukewilk.hardware

import brainflow.BoardShim
import brainflow.BoardIds
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.mockito.Mockito

class BoardConnectionManagerGetNumberOfChannelsStaticTest {
    @Test
    fun testGetNumberOfChannelsUsesStaticMethodReturn() {
        val stateStore = StateStore(HardwareState(synthetic = false))
        val manager = BoardConnectionManager(stateStore)

        // Mock static BoardShim.get_eeg_channels to return 3 channels
        val ms = Mockito.mockStatic(BoardShim::class.java)
        ms.use { mockStatic ->
            mockStatic.`when`<IntArray> { BoardShim.get_eeg_channels(BoardIds.NEUROPAWN_KNIGHT_BOARD) }
                .thenReturn(intArrayOf(0, 1, 2))

            val channels = manager.getNumberOfChannels(BoardIds.NEUROPAWN_KNIGHT_BOARD, syntheticHint = false)
            assertEquals(3, channels)
        }
    }

    @Test
    fun testGetNumberOfChannelsFallsBackWhenNoChannels() {
        val stateStore = StateStore(HardwareState(synthetic = false))
        val manager = BoardConnectionManager(stateStore)

        val ms = Mockito.mockStatic(BoardShim::class.java)
        ms.use { mockStatic ->
            mockStatic.`when`<IntArray> { BoardShim.get_eeg_channels(BoardIds.NEUROPAWN_KNIGHT_BOARD) }
                .thenReturn(intArrayOf())

            val channels = manager.getNumberOfChannels(BoardIds.NEUROPAWN_KNIGHT_BOARD, syntheticHint = false)
            assertEquals(16, channels)
        }
    }

    @Test
    fun testGetNumberOfChannelsPropagatesException() {
        val stateStore = StateStore(HardwareState(synthetic = false))
        val manager = BoardConnectionManager(stateStore)

        val ms = Mockito.mockStatic(BoardShim::class.java)
        ms.use { mockStatic ->
            mockStatic.`when`<IntArray> { BoardShim.get_eeg_channels(BoardIds.NEUROPAWN_KNIGHT_BOARD) }
                .thenThrow(RuntimeException("boom"))

            assertFailsWith<RuntimeException> {
                manager.getNumberOfChannels(BoardIds.NEUROPAWN_KNIGHT_BOARD, syntheticHint = false)
            }
        }
    }
}

