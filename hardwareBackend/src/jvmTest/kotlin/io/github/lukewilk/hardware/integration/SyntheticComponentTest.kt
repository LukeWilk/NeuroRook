package io.github.lukewilk.hardware.integration

import io.github.lukewilk.hardware.BoardConnectionManager
import io.github.lukewilk.hardware.synthetic.SyntheticDataGenerator
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import io.github.lukewilk.shared.SyntheticMode
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.WaveType
import io.github.lukewilk.shared.logging.LoggerProvider
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end synthetic-wave sanity checks that exercise `BoardConnectionManager` plus the in-process
 * synthetic generator as one integrated component.
 */
internal class SyntheticComponentTest {
    private val logger = LoggerProvider.getLogger("SyntheticComponentTest")
    private val stateStore = StateStore(HardwareState())

    /** Configures the manager to generate a single enabled 1 Hz sine wave through the wave-generator mode. */
    private fun configuredSyntheticManager(): BoardConnectionManager = BoardConnectionManager(stateStore).also {
        stateStore.update { state ->
            state.copy(
                connected = true,
                synthetic = true,
                samplingRateHz = 100,
                channels = 1,
                enabledChannels = listOf(0),
                syntheticMode = SyntheticMode.WAVE_GENERATOR,
                waveSpecs = listOf(
                    WaveSpec(
                        enabled = true,
                        type = WaveType.SINE,
                        amplitude = 1.0,
                        frequencyHz = 1.0,
                        phaseShiftRad = 0.0
                    ),
                    WaveSpec(),
                    WaveSpec(),
                    WaveSpec(),
                    WaveSpec()
                )
            )
        }
    }

    /** Counts local maxima and minima so the test can verify a single clean sine cycle was generated. */
    private fun countLocalExtrema(samples: DoubleArray): Pair<Int, Int> {
        var maxima = 0
        var minima = 0
        for (index in 1 until samples.size - 1) {
            val previous = samples[index - 1]
            val current = samples[index]
            val next = samples[index + 1]
            if (current > previous && current > next) maxima++
            if (current < previous && current < next) minima++
        }
        return maxima to minima
    }

    /** Finds the first local maximum index in the generated signal. */
    private fun firstLocalMaximumIndex(samples: DoubleArray): Int =
        samples.indices.first { index ->
            index in 1 until samples.size - 1 &&
                samples[index] > samples[index - 1] &&
                samples[index] > samples[index + 1]
        }

    /** Finds the first local minimum index in the generated signal. */
    private fun firstLocalMinimumIndex(samples: DoubleArray): Int =
        samples.indices.first { index ->
            index in 1 until samples.size - 1 &&
                samples[index] < samples[index - 1] &&
                samples[index] < samples[index + 1]
        }

    /** Computes one DFT bin magnitude-squared so the test can confirm the 1 Hz component dominates. */
    private fun dftBinMagnitude(samples: DoubleArray, bin: Int): Double {
        var real = 0.0
        var imaginary = 0.0
        for (sampleIndex in samples.indices) {
            val angle = 2.0 * Math.PI * bin * sampleIndex / samples.size
            real += samples[sampleIndex] * cos(angle)
            imaginary -= samples[sampleIndex] * sin(angle)
        }
        return real * real + imaginary * imaginary
    }

    /** Detects sample indexes where the waveform changes sign. */
    private fun zeroCrossingIndices(samples: DoubleArray): List<Int> {
        val zeroIndices = mutableListOf<Int>()
        for (index in 1 until samples.size) {
            val previous = samples[index - 1]
            val current = samples[index]
            if ((previous <= 0.0 && current > 0.0) || (previous >= 0.0 && current < 0.0)) {
                zeroIndices += index
            }
        }
        return zeroIndices
    }

