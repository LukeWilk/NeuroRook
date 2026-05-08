package io.github.lukewilk.hardware.pipeline.signal
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
/**
 * PSD and band-power behavior tests for `Welch.kt`.
 */
class WelchBehaviorTest {
    /** Verifies Welch PSD detects the dominant sine-wave peak and yields positive local band power. */
    @Test
    fun `compute welch psd detects a sine peak`() {
        val samplingRateHz = 100.0
        val sampleCount = 128
        val targetFrequencyHz = 5.0
        val samples = DoubleArray(sampleCount) { index ->
            sin(2.0 * PI * targetFrequencyHz * index / samplingRateHz)
        }
        val config = WelchConfig(samplingRateHz = samplingRateHz, padToNextPowerOfTwo = false)
        val psd = computeWelchPSD(samples, config)
        val (maxFrequency, maxPower) = psd.maxByOrNull { it.second }!!
        val frequencyResolution = samplingRateHz / sampleCount
        assertTrue(abs(maxFrequency - targetFrequencyHz) <= frequencyResolution * 1.1, "Peak frequency $maxFrequency not close to $targetFrequencyHz")
        assertTrue(maxPower > 0.0, "Peak power should be positive")
        val localBandPower = bandPower(psd, targetFrequencyHz - 1.0, targetFrequencyHz + 1.0)
        assertTrue(localBandPower > 0.0, "Band power around $targetFrequencyHz should be positive")
    }
    /** Verifies band-power integration matches the expected trapezoidal result on a tiny hand-built PSD. */
    @Test
    fun `band power uses trapezoidal integration`() {
        val psd = arrayOf(
            0.0 to 1.0,
            1.0 to 2.0,
            2.0 to 3.0
        )
        val power = bandPower(psd, 0.0, 2.0)
        assertTrue(abs(power - 4.0) < 1e-12, "Expected integrated power ~4.0, got $power")
    }
    /** Verifies band power is zero when no PSD points fall inside the requested band. */
    @Test
    fun `band power returns zero when no frequency falls inside the band`() {
        val psd = arrayOf(
            1.0 to 1.0,
            2.0 to 2.0,
            3.0 to 3.0
        )
        assertTrue(bandPower(psd, 10.0, 12.0) == 0.0)
    }
    /** Verifies single-sample PSD generation still returns the smallest valid branch result. */
    @Test
    fun `compute welch psd single sample uses the smallest power of two branch`() {
        val psd = computeWelchPSD(
            windowedSignal = doubleArrayOf(42.0),
            config = WelchConfig(samplingRateHz = 250.0, padToNextPowerOfTwo = true)
        )
        assertTrue(psd.size == 1)
        assertTrue(psd[0].first == 0.0)
        assertTrue(psd[0].second.isNaN() || psd[0].second >= 0.0)
    }

    /** Verifies the configurable padding floor can emit a denser one-sided spectrum for UI consumers. */
    @Test
    fun `compute welch psd honors the minimum nfft padding floor`() {
        val psd = computeWelchPSD(
            windowedSignal = DoubleArray(256) { index -> sin(2.0 * PI * 10.0 * index / 250.0) },
            config = WelchConfig(
                samplingRateHz = 250.0,
                padToNextPowerOfTwo = true,
                minimumNfft = 1024
            )
        )

        assertEquals(513, psd.size)
    }
}
