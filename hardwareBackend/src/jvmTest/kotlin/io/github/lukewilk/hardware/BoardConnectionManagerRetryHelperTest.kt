package io.github.lukewilk.hardware
import brainflow.BoardIds
import brainflow.BrainFlowInputParams
import brainflow.BoardShim
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
/**
 * Direct retry-helper and retry-predicate tests for `BoardConnectionManager`.
 */
class BoardConnectionManagerRetryHelperTest : BoardConnectionManagerTestSupport() {
    /** Verifies retryPrepareSession swaps to a replacement shim when called directly. */
    @Test
    fun `retry prepare session swaps in the replacement shim`() {
        val params = BrainFlowInputParams()
        val firstShim = object : BoardShim(BoardIds.NEUROPAWN_KNIGHT_BOARD, params) {
            override fun prepare_session() = Unit
        }
        val secondShim = object : BoardShim(BoardIds.NEUROPAWN_KNIGHT_BOARD, params) {
            override fun prepare_session() = Unit
        }
        val retryManager = realBoardManager(boardShimFactory = { _, _ -> secondShim })
        setBoardShim(retryManager, firstShim)
        retryManager.retryPrepareSession(BoardIds.NEUROPAWN_KNIGHT_BOARD, params)
        assertTrue(retryManager.getBoardShim() === secondShim)
    }
    /** Verifies the retry predicate only matches the expected BrainFlow error message fragment. */
    @Test
    fun `is another board created error matches the expected message only`() {
        val localManager = BoardConnectionManager(StateStore(HardwareState()))
        assertTrue(localManager.isAnotherBoardCreatedError(RuntimeException("ANOTHER_BOARD_IS_CREATED_ERROR")))
        assertFalse(localManager.isAnotherBoardCreatedError(RuntimeException("different error")))
        assertFalse(localManager.isAnotherBoardCreatedError(Exception()))
    }
    /** Verifies retryPrepareSession still swaps the shim when release_session itself fails. */
    @Test
    fun `retry prepare session handles release failures directly`() {
        val params = BrainFlowInputParams()
        val firstShim = object : BoardShim(BoardIds.NEUROPAWN_KNIGHT_BOARD, params) {
            override fun release_session() {
                throw RuntimeException("release failed")
            }
        }
        val secondShim = object : BoardShim(BoardIds.NEUROPAWN_KNIGHT_BOARD, params) {
            override fun prepare_session() = Unit
        }
        val retryManager = realBoardManager(boardShimFactory = { _, _ -> secondShim })
        setBoardShim(retryManager, firstShim)
        retryManager.retryPrepareSession(BoardIds.NEUROPAWN_KNIGHT_BOARD, params)
        assertTrue(retryManager.getBoardShim() === secondShim)
    }
    /** Verifies retryPrepareSession also works when there is no existing shim to release first. */
    @Test
    fun `retry prepare session handles a missing existing shim`() {
        val params = BrainFlowInputParams()
        val replacementShim = object : BoardShim(BoardIds.NEUROPAWN_KNIGHT_BOARD, params) {
            override fun prepare_session() = Unit
        }
        val retryManager = realBoardManager(boardShimFactory = { _, _ -> replacementShim })
        retryManager.retryPrepareSession(BoardIds.NEUROPAWN_KNIGHT_BOARD, params)
        assertTrue(retryManager.getBoardShim() === replacementShim)
    }
}
