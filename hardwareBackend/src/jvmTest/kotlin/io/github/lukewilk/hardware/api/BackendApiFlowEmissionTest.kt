package io.github.lukewilk.hardware.api

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Flow-emission tests for `HardwareBackendApi`.
 */
class BackendApiFlowEmissionTest : BackendApiTestSupport() {
    /** Verifies the filtered flow publishes at least one non-empty emission while streaming. */
    @Test
    fun `filtered flow emits while streaming`() = runBlocking {
        prepareSyntheticStreamingScenario()
        assertTrue(api.startStreaming())

        val filtered = withTimeout(2_000) { api.filteredFlow.first { it.isNotEmpty() } }

        assertTrue(filtered.isNotEmpty(), "Expected filteredFlow to emit data while streaming")
    }

    /** Verifies the band-power flow publishes at least one non-empty emission while streaming. */
    @Test
    fun `band powers flow emits while streaming`() = runBlocking {
        prepareSyntheticStreamingScenario()
        assertTrue(api.startStreaming())

        val bandPowers = withTimeout(2_000) { api.bandPowersFlow.first { it.isNotEmpty() } }

        assertTrue(bandPowers.isNotEmpty(), "Expected bandPowersFlow to emit data while streaming")
    }

    /** Verifies the FFT flow publishes at least one non-empty emission while streaming. */
    @Test
    fun `fft result flow emits while streaming`() = runBlocking {
        prepareSyntheticStreamingScenario()
        assertTrue(api.startStreaming())

        val fftResult = withTimeout(2_000) { api.fftResultFlow.first { it.isNotEmpty() } }

        assertTrue(fftResult.isNotEmpty(), "Expected fftResultFlow to emit data while streaming")
    }

    /** Verifies the filtered flow stays quiet until streaming starts. */
    @Test
    fun `filtered flow does not emit while not streaming`() = runBlocking {
        prepareSyntheticStreamingScenario()
        var emitted = false

        kotlin.runCatching {
            withTimeout(1_000) {
                api.filteredFlow.first { it.isNotEmpty() }
                emitted = true
            }
        }

        assertFalse(emitted, "Expected filteredFlow to stay idle while streaming is stopped")
    }

    /** Verifies nullable listeners can remain unset while the pipeline still emits data safely. */
    @Test
    fun `streaming with null listeners still emits flow updates`() = runBlocking {
        api.setOnFilteredListener(null)
        api.setOnBandPowersListener(null)
        api.setOnFFTResultListener(null)
        prepareSyntheticStreamingScenario()
        assertTrue(api.startStreaming())

        val filtered = withTimeout(2_000) { api.filteredFlow.first { it.isNotEmpty() } }

        assertTrue(filtered.isNotEmpty(), "Expected flow emissions even when listeners are null")
        assertTrue(api.getState().streaming)
    }
}

