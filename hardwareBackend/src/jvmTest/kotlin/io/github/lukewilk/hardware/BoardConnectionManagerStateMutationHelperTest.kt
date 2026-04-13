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
 * State-mutation helper and config-resilience tests for `BoardConnectionManager`.
 */
internal class BoardConnectionManagerStateMutationHelperTest : BoardConnectionManagerSyntheticTestSupport() {
    /** Verifies helper mutators update waves and keep band lists capped at ten entries. */
    @Test
    fun `state mutator helpers update waves and trim oversized band collections`() {
        val helperManager = BoardConnectionManager(
            StateStore(
                HardwareState(
                    waveSpecs = listOf(
                        WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 8.0)
                    ),
                    bands = listOf(Band("A", 1.0, 2.0))
                )
            )
        )
        helperManager.setWaveSpec(
            0,
            enabled = false,
            type = WaveType.TRIANGLE,
            amplitude = 2.0,
            frequencyHz = 12.0,
            phaseShiftRad = 0.5
        )
        assertEquals(WaveType.TRIANGLE, helperManager.state.value.waveSpecs[0].type)
        assertFalse(helperManager.state.value.waveSpecs[0].enabled)
        val oversizedBands = (1..12).map { Band("Band$it", it.toDouble(), it + 1.0) }
        helperManager.setBands(oversizedBands)
        assertEquals(10, helperManager.state.value.bands.size)
        helperManager.addBand(Band("Extra", 20.0, 25.0))
        assertEquals(10, helperManager.state.value.bands.size)
        helperManager.removeBand("Band1")
        assertTrue(helperManager.state.value.bands.none { it.name == "Band1" })
    }
    /** Verifies config_board failures are swallowed so state cleanup still completes. */
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
    /** Verifies helper mutators ignore oversized wave indexes but preserve valid band collections. */
    @Test
    fun `set wave spec ignores oversized indexes and set bands preserves valid collections`() {
        val helperManager = BoardConnectionManager(
            StateStore(
                HardwareState(
                    waveSpecs = listOf(
                        WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 8.0)
                    ),
                    bands = listOf(Band("Alpha", 8.0, 12.0))
                )
            )
        )
        val before = helperManager.state.value.waveSpecs
        helperManager.setWaveSpec(
            5,
            enabled = false,
            type = WaveType.NOISE,
            amplitude = 0.0,
            frequencyHz = 0.0,
            phaseShiftRad = 0.0
        )
        assertEquals(before, helperManager.state.value.waveSpecs)
        val bands = listOf(Band("Theta", 4.0, 8.0), Band("Beta", 12.0, 30.0))
        helperManager.setBands(bands)
        assertEquals(bands, helperManager.state.value.bands)
        helperManager.registerStreamingJob(null)
        assertNull(registeredStreamingJob(helperManager))
    }
    /** Verifies negative wave indexes are ignored the same way oversized indexes are. */
    @Test
    fun `set wave spec ignores negative indexes`() {
        val helperManager = BoardConnectionManager(
            StateStore(
                HardwareState(
                    waveSpecs = listOf(
                        WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 8.0)
                    )
                )
            )
        )
        val before = helperManager.state.value.waveSpecs
        helperManager.setWaveSpec(-1, enabled = false, type = WaveType.NOISE, amplitude = 0.0, frequencyHz = 0.0, phaseShiftRad = 0.0)
        assertEquals(before, helperManager.state.value.waveSpecs)
    }
}
