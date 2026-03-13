package io.github.lukewilk.hardware.integration

import io.github.lukewilk.hardware.BoardConnectionManager
import io.github.lukewilk.hardware.LoggerProvider
import io.github.lukewilk.hardware.synthetic.SyntheticDataGenerator
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import io.github.lukewilk.shared.SyntheticMode
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.WaveType
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyntheticComponentTest {
    private val logger = LoggerProvider.getLogger("SyntheticComponentTest")

    val stateStore = StateStore(HardwareState())

    // This test verifies that the synthetic data generator produces a 1 Hz sine wave correctly
    // sampled at 100 Hz over a 1-second interval.
    @Test
    fun testSyntheticBoardGeneratesOneSecondOfData() = runBlocking {
        val manager = BoardConnectionManager(stateStore)
        try {
            // Configure manager to use the in-process wave generator
            val samplingRate =
                100 // Hz, 1 second -> 100 samples (we'll request +1 to include both endpoints)
            stateStore.update { st ->
                st.copy(
                    connected = true,
                    synthetic = true,
                    samplingRateHz = samplingRate,
                    channels = 1,
                    enabledChannels = listOf(0), // Enable only channel 0 by index
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

            // Generate one second of data (+1 sample to include both t=0 and t=1)
            val seconds = 1
            val samples = samplingRate * seconds + 1 // 101 samples: t=0..1 inclusive
            // Reset phase accumulators to ensure deterministic phase (start at 0)
            SyntheticDataGenerator.resetPhases()
            val block = manager.generateSyntheticData(samples)
            assertTrue(block.isNotEmpty(), "Generated block should not be empty")
            val channelData = block[0]
            assertEquals(
                samples,
                channelData.size,
                "Should generate $samples samples for one second at $samplingRate Hz"
            )

            // Print samples on separate lines
            logger.i { "Generated ${channelData.size} samples (one per line):" }
            channelData.forEachIndexed { i, v ->
                logger.i { "$i: $v" }
            }

            // Compare first and last samples - for 1Hz sine over exactly 1s they should be close
            val first = channelData.first()
            val last = channelData.last()
            val diff = abs(first - last)
            logger.i { "First sample=$first, last sample=$last, diff=$diff" }
            // threshold: allow small error due to discrete sampling
            assertTrue(diff < 0.12, "First and last sample should be close. diff=$diff")

            // Count local maxima and minima
            var maxima = 0
            var minima = 0
            for (i in 1 until channelData.size - 1) {
                val prev = channelData[i - 1]
                val cur = channelData[i]
                val next = channelData[i + 1]
                if (cur > prev && cur > next) maxima++
                if (cur < prev && cur < next) minima++
            }
            logger.i { "Found maxima=$maxima, minima=$minima" }
            assertEquals(1, maxima, "Expected exactly 1 local maximum in one sine cycle")
            assertEquals(1, minima, "Expected exactly 1 local minimum in one sine cycle")

            // Check monotonic segments around extrema: before max should be increasing, after max decreasing
            // Find index of max and min
            val maxIdx =
                channelData.indices.first { i -> i in 1 until channelData.size - 1 && channelData[i] > channelData[i - 1] && channelData[i] > channelData[i + 1] }
            val minIdx =
                channelData.indices.first { i -> i in 1 until channelData.size - 1 && channelData[i] < channelData[i - 1] && channelData[i] < channelData[i + 1] }
            logger.i { "Max at index $maxIdx, Min at index $minIdx" }

            // Verify monotonic increase from start to maxIdx
            for (i in 1..maxIdx) {
                assertTrue(
                    channelData[i] >= channelData[i - 1] - 1e-9,
                    "Signal should be non-decreasing up to the first maximum (idx $i)"
                )
            }
            // Verify monotonic decrease from maxIdx to minIdx
            for (i in maxIdx + 1..minIdx) {
                assertTrue(
                    channelData[i] <= channelData[i - 1] + 1e-9,
                    "Signal should be non-increasing between max and min (idx $i)"
                )
            }
            // Verify monotonic increase from minIdx to end
            for (i in minIdx + 1 until channelData.size) {
                assertTrue(
                    channelData[i] >= channelData[i - 1] - 1e-9,
                    "Signal should be non-decreasing after the minimum (idx $i)"
                )
            }

            // --- Additional checks requested: FFT (DFT) based dominant frequency check and zero-crossing spacing ---

            // 1) DFT: compute bin for 1 Hz and assert it dominates energy
            val n = channelData.size
            fun dftBinMagnitude(bin: Int): Double {
                var real = 0.0
                var imag = 0.0
                for (k in 0 until n) {
                    val angle = 2.0 * Math.PI * bin * k / n
                    real += channelData[k] * cos(angle)
                    imag -= channelData[k] * sin(angle)
                }
                return real * real + imag * imag
            }

            val totalPower = channelData.sumOf { it * it }
            val targetBin = (1.0 * n / samplingRate).roundToInt() // bin corresponding to 1 Hz
            val binMag = dftBinMagnitude(targetBin)
            val ratio = if (totalPower <= 0.0) 0.0 else binMag / totalPower
            logger.i { "DFT: targetBin=$targetBin, binMag=$binMag, totalPower=$totalPower, ratio=$ratio" }
            // require that the 1 Hz bin holds most of the power (allow threshold 0.5 for non-power-of-two length)
            assertTrue(ratio > 0.5, "Dominant frequency should be 1Hz; power ratio=$ratio")

            // 2) Zero-crossing spacing: detect zero crossings and check spacing uniformity
            val zeroIndices = mutableListOf<Int>()
            for (i in 1 until channelData.size) {
                val prev = channelData[i - 1]
                val cur = channelData[i]
                if ((prev <= 0.0 && cur > 0.0) || (prev >= 0.0 && cur < 0.0)) {
                    zeroIndices.add(i)
                }
            }
            logger.i { "Zero crossing indices: $zeroIndices" }
            // We expect approximately two zero crossings in 1s for sine at 1Hz (t=0 and t=0.5)
            assertTrue(
                zeroIndices.size >= 2,
                "Expected at least two zero crossings, found ${zeroIndices.size}"
            )
            // check spacing between consecutive zero crossings ~ samples/2 (half period)
            val spacings = zeroIndices.zipWithNext { a, b -> b - a }
            val meanSpacing = spacings.average()
            logger.i { "Zero-cross spacings: $spacings, mean=$meanSpacing" }
            val expectedHalfPeriod = samplingRate / 2.0
            assertTrue(
                abs(meanSpacing - expectedHalfPeriod) < 3.0,
                "Zero-cross spacing mean should be close to half period ($expectedHalfPeriod), got $meanSpacing"
            )

        } finally {
            manager.close()
        }
    }

}