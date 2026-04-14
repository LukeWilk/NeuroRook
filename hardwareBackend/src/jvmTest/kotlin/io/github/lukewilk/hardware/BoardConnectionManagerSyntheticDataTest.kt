package io.github.lukewilk.hardware
import brainflow.BoardIds
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import io.github.lukewilk.shared.SyntheticMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
/**
 * Synthetic data generation and synthetic-board shim tests for `BoardConnectionManager`.
 */
internal class BoardConnectionManagerSyntheticDataTest : BoardConnectionManagerSyntheticTestSupport() {
    /** Verifies synthetic data generation changes with the configured synthetic mode. */
    @Test
    fun `generate synthetic data respects the configured synthetic mode`() {
        val generatorManager = syntheticManager(
            syntheticState(channels = 1, syntheticMode = SyntheticMode.WAVE_GENERATOR)
        )
        val generated = generatorManager.generateSyntheticData(16)
        assertTrue(generated[0].any { it != 0.0 })
        generatorManager.setSyntheticMode(SyntheticMode.SYNTHETIC_EEG_SIGNAL)
        val zeros = generatorManager.generateSyntheticData(16)
        assertTrue(zeros[0].all { it == 0.0 })
    }
    /** Verifies the synthetic shim returns deterministic generated board data and a non-zero count. */
    @Test
    fun `synthetic board shim generates synthetic data`() {
        val syntheticManager = syntheticManager(syntheticState(channels = 2))
        assertTrue(syntheticManager.connect(BoardIds.SYNTHETIC_BOARD, serialPort = ""))
        val shim = syntheticManager.getBoardShim()!!
        val boardData = shim.get_board_data(4)
        val currentData = shim.get_current_board_data(4)
        assertEquals(2, boardData.size)
        assertTrue(boardData[0].any { it != 0.0 })
        assertTrue(boardData[1].all { it == 0.0 })
        assertEquals(4, currentData[0].size)
        assertEquals(syntheticManager.state.value.windowSize.coerceAtLeast(1), shim.get_board_data_count())
    }
    /** Verifies the synthetic shim coerces non-positive sample requests to a single sample. */
    @Test
    fun `synthetic board shim coerces non positive sample counts`() {
        val syntheticManager = syntheticManager(syntheticState(channels = 1))
        assertTrue(syntheticManager.connect(BoardIds.SYNTHETIC_BOARD, serialPort = ""))
        val shim = syntheticManager.getBoardShim()!!
        assertEquals(1, shim.get_board_data(0)[0].size)
        assertEquals(1, shim.get_current_board_data(-4)[0].size)
    }
    /** Verifies synthetic state alone is enough to force the synthetic channel count fallback. */
    @Test
    fun `get number of channels uses synthetic state without an explicit hint`() {
        val syntheticManager = BoardConnectionManager(StateStore(HardwareState(synthetic = true)))
        assertEquals(16, syntheticManager.getNumberOfChannels(BoardIds.SYNTHETIC_BOARD))
    }
    /** Verifies non-synthetic states return all-zero synthetic data buffers. */
    @Test
    fun `generate synthetic data returns zeros when the state is not synthetic`() {
        val zeroManager = BoardConnectionManager(
            StateStore(HardwareState(synthetic = false, syntheticMode = SyntheticMode.WAVE_GENERATOR, channels = 2))
        )
        val data = zeroManager.generateSyntheticData(8)
        assertEquals(2, data.size)
        assertTrue(data.all { row -> row.all { it == 0.0 } })
    }
}
