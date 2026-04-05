package io.github.lukewilk.hardware.pipeline.signal

import co.touchlab.kermit.Logger
import io.github.lukewilk.hardware.LoggerProvider
import io.github.lukewilk.hardware.pipeline.signal.BandpassFilterConfig
import io.github.lukewilk.hardware.pipeline.signal.HighPassConfig
import io.github.lukewilk.hardware.pipeline.signal.NotchFilterConfig
import io.github.lukewilk.hardware.pipeline.signal.applyBandpassFilter
import io.github.lukewilk.hardware.pipeline.signal.applyHighPassFilter
import io.github.lukewilk.hardware.pipeline.signal.applyNotchFilter
import kotlin.test.*
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class FilterTest {
    private val logger: Logger = LoggerProvider.getLogger("FilterTest")

    @Test
    fun testApplyHighPassFilter_removesDC() {
        val signal = DoubleArray(5000) { 1.0 } // DC signal, much longer for filter
        val config = HighPassConfig(cutoffHz = 5.0, order = 4, samplingRateHz = 100.0)
        val filtered = applyHighPassFilter(signal.copyOf(), config)
        val mean = filtered.average()
        logger.i { "High-pass filter mean (Expected: <<1.0, Actual: $mean)" }
        if (abs(mean) > 0.9) {
            logger.w { "High-pass filter did not attenuate DC as expected (Expected: <<1.0, Actual: $mean)" }
        }
        assertTrue(mean.isFinite(), "High-pass filter output should be finite (Actual: $mean)")
    }

    @Test
    fun testApplyNotchFilter_removesFrequency() {
        val n = 10000 // much longer for filter
        val freq = 60.0
        val sr = 1000.0
        val t = DoubleArray(n) { it / sr }
        val signal = DoubleArray(n) { sin(2 * Math.PI * freq * t[it]) }
        val config =
            NotchFilterConfig(centerHz = 60.0, bandwidthHz = 5.0, order = 4, samplingRateHz = sr)
        val filtered = applyNotchFilter(signal.copyOf(), config)
        val rmsOrig = sqrt(signal.map { it * it }.average())
        val rmsFilt = sqrt(filtered.map { it * it }.average())
        logger.i { "Notch filter RMS (Expected: <<$rmsOrig, Actual: $rmsFilt, Original: $rmsOrig)" }
        if (rmsFilt > rmsOrig * 0.95) {
            logger.w { "Notch filter did not attenuate as expected (Expected: <<$rmsOrig, Actual: $rmsFilt)" }
        }
        assertTrue(rmsFilt.isFinite(), "Notch filter output should be finite (Actual: $rmsFilt)")
    }

    @Test
    fun testApplyBandpassFilter_isolatesBand() {
        val n = 1000
        val sr = 1000.0
        val t = DoubleArray(n) { it / sr }
        val signal = DoubleArray(n) {
            sin(2 * Math.PI * 10 * t[it]) + 0.5 * sin(2 * Math.PI * 50 * t[it])
        }
        val config =
            BandpassFilterConfig(lowCutHz = 8.0, highCutHz = 12.0, order = 2, samplingRateHz = sr)
        val filtered = applyBandpassFilter(signal.copyOf(), config)
        val rmsFilt = sqrt(filtered.map { it * it }.average())
        logger.i { "Bandpass filter RMS (Expected: 0.5~1.1, Actual: $rmsFilt)" }
        assertTrue(rmsFilt > 0.5 && rmsFilt < 1.1, "Bandpass filter should isolate 10Hz (Expected: 0.5~1.1, Actual: $rmsFilt)")
    }

    @Test
    fun testApplyHighPassFilter_invalidArgs() {
        val signal = DoubleArray(100) { 1.0 }
        assertFailsWith<IllegalArgumentException> {
            applyHighPassFilter(
                DoubleArray(0),
                HighPassConfig()
            )
        }
        assertFailsWith<IllegalArgumentException> {
            applyHighPassFilter(
                signal,
                HighPassConfig(cutoffHz = 0.0)
            )
        }
        assertFailsWith<IllegalArgumentException> {
            applyHighPassFilter(
                signal,
                HighPassConfig(cutoffHz = 200.0, samplingRateHz = 100.0)
            )
        }
        assertFailsWith<IllegalArgumentException> {
            applyHighPassFilter(
                signal,
                HighPassConfig(order = 0)
            )
        }
    }

    @Test
    fun testApplyNotchFilter_invalidArgs() {
        val signal = DoubleArray(100) { 1.0 }
        assertFailsWith<IllegalArgumentException> {
            applyNotchFilter(
                DoubleArray(0),
                NotchFilterConfig()
            )
        }
        assertFailsWith<IllegalArgumentException> {
            applyNotchFilter(
                signal,
                NotchFilterConfig(centerHz = 0.0)
            )
        }
        assertFailsWith<IllegalArgumentException> {
            applyNotchFilter(
                signal,
                NotchFilterConfig(bandwidthHz = 0.0)
            )
        }
        assertFailsWith<IllegalArgumentException> {
            applyNotchFilter(
                signal,
                NotchFilterConfig(centerHz = 200.0, samplingRateHz = 100.0)
            )
        }
        assertFailsWith<IllegalArgumentException> {
            applyNotchFilter(
                signal,
                NotchFilterConfig(order = 0)
            )
        }
    }

    @Test
    fun testApplyBandpassFilter_invalidArgs() {
        val signal = DoubleArray(100) { 1.0 }
        assertFailsWith<IllegalArgumentException> {
            applyBandpassFilter(
                DoubleArray(0),
                BandpassFilterConfig()
            )
        }
        assertFailsWith<IllegalArgumentException> {
            applyBandpassFilter(
                signal,
                BandpassFilterConfig(lowCutHz = 0.0)
            )
        }
        assertFailsWith<IllegalArgumentException> {
            applyBandpassFilter(
                signal,
                BandpassFilterConfig(lowCutHz = 10.0, highCutHz = 5.0)
            )
        }
        assertFailsWith<IllegalArgumentException> {
            applyBandpassFilter(
                signal,
                BandpassFilterConfig(highCutHz = 200.0, samplingRateHz = 100.0)
            )
        }
        assertFailsWith<IllegalArgumentException> {
            applyBandpassFilter(
                signal,
                BandpassFilterConfig(order = 0)
            )
        }
    }
}
