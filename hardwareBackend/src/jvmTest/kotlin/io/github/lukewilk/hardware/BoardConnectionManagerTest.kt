package io.github.lukewilk.hardware

import brainflow.BoardIds
import brainflow.BoardShim
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

/**
 * Connection and channel/RLD configuration tests for `BoardConnectionManager`.
 */
class BoardConnectionManagerTest : BoardConnectionManagerTestSupport() {

    /** Verifies the synthetic board connects successfully without requiring a native session. */
    @Test
    fun `connect synthetic board succeeds`() = runBlocking {
        val result = manager.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")

        assertTrue(result, "Expected SYNTHETIC_BOARD to connect successfully")
    }

    /** Verifies `isConnected()` follows connect and close state transitions. */
    @Test
    fun `is connected reflects connection state`() = runBlocking {
        assertTrue(!manager.state.value.connected, "Expected the manager to start disconnected")

        manager.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")
        assertTrue(manager.state.value.connected, "Expected connect() to mark the manager connected")

        manager.close()
        assertTrue(!manager.state.value.connected, "Expected close() to reset the connected flag")
    }

    /** Verifies closing without an active session is a no-op for the public connection state. */
    @Test
    fun `close handles an absent session`() = runBlocking {
        manager.close()

        assertTrue(!manager.state.value.connected, "Expected close() without a session to keep the manager disconnected")
    }

    /** Verifies failed connections return false and leave the state disconnected. */
    @Test
    fun `connect handles connection exceptions`() = runBlocking {
        val result = manager.connect(boardId = BoardIds.NO_BOARD, serialPort = "invalid")

        assertTrue(!result, "Expected connection failure to return false")
        assertTrue(!manager.state.value.connected, "Expected failed connection attempts to leave the manager disconnected")
    }

    /** Verifies channel toggles update the enabled-channel state for a connected synthetic board. */
    @Test
    fun `enable and disable channel update state`() = runBlocking {
        manager.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")

        manager.enableChannel(0)
        assertTrue(0 in manager.state.value.enabledChannels, "Expected channel 0 to be enabled")

        manager.disableChannel(0)
        assertTrue(0 !in manager.state.value.enabledChannels, "Expected channel 0 to be disabled")
    }

    /** Verifies channel and RLD changes still update local state even when no board is connected. */
    @Test
    fun `channel and rld operations without connection only touch local state`() {
        val disconnectedManager = BoardConnectionManager(StateStore(HardwareState(synthetic = false)))

        disconnectedManager.enableChannel(1)
        disconnectedManager.enableRLD(1)
        assertEquals(listOf(1), disconnectedManager.state.value.enabledChannels)
        assertEquals(listOf(1), disconnectedManager.state.value.rldEnabled)

        disconnectedManager.disableChannel(1)
        disconnectedManager.disableRLD(1)
        assertTrue(disconnectedManager.state.value.enabledChannels.isEmpty())
        assertTrue(disconnectedManager.state.value.rldEnabled.isEmpty())
    }

    /** Verifies repeated channel enable calls stay idempotent and disabling clears verification state. */
    @Test
    fun `enable channel is idempotent and disable clears verified channels`() = runBlocking {
        manager.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")
        manager.enableChannel(0)
        manager.enableChannel(0)
        stateStore.update { it.copy(verifiedChannels = listOf(0)) }

        assertEquals(listOf(0), manager.state.value.enabledChannels)

        manager.disableChannel(0)
        assertTrue(manager.state.value.verifiedChannels.isEmpty())
    }

    /** Verifies RLD toggles update the tracked state for a connected synthetic board. */
    @Test
    fun `enable and disable rld update state`() = runBlocking {
        manager.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")

        manager.enableRLD(0)
        assertTrue(0 in manager.state.value.rldEnabled, "Expected RLD for channel 0 to be enabled")

        manager.disableRLD(0)
        assertTrue(0 !in manager.state.value.rldEnabled, "Expected RLD for channel 0 to be disabled")
    }

    /** Verifies repeated RLD enable calls remain idempotent. */
    @Test
    fun `enable rld is idempotent`() = runBlocking {
        manager.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")
        manager.enableRLD(0)
        manager.enableRLD(0)

        assertEquals(listOf(0), manager.state.value.rldEnabled)

        manager.disableRLD(0)
        assertTrue(manager.state.value.rldEnabled.isEmpty())
    }

    /** Verifies invalid channel indexes do not mutate enabled-channel state. */
    @Test
    fun `invalid channel indexes leave channel state unchanged`() = runBlocking {
        manager.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")
        val before = manager.state.value.enabledChannels.size

        manager.enableChannel(100)
        manager.disableChannel(100)

        assertEquals(before, manager.state.value.enabledChannels.size)
    }

    /** Verifies invalid RLD indexes do not mutate the tracked RLD state. */
    @Test
    fun `invalid rld indexes leave rld state unchanged`() = runBlocking {
        manager.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")
        val before = manager.state.value.rldEnabled.size

        manager.enableRLD(100)
        manager.disableRLD(100)

        assertEquals(before, manager.state.value.rldEnabled.size)
    }

