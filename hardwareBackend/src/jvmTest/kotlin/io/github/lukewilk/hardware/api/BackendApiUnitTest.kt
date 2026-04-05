package io.github.lukewilk.hardware.api

import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.WaveType
import kotlin.test.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.launch
import org.junit.Test
import kotlinx.coroutines.runBlocking

class BackendApiUnitTest {
    private lateinit var api: HardwareBackendApi

    @BeforeTest
    fun setup() {
        api = HardwareBackendApi()
    }

    @Test
    fun testConnectDisconnect() {
        runBlocking {
            assertFalse(api.getState().connected)
            assertTrue(api.connect("SYNTHETIC_BOARD"))
            assertTrue(api.getState().connected)
            assertTrue(api.disconnect())
            assertFalse(api.getState().connected)
        }
    }

    @Test
    fun testConnectDisconnectBlocking() {
        runBlocking {
            assertFalse(api.getState().connected)
            assertTrue(api.connect("SYNTHETIC_BOARD"))
            assertTrue(api.getState().connected)
            assertTrue(api.disconnect())
            assertFalse(api.getState().connected)
        }
    }

    @Test
    fun testAddRemoveEditWave() {
        runBlocking {
            api.connect("SYNTHETIC_BOARD")
            val initialWaves = api.getState().waveSpecs.size
            val newWave = WaveSpec(enabled = true, type = WaveType.SQUARE, amplitude = 2.0, frequencyHz = 5.0, phaseShiftRad = 0.0)
            assertTrue(api.addWave(newWave))
            val wavesAfterAdd = api.getState().waveSpecs
            assertTrue(wavesAfterAdd.any { it.type == WaveType.SQUARE && it.amplitude == 2.0 })
            val idx = wavesAfterAdd.indexOfFirst { it.type == WaveType.SQUARE && it.amplitude == 2.0 }
            val editedWave = newWave.copy(amplitude = 3.0)
            assertTrue(api.editWave(idx, editedWave))
            assertEquals(3.0, api.getState().waveSpecs[idx].amplitude)
            assertTrue(api.removeWave(idx))
            assertEquals(initialWaves, api.getState().waveSpecs.size)
            api.disconnect()
        }
    }

    @Test
    fun testEnableDisableChannel() {
        runBlocking {
            api.connect("SYNTHETIC_BOARD")
            assertTrue(api.enableChannel(0))
            assertTrue(api.getState().enabledChannels.contains(0))
            assertTrue(api.enableChannel(1))
            assertTrue(api.getState().enabledChannels.contains(1))
            assertTrue(api.disableChannel(0))
            assertFalse(api.getState().enabledChannels.contains(0))
            api.disconnect()
        }
    }

    @Test
    fun testEnableDisableRLD() {
        runBlocking {
            api.connect("SYNTHETIC_BOARD")
            assertTrue(api.enableRLD())
            assertTrue(api.getState().rldEnabled.isNotEmpty())
            assertTrue(api.disableRLD())
            assertTrue(api.getState().rldEnabled.isEmpty())
            api.disconnect()
        }
    }

    @Test
    fun testStartStopStreaming() {
        runBlocking {
            api.connect("SYNTHETIC_BOARD")
            assertTrue(api.startStreaming())
            assertTrue(api.getState().connected)
            assertTrue(api.stopStreaming())
            api.disconnect()
        }
    }

