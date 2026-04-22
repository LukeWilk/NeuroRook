package io.github.lukewilk.hardware.pipeline

import io.github.lukewilk.hardware.RawFrame
import io.github.lukewilk.shared.Band
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
/**
 * Window-emission and configuration-path tests for `buffer()`.
 */
class BufferWindowEmissionTest {
    /** Verifies interleaved channel frames are buffered independently instead of being merged into mixed windows. */
    @Test
    fun `stored buffering keeps interleaved channels isolated`() = runBlocking {
        val channel0Samples = (1..8).toList()
        val channel1Samples = (101..108).toList()
        val interleavedFrames = buildList {
            for (index in channel0Samples.indices) {
                add(RawFrame(timestampMs = (index + 1).toLong(), channel = 0, data = doubleArrayOf(channel0Samples[index].toDouble())))
                add(RawFrame(timestampMs = ((index + 1) * 10L), channel = 1, data = doubleArrayOf(channel1Samples[index].toDouble())))
            }
        }
        val windows = collectBufferedWindows(
            stateStore = storedBufferState(windowSize = 8, overlap = 0, channelCount = 2),
            frames = interleavedFrames
        )
        assertEquals(2, windows.size)
        assertEquals(0, windows[0].channel)
        assertEquals(8L, windows[0].timestampMs)
        assertContentEquals(channel0Samples.map(Int::toDouble).toDoubleArray(), windows[0].data)
        assertEquals(1, windows[1].channel)
        assertEquals(80L, windows[1].timestampMs)
        assertContentEquals(channel1Samples.map(Int::toDouble).toDoubleArray(), windows[1].data)
    }

    /** Verifies computed buffering also preserves per-channel isolation when frames arrive interleaved. */
    @Test
    fun `computed buffering keeps interleaved channels isolated`() = runBlocking {
        val bands = listOf(Band("HF", 100.0, 110.0))
        val windowSize = computedWindowSamples(256.0, bands, preferredOverlap = 0.5)
        val channel0 = (1..windowSize).map { value ->
            RawFrame(timestampMs = value.toLong(), channel = 0, data = doubleArrayOf(value.toDouble()))
        }
        val channel1 = (1..windowSize).map { value ->
            RawFrame(timestampMs = (value * 10L), channel = 1, data = doubleArrayOf(100.0 + value))
        }
        val interleavedFrames = buildList {
            for (index in 0 until windowSize) {
                add(channel0[index])
                add(channel1[index])
            }
        }

        val windows = collectBufferedWindows(
            stateStore = computedBufferState(samplingRateHz = 256, bands = bands, preferredOverlap = 0.5, windowSize = 16),
            frames = interleavedFrames
        )

        assertEquals(2, windows.size)
        assertEquals(0, windows[0].channel)
        assertEquals(windowSize.toLong(), windows[0].timestampMs)
        assertContentEquals((1..windowSize).map(Int::toDouble).toDoubleArray(), windows[0].data)
        assertEquals(1, windows[1].channel)
        assertEquals(windowSize * 10L, windows[1].timestampMs)
        assertContentEquals((1..windowSize).map { 100.0 + it }.toDoubleArray(), windows[1].data)
    }

    /** Verifies overlapping windows continue reusing the correct per-channel deque after earlier interleaved emissions. */
    @Test
    fun `stored buffering reuses each channel buffer independently across overlapping windows`() = runBlocking {
        val channel0Samples = (1..12).toList()
        val channel1Samples = (101..112).toList()
        val interleavedFrames = buildList {
            for (index in channel0Samples.indices) {
                add(RawFrame(timestampMs = (index + 1).toLong(), channel = 0, data = doubleArrayOf(channel0Samples[index].toDouble())))
                add(RawFrame(timestampMs = ((index + 1) * 10L), channel = 1, data = doubleArrayOf(channel1Samples[index].toDouble())))
            }
        }

        val windows = collectBufferedWindows(
            stateStore = storedBufferState(windowSize = 8, overlap = 4, channelCount = 2),
            frames = interleavedFrames
        )

        assertEquals(4, windows.size)
        assertEquals(0, windows[0].channel)
        assertContentEquals((1..8).map(Int::toDouble).toDoubleArray(), windows[0].data)
        assertEquals(1, windows[1].channel)
        assertContentEquals((101..108).map(Int::toDouble).toDoubleArray(), windows[1].data)
        assertEquals(0, windows[2].channel)
        assertEquals(12L, windows[2].timestampMs)
        assertContentEquals((5..12).map(Int::toDouble).toDoubleArray(), windows[2].data)
        assertEquals(1, windows[3].channel)
        assertEquals(120L, windows[3].timestampMs)
        assertContentEquals((105..112).map(Int::toDouble).toDoubleArray(), windows[3].data)
    }

