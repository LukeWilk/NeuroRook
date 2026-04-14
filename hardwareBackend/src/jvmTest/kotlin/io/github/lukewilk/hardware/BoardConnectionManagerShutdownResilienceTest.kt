package io.github.lukewilk.hardware

import brainflow.BoardIds
import brainflow.BrainFlowInputParams
import brainflow.BoardShim
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.Mockito

/**
 * Shutdown-resilience tests for `BoardConnectionManager`.
 */
class BoardConnectionManagerShutdownResilienceTest : BoardConnectionManagerTestSupport() {

    /** Verifies close swallows release failures but still clears the public state and shim reference. */
    @Test
    fun `close swallows release session failures and resets state`() {
        val closeManager = BoardConnectionManager(
            stateStore = StateStore(HardwareState()),
            boardShimFactory = {
                    _, _ ->
                object : BoardShim(BoardIds.NEUROPAWN_KNIGHT_BOARD, BrainFlowInputParams()) {
                    override fun prepare_session() = Unit
                    override fun release_session() {
                        throw RuntimeException("release failed")
                    }
                }
            },
            samplingRateProvider = { 128 }
        )
        assertTrue(closeManager.connect(BoardIds.NEUROPAWN_KNIGHT_BOARD, serialPort = ""))

        closeManager.close()

        assertFalse(closeManager.state.value.connected)
        assertNull(closeManager.getBoardShim())
    }

    /** Verifies a slow native stop is interrupted when it exceeds the shutdown timeout. */
    @Test
    fun `stop stream interrupts a long running native stop`() {
        val interrupted = AtomicBoolean(false)
        val enteredStop = CountDownLatch(1)
        val slowShim = object : BoardShim(BoardIds.NEUROPAWN_KNIGHT_BOARD, BrainFlowInputParams()) {
            override fun prepare_session() = Unit
            override fun start_stream(buffer_size: Int, streamer_params: String?) = Unit
            override fun stop_stream() {
                enteredStop.countDown()
                try {
                    Thread.sleep(2_000)
                } catch (_: InterruptedException) {
                    interrupted.set(true)
                }
            }
        }
        val slowManager = BoardConnectionManager(
            stateStore = StateStore(HardwareState()),
            boardShimFactory = { _, _ -> slowShim },
            samplingRateProvider = { 128 }
        )
        assertTrue(slowManager.connect(BoardIds.NEUROPAWN_KNIGHT_BOARD, serialPort = ""))
        slowManager.startStream()

        slowManager.stopStream()

        assertTrue(enteredStop.await(1, TimeUnit.SECONDS), "Expected stop_stream to be invoked")
        Thread.sleep(200)
        assertTrue(interrupted.get(), "Expected the native stop thread to be interrupted when it exceeds the timeout")
    }

    /** Verifies stopStream survives caller interruption and still leaves streaming inactive. */
    @Test
    fun `stop stream handles an interrupted caller during shutdown`() {
        val slowShim = object : BoardShim(BoardIds.NEUROPAWN_KNIGHT_BOARD, BrainFlowInputParams()) {
            override fun prepare_session() = Unit
            override fun start_stream(buffer_size: Int, streamer_params: String?) = Unit
            override fun stop_stream() {
                Thread.sleep(500)
            }
        }
        val localManager = BoardConnectionManager(
            stateStore = StateStore(HardwareState(synthetic = false)),
            boardShimFactory = { _, _ -> slowShim },
            samplingRateProvider = { 128 }
        )
        assertTrue(localManager.connect(BoardIds.NEUROPAWN_KNIGHT_BOARD, serialPort = ""))
        localManager.startStream()
        localManager.registerStreamingJob(Job())

        Thread.currentThread().interrupt()
        try {
            localManager.stopStream()
            assertFalse(localManager.isStreaming())
            assertFalse(localManager.state.value.streaming)
            assertNull(registeredStreamingJob(localManager))
        } finally {
            Thread.interrupted()
        }
    }

    /** Verifies stopStream cancels even slow-finishing jobs and clears the stored reference. */
    @Test
    fun `stop stream cancels jobs that do not finish immediately`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        val job = scope.launch {
            try {
                delay(Long.MAX_VALUE)
            } finally {
                withContext(NonCancellable) {
                    delay(1_000)
                }
            }
        }
        manager.registerStreamingJob(job)

        manager.stopStream()

        assertTrue(job.isCancelled)
        assertNull(registeredStreamingJob(manager))
    }

    /** Verifies cancelRegisteredStreamingJob swallows cancel failures while still clearing the private reference. */
    @Test
    fun `cancel registered streaming job swallows cancel failures and clears the reference`() {
        val localManager = BoardConnectionManager(StateStore(HardwareState(synthetic = true)))
        val failingJob = Mockito.mock(Job::class.java)
        Mockito.doThrow(RuntimeException("cancel failed")).`when`(failingJob).cancel()
        localManager.registerStreamingJob(failingJob)

        invokeCancelRegisteredStreamingJob(localManager)

        assertNull(registeredStreamingJob(localManager))
    }

    /** Verifies stopStream keeps going when awaiting a registered job fails unexpectedly. */
    @Test
    fun `stop stream tolerates join failures while cleaning up registered jobs`() {
        val failingJoinManager = object : BoardConnectionManager(StateStore(HardwareState(synthetic = false))) {
            override fun awaitRegisteredStreamingJob(job: Job) {
                throw RuntimeException("join failed")
            }
        }
        val shim = object : BoardShim(BoardIds.NEUROPAWN_KNIGHT_BOARD, BrainFlowInputParams()) {
            override fun prepare_session() = Unit
            override fun start_stream(buffer_size: Int, streamer_params: String?) = Unit
        }
        val managerWithShim = BoardConnectionManager(
            stateStore = StateStore(HardwareState(synthetic = false)),
            boardShimFactory = { _, _ -> shim },
            samplingRateProvider = { 128 }
        )
        managerWithShim.registerStreamingJob(Job())

        invokeCancelRegisteredStreamingJob(managerWithShim)
        assertNull(registeredStreamingJob(managerWithShim))

        failingJoinManager.registerStreamingJob(Job())
        failingJoinManager.stopStream()
        assertNull(registeredStreamingJob(failingJoinManager))
        assertFalse(failingJoinManager.state.value.streaming)
    }

    /** Verifies interruption during the private runBlocking wait still clears the stored job reference. */
    @Test
    fun `cancel registered streaming job handles interrupted runBlocking`() {
        val localManager = BoardConnectionManager(StateStore(HardwareState(synthetic = true)))
        localManager.registerStreamingJob(Job())

        Thread.currentThread().interrupt()
        try {
            invokeCancelRegisteredStreamingJob(localManager)
            assertNull(registeredStreamingJob(localManager))
        } finally {
            Thread.interrupted()
        }
    }

}

