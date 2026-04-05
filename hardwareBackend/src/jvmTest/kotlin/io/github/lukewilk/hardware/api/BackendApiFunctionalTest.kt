package io.github.lukewilk.hardware.api

import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.WaveType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackendApiFunctionalTest {
    private lateinit var api: HardwareBackendApi

    @Before
    fun setup() {
        api = HardwareBackendApi()
    }

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
            val filtered = api.filteredFlow.first { it.isNotEmpty() }
            assertTrue(filtered.isNotEmpty(), "Should receive filtered data from flow")
            val bandPowers = api.bandPowersFlow.first { it.isNotEmpty() }
            assertTrue(bandPowers.isNotEmpty(), "Should receive band powers from flow")
            val fftResult = api.fftResultFlow.first { it.isNotEmpty() }
            assertTrue(fftResult.isNotEmpty(), "Should receive FFT result from flow")

            // 6. Enable two more channels
            assertTrue(api.enableChannel(1))
            assertTrue(api.enableChannel(2))
            assertTrue(api.getState().enabledChannels.containsAll(listOf(0, 1, 2)))

            // Check that we're receiving data from all enabled channels
            val enabledChannels = api.getState().enabledChannels.toSet()
            val receivedChannels = mutableSetOf<Int>()
            repeat(10) {
                val filtered = api.filteredFlow.first { it.isNotEmpty() }
                receivedChannels.addAll(0 until filtered.size)
                if (receivedChannels.containsAll(enabledChannels)) return@repeat
            }
            assertTrue(receivedChannels.containsAll(enabledChannels), "Should receive data from all enabled channels: $enabledChannels, got $receivedChannels")

            // 7. Stop streaming
            assertTrue(api.stopStreaming())

            // 8. Disconnect from the board
            assertTrue(api.disconnect())
            assertFalse(api.getState().connected)
        }
    }
}
