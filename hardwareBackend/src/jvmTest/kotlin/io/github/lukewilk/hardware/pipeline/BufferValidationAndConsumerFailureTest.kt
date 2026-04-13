package io.github.lukewilk.hardware.pipeline

import io.github.lukewilk.shared.Band
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Validation and consumer-failure tests for `buffer()`.
 */
class BufferValidationAndConsumerFailureTest {
    /** Verifies computed buffering rejects derived windows smaller than the supported minimum. */
    @Test
    fun `computed buffering rejects undersized derived windows`() {
        runBlocking {
            val bands = listOf(Band("HF", 100.0, 110.0))
            assertFailsWith<IllegalArgumentException> {
                collectBufferedWindows(
                    stateStore = computedBufferState(samplingRateHz = 1, bands = bands),
                    frames = sampleFrames(1..2, channel = 1)
                )
            }
        }
    }

    /** Verifies stored buffering rejects configured windows smaller than the supported minimum. */
    @Test
    fun `stored buffering rejects undersized windows`() {
        runBlocking {
            assertFailsWith<IllegalArgumentException> {
                collectBufferedWindows(
                    stateStore = storedBufferState(windowSize = 4, overlap = 2),
                    frames = sampleFrames(1..4)
                )
            }
        }
    }

    /** Verifies stored buffering rejects overlaps that are equal to the window size. */
    @Test
    fun `stored buffering rejects overlap equal to the window size`() {
        runBlocking {
            assertFailsWith<IllegalArgumentException> {
                collectBufferedWindows(
                    stateStore = storedBufferState(windowSize = 8, overlap = 8),
                    frames = sampleFrames(1..8)
                )
            }
        }
    }

    /** Verifies an invalid computed overlap fraction is rejected before any window is emitted. */
    @Test
    fun `computed buffering rejects overlap fractions outside the supported range`() {
        runBlocking {
            val bands = listOf(Band("HF", 100.0, 110.0))
            assertFailsWith<IllegalArgumentException> {
                collectBufferedWindows(
                    stateStore = computedBufferState(samplingRateHz = 256, bands = bands, preferredOverlap = 1.5, windowSize = 16),
                    frames = sampleFrames(1..16)
                )
            }
        }
    }

    /** Verifies runtime exceptions raised by the consumer are propagated unchanged. */
    @Test
    fun `stored buffering propagates runtime exceptions from the consumer`() {
        runBlocking {
            assertFailsWith<IllegalStateException> {
                collectBufferedWindows(
                    stateStore = storedBufferState(windowSize = 8, overlap = 4),
                    frames = sampleFrames(1..8)
                ) {
                    throw IllegalStateException("boom")
                }
            }
        }
    }

    /** Verifies computed buffering also propagates cancellation signals raised by the consumer. */
    @Test
    fun `computed buffering propagates consumer cancellation`() {
        runBlocking {
            val bands = listOf(Band("HF", 80.0, 120.0))
            val windowSize = computedWindowSamples(256.0, bands, preferredOverlap = 0.5)
            assertFailsWith<CancellationException> {
                collectBufferedWindows(
                    stateStore = computedBufferState(samplingRateHz = 256, bands = bands, preferredOverlap = 0.5),
                    frames = sampleFrames(1..windowSize, channel = 1)
                ) {
                    throw CancellationException("stop")
                }
            }
        }
    }
}
