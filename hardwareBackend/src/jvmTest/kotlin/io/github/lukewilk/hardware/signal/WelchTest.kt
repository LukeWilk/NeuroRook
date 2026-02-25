package io.github.lukewilk.hardware.signal

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WelchTest {

    @Test
    fun testComputeWelchPSDThrowsOnEmptyInput() {
        val empty = DoubleArray(0)
        assertFailsWith<IllegalArgumentException> { computeWelchPSD(empty, WelchConfig()) }
    }

    @Test
    fun testComputeWelchPSDDetectsSinePeak() {
        val fs = 100.0
        val n = 128 // power of two
        val f0 = 5.0
        val samples = DoubleArray(n) { i ->
            sin(2.0 * PI * f0 * i / fs)
        }

        val config = WelchConfig(samplingRateHz = fs, padToNextPowerOfTwo = false)
        val psd = computeWelchPSD(samples, config)

        // find frequency with maximum power
        val (maxFreq, maxPower) = psd.maxByOrNull { it.second }!!

        // frequency resolution
        val freqResolution = fs / n

        // The detected peak should be near f0 within one bin
        assertTrue(abs(maxFreq - f0) <= freqResolution * 1.1, "Peak frequency $maxFreq not close to $f0")
        assertTrue(maxPower > 0.0, "Peak power should be positive")

        // band power around f0 (±1 Hz) should be > 0
        val bp = bandPower(psd, f0 - 1.0, f0 + 1.0)
        assertTrue(bp > 0.0, "Band power around $f0 should be positive")
    }

    @Test
    fun testBandPowerTrapezoidalIntegration() {
        // Construct a simple PSD: frequencies 0,1,2 with powers 1,2,3
        val psd = arrayOf(
            Pair(0.0, 1.0),
            Pair(1.0, 2.0),
            Pair(2.0, 3.0)
        )

        // Integrate full band 0..2: expected = trapezoidal: (1+2)/2*1 + (2+3)/2*1 = 1.5 + 2.5 = 4.0
        val power = bandPower(psd, 0.0, 2.0)
        assertTrue(abs(power - 4.0) < 1e-12, "Expected integrated power ~4.0, got $power")
    }

    @Test
    fun testBandPowerInvalidInputsThrow() {
        val psd = arrayOf(Pair(0.0, 1.0))
        // invalid bounds
        assertFailsWith<IllegalArgumentException> { bandPower(psd, -1.0, 1.0) }
        assertFailsWith<IllegalArgumentException> { bandPower(psd, 2.0, 1.0) }
        // empty PSD
        assertFailsWith<IllegalArgumentException> { bandPower(arrayOf(), 0.0, 1.0) }
    }
}
