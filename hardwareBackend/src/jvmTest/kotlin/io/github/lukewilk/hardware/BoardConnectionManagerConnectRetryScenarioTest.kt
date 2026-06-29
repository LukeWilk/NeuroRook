package io.github.lukewilk.hardware
import brainflow.BoardIds
import brainflow.BrainFlowError
import brainflow.BrainFlowInputParams
import brainflow.BoardShim
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
/**
 * Connect-time retry scenario tests for `BoardConnectionManager`.
 */
class BoardConnectionManagerConnectRetryScenarioTest : BoardConnectionManagerTestSupport() {
    /** Verifies connect retries session preparation after BrainFlow reports another board already exists. */
    @Test
    fun `connect retries prepare session after another board errors`() {
        val params = BrainFlowInputParams()
        var factoryCalls = 0
        var firstReleased = false
        val firstShim = object : BoardShim(BoardIds.NEUROPAWN_KNIGHT_BOARD, params) {
            override fun prepare_session() {
                throw BrainFlowError("ANOTHER_BOARD_IS_CREATED_ERROR", 1)
            }
            override fun release_session() {
                firstReleased = true
            }
        }
        val secondShim = object : BoardShim(BoardIds.NEUROPAWN_KNIGHT_BOARD, params) {
            override fun prepare_session() = Unit
        }
        val retryManager = realBoardManager(
            boardShimFactory = { _, _ -> if (++factoryCalls == 1) firstShim else secondShim }
        )
        assertTrue(retryManager.connect(BoardIds.NEUROPAWN_KNIGHT_BOARD, serialPort = ""))
        assertEquals(2, factoryCalls)
        assertTrue(firstReleased || retryManager.state.value.connected)
    }
    /** Verifies retry-based reconnect still succeeds when releasing the first shim fails. */
    @Test
    fun `connect retry still succeeds when release session fails`() {
        val params = BrainFlowInputParams()
        var factoryCalls = 0
        val firstShim = object : BoardShim(BoardIds.NEUROPAWN_KNIGHT_BOARD, params) {
            override fun prepare_session() {
                throw BrainFlowError("ANOTHER_BOARD_IS_CREATED_ERROR", 1)
            }
            override fun release_session() {
                throw RuntimeException("release failed")
            }
        }
        val secondShim = object : BoardShim(BoardIds.NEUROPAWN_KNIGHT_BOARD, params) {
            override fun prepare_session() = Unit
        }
        val retryManager = realBoardManager(
            boardShimFactory = { _, _ -> if (++factoryCalls == 1) firstShim else secondShim }
        )
        assertTrue(retryManager.connect(BoardIds.NEUROPAWN_KNIGHT_BOARD, serialPort = ""))
        assertEquals(2, factoryCalls)
        assertTrue(retryManager.state.value.connected)
    }
    /** Verifies connect fails cleanly when the prepare-session error is not retryable. */
    @Test
    fun `connect fails when prepare session throws a non retryable error`() {
        val failingShim = object : BoardShim(BoardIds.NEUROPAWN_KNIGHT_BOARD, BrainFlowInputParams()) {
            override fun prepare_session() {
                throw RuntimeException("prepare failed")
            }
        }
        val failingManager = BoardConnectionManager(
            stateStore = StateStore(HardwareState(enabledChannels = listOf(1), rldEnabled = listOf(2))),
            boardShimFactory = { _, _ -> failingShim },
            samplingRateProvider = { 128 }
        )
        assertFalse(failingManager.connect(BoardIds.NEUROPAWN_KNIGHT_BOARD, serialPort = ""))
        assertFalse(failingManager.state.value.connected)
        assertTrue(failingManager.state.value.enabledChannels.isEmpty())
        assertTrue(failingManager.state.value.rldEnabled.isEmpty())
        assertNull(failingManager.getBoardShim(), "Failed connect should not leave a stale board shim published")
    }
    /** Verifies connect triggers retryPrepareSession when the retry predicate explicitly opts in. */
    @Test
    fun `connect retries prepare session when the predicate returns true`() {
        val params = BrainFlowInputParams()
        var factoryCalls = 0
        var retryCalled = false
        val firstShim = object : BoardShim(BoardIds.NEUROPAWN_KNIGHT_BOARD, params) {
            override fun prepare_session() {
                throw RuntimeException("prepare failed")
            }
        }
        val secondShim = object : BoardShim(BoardIds.NEUROPAWN_KNIGHT_BOARD, params) {
            override fun prepare_session() = Unit
        }
        val retryManager = object : BoardConnectionManager(
            stateStore = StateStore(HardwareState()),
            boardShimFactory = { _, _ -> if (++factoryCalls == 1) firstShim else secondShim },
            samplingRateProvider = { 128 }
        ) {
            override fun isAnotherBoardCreatedError(error: Exception): Boolean = true
            override fun retryPrepareSession(boardId: BoardIds, params: BrainFlowInputParams) {
                retryCalled = true
                super.retryPrepareSession(boardId, params)
            }
        }
        assertTrue(retryManager.connect(BoardIds.NEUROPAWN_KNIGHT_BOARD, serialPort = ""))
        assertTrue(retryCalled)
        assertEquals(2, factoryCalls)
        assertTrue(retryManager.getBoardShim() === secondShim)
    }
    /** Verifies retry reconnect still succeeds when the error only matches by message text. */
    @Test
    fun `connect retry succeeds when the retryable error is provided as plain message text`() {
        val params = BrainFlowInputParams()
        var factoryCalls = 0
        val firstShim = object : BoardShim(BoardIds.NEUROPAWN_KNIGHT_BOARD, params) {
            override fun prepare_session() {
                throw RuntimeException("ANOTHER_BOARD_IS_CREATED_ERROR")
            }
            override fun release_session() {
                throw RuntimeException("release failed")
            }
        }
        val secondShim = object : BoardShim(BoardIds.NEUROPAWN_KNIGHT_BOARD, params) {
            override fun prepare_session() = Unit
        }
        val retryManager = realBoardManager(
            boardShimFactory = { _, _ -> if (++factoryCalls == 1) firstShim else secondShim }
        )
        assertTrue(retryManager.connect(BoardIds.NEUROPAWN_KNIGHT_BOARD, serialPort = ""))
        assertEquals(2, factoryCalls)
        assertTrue(retryManager.getBoardShim() === secondShim)
    }
}
