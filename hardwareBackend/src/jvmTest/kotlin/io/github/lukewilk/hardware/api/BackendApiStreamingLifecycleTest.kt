package io.github.lukewilk.hardware.api

import brainflow.BoardIds
import brainflow.BoardShim
import io.github.lukewilk.shared.model.SystemLogSeverity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito

/**
 * Streaming lifecycle tests for `HardwareBackendApi`.
 */
class BackendApiStreamingLifecycleTest : BackendApiTestSupport() {
    /** Verifies the happy path toggles streaming state on and back off for the synthetic board. */
    @Test
    fun `start and stop streaming toggles state`() = runBlocking {
        prepareSyntheticStreamingScenario()
        assertTrue(api.startStreaming())
        assertTrue(api.getState().streaming)
        assertTrue(api.stopStreaming())
        assertFalse(api.getState().streaming)
    }

    /** Verifies the API refuses to start streaming when no board is connected. */
    @Test
    fun `start streaming without connection logs a clear error`() = runBlocking {
        assertFalse(api.startStreaming())
        assertSystemLogContains(SystemLogSeverity.ERROR, "Cannot start stream because no board is connected")
    }

    /** Verifies the API reports start failure when the backend manager never marks streaming active. */
    @Test
    fun `start streaming returns false when the manager does not activate the stream`() = runBlocking {
        val mockShim = Mockito.mock(BoardShim::class.java)
        Mockito.doThrow(RuntimeException("start_stream failed"))
            .`when`(mockShim)
            .start_stream(anyInt(), anyString())
        setManagerBoardShim(mockShim)
        setConnectedBoardId(BoardIds.NO_BOARD)
        stateStore().update { it.copy(connected = true, synthetic = false, streaming = false) }
        assertFalse(api.startStreaming())
        assertFalse(api.getState().streaming)
        assertSystemLogContains(SystemLogSeverity.ERROR, "Stream failed to start for NO_BOARD")
    }

    /** Verifies the start failure path uses the fallback board label when the cached board id is missing. */
    @Test
    fun `start streaming failure uses fallback board label when board id is missing`() = runBlocking {
        val mockShim = Mockito.mock(BoardShim::class.java)
        Mockito.doThrow(RuntimeException("start_stream failed"))
            .`when`(mockShim)
            .start_stream(anyInt(), anyString())
        setManagerBoardShim(mockShim)
        setConnectedBoardId(null)
        stateStore().update { it.copy(connected = true, synthetic = false, streaming = false) }
        assertFalse(api.startStreaming())
        assertSystemLogContains(SystemLogSeverity.INFO, "Starting stream for the connected board")
        assertSystemLogContains(SystemLogSeverity.ERROR, "Stream failed to start for the connected board")
    }

    /** Verifies repeated start requests reuse the existing stream instead of spawning a second one. */
    @Test
    fun `start streaming returns early when streaming is already active`() = runBlocking {
        prepareSyntheticStreamingScenario()
        assertTrue(api.startStreaming())
        assertTrue(api.startStreaming())
        assertSystemLogContains(SystemLogSeverity.WARN, "Streaming is already active for SYNTHETIC_BOARD")
    }

    /** Verifies repeated start requests still log clearly when the board label must fall back to generic wording. */
    @Test
    fun `start streaming already active warns with fallback board label`() = runBlocking {
        stateStore().update { it.copy(connected = true) }
        setConnectedBoardId(null)
        val job = launch { delay(Long.MAX_VALUE) }
        setStreamingJob(job)
        assertTrue(api.startStreaming())
        assertSystemLogContains(SystemLogSeverity.WARN, "Streaming is already active for the connected board")
        job.cancel()
        setStreamingJob(null)
    }

    /** Verifies repeated start requests name the explicit board when that information is available. */
    @Test
    fun `start streaming already active warns with explicit board name`() = runBlocking {
        stateStore().update { it.copy(connected = true) }
        setConnectedBoardId(BoardIds.SYNTHETIC_BOARD)
        val job = launch { delay(Long.MAX_VALUE) }
        setStreamingJob(job)
        assertTrue(api.startStreaming())
        assertSystemLogContains(SystemLogSeverity.WARN, "Streaming is already active for SYNTHETIC_BOARD")
        job.cancel()
        setStreamingJob(null)
    }

