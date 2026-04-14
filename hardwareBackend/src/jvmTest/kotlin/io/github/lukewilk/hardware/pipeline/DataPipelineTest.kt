package io.github.lukewilk.hardware.pipeline

import io.github.lukewilk.shared.Band
import io.github.lukewilk.shared.FilterConfig
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Callback and configuration tests for `startDataPipeline`.
 */
class DataPipelineTest : DataPipelineTestSupport() {

    /** Verifies the full pipeline processes filters and invokes every callback type for a synthetic stream. */
    @Test
    fun `start data pipeline processes filters and invokes callbacks`() = runBlocking {
        val stateStore = filteredPipelineStateStore()
        val manager = io.github.lukewilk.hardware.BoardConnectionManager(stateStore)
        connectAndStartSynthetic(manager)

        var filteredCalled = false
        var bandPowersCalled = false
        var fftCalled = false

        val pipelineJob = launch {
            startDataPipeline(
                onFiltered = {
                    filteredCalled = true
                    // Flip sampling to the fallback path after the first callback so the run covers both rates.
                    stateStore.update { st -> st.copy(samplingRateHz = 0) }
                },
                onBandPowers = {
                    if (it.isNotEmpty()) bandPowersCalled = true
                },
                onFFTResult = {
                    if (it.isNotEmpty()) fftCalled = true
                },
                stateStore = stateStore,
                manager = manager
            )
        }

        waitUntil { filteredCalled && bandPowersCalled && fftCalled }

        manager.stopStream()
        waitUntil(timeoutMs = 3_000) { pipelineJob.isCompleted }

        assertTrue(filteredCalled)
        assertTrue(bandPowersCalled)
        assertTrue(fftCalled)
        assertTrue(pipelineJob.isCompleted)
        manager.close()
    }

    /** Verifies the pipeline can run successfully when all callbacks are omitted. */
    @Test
    fun `start data pipeline supports null callbacks`() = runBlocking {
        val stateStore = pipelineStateStore()
        val manager = io.github.lukewilk.hardware.BoardConnectionManager(stateStore)
        connectAndStartSynthetic(manager)

        val pipelineJob = launch {
            startDataPipeline(
                stateStore = stateStore,
                manager = manager
            )
        }

        kotlinx.coroutines.delay(200)
        manager.stopStream()
        waitUntil(timeoutMs = 3_000) { pipelineJob.isCompleted }

        assertTrue(pipelineJob.isCompleted)
        manager.close()
    }

    /** Verifies default sampling-rate fallback, no filters, and empty bands still produce filtered output. */
    @Test
    fun `start data pipeline handles default sampling no filters and empty bands`() = runBlocking {
        val stateStore = pipelineStateStore(
            samplingRateHz = 0,
            bands = emptyList(),
            filterConfig = FilterConfig(bandpass = null, bandstopFilters = emptyList())
        )
        val manager = io.github.lukewilk.hardware.BoardConnectionManager(stateStore)
        connectAndStartSynthetic(manager)

        var filteredLength = 0
        var bandPowerSize = -1
        val pipelineJob = launch {
            startDataPipeline(
                onFiltered = { filteredLength = it.size },
                onBandPowers = { bandPowerSize = it.size },
                stateStore = stateStore,
                manager = manager
            )
        }

        waitUntil { filteredLength == 32 && bandPowerSize >= 0 }

        manager.stopStream()
        waitUntil(timeoutMs = 3_000) { pipelineJob.isCompleted }

        assertEquals(32, filteredLength)
        assertEquals(0, bandPowerSize, "Empty bands should produce an empty band-power list")
        manager.close()
    }

    /** Verifies the pipeline supports installing only a filtered-signal callback. */
    @Test
    fun `start data pipeline supports mixed callback configuration`() = runBlocking {
        val stateStore = pipelineStateStore()
        val manager = io.github.lukewilk.hardware.BoardConnectionManager(stateStore)
        connectAndStartSynthetic(manager)

        var filteredCalled = false
        val pipelineJob = launch {
            startDataPipeline(
                onFiltered = { filteredCalled = true },
                onBandPowers = null,
                onFFTResult = null,
                stateStore = stateStore,
                manager = manager
            )
        }

        waitUntil { filteredCalled }

        manager.stopStream()
        waitUntil(timeoutMs = 3_000) { pipelineJob.isCompleted }

        assertTrue(filteredCalled)
        manager.close()
    }

    /** Verifies the pipeline can emit band powers when only the band-power callback is installed. */
    @Test
    fun `start data pipeline supports only band power callback`() = runBlocking {
        val stateStore = pipelineStateStore(bands = listOf(Band("Alpha", 8.0, 12.0)))
        val manager = io.github.lukewilk.hardware.BoardConnectionManager(stateStore)
        connectAndStartSynthetic(manager)

        var bandPowersCalled = false
        val pipelineJob = launch {
            startDataPipeline(
                onBandPowers = { if (it.isNotEmpty()) bandPowersCalled = true },
                stateStore = stateStore,
                manager = manager
            )
        }

        waitUntil { bandPowersCalled }

        manager.stopStream()
        waitUntil(timeoutMs = 3_000) { pipelineJob.isCompleted }

        assertTrue(bandPowersCalled)
        manager.close()
    }

    /** Verifies the pipeline can emit FFT results when only the FFT callback is installed. */
    @Test
    fun `start data pipeline supports only fft callback`() = runBlocking {
        val stateStore = pipelineStateStore()
        val manager = io.github.lukewilk.hardware.BoardConnectionManager(stateStore)
        connectAndStartSynthetic(manager)

        var fftCalled = false
        val pipelineJob = launch {
            startDataPipeline(
                onFFTResult = { if (it.isNotEmpty()) fftCalled = true },
                stateStore = stateStore,
                manager = manager
            )
        }

        waitUntil { fftCalled }

        manager.stopStream()
        waitUntil(timeoutMs = 3_000) { pipelineJob.isCompleted }

        assertTrue(fftCalled)
        manager.close()
    }
}