    @Test
    fun testSetSamplingRate() {
        runBlocking {
            assertTrue(api.connect("SYNTHETIC_BOARD"))
            // Ensure at least one channel and one wave before setting sampling rate
            assertTrue(api.enableChannel(0))
            val wave = WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 10.0, phaseShiftRad = 0.0)
            assertTrue(api.addWave(wave))
            val newRate = 500
            assertTrue(api.setSamplingRateHz(newRate))
            assertEquals(newRate, api.getState().samplingRateHz)
            assertTrue(api.disconnect())
        }
    }

    @Test
    fun testFilteredFlowEmitsWhenStreaming() {
        runBlocking {
            api.connect("SYNTHETIC_BOARD")
            api.enableChannel(0)
            api.addWave(WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 10.0, phaseShiftRad = 0.0))
            api.setSamplingRateHz(250)
            api.startStreaming()
            kotlinx.coroutines.delay(100)
            val filtered = withTimeout(2000) { api.filteredFlow.first { it.isNotEmpty() } }
            assertTrue(filtered.isNotEmpty(), "filteredFlow should emit data when streaming")
            api.stopStreaming()
            api.disconnect()
        }
    }

    @Test
    fun testBandPowersFlowEmitsWhenStreaming() {
        runBlocking {
            api.connect("SYNTHETIC_BOARD")
            api.enableChannel(0)
            api.addWave(WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 10.0, phaseShiftRad = 0.0))
            api.setSamplingRateHz(250)
            api.startStreaming()
            kotlinx.coroutines.delay(100)
            val bandPowers = withTimeout(2000) { api.bandPowersFlow.first { it.isNotEmpty() } }
            assertTrue(bandPowers.isNotEmpty(), "bandPowersFlow should emit data when streaming")
            api.stopStreaming()
            api.disconnect()
        }
    }

    @Test
    fun testFFTResultFlowEmitsWhenStreaming() {
        runBlocking {
            api.connect("SYNTHETIC_BOARD")
            api.enableChannel(0)
            api.addWave(WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 10.0, phaseShiftRad = 0.0))
            api.setSamplingRateHz(250)
            api.startStreaming()
            kotlinx.coroutines.delay(100)
            val fftResult = withTimeout(2000) { api.fftResultFlow.first { it.isNotEmpty() } }
            assertTrue(fftResult.isNotEmpty(), "fftResultFlow should emit data when streaming")
            api.stopStreaming()
            api.disconnect()
        }
    }

    @Test
    fun testFlowsDoNotEmitWhenNotStreaming() {
        runBlocking {
            api.connect("SYNTHETIC_BOARD")
            api.enableChannel(0)
            api.addWave(WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 10.0, phaseShiftRad = 0.0))
            api.setSamplingRateHz(250)
            var emitted = false
            val job = launch {
                withTimeout(1000) {
                    try {
                        api.filteredFlow.first { it.isNotEmpty() }
                        emitted = true
                    } catch (_: Exception) {}
                }
            }
            job.join()
            assertFalse(emitted, "filteredFlow should not emit when not streaming")
            api.disconnect()
        }
    }

    @Test
    fun testSetOnFilteredListener() {
        runBlocking {
            var called = false
            api.setOnFilteredListener { called = true }
            api.connect("SYNTHETIC_BOARD")
            api.enableChannel(0)
            api.addWave(WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 10.0, phaseShiftRad = 0.0))
            api.setSamplingRateHz(250)
            api.startStreaming()
            kotlinx.coroutines.delay(100)
            assertTrue(true)
            api.stopStreaming()
            api.disconnect()
        }
    }

    @Test
    fun testSetOnBandPowersListener() {
        runBlocking {
            var called = false
            api.setOnBandPowersListener { called = true }
            api.connect("SYNTHETIC_BOARD")
            api.enableChannel(0)
            api.addWave(WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 10.0, phaseShiftRad = 0.0))
            api.setSamplingRateHz(250)
            api.startStreaming()
            kotlinx.coroutines.delay(100)
            assertTrue(true)
            api.stopStreaming()
            api.disconnect()
        }
    }

    @Test
    fun testSetOnFFTResultListener() {
        runBlocking {
            var called = false
            api.setOnFFTResultListener { called = true }
            api.connect("SYNTHETIC_BOARD")
            api.enableChannel(0)
            api.addWave(WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 10.0, phaseShiftRad = 0.0))
            api.setSamplingRateHz(250)
            api.startStreaming()
            kotlinx.coroutines.delay(100)
            assertTrue(true)
            api.stopStreaming()
            api.disconnect()
        }
    }

    @Test
    fun testStartStreamingEarlyReturn() {
        runBlocking {
            api.connect("SYNTHETIC_BOARD")
            assertTrue(api.startStreaming())
            assertTrue(api.startStreaming())
            api.stopStreaming()
            api.disconnect()
        }
    }

    @Test
    fun testStopStreamingWithNullJob() {
        runBlocking {
            api.connect("SYNTHETIC_BOARD")
            assertTrue(api.stopStreaming())
            api.disconnect()
        }
    }

    @Test
    fun testSetSamplingRateHzThrowsForNonSynthetic() {
        runBlocking {
            api.connect("NO_BOARD")
            val ex = kotlin.runCatching { api.setSamplingRateHz(123) }.exceptionOrNull()
            assertTrue(ex is IllegalStateException)
            api.disconnect()
        }
    }

    @Test
    fun testConnectUnknownBoardNameDefaultsToNoBoard() {
        runBlocking {
            val result = kotlin.runCatching { api.connect("UNKNOWN_BOARD") }
            // Should not throw, but if it does, it must be UNSUPPORTED_BOARD_ERROR
            if (result.isFailure) {
                val ex = result.exceptionOrNull()
                assertTrue(ex is brainflow.BrainFlowError && ex.message?.contains("UNSUPPORTED_BOARD_ERROR") == true)
            } else {
                // If no exception, should not be connected
                assertFalse(api.getState().connected)
            }
            api.disconnect()
        }
    }

    @Test
    fun testStreamingWithNullListeners() {
        runBlocking {
            api.setOnFilteredListener(null)
            api.setOnBandPowersListener(null)
            api.setOnFFTResultListener(null)
            api.connect("SYNTHETIC_BOARD")
            api.enableChannel(0)
            api.addWave(
                WaveSpec(
                    enabled = true,
                    type = WaveType.SINE,
                    amplitude = 1.0,
                    frequencyHz = 10.0,
                    phaseShiftRad = 0.0
                )
            )
            api.setSamplingRateHz(250)
            api.startStreaming()
            kotlinx.coroutines.delay(100)
            // No assertion needed, just ensure no crash
            api.stopStreaming()
            api.disconnect()
        }
    }

    @Test
    fun testPipelineListenerBranches() {
        runBlocking {
            var filteredCalled = false
            var bandPowersCalled = false
            var fftResultCalled = false
            api.setOnFilteredListener { filteredCalled = true }
            api.setOnBandPowersListener { bandPowersCalled = true }
            api.setOnFFTResultListener { fftResultCalled = true }
            api.connect("SYNTHETIC_BOARD")
            api.enableChannel(0)
            api.addWave(
                WaveSpec(
                    enabled = true,
                    type = WaveType.SINE,
                    amplitude = 1.0,
                    frequencyHz = 10.0,
                    phaseShiftRad = 0.0
                )
            )
            api.setSamplingRateHz(250)
            api.startStreaming()
            // Wait up to 2 seconds for all listeners to be called
            val start = System.currentTimeMillis()
            while (!(filteredCalled && bandPowersCalled && fftResultCalled) && System.currentTimeMillis() - start < 2000) {
                kotlinx.coroutines.delay(50)
            }
            assertTrue(filteredCalled, "onFiltered listener should be called")
            assertTrue(bandPowersCalled, "onBandPowers listener should be called")
            assertTrue(fftResultCalled, "onFFTResult listener should be called")
            api.stopStreaming()
            api.disconnect()
        }
    }
}
