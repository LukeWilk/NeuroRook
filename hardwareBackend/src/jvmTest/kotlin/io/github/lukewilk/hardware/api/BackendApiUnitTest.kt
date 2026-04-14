package io.github.lukewilk.hardware.api

import brainflow.BoardIds
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.WaveType
import io.github.lukewilk.shared.model.SystemLogSeverity
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Configuration, logging, and state mutation tests for `HardwareBackendApi`.
 */
class BackendApiUnitTest : BackendApiTestSupport() {
    /** Verifies connect followed by disconnect flips the public connection state as expected. */
    @Test
    fun `connect and disconnect update the public state`() = runBlocking {
        assertFalse(api.getState().connected)
        assertTrue(api.connect("SYNTHETIC_BOARD"))
        assertTrue(api.getState().connected)
        assertTrue(api.disconnect())
        assertFalse(api.getState().connected)
    }
    /** Verifies wave management operations mutate the stored list in place and preserve indexes. */
    @Test
    fun `wave management adds edits and removes entries`() = runBlocking {
        api.connect("SYNTHETIC_BOARD")
        val initialSize = api.getState().waveSpecs.size
        val newWave = WaveSpec(
            enabled = true,
            type = WaveType.SQUARE,
            amplitude = 2.0,
            frequencyHz = 5.0,
            phaseShiftRad = 0.0
        )
        assertTrue(api.addWave(newWave))
        val addedIndex = api.getState().waveSpecs.indexOfFirst { it.type == WaveType.SQUARE && it.amplitude == 2.0 }
        assertTrue(addedIndex >= 0, "Expected the added wave to be visible in the state")
        assertTrue(api.editWave(addedIndex, newWave.copy(amplitude = 3.0)))
        assertEquals(3.0, api.getState().waveSpecs[addedIndex].amplitude)
        assertTrue(api.removeWave(addedIndex))
        assertEquals(initialSize, api.getState().waveSpecs.size)
    }
    /** Verifies channel toggles update the enabled channel list exposed through the API state. */
    @Test
    fun `channel toggles update the enabled channel list`() = runBlocking {
        api.connect("SYNTHETIC_BOARD")
        assertTrue(api.enableChannel(0))
        assertTrue(api.enableChannel(1))
        assertEquals(listOf(0, 1), api.getState().enabledChannels)
        assertTrue(api.disableChannel(0))
        assertEquals(listOf(1), api.getState().enabledChannels)
    }
    /** Verifies RLD toggles update the state that the UI consumes. */
    @Test
    fun `rld toggles update the enabled rld channels`() = runBlocking {
        api.connect("SYNTHETIC_BOARD")
        assertTrue(api.enableRLD())
        assertTrue(api.getState().rldEnabled.isNotEmpty())
        assertTrue(api.disableRLD())
        assertTrue(api.getState().rldEnabled.isEmpty())
    }
    /** Verifies verification uses the current enabled channels and records a human-readable success log. */
    @Test
    fun `verify channels copies enabled channels into verified channels`() = runBlocking {
        api.connect("SYNTHETIC_BOARD")
        api.enableChannel(0)
        api.enableChannel(2)
        assertTrue(api.verifyChannels())
        assertEquals(listOf(0, 2), api.getState().verifiedChannels)
        assertSystemLogContains(SystemLogSeverity.INFO, "Verification completed for 2 enabled channel(s): 1, 3")
    }
    /** Verifies verification on an empty selection still succeeds but logs a warning with clear guidance. */
    @Test
    fun `verify channels warns when no channels are enabled`() = runBlocking {
        api.connect("SYNTHETIC_BOARD")
        assertTrue(api.verifyChannels())
        assertEquals(emptyList(), api.getState().verifiedChannels)
        assertSystemLogContains(SystemLogSeverity.WARN, "there were no enabled channels to check")
    }
    /** Verifies verification fails gracefully when the user has not connected a board yet. */
    @Test
    fun `verify channels without a connection logs the reason`() = runBlocking {
        assertFalse(api.verifyChannels())
        assertSystemLogContains(SystemLogSeverity.WARN, "Channel verification skipped because no board is connected")
    }
    /** Verifies synthetic boards allow sampling-rate changes after the board is configured for streaming. */
    @Test
    fun `set sampling rate updates synthetic board state`() = runBlocking {
        assertTrue(api.connect("SYNTHETIC_BOARD"))
        assertTrue(api.enableChannel(0))
        assertTrue(api.addWave(standardWave()))
        assertTrue(api.setSamplingRateHz(500))
        assertEquals(500, api.getState().samplingRateHz)
    }
    /** Verifies real boards reject sampling-rate changes and surface a precise error contract. */
    @Test
    fun `set sampling rate throws for non synthetic boards`() = runBlocking {
        api.connect("NO_BOARD")
        val failure = kotlin.runCatching { api.setSamplingRateHz(123) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertSystemLogContains(SystemLogSeverity.ERROR, "Sampling rate can only be changed for the synthetic board")
    }
    /** Verifies the BrainFlow board list excludes the sentinel `NO_BOARD` entry and records the load event. */
    @Test
    fun `get brainflow boards excludes no board`() {
        val boards = api.getBrainflowBoards()
        assertTrue(boards.isNotEmpty(), "Expected at least one board entry")
        assertFalse(boards.contains("NO_BOARD"), "Expected NO_BOARD to be hidden from the UI list")
        assertTrue(boards.contains("SYNTHETIC_BOARD"), "Expected SYNTHETIC_BOARD to remain selectable")
        assertSystemLogContains(SystemLogSeverity.INFO, "Loaded")
    }
    /** Verifies connection, streaming, and disconnection each publish the expected state and log updates. */
    @Test
    fun `hardware state and system logs reflect the connection lifecycle`() = runBlocking {
        assertFalse(api.hardwareStateFlow.value.connected)
        assertTrue(api.connect("SYNTHETIC_BOARD"))
        assertTrue(api.hardwareStateFlow.value.connected)
        assertSystemLogContains(SystemLogSeverity.INFO, "Connected to SYNTHETIC_BOARD with")
        prepareSyntheticStreamingScenario()
        assertTrue(api.startStreaming())
        assertTrue(api.hardwareStateFlow.value.streaming)
        assertSystemLogContains(SystemLogSeverity.INFO, "Stream started for SYNTHETIC_BOARD")
        assertTrue(api.stopStreaming())
        assertFalse(api.hardwareStateFlow.value.streaming)
        assertTrue(api.hardwareStateFlow.value.connected)
        assertSystemLogContains(SystemLogSeverity.INFO, "Stream stopped for SYNTHETIC_BOARD")
        assertTrue(api.disconnect())
        assertFalse(api.hardwareStateFlow.value.connected)
        assertSystemLogContains(SystemLogSeverity.INFO, "Disconnected from board")
    }
    /** Verifies unknown board names warn the caller before falling back to `NO_BOARD`. */
    @Test
    fun `connecting an unknown board name warns before falling back`() = runBlocking {
        val result = kotlin.runCatching { api.connect("UNKNOWN_BOARD") }
        assertSystemLogContains(SystemLogSeverity.WARN, "Falling back to NO_BOARD")
        if (result.isFailure) {
            val error = result.exceptionOrNull()
            assertTrue(error?.message?.contains("UNSUPPORTED_BOARD_ERROR") == true)
        } else {
            assertFalse(api.getState().connected)
        }
    }
    /** Verifies disconnect logging uses a generic board label when no explicit board id is cached. */
    @Test
    fun `disconnect without a connection uses the fallback board label`() = runBlocking {
        assertTrue(api.disconnect())
        assertSystemLogContains(SystemLogSeverity.INFO, "Disconnect requested for current board")
    }
    /** Verifies disconnect logging uses the explicit board name when it is known. */
    @Test
    fun `disconnect uses the explicit board label when connected board is known`() = runBlocking {
        assertTrue(api.connect("SYNTHETIC_BOARD"))
        assertTrue(api.disconnect())
        assertSystemLogContains(SystemLogSeverity.INFO, "Disconnect requested for SYNTHETIC_BOARD")
    }
    /** Verifies the rolling system log keeps only the configured number of recent entries. */
    @Test
    fun `system log flow trims to the configured maximum`() {
        repeat(205) {
            api.getBrainflowBoards()
        }
        assertEquals(200, api.systemLogFlow.value.size)
        assertTrue(api.systemLogFlow.value.all { it.message.contains("Loaded") })
    }
    /** Verifies the connection log includes the caller-supplied serial port and timeout values verbatim. */
    @Test
    fun `connect logs the explicit serial port and timeout`() = runBlocking {
        assertTrue(api.connect("SYNTHETIC_BOARD", serialPort = "/dev/ttyUSB0", timeoutSeconds = 7))
        assertSystemLogContains(SystemLogSeverity.INFO, "Connecting to SYNTHETIC_BOARD using /dev/ttyUSB0 (timeout=7s)")
    }
    /** Verifies the private board-label helper returns either the fallback label or the explicit board name. */
    @Test
    fun `connected board label helper supports fallback and explicit ids`() {
        val helper = HardwareBackendApi::class.java.getDeclaredMethod("connectedBoardLabel", String::class.java).apply {
            isAccessible = true
        }
        setConnectedBoardId(null)
        assertEquals("fallback board", helper.invoke(api, "fallback board"))
        setConnectedBoardId(BoardIds.SYNTHETIC_BOARD)
        assertEquals("SYNTHETIC_BOARD", helper.invoke(api, "fallback board"))
    }
}
