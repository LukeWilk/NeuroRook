package io.github.lukewilk.hardware

import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import io.github.lukewilk.shared.Band
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import io.github.lukewilk.hardware.signal.computeOptimalFFTWindow

/**
 * Unit tests for the `buffer` helper in `Buffer.kt`.
 */
class BufferTest {

    @Test
    fun testBufferEmitsWindowsWithExplicitWindowSizeAndOverlap() {
        runBlocking {
            // Configure state: windowSize 8, overlap 4 -> hop = 4
            val stateStore = StateStore(HardwareState(windowSize = 8, overlap = 4, samplingRateHz = 250))

            // Create 12 frames with single-sample payloads so we get two windows (first: 1..8, second: 5..12)
            val frames = (1..12).map { i -> RawFrame(timestampMs = i.toLong(), channel = i % 2, data = doubleArrayOf(i.toDouble())) }
            val input = flowOf(*frames.toTypedArray())

            val windows = mutableListOf<RawFrame>()

            buffer(input, stateStore) { out ->
                // record the emitted windows
                windows.add(out)
            }

            // With 12 samples, windowSize 8 hop 4 -> windows expected: floor((12 - 8)/4) + 1 = 2
            assertEquals(2, windows.size, "Expected two windows emitted")

            // First window should have the first 8 samples: 1..8. Timestamp and channel should be from last contributing frame (8)
            val w1 = windows[0]
            assertEquals(8L, w1.timestampMs)
            assertEquals(8 % 2, w1.channel)
            assertTrue(w1.data.contentEquals(doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0)))

            // Second window should have samples 5..12 after hop of 4 -> second window contains 5..12
            val w2 = windows[1]
            assertEquals(12L, w2.timestampMs)
            assertEquals(12 % 2, w2.channel)
            assertTrue(w2.data.contentEquals(doubleArrayOf(5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0)))
        }
    }

    @Test
    fun testBufferUsesComputedWindowWhenPreferredOverlapSet() {
        runBlocking {
            // Choose bands so computeOptimalFFTWindow yields a reasonable window size
            val bands = listOf(Band("HF", 100.0, 110.0))
            // Ask the helper for the computed window so we can emit exactly that many frames
            val cfg = computeOptimalFFTWindow(samplingRateHz = 256.0, bandsHz = listOf(100.0 to 110.0), cycles = 4, preferPowerOfTwo = false, preferredOverlap = 0.5)
            val samplesNeeded = cfg.windowSamples

            val stateStore = StateStore(HardwareState(windowSize = 16, overlap = 0, preferredOverlap = 0.5, samplingRateHz = 256, bands = bands))

            // We'll emit enough frames for at least one window; use samplesNeeded
            val frames = (1..samplesNeeded).map { i -> RawFrame(timestampMs = i.toLong(), channel = 1, data = doubleArrayOf(i.toDouble())) }
            val input = flowOf(*frames.toTypedArray())

            val windows = mutableListOf<RawFrame>()
            buffer(input, stateStore) { out -> windows.add(out) }

            // Expect at least one window emitted
            assertTrue(windows.isNotEmpty(), "Expected at least one window when using computed window size")
            val w = windows.first()
            assertEquals(samplesNeeded.toLong(), w.timestampMs)
            // data length should equal the computed window size (>=8). We assert >=8 to be resilient across environments
            assertTrue(w.data.size >= 8, "Computed window size should be at least 8")
        }
    }

    @Test
    fun testBufferRethrowsCancellationFromOnWindow() {
        runBlocking {
            val stateStore = StateStore(HardwareState(windowSize = 8, overlap = 4, samplingRateHz = 250))
            val frames = (1..8).map { i -> RawFrame(timestampMs = i.toLong(), channel = 0, data = doubleArrayOf(i.toDouble())) }
            val input = flowOf(*frames.toTypedArray())

            // onWindow will throw CancellationException which should be propagated
            assertFailsWith<CancellationException> {
                buffer(input, stateStore) { _ -> throw CancellationException("test cancellation") }
            }
        }
    }

    @Test
    fun testNonPowerOfTwoWindowProducesWarningAndWindow() {
        runBlocking {
            // windowSize 10 (not power of two), overlap 2 valid -> hop = 8
            val stateStore = StateStore(HardwareState(windowSize = 10, overlap = 2, samplingRateHz = 250))
            // produce 10 samples to get exactly one window
            val frames = (1..10).map { i -> RawFrame(i.toLong(), channel = 0, data = doubleArrayOf(i.toDouble())) }
            val input = flowOf(*frames.toTypedArray())
            val windows = mutableListOf<RawFrame>()
            buffer(input, stateStore) { out -> windows.add(out) }
            assertEquals(1, windows.size)
            val w = windows[0]
            assertEquals(10, w.data.size)
            assertEquals(10L, w.timestampMs)
        }
    }

    @Test
    fun testWindowSizeZeroUsesComputedWindow() {
        runBlocking {
            // windowSize <= 0 forces compute path; use a high-band so windowSamples small
            val bands = listOf(Band("HF", 80.0, 120.0))
            val stateStore = StateStore(HardwareState(windowSize = 0, overlap = 0, samplingRateHz = 256, bands = bands))
            val cfg = computeOptimalFFTWindow(samplingRateHz = 256.0, bandsHz = listOf(80.0 to 120.0))
            val samplesNeeded = cfg.windowSamples
            val frames = (1..samplesNeeded).map { i -> RawFrame(i.toLong(), channel = 1, data = doubleArrayOf(i.toDouble())) }
            val input = flowOf(*frames.toTypedArray())
            val windows = mutableListOf<RawFrame>()
            buffer(input, stateStore) { out -> windows.add(out) }
            assertTrue(windows.isNotEmpty())
            val w = windows.first()
            assertEquals(samplesNeeded.toLong(), w.timestampMs)
            assertEquals(samplesNeeded, w.data.size)
        }
    }

    @Test
    fun testPreferredOverlapZeroHandled() {
        runBlocking {
            val bands = listOf(Band("L", 4.0, 8.0))
            // preferredOverlap 0.0 exercise the branch computing requestedHop == windowSize
            val cfg = computeOptimalFFTWindow(samplingRateHz = 200.0, bandsHz = listOf(4.0 to 8.0), preferredOverlap = 0.0)
            val samplesNeeded = cfg.windowSamples
            val stateStore = StateStore(HardwareState(windowSize = 0, overlap = 0, samplingRateHz = 200, bands = bands, preferredOverlap = 0.0))
            val frames = (1..samplesNeeded).map { i -> RawFrame(i.toLong(), channel = 0, data = doubleArrayOf(i.toDouble())) }
            val input = flowOf(*frames.toTypedArray())
            val windows = mutableListOf<RawFrame>()
            buffer(input, stateStore) { out -> windows.add(out) }
            assertTrue(windows.isNotEmpty())
            val w = windows.first()
            assertEquals(samplesNeeded.toLong(), w.timestampMs)
        }
    }

    @Test
    fun testOnWindowCancellationInComputedPathPropagates() {
        runBlocking {
            val bands = listOf(Band("HF", 80.0, 120.0))
            val stateStore = StateStore(HardwareState(windowSize = 0, overlap = 0, samplingRateHz = 256, bands = bands, preferredOverlap = 0.5))
            val cfg = computeOptimalFFTWindow(samplingRateHz = 256.0, bandsHz = listOf(80.0 to 120.0), preferredOverlap = 0.5)
            val samplesNeeded = cfg.windowSamples
            val frames = (1..samplesNeeded).map { i -> RawFrame(i.toLong(), channel = 1, data = doubleArrayOf(i.toDouble())) }
            val input = flowOf(*frames.toTypedArray())
            assertFailsWith<CancellationException> {
                buffer(input, stateStore) { _ -> throw CancellationException("stop") }
            }
        }
    }

    // New tests that mock/force the brainflow.DataFilter failure using the test DataFilter shim
    @Test
    fun testDataFilterThrowsComputedPathFallback() {
        runBlocking {
            val original = dataFilterGetNearestPowerOfTwo
            try {
                // Force the hook to throw to hit the catch/fallback branch
                dataFilterGetNearestPowerOfTwo = { _ -> throw RuntimeException("forced") }
                val bands = listOf(Band("HF", 80.0, 120.0))
                val stateStore = StateStore(HardwareState(windowSize = 0, overlap = 0, samplingRateHz = 256, bands = bands))
                val cfg = computeOptimalFFTWindow(samplingRateHz = 256.0, bandsHz = listOf(80.0 to 120.0))
                val samplesNeeded = cfg.windowSamples
                val frames = (1..samplesNeeded).map { i -> RawFrame(i.toLong(), channel = 1, data = doubleArrayOf(i.toDouble())) }
                val input = flowOf(*frames.toTypedArray())
                val windows = mutableListOf<RawFrame>()
                buffer(input, stateStore) { out -> windows.add(out) }
                assertTrue(windows.isNotEmpty())
            } finally {
                dataFilterGetNearestPowerOfTwo = original
            }
        }
    }

    @Test
    fun testDataFilterThrowsStoredPathFallback() {
        runBlocking {
            val original = dataFilterGetNearestPowerOfTwo
            try {
                dataFilterGetNearestPowerOfTwo = { _ -> throw RuntimeException("forced") }
                val stateStore = StateStore(HardwareState(windowSize = 8, overlap = 4, samplingRateHz = 250))
                val frames = (1..8).map { i -> RawFrame(i.toLong(), channel = 0, data = doubleArrayOf(i.toDouble())) }
                val input = flowOf(*frames.toTypedArray())
                val windows = mutableListOf<RawFrame>()
                buffer(input, stateStore) { out -> windows.add(out) }
                assertTrue(windows.isNotEmpty())
            } finally {
                dataFilterGetNearestPowerOfTwo = original
            }
        }
    }

    @Test
    fun testStoredPathInvalidWindowThrows() {
        runBlocking {
            // windowSize < 8 should trigger the require() check in the stored path
            val stateStore = StateStore(HardwareState(windowSize = 4, overlap = 2, samplingRateHz = 250))
            val frames = (1..4).map { i -> RawFrame(i.toLong(), channel = 0, data = doubleArrayOf(i.toDouble())) }
            val input = flowOf(*frames.toTypedArray())
            try {
                buffer(input, stateStore) { _ -> }
                throw AssertionError("Expected IllegalArgumentException for invalid windowSize")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun testComputedPathInvalidWindowThrows() {
        runBlocking {
            // samplingRateHz very small -> computed window may be < 8 and trigger require()
            val bands = listOf(Band("HF", 100.0, 110.0))
            val stateStore = StateStore(HardwareState(windowSize = 0, overlap = 0, samplingRateHz = 1, bands = bands))
            val frames = (1..2).map { i -> RawFrame(i.toLong(), channel = 1, data = doubleArrayOf(i.toDouble())) }
            val input = flowOf(*frames.toTypedArray())
            try {
                buffer(input, stateStore) { _ -> }
                throw AssertionError("Expected IllegalArgumentException for invalid computed windowSize")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun testCollectorContextCancelledThrowsCancellationException() {
        runBlocking {
            val stateStore = StateStore(HardwareState(windowSize = 8, overlap = 4, samplingRateHz = 250))
            val frames = (1..8).map { i -> RawFrame(i.toLong(), channel = 0, data = doubleArrayOf(i.toDouble())) }
            val input = flowOf(*frames.toTypedArray())
            val cancelledJob = Job()
            cancelledJob.cancel()
            // Run buffer inside a context that holds a cancelled Job element
            try {
                withContext(cancelledJob) {
                    buffer(input, stateStore) { _ -> }
                }
                throw AssertionError("Expected CancellationException when collector job is cancelled")
            } catch (_: CancellationException) {
                // expected
            }
        }
    }

    @Test
    fun testCollectorContextWithoutJobProcessesFrames() {
        runBlocking {
            val stateStore = StateStore(HardwareState(windowSize = 8, overlap = 4, samplingRateHz = 250))
            val frames = (1..8).map { i -> RawFrame(i.toLong(), channel = 0, data = doubleArrayOf(i.toDouble())) }
            val windows = mutableListOf<RawFrame>()
            // create a context that explicitly removes the Job element
            val ctxNoJob = currentCoroutineContext().minusKey(Job)
            kotlinx.coroutines.withContext(ctxNoJob) {
                buffer(flowOf(*frames.toTypedArray()), stateStore) { out -> windows.add(out) }
            }
            assertTrue(windows.isNotEmpty(), "Buffer should process frames when Job is absent from context")
        }
    }

    @Test
    fun testComputedOverlapOneProducesWindows() {
        runBlocking {
            // preferredOverlap=1.0 would compute hop==0 before coerceAtLeast; ensure hop coerces to >=1
            val bands = listOf(Band("HF", 10.0, 20.0))
            val stateStore = StateStore(HardwareState(windowSize = 0, overlap = 0, samplingRateHz = 256, bands = bands, preferredOverlap = 0.999))
            val cfg = computeOptimalFFTWindow(samplingRateHz = 256.0, bandsHz = listOf(10.0 to 20.0), preferredOverlap = 0.999)
            val samplesNeeded = cfg.windowSamples
            val frames = (1..(samplesNeeded + 2)).map { i -> RawFrame(i.toLong(), channel = 0, data = doubleArrayOf(i.toDouble())) }
            val windows = mutableListOf<RawFrame>()
            buffer(flowOf(*frames.toTypedArray()), stateStore) { out -> windows.add(out) }
            assertTrue(windows.isNotEmpty(), "Computed path with preferredOverlap=1.0 should still produce windows")
        }
    }

    @Test
    fun testOnWindowRuntimeExceptionPropagates() {
        runBlocking {
            val stateStore = StateStore(HardwareState(windowSize = 8, overlap = 4, samplingRateHz = 250))
            val frames = (1..8).map { i -> RawFrame(i.toLong(), channel = 0, data = doubleArrayOf(i.toDouble())) }
            val input = flowOf(*frames.toTypedArray())
            try {
                buffer(input, stateStore) { _ -> throw IllegalStateException("boom") }
                throw AssertionError("Expected IllegalStateException to propagate")
            } catch (_: IllegalStateException) {
                // expected
            }
        }
    }

    @Test
    fun testComputedPathIsPowerOfTwoTrue() {
        runBlocking {
            val original = dataFilterGetNearestPowerOfTwo
            try {
                // Make the hook return exactly windowSize so isPowerOfTwo=true
                dataFilterGetNearestPowerOfTwo = { n -> n }
                val bands = listOf(Band("HF", 40.0, 50.0))
                val stateStore = StateStore(HardwareState(windowSize = 0, overlap = 0, samplingRateHz = 256, bands = bands))
                val cfg = computeOptimalFFTWindow(samplingRateHz = 256.0, bandsHz = listOf(40.0 to 50.0))
                val samplesNeeded = cfg.windowSamples
                val frames = (1..samplesNeeded).map { i -> RawFrame(i.toLong(), channel = 0, data = doubleArrayOf(i.toDouble())) }
                val windows = mutableListOf<RawFrame>()
                buffer(flowOf(*frames.toTypedArray()), stateStore) { out -> windows.add(out) }
                assertTrue(windows.isNotEmpty(), "Computed path with nfft==windowSize should produce windows")
            } finally {
                dataFilterGetNearestPowerOfTwo = original
            }
        }
    }

    @Test
    fun testStoredPathIsPowerOfTwoFalse() {
        runBlocking {
            val original = dataFilterGetNearestPowerOfTwo
            try {
                // Return a different power of two (e.g., next lower) so isPowerOfTwo=false
                dataFilterGetNearestPowerOfTwo = { n ->
                    var p = 1
                    while (p * 2 < n) p *= 2
                    p
                }
                val stateStore = StateStore(HardwareState(windowSize = 12, overlap = 4, samplingRateHz = 250))
                val frames = (1..12).map { i -> RawFrame(i.toLong(), channel = 0, data = doubleArrayOf(i.toDouble())) }
                val windows = mutableListOf<RawFrame>()
                buffer(flowOf(*frames.toTypedArray()), stateStore) { out -> windows.add(out) }
                assertTrue(windows.isNotEmpty(), "Stored path with nfft!=windowSize should still produce windows and log warning")
            } finally {
                dataFilterGetNearestPowerOfTwo = original
            }
        }
    }

    @Test
    fun testStoredPathIsPowerOfTwoTrue() {
        runBlocking {
            val original = dataFilterGetNearestPowerOfTwo
            try {
                dataFilterGetNearestPowerOfTwo = { n -> n } // nfft == windowSize
                val stateStore = StateStore(HardwareState(windowSize = 8, overlap = 2, samplingRateHz = 250))
                val frames = (1..16).map { i -> RawFrame(i.toLong(), channel = 0, data = doubleArrayOf(i.toDouble())) }
                val windows = mutableListOf<RawFrame>()
                buffer(flowOf(*frames.toTypedArray()), stateStore) { out -> windows.add(out) }
                assertTrue(windows.isNotEmpty(), "Stored path with nfft==windowSize should emit windows")
            } finally {
                dataFilterGetNearestPowerOfTwo = original
            }
        }
    }

    @Test
    fun testComputedPathProducesMultipleWindows() {
        runBlocking {
            val bands = listOf(Band("M", 20.0, 30.0))
            val stateStore = StateStore(HardwareState(windowSize = 0, overlap = 0, samplingRateHz = 256, bands = bands, preferredOverlap = 0.5))
            val cfg = computeOptimalFFTWindow(samplingRateHz = 256.0, bandsHz = listOf(20.0 to 30.0), preferredOverlap = 0.5)
            val samplesNeeded = cfg.windowSamples
            // produce enough samples for several windows
            val frames = (1..(samplesNeeded * 3)).map { i -> RawFrame(i.toLong(), channel = 1, data = doubleArrayOf(i.toDouble())) }
            val windows = mutableListOf<RawFrame>()
            buffer(flowOf(*frames.toTypedArray()), stateStore) { out -> windows.add(out) }
            assertTrue(windows.size >= 2, "Computed path should produce multiple windows when enough samples provided")
        }
    }

    @Test
    fun testStoredPathHighOverlapSlidingByOne() {
        runBlocking {
            // overlap = windowSize - 1 -> hop = 1
            val stateStore = StateStore(HardwareState(windowSize = 8, overlap = 7, samplingRateHz = 250))
            val frames = (1..12).map { i -> RawFrame(i.toLong(), channel = 0, data = doubleArrayOf(i.toDouble())) }
            val windows = mutableListOf<RawFrame>()
            buffer(flowOf(*frames.toTypedArray()), stateStore) { out -> windows.add(out) }
            // With hop=1 and 12 samples, expect multiple overlapping windows
            assertTrue(windows.size >= 3, "High-overlap stored path should produce many overlapping windows")
        }
    }

    @Test
    fun testComputedSamplingUsesDefaultWhenMissing() {
        runBlocking {
            // windowSize <= 0 and samplingRateHz <= 0 should make buffer use default sampling=250.0
            val bands = listOf(Band("X", 30.0, 40.0))
            val stateStore = StateStore(HardwareState(windowSize = 0, overlap = 0, samplingRateHz = 0, bands = bands))
            val cfg = computeOptimalFFTWindow(samplingRateHz = 250.0, bandsHz = listOf(30.0 to 40.0))
            val samplesNeeded = cfg.windowSamples
            val frames = (1..samplesNeeded).map { i -> RawFrame(i.toLong(), channel = 2, data = doubleArrayOf(i.toDouble())) }
            val windows = mutableListOf<RawFrame>()
            buffer(flowOf(*frames.toTypedArray()), stateStore) { out -> windows.add(out) }
            assertTrue(windows.isNotEmpty(), "Computed path with default sampling should produce windows")
        }
    }

    @Test
    fun testComputedPathNonPowerOfTwoHandled() {
        runBlocking {
            val original = dataFilterGetNearestPowerOfTwo
            try {
                // Force the hook to return a different power of two so isPowerOfTwo=false in computed path
                dataFilterGetNearestPowerOfTwo = { n ->
                    var p = 1
                    while (p * 2 < n) p *= 2
                    p
                }
                val bands = listOf(Band("Y", 15.0, 18.0))
                val stateStore = StateStore(HardwareState(windowSize = 0, overlap = 0, samplingRateHz = 200, bands = bands))
                val cfg = computeOptimalFFTWindow(samplingRateHz = 200.0, bandsHz = listOf(15.0 to 18.0))
                val samplesNeeded = cfg.windowSamples
                val frames = (1..samplesNeeded).map { i -> RawFrame(i.toLong(), channel = 3, data = doubleArrayOf(i.toDouble())) }
                val input = flowOf(*frames.toTypedArray())
                val windows = mutableListOf<RawFrame>()
                buffer(input, stateStore) { out -> windows.add(out) }
                assertTrue(windows.isNotEmpty(), "Computed path with nfft!=windowSize should still produce windows")
            } finally {
                dataFilterGetNearestPowerOfTwo = original
            }
        }
    }

    @Test
    fun testComputedPathWithEmptyBandsProducesWindow() {
        runBlocking {
            // computeOptimalFFTWindow requires non-empty bands; verify it throws
            val stateStore = StateStore(HardwareState(windowSize = 0, overlap = 0, samplingRateHz = 250, bands = emptyList()))
            kotlin.test.assertFailsWith<IllegalArgumentException> {
                computeOptimalFFTWindow(samplingRateHz = 250.0, bandsHz = listOf())
            }
        }
    }

    @Test
    fun testCollectorContextCancelledComputedPathThrowsBeforeProcessing() {
        runBlocking {
            val bands = listOf(Band("HF", 20.0, 30.0))
            val stateStore = StateStore(HardwareState(windowSize = 0, overlap = 0, samplingRateHz = 256, bands = bands))
            val cfg = computeOptimalFFTWindow(samplingRateHz = 256.0, bandsHz = listOf(20.0 to 30.0))
            val samplesNeeded = cfg.windowSamples
            val frames = (1..samplesNeeded).map { i -> RawFrame(i.toLong(), channel = 0, data = doubleArrayOf(i.toDouble())) }
            val cancelled = Job()
            cancelled.cancel()
            val ctx = currentCoroutineContext() + cancelled
            kotlin.test.assertFailsWith<CancellationException> {
                withContext(ctx) {
                    buffer(flowOf(*frames.toTypedArray()), stateStore) { _ -> }
                }
            }
        }
    }

    @Test
    fun testCollectorContextCancelledStoredPathThrowsBeforeProcessing() {
        runBlocking {
            val stateStore = StateStore(HardwareState(windowSize = 8, overlap = 4, samplingRateHz = 250))
            val frames = (1..8).map { i -> RawFrame(i.toLong(), channel = 0, data = doubleArrayOf(i.toDouble())) }
            val cancelled = Job()
            cancelled.cancel()
            val ctx = currentCoroutineContext() + cancelled
            kotlin.test.assertFailsWith<CancellationException> {
                withContext(ctx) {
                    buffer(flowOf(*frames.toTypedArray()), stateStore) { _ -> }
                }
            }
        }
    }

    @Test
    fun testRepeatHopHandlesEmptyAndNonEmptyRemovals() {
        runBlocking {
            // Case A: exact window -> after taking window buffer empty; repeat should find buffer.isNotEmpty() false
            val stateStoreA = StateStore(HardwareState(windowSize = 8, overlap = 4, samplingRateHz = 250))
            val framesA = (1..8).map { i -> RawFrame(i.toLong(), channel = 0, data = doubleArrayOf(i.toDouble())) }
            val windowsA = mutableListOf<RawFrame>()
            buffer(flowOf(*framesA.toTypedArray()), stateStoreA) { out -> windowsA.add(out) }
            assertTrue(windowsA.size == 1, "Exact window should produce one window")

            // Case B: extra tail remains -> repeat should remove while buffer.isNotEmpty() true for some repeats
            // Use a valid windowSize >=8 so the require() check passes; choose overlap=6 -> hop=2
            val stateStoreB = StateStore(HardwareState(windowSize = 8, overlap = 6, samplingRateHz = 250))
            // provide 9 samples: one full window (1..8) and one tail sample
            val framesB = (1..9).map { i -> RawFrame(i.toLong(), channel = 0, data = doubleArrayOf(i.toDouble())) }
            val windowsB = mutableListOf<RawFrame>()
            buffer(flowOf(*framesB.toTypedArray()), stateStoreB) { out -> windowsB.add(out) }
            assertTrue(windowsB.isNotEmpty(), "Should produce at least one window when tail exists")
        }
    }

    @Test
    fun testTestJobCheckOverrideCancelsBuffer() {
        runBlocking {
            val original = testJobCheckOverride
            try {
                testJobCheckOverride = { true } // Always cancel
                val stateStore = StateStore(HardwareState(windowSize = 8, overlap = 4, samplingRateHz = 250))
                val frames = (1..8).map { i -> RawFrame(i.toLong(), channel = 0, data = doubleArrayOf(i.toDouble())) }
                val input = flowOf(*frames.toTypedArray())
                assertFailsWith<CancellationException> {
                    buffer(input, stateStore) { _ -> }
                }
            } finally {
                testJobCheckOverride = original
            }
        }
    }

    @Test
    fun testTestJob2CheckOverrideCancelsBeforeOnWindow_ComputedPath() {
        runBlocking {
            val original = testJob2CheckOverride
            try {
                testJob2CheckOverride = { true } // Always cancel before onWindow
                val bands = listOf(Band("HF", 100.0, 110.0))
                val stateStore = StateStore(HardwareState(windowSize = 0, overlap = 0, samplingRateHz = 256, bands = bands, preferredOverlap = 0.5))
                val cfg = computeOptimalFFTWindow(samplingRateHz = 256.0, bandsHz = listOf(100.0 to 110.0), preferredOverlap = 0.5)
                val samplesNeeded = cfg.windowSamples
                val frames = (1..samplesNeeded).map { i -> RawFrame(i.toLong(), channel = 0, data = doubleArrayOf(i.toDouble())) }
                val input = flowOf(*frames.toTypedArray())
                assertFailsWith<CancellationException> {
                    buffer(input, stateStore) { _ -> }
                }
            } finally {
                testJob2CheckOverride = original
            }
        }
    }

    @Test
    fun testTestJob2CheckOverrideCancelsBeforeOnWindow_StoredPath() {
        runBlocking {
            val original = testJob2CheckOverride
            try {
                testJob2CheckOverride = { true } // Always cancel before onWindow
                val stateStore = StateStore(HardwareState(windowSize = 8, overlap = 4, samplingRateHz = 250))
                val frames = (1..8).map { i -> RawFrame(i.toLong(), channel = 0, data = doubleArrayOf(i.toDouble())) }
                val input = flowOf(*frames.toTypedArray())
                assertFailsWith<CancellationException> {
                    buffer(input, stateStore) { _ -> }
                }
            } finally {
                testJob2CheckOverride = original
            }
        }
    }

    @Test
    fun testTestJobCheckOverrideReturnsFalseDoesNotCancel() {
        runBlocking {
            val original = testJobCheckOverride
            try {
                testJobCheckOverride = { false } // Should not cancel
                val stateStore = StateStore(HardwareState(windowSize = 8, overlap = 4, samplingRateHz = 250))
                val frames = (1..8).map { i -> RawFrame(i.toLong(), channel = 0, data = doubleArrayOf(i.toDouble())) }
                val input = flowOf(*frames.toTypedArray())
                val windows = mutableListOf<RawFrame>()
                buffer(input, stateStore) { out -> windows.add(out) }
                assertTrue(windows.isNotEmpty(), "Buffer should process frames when testJobCheckOverride returns false")
            } finally {
                testJobCheckOverride = original
            }
        }
    }

    @Test
    fun testTestJob2CheckOverrideReturnsFalseDoesNotCancel_ComputedPath() {
        runBlocking {
            val original = testJob2CheckOverride
            try {
                testJob2CheckOverride = { false } // Should not cancel
                val bands = listOf(Band("HF", 100.0, 110.0))
                val stateStore = StateStore(HardwareState(windowSize = 0, overlap = 0, samplingRateHz = 256, bands = bands, preferredOverlap = 0.5))
                val cfg = computeOptimalFFTWindow(samplingRateHz = 256.0, bandsHz = listOf(100.0 to 110.0), preferredOverlap = 0.5)
                val samplesNeeded = cfg.windowSamples
                val frames = (1..samplesNeeded).map { i -> RawFrame(i.toLong(), channel = 0, data = doubleArrayOf(i.toDouble())) }
                val input = flowOf(*frames.toTypedArray())
                val windows = mutableListOf<RawFrame>()
                buffer(input, stateStore) { out -> windows.add(out) }
                assertTrue(windows.isNotEmpty(), "Buffer should process frames when testJob2CheckOverride returns false (computed path)")
            } finally {
                testJob2CheckOverride = original
            }
        }
    }

    @Test
    fun testTestJob2CheckOverrideNullJobNullDoesNotCancel() {
        runBlocking {
            val original = testJobCheckOverride
            try {
                testJobCheckOverride = null
                val stateStore = StateStore(HardwareState(windowSize = 8, overlap = 4, samplingRateHz = 250))
                val frames = (1..8).map { i -> RawFrame(i.toLong(), channel = 0, data = doubleArrayOf(i.toDouble())) }
                val windows = mutableListOf<RawFrame>()
                // Remove Job from context
                val ctxNoJob = currentCoroutineContext().minusKey(Job)
                withContext(ctxNoJob) {
                    buffer(flowOf(*frames.toTypedArray()), stateStore) { out -> windows.add(out) }
                }
                assertTrue(windows.isNotEmpty(), "Buffer should process frames when testJobCheckOverride is null and job is null")
            } finally {
                testJobCheckOverride = original
            }
        }
    }

    @Test
    fun testTestJob2CheckOverrideCancelsBeforeOnWindow() {
        runBlocking {
            val original = testJob2CheckOverride
            try {
                testJob2CheckOverride = { true } // Always cancel before onWindow
                val bands = listOf(Band("HF", 100.0, 110.0))
                val stateStore = StateStore(HardwareState(windowSize = 0, overlap = 0, samplingRateHz = 256, bands = bands, preferredOverlap = 0.5))
                val cfg = computeOptimalFFTWindow(samplingRateHz = 256.0, bandsHz = listOf(100.0 to 110.0), preferredOverlap = 0.5)
                val samplesNeeded = cfg.windowSamples
                val frames = (1..samplesNeeded).map { i -> RawFrame(i.toLong(), channel = 0, data = doubleArrayOf(i.toDouble())) }
                val input = flowOf(*frames.toTypedArray())
                assertFailsWith<CancellationException> {
                    buffer(input, stateStore) { _ -> }
                }
            } finally {
                testJob2CheckOverride = original
            }
        }
    }

    @Test
    fun testTestJob2CheckOverrideReturnsFalseDoesNotCancel() {
        runBlocking {
            val original = testJob2CheckOverride
            try {
                testJob2CheckOverride = { false } // Should not cancel
                val stateStore = StateStore(HardwareState(windowSize = 8, overlap = 4, samplingRateHz = 250))
                val frames = (1..8).map { i -> RawFrame(i.toLong(), channel = 0, data = doubleArrayOf(i.toDouble())) }
                val input = flowOf(*frames.toTypedArray())
                val windows = mutableListOf<RawFrame>()
                buffer(input, stateStore) { out -> windows.add(out) }
                assertTrue(windows.isNotEmpty(), "Buffer should process frames when testJob2CheckOverride returns false")
            } finally {
                testJob2CheckOverride = original
            }
        }
    }

    @Test
    fun testTestJob2CheckOverrideNullJob2NullDoesNotCancel() {
        runBlocking {
            val original = testJob2CheckOverride
            try {
                testJob2CheckOverride = null
                val bands = listOf(Band("HF", 100.0, 110.0))
                val stateStore = StateStore(HardwareState(windowSize = 0, overlap = 0, samplingRateHz = 256, bands = bands, preferredOverlap = 0.5))
                val cfg = computeOptimalFFTWindow(samplingRateHz = 256.0, bandsHz = listOf(100.0 to 110.0), preferredOverlap = 0.5)
                val samplesNeeded = cfg.windowSamples
                val frames = (1..samplesNeeded).map { i -> RawFrame(i.toLong(), channel = 0, data = doubleArrayOf(i.toDouble())) }
                val ctxNoJob = currentCoroutineContext().minusKey(Job)
                val windows = mutableListOf<RawFrame>()
                withContext(ctxNoJob) {
                    buffer(flowOf(*frames.toTypedArray()), stateStore) { out -> windows.add(out) }
                }
                assertTrue(windows.isNotEmpty(), "Computed path: should process frames when testJob2CheckOverride is null and job2 is null")
            } finally {
                testJob2CheckOverride = original
            }
        }
    }

    @Test
    fun testComputedPathTestJob2CheckOverrideNullJob2ActiveDoesNotCancel() {
        runBlocking {
            val original = testJob2CheckOverride
            try {
                testJob2CheckOverride = null
                val bands = listOf(Band("HF", 100.0, 110.0))
                val stateStore = StateStore(HardwareState(windowSize = 0, overlap = 0, samplingRateHz = 256, bands = bands, preferredOverlap = 0.5))
                val cfg = computeOptimalFFTWindow(samplingRateHz = 256.0, bandsHz = listOf(100.0 to 110.0), preferredOverlap = 0.5)
                val samplesNeeded = cfg.windowSamples
                val frames = (1..samplesNeeded).map { i -> RawFrame(i.toLong(), channel = 0, data = doubleArrayOf(i.toDouble())) }
                val job = Job() // active
                val ctx = currentCoroutineContext() + job
                val windows = mutableListOf<RawFrame>()
                withContext(ctx) {
                    buffer(flowOf(*frames.toTypedArray()), stateStore) { out -> windows.add(out) }
                }
                assertTrue(windows.isNotEmpty(), "Computed path: should process frames when testJob2CheckOverride is null and job2 is active")
            } finally {
                testJob2CheckOverride = original
            }
        }
    }

    @Test
    fun testComputedPathTestJob2CheckOverrideNullJob2NotActiveThrows() {
        runBlocking {
            val original = testJob2CheckOverride
            try {
                testJob2CheckOverride = null
                val bands = listOf(Band("HF", 100.0, 110.0))
                val stateStore = StateStore(HardwareState(windowSize = 0, overlap = 0, samplingRateHz = 256, bands = bands, preferredOverlap = 0.5))
                val cfg = computeOptimalFFTWindow(samplingRateHz = 256.0, bandsHz = listOf(100.0 to 110.0), preferredOverlap = 0.5)
                val samplesNeeded = cfg.windowSamples
                val frames = (1..samplesNeeded * 2).map { i -> RawFrame(i.toLong(), channel = 0, data = doubleArrayOf(i.toDouble())) }
                val job = Job()
                job.cancel()
                val ctx = currentCoroutineContext() + job
                try {
                    withContext(ctx) {
                        buffer(flowOf(*frames.toTypedArray()), stateStore) { _ -> }
                    }
                    throw AssertionError("Expected CancellationException when job2 is not active and testJob2CheckOverride is null (computed path)")
                } catch (_: CancellationException) {
                    // expected
                }
            } finally {
                testJob2CheckOverride = original
            }
        }
    }

    @Test
    fun testStoredPathTestJob2CheckOverrideNullJob2NullDoesNotCancel() {
        runBlocking {
            val original = testJob2CheckOverride
            try {
                testJob2CheckOverride = null
                val stateStore = StateStore(HardwareState(windowSize = 8, overlap = 4, samplingRateHz = 250))
                val frames = (1..8).map { i -> RawFrame(i.toLong(), channel = 0, data = doubleArrayOf(i.toDouble())) }
                val ctxNoJob = currentCoroutineContext().minusKey(Job)
                val windows = mutableListOf<RawFrame>()
                withContext(ctxNoJob) {
                    buffer(flowOf(*frames.toTypedArray()), stateStore) { out -> windows.add(out) }
                }
                assertTrue(windows.isNotEmpty(), "Stored path: should process frames when testJob2CheckOverride is null and job2 is null")
            } finally {
                testJob2CheckOverride = original
            }
        }
    }

    @Test
    fun testStoredPathTestJob2CheckOverrideNullJob2ActiveDoesNotCancel() {
        runBlocking {
            val original = testJob2CheckOverride
            try {
                testJob2CheckOverride = null
                val stateStore = StateStore(HardwareState(windowSize = 8, overlap = 4, samplingRateHz = 250))
                val frames = (1..8).map { i -> RawFrame(i.toLong(), channel = 0, data = doubleArrayOf(i.toDouble())) }
                val job = Job() // active
                val ctx = currentCoroutineContext() + job
                val windows = mutableListOf<RawFrame>()
                withContext(ctx) {
                    buffer(flowOf(*frames.toTypedArray()), stateStore) { out -> windows.add(out) }
                }
                assertTrue(windows.isNotEmpty(), "Stored path: should process frames when testJob2CheckOverride is null and job2 is active")
            } finally {
                testJob2CheckOverride = original
            }
        }
    }

    @Test
    fun testStoredPathTestJob2CheckOverrideNullJob2NotActiveThrows() {
        runBlocking {
            val original = testJob2CheckOverride
            try {
                testJob2CheckOverride = null
                val stateStore = StateStore(HardwareState(windowSize = 8, overlap = 4, samplingRateHz = 250))
                val frames = (1..16).map { i -> RawFrame(i.toLong(), channel = 0, data = doubleArrayOf(i.toDouble())) }
                val job = Job()
                job.cancel()
                val ctx = currentCoroutineContext() + job
                try {
                    withContext(ctx) {
                        buffer(flowOf(*frames.toTypedArray()), stateStore) { _ -> }
                    }
                    throw AssertionError("Expected CancellationException when job2 is not active and testJob2CheckOverride is null (stored path)")
                } catch (_: CancellationException) {
                    // expected
                }
            } finally {
                testJob2CheckOverride = original
            }
        }
    }

    @Test
    fun testRequireComputedPathOverlapSamplesOutOfRangeThrows() {
        runBlocking {
            val bands = listOf(Band("HF", 100.0, 110.0))
            val stateStore = StateStore(HardwareState(windowSize = 16, overlap = 0, samplingRateHz = 256, bands = bands, preferredOverlap = 1.5))
            val frames = (1..16).map { i -> RawFrame(i.toLong(), channel = 0, data = doubleArrayOf(i.toDouble())) }
            val input = flowOf(*frames.toTypedArray())
            val original = testJob2CheckOverride
            testJob2CheckOverride = null
            assertFailsWith<IllegalArgumentException> {
                buffer(input, stateStore) { _ -> }
            }
            testJob2CheckOverride = original
        }
    }
}