    /** Verifies real knight-board channel commands are forwarded through `config_board`. */
    @Test
    fun `real knight board channel operations call config board`() = runBlocking {
        val mockShim = Mockito.mock(BoardShim::class.java)
        val localManager = BoardConnectionManager(
            stateStore = stateStore,
            boardShimFactory = { _, _ -> mockShim },
            samplingRateProvider = { 128 }
        )
        localManager.connect(boardId = BoardIds.NEUROPAWN_KNIGHT_BOARD, serialPort = "")

        localManager.enableChannel(2)
        verify(mockShim, times(1)).config_board("enable_channel 2")
        assertTrue(2 in localManager.state.value.enabledChannels)

        localManager.disableChannel(2)
        verify(mockShim, times(1)).config_board("disable_channel 2")
        assertTrue(2 !in localManager.state.value.enabledChannels)
    }

    /** Verifies real knight-board RLD commands are forwarded through `config_board`. */
    @Test
    fun `real knight board rld operations call config board`() = runBlocking {
        val mockShim = Mockito.mock(BoardShim::class.java)
        val localManager = BoardConnectionManager(
            stateStore = stateStore,
            boardShimFactory = { _, _ -> mockShim },
            samplingRateProvider = { 128 }
        )
        localManager.connect(boardId = BoardIds.NEUROPAWN_KNIGHT_BOARD, serialPort = "")

        localManager.enableRLD(3)
        verify(mockShim, times(1)).config_board("enable_rld 3")
        assertTrue(3 in localManager.state.value.rldEnabled)

        localManager.disableRLD(3)
        verify(mockShim, times(1)).config_board("disable_rld 3")
        assertTrue(3 !in localManager.state.value.rldEnabled)
    }

    /** Verifies synthetic boards skip `config_board` for channel operations. */
    @Test
    fun `synthetic board channel operations do not call config board`() = runBlocking {
        val mockShim = Mockito.mock(BoardShim::class.java)
        val localManager = BoardConnectionManager(
            stateStore = stateStore,
            boardShimFactory = { _, _ -> mockShim },
            samplingRateProvider = { 128 }
        )
        localManager.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")

        localManager.enableChannel(1)
        verify(mockShim, times(0)).config_board(any())
        assertTrue(1 in localManager.state.value.enabledChannels)

        localManager.disableChannel(1)
        verify(mockShim, times(0)).config_board(any())
        assertTrue(1 !in localManager.state.value.enabledChannels)
    }

    /** Verifies synthetic boards skip `config_board` for RLD operations. */
    @Test
    fun `synthetic board rld operations do not call config board`() = runBlocking {
        val mockShim = Mockito.mock(BoardShim::class.java)
        val localManager = BoardConnectionManager(
            stateStore = stateStore,
            boardShimFactory = { _, _ -> mockShim },
            samplingRateProvider = { 128 }
        )
        localManager.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")

        localManager.enableRLD(1)
        verify(mockShim, times(0)).config_board(any())
        assertTrue(1 in localManager.state.value.rldEnabled)

        localManager.disableRLD(1)
        verify(mockShim, times(0)).config_board(any())
        assertTrue(1 !in localManager.state.value.rldEnabled)
    }

    /** Verifies the board shim starts out absent before any connection attempt. */
    @Test
    fun `get board shim returns null initially`() {
        assertNull(manager.getBoardShim())
    }


    /** Verifies connect preserves seeded sampling, channel, and selection state when available. */
    @Test
    fun `connect preserves previous sampling channels and selections`() {
        val seededStateStore = StateStore(
            HardwareState(
                samplingRateHz = 500,
                channels = 8,
                enabledChannels = listOf(1, 3),
                rldEnabled = listOf(2)
            )
        )
        val seededManager = BoardConnectionManager(
            stateStore = seededStateStore,
            boardShimFactory = { _, _ -> Mockito.mock(BoardShim::class.java) },
            samplingRateProvider = { throw RuntimeException("sampling unavailable") }
        )

        assertTrue(seededManager.connect(BoardIds.NEUROPAWN_KNIGHT_BOARD, serialPort = ""))
        assertEquals(500, seededManager.state.value.samplingRateHz)
        assertEquals(8, seededManager.state.value.channels)
        assertEquals(listOf(1, 3), seededManager.state.value.enabledChannels)
        assertEquals(listOf(2), seededManager.state.value.rldEnabled)
        assertEquals(emptyList(), seededManager.state.value.verifiedChannels)
    }

    /** Verifies connect falls back to the default sampling rate when discovery fails and no previous rate exists. */
    @Test
    fun `connect falls back to the default sampling rate when discovery fails`() {
        val fallbackManager = BoardConnectionManager(
            stateStore = StateStore(HardwareState()),
            boardShimFactory = { _, _ -> Mockito.mock(BoardShim::class.java) },
            samplingRateProvider = { throw RuntimeException("sampling unavailable") }
        )

        assertTrue(fallbackManager.connect(BoardIds.NEUROPAWN_KNIGHT_BOARD, serialPort = ""))
        assertEquals(250, fallbackManager.state.value.samplingRateHz)
    }
}
