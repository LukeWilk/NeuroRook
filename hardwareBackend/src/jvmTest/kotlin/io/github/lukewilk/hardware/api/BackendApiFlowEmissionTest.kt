package io.github.lukewilk.hardware.api

import io.github.lukewilk.shared.model.ChannelData
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.assertEquals
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

        val filtered = withTimeout(2_000) { api.filteredFlow.first { it.payload.isNotEmpty() } }

        assertTrue(filtered.payload.isNotEmpty(), "Expected filteredFlow to emit data while streaming")
    }

    /** Verifies the band-power flow publishes at least one non-empty emission while streaming. */
    @Test
    fun `band powers flow emits while streaming`() = runBlocking {
        prepareSyntheticStreamingScenario()
        assertTrue(api.startStreaming())

        val bandPowers = withTimeout(2_000) { api.bandPowersFlow.first { it.payload.isNotEmpty() } }

        assertTrue(bandPowers.payload.isNotEmpty(), "Expected bandPowersFlow to emit data while streaming")
    }

    /** Verifies the FFT flow publishes at least one non-empty emission while streaming. */
    @Test
    fun `fft result flow emits while streaming`() = runBlocking {
        prepareSyntheticStreamingScenario()
        assertTrue(api.startStreaming())

        val fftResult = withTimeout(2_000) { api.fftResultFlow.first { it.payload.isNotEmpty() } }

        assertTrue(fftResult.payload.isNotEmpty(), "Expected fftResultFlow to emit data while streaming")
    }

    /** Verifies the filtered flow stays quiet until streaming starts. */
    @Test
    fun `filtered flow does not emit while not streaming`() = runBlocking {
        prepareSyntheticStreamingScenario()
        var emitted = false

        kotlin.runCatching {
            withTimeout(1_000) {
                api.filteredFlow.first { it.payload.isNotEmpty() }
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

        val filtered = withTimeout(2_000) { api.filteredFlow.first { it.payload.isNotEmpty() } }

        assertTrue(filtered.payload.isNotEmpty(), "Expected flow emissions even when listeners are null")
        assertTrue(api.getState().streaming)
    }

    /** Verifies filtered preferred-flow emissions keep their source channel id under interleaved multi-channel streaming. */
    @Test
    fun `filtered flow preserves channel identity across enabled channels`() = runBlocking {
        prepareSyntheticStreamingScenario()
        assertTrue(api.enableChannel(1))
        assertTrue(api.enableChannel(2))
        val expectedChannels = api.getState().enabledChannels.toSet()
        assertTrue(api.startStreaming())

        val receivedChannels = awaitChannels(api.filteredFlow, expectedChannels) { it.isNotEmpty() }

        assertEquals(expectedChannels, receivedChannels)
    }

    /** Verifies band-power preferred-flow emissions keep their source channel id under interleaved multi-channel streaming. */
    @Test
    fun `band powers flow preserves channel identity across enabled channels`() = runBlocking {
        prepareSyntheticStreamingScenario()
        assertTrue(api.enableChannel(1))
        assertTrue(api.enableChannel(2))
        val expectedChannels = api.getState().enabledChannels.toSet()
        assertTrue(api.startStreaming())

        val receivedChannels = awaitChannels(api.bandPowersFlow, expectedChannels) { it.isNotEmpty() }

        assertEquals(expectedChannels, receivedChannels)
    }

    /** Verifies FFT preferred-flow emissions keep their source channel id under interleaved multi-channel streaming. */
    @Test
    fun `fft result flow preserves channel identity across enabled channels`() = runBlocking {
        prepareSyntheticStreamingScenario()
        assertTrue(api.enableChannel(1))
        assertTrue(api.enableChannel(2))
        val expectedChannels = api.getState().enabledChannels.toSet()
        assertTrue(api.startStreaming())

        val receivedChannels = awaitChannels(api.fftResultFlow, expectedChannels) { it.isNotEmpty() }

        assertEquals(expectedChannels, receivedChannels)
    }

    /** Verifies stopping a stream clears preferred-flow replay caches so new collectors do not see stale session data. */
    @Test
    fun `stopped preferred flows do not replay stale session data`() = runBlocking {
        prepareSyntheticStreamingScenario()
        assertTrue(api.startStreaming())

        withTimeout(2_000) { api.filteredFlow.first { it.payload.isNotEmpty() } }
        withTimeout(2_000) { api.bandPowersFlow.first { it.payload.isNotEmpty() } }
        withTimeout(2_000) { api.fftResultFlow.first { it.payload.isNotEmpty() } }

        assertTrue(api.stopStreaming())
        delay(200)

        assertFalse(collectsAnyEmissionWithin(api.filteredFlow), "Filtered flow should stay idle after stop without replaying stale data")
        assertFalse(collectsAnyEmissionWithin(api.bandPowersFlow), "Band-power flow should stay idle after stop without replaying stale data")
        assertFalse(collectsAnyEmissionWithin(api.fftResultFlow), "FFT flow should stay idle after stop without replaying stale data")
    }

    /** Keeps one collector subscribed until every expected channel id has produced a payload, avoiding replay-only assertions. */
    private suspend fun <T> awaitChannels(
        flow: Flow<ChannelData<T>>,
        expectedChannels: Set<Int>,
        hasPayload: (T) -> Boolean
    ): Set<Int> {
        val seenChannels = mutableSetOf<Int>()
        withTimeout(4_000) {
            flow.first { emission ->
                if (hasPayload(emission.payload)) {
                    seenChannels += emission.channelId
                }
                seenChannels.containsAll(expectedChannels)
            }
        }
        return seenChannels
    }

    /** Returns true only when a brand-new collector can observe an emission within the timeout window. */
    private suspend fun <T> collectsAnyEmissionWithin(flow: Flow<T>, timeoutMillis: Long = 250): Boolean {
        return try {
            withTimeout(timeoutMillis) {
                flow.first()
                true
            }
        } catch (_: TimeoutCancellationException) {
            false
        }
    }
}
