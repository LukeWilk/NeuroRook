package io.github.lukewilk.hardware.pipeline

import brainflow.BoardIds
import io.github.lukewilk.hardware.BoardConnectionManager
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Failure-handling and cleanup tests for `startDataPipeline`.
 */
class DataPipelineFailureAndCleanupTest : DataPipelineTestSupport() {

    /** Verifies the pipeline cancels cleanly when started while the state is already marked non-streaming. */
    @Test
    fun `start data pipeline completes when not streaming`() = runBlocking {
        val stateStore = StateStore(
            HardwareState(
                connected = true,
                streaming = false,
                synthetic = true,
                samplingRateHz = 250,
                channels = 1,
                enabledChannels = listOf(0),
                waveSpecs = pipelineStateStore().get().waveSpecs
            )
        )
        val manager = BoardConnectionManager(stateStore)
        assertTrue(manager.connect(BoardIds.SYNTHETIC_BOARD, serialPort = ""))

        val pipelineJob = launch {
            startDataPipeline(
                onFiltered = null,
                onBandPowers = null,
                onFFTResult = null,
                stateStore = stateStore,
                manager = manager
            )
        }

        withTimeout(3_000) { pipelineJob.join() }
        manager.close()

        assertTrue(pipelineJob.isCompleted)
    }

    /** Verifies the pipeline keeps processing frames even if job registration fails and still cleans up state. */
    @Test
    fun `start data pipeline continues when job registration fails and cleans up`() = runBlocking {
        val stateStore = pipelineStateStore()
        val throwingManager = object : BoardConnectionManager(stateStore) {
            override fun registerStreamingJob(job: kotlinx.coroutines.Job?) {
                if (job == null) {
                    throw RuntimeException("cleanup registration failed")
                }
                throw RuntimeException("registration failed")
            }
        }
        assertTrue(throwingManager.connect(BoardIds.SYNTHETIC_BOARD, serialPort = ""))
        throwingManager.startStream()

        var filteredCalled = false
        val pipelineJob = launch {
            startDataPipeline(
                onFiltered = { filteredCalled = true },
                stateStore = stateStore,
                manager = throwingManager
            )
        }

        waitUntil { filteredCalled }

        pipelineJob.cancel()
        throwingManager.stopStream()
        withTimeout(3_000) { pipelineJob.join() }

        assertTrue(filteredCalled)
        assertTrue(!throwingManager.state.value.streaming)
        assertTrue(throwingManager.state.value.connected)
    }

    /** Verifies callback failures are rethrown only after the pipeline unregisters its streaming job. */
    @Test
    fun `start data pipeline rethrows callback failure after cleanup`() = runBlocking {
        val stateStore = pipelineStateStore()
        var cleanupCalled = false
        val manager = object : BoardConnectionManager(stateStore) {
            override fun registerStreamingJob(job: kotlinx.coroutines.Job?) {
                if (job == null) {
                    cleanupCalled = true
                }
                super.registerStreamingJob(job)
            }
        }
        assertTrue(manager.connect(BoardIds.SYNTHETIC_BOARD, serialPort = ""))
        manager.startStream()

        val failure = kotlin.runCatching {
            withTimeout(3_000) {
                startDataPipeline(
                    onFiltered = { throw IllegalStateException("callback failed") },
                    stateStore = stateStore,
                    manager = manager
                )
            }
        }.exceptionOrNull()

        manager.stopStream()

        assertTrue(failure is IllegalStateException, "The original callback failure should be rethrown after cleanup")
        assertTrue(cleanupCalled, "Pipeline cleanup should unregister the streaming job before propagating the failure")
    }

    /** Verifies cleanup tolerates unregister failures after the pipeline stops without aborting the host coroutine. */
    @Test
    fun `start data pipeline survives streaming job unregister failure during cleanup`() = runBlocking {
        val stateStore = pipelineStateStore()
        val manager = object : BoardConnectionManager(stateStore) {
            override fun registerStreamingJob(job: kotlinx.coroutines.Job?) {
                if (job == null) {
                    throw RuntimeException("simulated unregister failure")
                }
                super.registerStreamingJob(job)
            }
        }
        assertTrue(manager.connect(BoardIds.SYNTHETIC_BOARD, serialPort = ""))
        manager.startStream()

        val pipelineJob = launch {
            startDataPipeline(
                onFiltered = null,
                onBandPowers = null,
                onFFTResult = null,
                stateStore = stateStore,
                manager = manager
            )
        }

        delay(100)
        pipelineJob.cancel()
        withTimeout(3_000) { pipelineJob.join() }
        manager.stopStream()
        manager.close()

        assertTrue(pipelineJob.isCompleted)
    }
}