    /** Verifies stop requests succeed even when no pipeline job was ever registered. */
    @Test
    fun `stop streaming with a null job succeeds`() = runBlocking {
        api.connect("SYNTHETIC_BOARD")
        assertTrue(api.stopStreaming())
        assertFalse(api.getState().streaming)
    }

    /** Verifies stop requests use the fallback board label when no stream was running. */
    @Test
    fun `stop streaming without an active stream uses the fallback label`() = runBlocking {
        stateStore().update { it.copy(connected = true, streaming = false) }
        setConnectedBoardId(null)
        assertTrue(api.stopStreaming())
        assertSystemLogContains(SystemLogSeverity.WARN, "no active stream was running for the current board")
    }

    /** Verifies stop requests use the explicit board name when no stream was running. */
    @Test
    fun `stop streaming without an active stream uses the explicit board label`() = runBlocking {
        stateStore().update { it.copy(connected = true, streaming = false) }
        setConnectedBoardId(BoardIds.SYNTHETIC_BOARD)
        assertTrue(api.stopStreaming())
        assertSystemLogContains(SystemLogSeverity.WARN, "no active stream was running for SYNTHETIC_BOARD")
    }

    /** Verifies stop requests still clear streaming state when the state says streaming but no job is registered. */
    @Test
    fun `stop streaming clears state when the job is missing but the state still says streaming`() = runBlocking {
        stateStore().update { it.copy(connected = true, streaming = true, synthetic = true) }
        setConnectedBoardId(null)
        setStreamingJob(null)
        assertTrue(api.stopStreaming())
        assertFalse(api.getState().streaming)
        assertSystemLogContains(SystemLogSeverity.INFO, "Stopping stream for the current board")
        assertSystemLogContains(SystemLogSeverity.INFO, "Stream stopped for the current board")
    }

    /** Verifies stop requests keep explicit board labels when a stream is being shut down. */
    @Test
    fun `stop streaming logs the explicit board name when shutting down an active stream`() = runBlocking {
        stateStore().update { it.copy(connected = true, streaming = true, synthetic = true) }
        setConnectedBoardId(BoardIds.SYNTHETIC_BOARD)
        setStreamingJob(null)
        assertTrue(api.stopStreaming())
        assertSystemLogContains(SystemLogSeverity.INFO, "Stopping stream for SYNTHETIC_BOARD")
        assertSystemLogContains(SystemLogSeverity.INFO, "Stream stopped for SYNTHETIC_BOARD")
    }

    /** Verifies a fallback-labelled synthetic lifecycle still logs both the start and stop messages. */
    @Test
    fun `streaming lifecycle uses fallback labels when the board id is missing`() = runBlocking {
        stateStore().update {
            it.copy(
                connected = true,
                synthetic = true,
                channels = 1,
                samplingRateHz = 250,
                enabledChannels = listOf(0),
                waveSpecs = listOf(standardWave())
            )
        }
        setConnectedBoardId(null)
        assertTrue(api.startStreaming())
        assertSystemLogContains(SystemLogSeverity.INFO, "Starting stream for the connected board")
        assertSystemLogContains(SystemLogSeverity.INFO, "Stream started for the connected board")
        assertTrue(api.stopStreaming())
        assertSystemLogContains(SystemLogSeverity.INFO, "Stopping stream for the current board")
        assertSystemLogContains(SystemLogSeverity.INFO, "Stream stopped for the current board")
    }

    /** Verifies disconnecting while streaming tears down both the connection and streaming state. */
    @Test
    fun `disconnect while streaming clears connection state`() = runBlocking {
        prepareSyntheticStreamingScenario()
        assertTrue(api.startStreaming())
        assertTrue(api.disconnect())
        assertFalse(api.getState().connected)
        assertFalse(api.getState().streaming)
    }
}
