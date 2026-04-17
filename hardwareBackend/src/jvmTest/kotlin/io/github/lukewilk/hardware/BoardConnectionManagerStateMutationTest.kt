package io.github.lukewilk.hardware
import brainflow.BoardIds
import brainflow.BoardShim
import io.github.lukewilk.shared.Band
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.WaveType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.Mockito
import org.mockito.kotlin.any
/**
 * State-mutation and config-resilience tests for `BoardConnectionManager`.
 */
internal class BoardConnectionManagerStateMutationTest : BoardConnectionManagerSyntheticTestSupport() {
    @Test
    fun `state mutators update waves and trim oversized band collections`() {
        val manager = BoardConnectionManager(
            StateStore(
                HardwareState(
                    waveSpecs = listOf(
                        WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 8.0)
                    ),
                    bands = listOf(Band("A", 1.0, 2.0))
                )
            )
        )
        manager.setWaveSpec(
            0,
            enabled = false,
            type = WaveType.TRIANGLE,
            amplitude = 2.0,
            frequencyHz = 12.0,
            phaseShiftRad = 0.5
        )
        assertEquals(WaveType.TRIANGLE, manager.state.value.waveSpecs[0].type)
        assertFalse(manager.state.value.waveSpecs[0].enabled)
        val oversizedBands = (1..12).map { Band("Band$it", it.toDouble(), it + 1.0) }
        manager.setBands(oversizedBands)
        assertEquals(10, manager.state.value.bands.size)
        manager.addBand(Band("Extra", 20.0, 25.0))
        assertEquals(10, manager.state.value.bands.size)
        manager.removeBand("Band1")
        assertTrue(manager.state.value.bands.none { it.name == "Band1" })
    }
    @Test
    fun `real board config operations swallow config exceptions`() {
        val failingShim = Mockito.mock(BoardShim::class.java)
        Mockito.doThrow(RuntimeException("config failed")).`when`(failingShim).config_board(any())
        val failingManager = realBoardManager(boardShimFactory = { _, _ -> failingShim })
        connectRealBoard(failingManager, boardId = BoardIds.NEUROPAWN_KNIGHT_BOARD)
        failingManager.enableChannel(1)
        failingManager.disableChannel(1)
        failingManager.enableRLD(1)
        failingManager.disableRLD(1)
        assertTrue(failingManager.state.value.enabledChannels.isEmpty())
        assertTrue(failingManager.state.value.rldEnabled.isEmpty())
    }
    @Test
    fun `set wave spec ignores oversized indexes and set bands preserves valid collections`() {
        val manager = BoardConnectionManager(
            StateStore(
                HardwareState(
                    waveSpecs = listOf(
                        WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 8.0)
                    ),
                    bands = listOf(Band("Alpha", 8.0, 12.0))
                )
            )
        )
        val before = manager.state.value.waveSpecs
        manager.setWaveSpec(
            5,
            enabled = false,
            type = WaveType.NOISE,
            amplitude = 0.0,
            frequencyHz = 0.0,
            phaseShiftRad = 0.0
        )
        assertEquals(before, manager.state.value.waveSpecs)
        val bands = listOf(Band("Theta", 4.0, 8.0), Band("Beta", 12.0, 30.0))
        manager.setBands(bands)
        assertEquals(bands, manager.state.value.bands)
        manager.registerStreamingJob(null)
        assertNull(registeredStreamingJob(manager))
    }
    @Test
    fun `set wave spec ignores negative indexes`() {
        val manager = BoardConnectionManager(
            StateStore(
                HardwareState(
                    waveSpecs = listOf(
                        WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 8.0)
                    )
                )
            )
        )
        val before = manager.state.value.waveSpecs
        manager.setWaveSpec(-1, enabled = false, type = WaveType.NOISE, amplitude = 0.0, frequencyHz = 0.0, phaseShiftRad = 0.0)
        assertEquals(before, manager.state.value.waveSpecs)
    }
}