    /** Verifies stored buffering emits overlapping windows with the expected timestamps and payloads. */
    @Test
    fun `stored buffering emits overlapping windows`() = runBlocking {
        val windows = collectBufferedWindows(
            stateStore = storedBufferState(windowSize = 8, overlap = 4),
            frames = sampleFrames(1..12)
        )
        assertEquals(2, windows.size)
        assertEquals(8L, windows[0].timestampMs)
        assertContentEquals(doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0), windows[0].data)
        assertEquals(12L, windows[1].timestampMs)
        assertContentEquals(doubleArrayOf(5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0), windows[1].data)
    }
    /** Verifies computed buffering derives the window size from bands and preferred overlap. */
    @Test
    fun `computed buffering uses the derived window size`() = runBlocking {
        val bands = listOf(Band("HF", 100.0, 110.0))
        val windowSize = computedWindowSamples(256.0, bands, preferredOverlap = 0.5)
        val windows = collectBufferedWindows(
            stateStore = computedBufferState(samplingRateHz = 256, bands = bands, preferredOverlap = 0.5, windowSize = 16),
            frames = sampleFrames(1..windowSize, channel = 1)
        )
        assertTrue(windows.isNotEmpty())
        assertEquals(windowSize, windows.first().data.size)
        assertEquals(windowSize.toLong(), windows.first().timestampMs)
    }
    /** Verifies non-power-of-two stored windows are still emitted so downstream padding can happen later. */
    @Test
    fun `stored buffering still emits non power of two windows`() = runBlocking {
        val windows = collectBufferedWindows(
            stateStore = storedBufferState(windowSize = 10, overlap = 2),
            frames = sampleFrames(1..10)
        )
        assertEquals(1, windows.size)
        assertEquals(10, windows.single().data.size)
        assertEquals(10L, windows.single().timestampMs)
    }
    /** Verifies a zero stored window delegates to computed window selection. */
    @Test
    fun `window size zero falls back to the computed configuration`() = runBlocking {
        val bands = listOf(Band("HF", 80.0, 120.0))
        val windowSize = computedWindowSamples(256.0, bands)
        val windows = collectBufferedWindows(
            stateStore = computedBufferState(samplingRateHz = 256, bands = bands, windowSize = 0),
            frames = sampleFrames(1..windowSize, channel = 1)
        )
        assertTrue(windows.isNotEmpty())
        assertEquals(windowSize, windows.first().data.size)
        assertEquals(windowSize.toLong(), windows.first().timestampMs)
    }
    /** Verifies computed buffering falls back to the default sampling rate when none is configured. */
    @Test
    fun `computed buffering uses the default sampling rate when the state omits it`() = runBlocking {
        val bands = listOf(Band("Beta", 30.0, 40.0))
        val windowSize = computedWindowSamples(250.0, bands)
        val windows = collectBufferedWindows(
            stateStore = computedBufferState(samplingRateHz = 0, bands = bands),
            frames = sampleFrames(1..windowSize, channel = 2)
        )
        assertTrue(windows.isNotEmpty())
        assertEquals(windowSize, windows.first().data.size)
    }
    /** Verifies a near-total preferred overlap still coerces the computed hop to a usable value. */
    @Test
    fun `computed buffering handles a near one preferred overlap`() = runBlocking {
        val bands = listOf(Band("Gamma", 10.0, 20.0))
        val windowSize = computedWindowSamples(256.0, bands, preferredOverlap = 0.999)
        val windows = collectBufferedWindows(
            stateStore = computedBufferState(samplingRateHz = 256, bands = bands, preferredOverlap = 0.999),
            frames = sampleFrames(1..(windowSize + 2))
        )
        assertTrue(windows.isNotEmpty())
    }
    /** Verifies computed buffering can emit multiple windows when enough samples are available. */
    @Test
    fun `computed buffering emits multiple windows when enough samples arrive`() = runBlocking {
        val bands = listOf(Band("Mid", 20.0, 30.0))
        val windowSize = computedWindowSamples(256.0, bands, preferredOverlap = 0.5)
        val windows = collectBufferedWindows(
            stateStore = computedBufferState(samplingRateHz = 256, bands = bands, preferredOverlap = 0.5),
            frames = sampleFrames(1..(windowSize * 3), channel = 1)
        )
        assertTrue(windows.size >= 2)
    }
    /** Verifies a high stored overlap slides the window one sample at a time. */
    @Test
    fun `stored buffering supports sliding by one sample`() = runBlocking {
        val windows = collectBufferedWindows(
            stateStore = storedBufferState(windowSize = 8, overlap = 7),
            frames = sampleFrames(1..12)
        )
        assertTrue(windows.size >= 3)
    }
    /** Verifies both buffering branches fall back to a manual power-of-two implementation when BrainFlow fails. */
    @Test
    fun `manual power of two fallback still allows window emission`() = runBlocking {
        withNearestPowerOfTwoOverride(override = { throw RuntimeException("forced") }) {
            val computedBands = listOf(Band("HF", 80.0, 120.0))
            val computedSize = computedWindowSamples(256.0, computedBands)
            val computedWindows = collectBufferedWindows(
                stateStore = computedBufferState(samplingRateHz = 256, bands = computedBands),
                frames = sampleFrames(1..computedSize, channel = 1)
            )
            val storedWindows = collectBufferedWindows(
                stateStore = storedBufferState(windowSize = 8, overlap = 4),
                frames = sampleFrames(1..8)
            )
            assertTrue(computedWindows.isNotEmpty())
            assertTrue(storedWindows.isNotEmpty())
        }
    }
}