    /** Asserts the signal is monotonic across the expected sections of a single sine cycle. */
    private fun assertMonotonicSingleCycleShape(samples: DoubleArray, maxIndex: Int, minIndex: Int) {
        for (index in 1..maxIndex) {
            assertTrue(
                samples[index] >= samples[index - 1] - 1e-9,
                "Signal should be non-decreasing up to the first maximum (idx $index)"
            )
        }
        for (index in maxIndex + 1..minIndex) {
            assertTrue(
                samples[index] <= samples[index - 1] + 1e-9,
                "Signal should be non-increasing between max and min (idx $index)"
            )
        }
        for (index in minIndex + 1 until samples.size) {
            assertTrue(
                samples[index] >= samples[index - 1] - 1e-9,
                "Signal should be non-decreasing after the minimum (idx $index)"
            )
        }
    }

    /**
     * Verifies the integrated synthetic board generates one clean one-second 1 Hz sine cycle sampled at 100 Hz.
     *
     * The assertions intentionally cover:
     * - sample count and endpoint agreement,
     * - one local maximum and one local minimum,
     * - monotonic shape around the extrema,
     * - dominant 1 Hz spectral energy,
     * - approximately half-period zero-crossing spacing.
     */
    @Test
    fun `synthetic board generates one second of 1 hz sine data`() = runBlocking {
        val samplingRateHz = 100
        val seconds = 1
        val samples = samplingRateHz * seconds + 1
        val manager = configuredSyntheticManager()

        try {
            SyntheticDataGenerator.resetPhases()
            val block = manager.generateSyntheticData(samples)
            assertTrue(block.isNotEmpty(), "Generated block should not be empty")
            val channelData = block[0]
            assertEquals(
                samples,
                channelData.size,
                "Should generate $samples samples for one second at $samplingRateHz Hz"
            )

            logger.i { "Generated ${channelData.size} samples (one per line):" }
            channelData.forEachIndexed { index, value ->
                logger.i { "$index: $value" }
            }

            val firstSample = channelData.first()
            val lastSample = channelData.last()
            val endpointDifference = abs(firstSample - lastSample)
            logger.i { "First sample=$firstSample, last sample=$lastSample, diff=$endpointDifference" }
            assertTrue(endpointDifference < 0.12, "First and last sample should be close. diff=$endpointDifference")

            val (maxima, minima) = countLocalExtrema(channelData)
            logger.i { "Found maxima=$maxima, minima=$minima" }
            assertEquals(1, maxima, "Expected exactly 1 local maximum in one sine cycle")
            assertEquals(1, minima, "Expected exactly 1 local minimum in one sine cycle")

            val maxIndex = firstLocalMaximumIndex(channelData)
            val minIndex = firstLocalMinimumIndex(channelData)
            logger.i { "Max at index $maxIndex, Min at index $minIndex" }
            assertMonotonicSingleCycleShape(channelData, maxIndex, minIndex)

            val totalPower = channelData.sumOf { it * it }
            val targetBin = (1.0 * channelData.size / samplingRateHz).roundToInt()
            val targetBinMagnitude = dftBinMagnitude(channelData, targetBin)
            val dominantPowerRatio = if (totalPower <= 0.0) 0.0 else targetBinMagnitude / totalPower
            logger.i {
                "DFT: targetBin=$targetBin, binMag=$targetBinMagnitude, totalPower=$totalPower, ratio=$dominantPowerRatio"
            }
            assertTrue(dominantPowerRatio > 0.5, "Dominant frequency should be 1Hz; power ratio=$dominantPowerRatio")

            val zeroIndices = zeroCrossingIndices(channelData)
            logger.i { "Zero crossing indices: $zeroIndices" }
            assertTrue(zeroIndices.size >= 2, "Expected at least two zero crossings, found ${zeroIndices.size}")

            val zeroCrossingSpacings = zeroIndices.zipWithNext { a, b -> b - a }
            val meanSpacing = zeroCrossingSpacings.average()
            val expectedHalfPeriod = samplingRateHz / 2.0
            logger.i { "Zero-cross spacings: $zeroCrossingSpacings, mean=$meanSpacing" }
            assertTrue(
                abs(meanSpacing - expectedHalfPeriod) < 3.0,
                "Zero-cross spacing mean should be close to half period ($expectedHalfPeriod), got $meanSpacing"
            )

        } finally {
            manager.close()
        }
    }

}