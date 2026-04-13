package io.github.lukewilk.hardware

import brainflow.BoardIds
import brainflow.BoardShim
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.Mockito
import org.mockito.kotlin.any

/**
 * Streaming lifecycle and state-transition tests for `BoardConnectionManager`.
 */
class BoardConnectionManagerStreamingLifecycleTest : BoardConnectionManagerTestSupport() {
    /** Verifies real-board streaming reports both success and failure through the public state. */
    @Test
    fun `start stream updates real board state on success and failure`() {
        val successShim = Mockito.mock(BoardShim::class.java)
        val successManager = realBoardManager(boardShimFactory = { _, _ -> successShim })
        connectRealBoard(successManager)

        successManager.startStream()

        assertTrue(successManager.isStreaming())
        assertTrue(successManager.state.value.streaming)

        val failureShim = Mockito.mock(BoardShim::class.java)
        Mockito.doThrow(RuntimeException("boom")).`when`(failureShim).start_stream(45000, "")
        val failureManager = realBoardManager(boardShimFactory = { _, _ -> failureShim })
        connectRealBoard(failureManager)

        failureManager.startStream()

        assertFalse(failureManager.isStreaming())
        assertFalse(failureManager.state.value.streaming)
    }

    /** Verifies real-board streaming fails safely when the shim session has gone missing. */
    @Test
    fun `start stream without a real board shim leaves streaming inactive`() {
        val localManager = realBoardManager(
            initialState = HardwareState(synthetic = false),
            boardShimFactory = { _, _ -> Mockito.mock(BoardShim::class.java) }
        )
        connectRealBoard(localManager)
        setBoardShim(localManager, null)

        localManager.startStream()

        assertFalse(localManager.isStreaming())
        assertFalse(localManager.state.value.streaming)
    }

    /** Verifies stopStream cancels the registered coroutine job and clears the private reference. */
    @Test
    fun `stop stream cancels the registered job and clears its reference`() = runBlocking {
        val job = Job()
        manager.registerStreamingJob(job)

        manager.stopStream()
        delay(10)

        assertTrue(job.isCancelled)
        assertNull(registeredStreamingJob(manager))
    }

    /** Verifies disable helpers remove the matching enabled, verified, and RLD channel entries. */
    @Test
    fun `disable helpers remove existing state entries`() = runBlocking {
        val mockShim = Mockito.mock(BoardShim::class.java)
        val localStateStore = StateStore(
            HardwareState(
                enabledChannels = listOf(1, 2),
                verifiedChannels = listOf(2, 3),
                rldEnabled = listOf(4, 5)
            )
        )
        val localManager = BoardConnectionManager(
            stateStore = localStateStore,
            boardShimFactory = { _, _ -> mockShim },
            samplingRateProvider = { 128 }
        )
        connectRealBoard(localManager)
        localStateStore.update {
            it.copy(enabledChannels = listOf(1, 2), verifiedChannels = listOf(2, 3), rldEnabled = listOf(4, 5))
        }

        localManager.disableChannel(2)
        localManager.disableRLD(5)

        assertEquals(listOf(1), localManager.state.value.enabledChannels)
        assertEquals(listOf(3), localManager.state.value.verifiedChannels)
        assertEquals(listOf(4), localManager.state.value.rldEnabled)
    }

    /** Verifies channel discovery failures fall back to zero channels instead of aborting connect(). */
    @Test
    fun `connect falls back to zero channels when discovery fails`() {
        val mockShim = Mockito.mock(BoardShim::class.java)
        val localManager = realBoardManager(
            initialState = HardwareState(synthetic = false),
            boardShimFactory = { _, _ -> mockShim }
        )

        Mockito.mockStatic(BoardShim::class.java).use { mockStatic ->
            mockStatic.`when`<IntArray> { BoardShim.get_eeg_channels(BoardIds.NEUROPAWN_KNIGHT_BOARD) }
                .thenThrow(RuntimeException("channel lookup failed"))

            connectRealBoard(localManager)
            assertEquals(0, localManager.state.value.channels)
        }
    }

    /** Verifies stopStream tolerates state update failures before it checks synthetic mode. */
    @Test
    fun `stop stream swallows state update failures`() {
        val mockedStateStore = Mockito.spy(StateStore(HardwareState(synthetic = true)))
        Mockito.doThrow(RuntimeException("update failed")).`when`(mockedStateStore).update(any())
        val localManager = BoardConnectionManager(mockedStateStore)

        localManager.stopStream()

        assertTrue(localManager.state.value.synthetic)
    }

    /** Verifies close survives state lookup failures and still drops the board shim reference. */
    @Test
    fun `close swallows state lookup failures`() {
        val mockedStateStore = Mockito.spy(StateStore(HardwareState(synthetic = false)))
        Mockito.doNothing().`when`(mockedStateStore).update(any())
        Mockito.doThrow(RuntimeException("get failed")).`when`(mockedStateStore).get()
        val localManager = BoardConnectionManager(mockedStateStore)

        localManager.close()

        assertNull(localManager.getBoardShim())
    }

    /** Verifies awaitRegisteredStreamingJob treats join failures as a handled wait failure instead of throwing. */
    @Test
    fun `await registered streaming job handles join exceptions`() {
        val exposingManager = object : BoardConnectionManager(StateStore(HardwareState())) {
            fun await(job: Job) = awaitRegisteredStreamingJob(job)
        }
        val failingJob = Mockito.mock(Job::class.java)
        runBlocking {
            Mockito.doThrow(RuntimeException("join failed")).`when`(failingJob).join()
        }

        exposingManager.await(failingJob)

        assertFalse(exposingManager.state.value.streaming)
    }

    /** Verifies awaitRegisteredStreamingJob tolerates jobs that do not finish before the timeout. */
    @Test
    fun `await registered streaming job handles timeouts`() {
        val exposingManager = object : BoardConnectionManager(StateStore(HardwareState())) {
            fun await(job: Job) = awaitRegisteredStreamingJob(job)
        }

        exposingManager.await(Job())

        assertFalse(exposingManager.state.value.streaming)
    }
}

