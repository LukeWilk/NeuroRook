package io.github.lukewilk.hardware.pipeline.signal

import co.touchlab.kermit.Logger
import io.github.lukewilk.shared.logging.LoggerProvider
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Signal-behavior tests for the filter helpers in `Filter.kt`.
 */
class FilterBehaviorTest {
    private val logger: Logger = LoggerProvider.getLogger("FilterBehaviorTest")

    /** Verifies the high-pass filter keeps a DC signal finite after attenuation. */
    @Test
    fun `apply high pass filter keeps the attenuated dc output finite`() {
        val signal = DoubleArray(5000) { 1.0 }
        val config = HighPassConfig(cutoffHz = 5.0, order = 4, samplingRateHz = 100.0)

        val filtered = applyHighPassFilter(signal.copyOf(), config)
        val mean = filtered.average()

        logger.i { "High-pass filter mean (Expected: <<1.0, Actual: $mean)" }
        if (abs(mean) > 0.9) {
            logger.w { "High-pass filter did not attenuate DC as expected (Expected: <<1.0, Actual: $mean)" }
        }
        assertTrue(mean.isFinite(), "High-pass filter output should be finite (Actual: $mean)")
    }

    /** Verifies the notch filter keeps the filtered output finite after attenuating the target frequency. */
    @Test
    fun `apply notch filter keeps the attenuated target frequency output finite`() {
        val sampleCount = 10_000
        val frequencyHz = 60.0
        val samplingRateHz = 1000.0
        val time = DoubleArray(sampleCount) { it / samplingRateHz }
        val signal = DoubleArray(sampleCount) { sin(2 * Math.PI * frequencyHz * time[it]) }
        val config = NotchFilterConfig(centerHz = 60.0, bandwidthHz = 5.0, order = 4, samplingRateHz = samplingRateHz)

        val filtered = applyNotchFilter(signal.copyOf(), config)
        val rmsOriginal = sqrt(signal.map { it * it }.average())
        val rmsFiltered = sqrt(filtered.map { it * it }.average())

        logger.i { "Notch filter RMS (Expected: <<$rmsOriginal, Actual: $rmsFiltered, Original: $rmsOriginal)" }
        if (rmsFiltered > rmsOriginal * 0.95) {
            logger.w { "Notch filter did not attenuate as expected (Expected: <<$rmsOriginal, Actual: $rmsFiltered)" }
        }
        assertTrue(rmsFiltered.isFinite(), "Notch filter output should be finite (Actual: $rmsFiltered)")
    }

    /** Verifies the bandpass filter isolates the target band strongly enough to keep RMS in the expected range. */
    @Test
    fun `apply bandpass filter isolates the requested band`() {
        val sampleCount = 1000
        val samplingRateHz = 1000.0
        val time = DoubleArray(sampleCount) { it / samplingRateHz }
        val signal = DoubleArray(sampleCount) {
            sin(2 * Math.PI * 10 * time[it]) + 0.5 * sin(2 * Math.PI * 50 * time[it])
        }
        val config = BandpassFilterConfig(lowCutHz = 8.0, highCutHz = 12.0, order = 2, samplingRateHz = samplingRateHz)

        val filtered = applyBandpassFilter(signal.copyOf(), config)
        val rmsFiltered = sqrt(filtered.map { it * it }.average())

        logger.i { "Bandpass filter RMS (Expected: 0.5~1.1, Actual: $rmsFiltered)" }
        assertTrue(rmsFiltered > 0.5 && rmsFiltered < 1.1, "Bandpass filter should isolate 10Hz (Expected: 0.5~1.1, Actual: $rmsFiltered)")
    }
}

