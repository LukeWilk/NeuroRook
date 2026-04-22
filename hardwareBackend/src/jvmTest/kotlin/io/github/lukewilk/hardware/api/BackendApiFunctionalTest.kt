package io.github.lukewilk.hardware.api

import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.WaveType
import io.github.lukewilk.shared.model.ChannelData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-end synthetic-board workflow tests for `HardwareBackendApi`.
 *
 * These scenarios verify that preferred flows stay usable across a realistic connect/stream/stop lifecycle and that
 * channel identity survives after additional channels are enabled mid-session.
 */
class BackendApiFunctionalTest {
    private lateinit var api: HardwareBackendApi

    @Before
    fun setup() {
        api = HardwareBackendApi()
    }

    /** Exercises a full synthetic workflow and confirms all preferred flows preserve enabled channel ids. */
    @Test
    fun testFullSyntheticBoardWorkflow() {
        runBlocking {
            // 1. Connect to synthetic board
            assertTrue(api.connect("SYNTHETIC_BOARD"))
            assertTrue(api.getState().connected)

            // 2. Change sampling rate
            val newRate = 500
            assertTrue(api.setSamplingRateHz(newRate))
            assertEquals(newRate, api.getState().samplingRateHz)

            // 3. Add new synthetic waves
            assertTrue(api.getState().waveSpecs.isEmpty(), "Should start with no default waves")
            val wave1 = WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 10.0, phaseShiftRad = 0.0)
            val wave2 = WaveSpec(enabled = true, type = WaveType.SQUARE, amplitude = 2.0, frequencyHz = 5.0, phaseShiftRad = 0.0)
            assertTrue(api.addWave(wave1))
            assertTrue(api.addWave(wave2))
            val waves = api.getState().waveSpecs
            assertTrue(waves.any { it.type == WaveType.SINE })
            assertTrue(waves.any { it.type == WaveType.SQUARE })

            // 4. Enable one channel
            assertTrue(api.enableChannel(0))
            assertTrue(api.getState().enabledChannels.contains(0))

            // 5. Start streaming
            assertTrue(api.startStreaming())

            // Check that we're receiving data using Flow-based API
            val filtered = api.filteredFlow.first { it.payload.isNotEmpty() }
            assertTrue(filtered.payload.isNotEmpty(), "Should receive filtered data from flow")
            val bandPowers = api.bandPowersFlow.first { it.payload.isNotEmpty() }
            assertTrue(bandPowers.payload.isNotEmpty(), "Should receive band powers from flow")
            val fftResult = api.fftResultFlow.first { it.payload.isNotEmpty() }
            assertTrue(fftResult.payload.isNotEmpty(), "Should receive FFT result from flow")

            // 6. Enable two more channels
            assertTrue(api.enableChannel(1))
            assertTrue(api.enableChannel(2))
            assertTrue(api.getState().enabledChannels.containsAll(listOf(0, 1, 2)))

            // Check that channel identities remain intact on interleaved flow emissions.
            val enabledChannels = api.getState().enabledChannels.toSet()
            val filteredChannels = awaitChannels(api.filteredFlow, enabledChannels) { it.isNotEmpty() }
            val bandPowerChannels = awaitChannels(api.bandPowersFlow, enabledChannels) { it.isNotEmpty() }
            val fftChannels = awaitChannels(api.fftResultFlow, enabledChannels) { it.isNotEmpty() }
            assertEquals(enabledChannels, filteredChannels, "Filtered flow should preserve enabled channel ids")
            assertEquals(enabledChannels, bandPowerChannels, "Band-power flow should preserve enabled channel ids")
            assertEquals(enabledChannels, fftChannels, "FFT flow should preserve enabled channel ids")

            // 7. Stop streaming
            assertTrue(api.stopStreaming())

            // 8. Disconnect from the board
            assertTrue(api.disconnect())
            assertFalse(api.getState().connected)
        }
    }

    /** Keeps one preferred-flow collector alive until every enabled channel has emitted, avoiding replay-only assertions. */
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
}
