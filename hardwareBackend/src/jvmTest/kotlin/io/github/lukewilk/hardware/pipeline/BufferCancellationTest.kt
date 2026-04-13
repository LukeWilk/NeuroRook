package io.github.lukewilk.hardware.pipeline

import io.github.lukewilk.shared.Band
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Cancellation-path tests for `buffer()`.
 */
class BufferCancellationTest {

    /** Verifies stored buffering propagates cancellation raised by the consumer. */
    @Test
    fun `stored buffering propagates consumer cancellation`() {
        runBlocking {
            assertFailsWith<CancellationException> {
                collectBufferedWindows(
                    stateStore = storedBufferState(windowSize = 8, overlap = 4),
                    frames = sampleFrames(1..8)
                ) {
                    throw CancellationException("test cancellation")
                }
            }
        }
    }

    /** Verifies a cancelled collector context aborts stored buffering before window emission. */
    @Test
    fun `cancelled collector context aborts stored buffering`() {
        runBlocking {
            val cancelledJob = Job().apply { cancel() }
            assertFailsWith<CancellationException> {
                withContext(cancelledJob) {
                    collectBufferedWindows(
                        stateStore = storedBufferState(windowSize = 8, overlap = 4),
                        frames = sampleFrames(1..8)
                    )
                }
            }
        }
    }

    /** Verifies buffering still works when the coroutine context has no Job element at all. */
    @Test
    fun `jobless collector context still allows buffering`() {
        runBlocking {
            val windows = mutableListOf<Any>()
            withoutJobInContext {
                windows.addAll(
                    collectBufferedWindows(
                        stateStore = storedBufferState(windowSize = 8, overlap = 4),
                        frames = sampleFrames(1..8)
                    )
                )
            }
            assertTrue(windows.isNotEmpty())
        }
    }

    /** Verifies the top-level cancellation override can force buffering to stop immediately. */
    @Test
    fun `top level cancellation override aborts buffering`() {
        runBlocking {
            assertFailsWith<CancellationException> {
                withBufferCancellationOverrides(topLevelOverride = { true }) {
                    collectBufferedWindows(
                        stateStore = storedBufferState(windowSize = 8, overlap = 4),
                        frames = sampleFrames(1..8)
                    )
                }
            }
        }
    }

    /** Verifies a permissive top-level override keeps buffering active even when no Job is present. */
    @Test
    fun `top level override can explicitly allow buffering without a job`() {
        runBlocking {
            val windows = mutableListOf<Any>()
            withBufferCancellationOverrides(topLevelOverride = { false }) {
                withoutJobInContext {
                    windows.addAll(
                        collectBufferedWindows(
                            stateStore = storedBufferState(windowSize = 8, overlap = 4),
                            frames = sampleFrames(1..8)
                        )
                    )
                }
            }
            assertTrue(windows.isNotEmpty())
        }
    }

    /** Verifies the pre-window cancellation override stops computed buffering just before the callback fires. */
    @Test
    fun `pre window cancellation override aborts computed buffering`() {
        runBlocking {
            val bands = listOf(Band("HF", 100.0, 110.0))
            val windowSize = computedWindowSamples(256.0, bands, preferredOverlap = 0.5)
            assertFailsWith<CancellationException> {
                withBufferCancellationOverrides(preWindowOverride = { true }) {
                    collectBufferedWindows(
                        stateStore = computedBufferState(samplingRateHz = 256, bands = bands, preferredOverlap = 0.5),
                        frames = sampleFrames(1..windowSize)
                    )
                }
            }
        }
    }

    /** Verifies the pre-window cancellation override also stops the stored-window branch before emission. */
    @Test
    fun `pre window cancellation override aborts stored buffering`() {
        runBlocking {
            assertFailsWith<CancellationException> {
                withBufferCancellationOverrides(preWindowOverride = { true }) {
                    collectBufferedWindows(
                        stateStore = storedBufferState(windowSize = 8, overlap = 4),
                        frames = sampleFrames(1..8)
                    )
                }
            }
        }
    }

    /** Verifies a permissive pre-window override still allows the computed branch to emit windows. */
    @Test
    fun `pre window override can explicitly allow computed buffering without a job`() {
        runBlocking {
            val bands = listOf(Band("HF", 100.0, 110.0))
            val windowSize = computedWindowSamples(256.0, bands, preferredOverlap = 0.5)
            val windows = mutableListOf<Any>()
            withBufferCancellationOverrides(preWindowOverride = { false }) {
                withoutJobInContext {
                    windows.addAll(
                        collectBufferedWindows(
                            stateStore = computedBufferState(samplingRateHz = 256, bands = bands, preferredOverlap = 0.5),
                            frames = sampleFrames(1..windowSize)
                        )
                    )
                }
            }
            assertTrue(windows.isNotEmpty())
        }
    }

    /** Verifies an inactive secondary Job cancels the computed branch when no override is installed. */
    @Test
    fun `inactive secondary job cancels computed buffering when no override is installed`() {
        runBlocking {
            val bands = listOf(Band("HF", 100.0, 110.0))
            val windowSize = computedWindowSamples(256.0, bands, preferredOverlap = 0.5)
            val cancelledJob = Job().apply { cancel() }
            val context = currentCoroutineContext() + cancelledJob
            withBufferCancellationOverrides(preWindowOverride = null) {
                assertFailsWith<CancellationException> {
                    withContext(context) {
                        collectBufferedWindows(
                            stateStore = computedBufferState(samplingRateHz = 256, bands = bands, preferredOverlap = 0.5),
                            frames = sampleFrames(1..(windowSize * 2))
                        )
                    }
                }
            }
        }
    }

    /** Verifies an inactive secondary Job cancels the stored branch when no override is installed. */
    @Test
    fun `inactive secondary job cancels stored buffering when no override is installed`() {
        runBlocking {
            val cancelledJob = Job().apply { cancel() }
            val context = currentCoroutineContext() + cancelledJob
            withBufferCancellationOverrides(preWindowOverride = null) {
                assertFailsWith<CancellationException> {
                    withContext(context) {
                        collectBufferedWindows(
                            stateStore = storedBufferState(windowSize = 8, overlap = 4),
                            frames = sampleFrames(1..16)
                        )
                    }
                }
            }
        }
    }
}
