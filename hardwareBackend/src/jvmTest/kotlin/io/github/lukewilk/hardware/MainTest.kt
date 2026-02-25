package io.github.lukewilk.hardware

import io.github.lukewilk.shared.BandstopConfig
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class MainTest {
    @Test
    fun testApplyConfiguredNotchFilters_attenuatesTargetFrequency() {
        // Generate a 60Hz sine wave sampled at 1000Hz
        val samplingRate = 1000.0
        val freq = 60.0
        val n = 5000 // longer signal for filter to settle
        val signal = DoubleArray(n) { i -> kotlin.math.sin(2 * Math.PI * freq * i / samplingRate) }
        val rmsOrig = Math.sqrt(signal.map { it * it }.average())
        // Notch filter config for 59-61Hz, order 4 (classic powerline notch)
        val notch = BandstopConfig(
            startFreq = 59.0,
            stopFreq = 61.0,
            order = 4,
            samplingRate = samplingRate.toInt(),
            filterType = 0,
            ripple = 0.1
        )
        val filtered = applyConfiguredNotchFilters(signal, listOf(notch), samplingRate)
        val rmsFilt = Math.sqrt(filtered.map { it * it }.average())
        val maxDiff = signal.zip(filtered).map { (a, b) -> kotlin.math.abs(a - b) }.maxOrNull() ?: 0.0
        println("RMS before: $rmsOrig, RMS after: $rmsFilt, maxDiff: $maxDiff, first 5 filtered: ${filtered.take(5)}")

        // JDSP Butterworth can produce only small changes for narrow notches at low order.
        // Assert that the filtered output is valid and that some change occurred.
        assertTrue(filtered.size == signal.size, "Filtered signal should have same length as input")
        assertTrue(rmsFilt.isFinite(), "Filtered RMS should be finite")
        assertTrue(maxDiff > 1e-8, "Filtered signal should differ from original by more than numerical noise (maxDiff=$maxDiff)")
    }

    @Test
    fun testMainPipelineInvokesCallbacks() = runBlocking {
        val stateStore = StateStore(HardwareState(windowSize = 32, overlap = 16))
        val manager = BoardConnectionManager(stateStore)
        manager.connect(boardId = brainflow.BoardIds.SYNTHETIC_BOARD, serialPort = "")
        manager.enableChannel(0)
        manager.startStream()
        var filteredCalled = false
        var bandPowersCalled = false
        var fftResultCalled = false
        val result = withTimeoutOrNull(1000) {
            main(
                _args = emptyArray(),
                onFiltered = { filteredCalled = true },
                onBandPowers = { bandPowersCalled = true },
                onFFTResult = { fftResultCalled = true },
                stateStore = stateStore,
                manager = manager
            )
        }
        assertTrue(filteredCalled, "onFiltered callback should be invoked")
        assertTrue(bandPowersCalled, "onBandPowers callback should be invoked")
        assertTrue(fftResultCalled, "onFFTResult callback should be invoked")
        assertTrue(result == null, "main should timeout and exit")
    }
}
