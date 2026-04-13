package io.github.lukewilk.hardware.api

import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.WaveType
import kotlinx.coroutines.runBlocking
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Listener-callback tests for `HardwareBackendApi`.
 */
class BackendApiListenerCallbackTest : BackendApiTestSupport() {
    /** Verifies the filtered listener receives a callback once the pipeline begins publishing. */
    @Test
    fun `filtered listener is invoked when streaming starts`() = runBlocking {
        var called = false
        api.setOnFilteredListener { called = true }
        prepareSyntheticStreamingScenario()
        assertTrue(api.startStreaming())

        assertTrue(awaitCondition { called }, "Expected the filtered listener to be invoked")
    }

    /** Verifies the band-power listener receives a callback once the pipeline begins publishing. */
    @Test
    fun `band power listener is invoked when streaming starts`() = runBlocking {
        var called = false
        api.setOnBandPowersListener { called = true }
        prepareSyntheticStreamingScenario()
        assertTrue(api.startStreaming())

        assertTrue(awaitCondition { called }, "Expected the band-power listener to be invoked")
    }

    /** Verifies the FFT listener receives a callback once the pipeline begins publishing. */
    @Test
    fun `fft listener is invoked when streaming starts`() = runBlocking {
        var called = false
        api.setOnFFTResultListener { called = true }
        prepareSyntheticStreamingScenario()
        assertTrue(api.startStreaming())

        assertTrue(awaitCondition { called }, "Expected the FFT listener to be invoked")
    }

    /** Verifies listeners still receive data for a non-default synthetic waveform configuration. */
    @Test
    fun `listeners receive updates for an alternate waveform`() = runBlocking {
        var called = false
        api.setOnFilteredListener { called = true }
        api.connect("SYNTHETIC_BOARD")
        api.enableChannel(0)
        api.addWave(
            WaveSpec(
                enabled = true,
                type = WaveType.SQUARE,
                amplitude = 2.0,
                frequencyHz = 5.0,
                phaseShiftRad = 0.0
            )
        )
        api.setSamplingRateHz(250)
        assertTrue(api.startStreaming())

        assertTrue(awaitCondition { called }, "Expected the filtered listener to receive data for the alternate waveform")
    }
}

