package io.github.lukewilk.hardware.pipeline

import brainflow.DataFilter
import io.github.lukewilk.hardware.RawFrame
import io.github.lukewilk.shared.StateStore
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.logging.LoggerProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Job
import io.github.lukewilk.hardware.pipeline.signal.computeOptimalFFTWindow

// Test hook: can be overridden in tests to simulate DataFilter failures.
var dataFilterGetNearestPowerOfTwo: (Int) -> Int = { n -> DataFilter.get_nearest_power_of_two(n) }

// Test hooks: can be overridden in tests to simulate Job cancellation checks.
var testJobCheckOverride: ((Job?) -> Boolean)? = null
var testJob2CheckOverride: ((Job?) -> Boolean)? = null

/**
 * Buffers incoming RawFrame samples into a sliding window and calls onWindow for each full window.
 * windowSize and overlap are read from stateStore, so they can be changed dynamically.
 * Preserves RawFrame metadata (timestamp, etc).
 */
suspend fun buffer(
    inputFlow: Flow<RawFrame>,
    stateStore: StateStore<HardwareState>,
    onWindow: suspend (RawFrame) -> Unit
) {
    val channelBuffers = mutableMapOf<Int, ArrayDeque<Double>>()
    val channelTimestamps = mutableMapOf<Int, Long>()

    inputFlow.collect { frame ->
        val ctx = currentCoroutineContext()
        val job = ctx[Job]
        // Use test hook if provided; otherwise default behavior
        if (shouldCancelBuffering(job, testJobCheckOverride)) throw CancellationException("Buffer coroutine cancelled")

        val channelBuffer = channelBuffers.getOrPut(frame.channel) { ArrayDeque() }
        channelBuffer.addAll(frame.data.asList())
        channelTimestamps[frame.channel] = frame.timestampMs

        val st = stateStore.get()
        var windowSize = st.windowSize
        val overlapFraction = st.preferredOverlap

        // If preferredOverlap is set or windowSize is non-positive, compute a suggested window using bands & sampling rate
        if (overlapFraction != null || windowSize <= 0) {
            val sampling = if (st.samplingRateHz > 0) st.samplingRateHz.toDouble() else 250.0
            val bands = st.bands.map { it.lowHz to it.highHz }
            val cfg = computeOptimalFFTWindow(
                samplingRateHz = sampling,
                bandsHz = bands,
                preferredOverlap = overlapFraction
            )
            windowSize = cfg.windowSamples
            val hop = (windowSize * (1.0 - cfg.overlap)).toInt().coerceAtLeast(1)
            val overlapSamples = (windowSize - hop).coerceIn(0, windowSize - 1)

            val nfft = try {
                dataFilterGetNearestPowerOfTwo(windowSize)
            } catch (_: Throwable) {
                var p = 1
                while (p < windowSize) p = p shl 1
                p
            }
            val isPowerOfTwo = (nfft == windowSize)
            validateComputedWindow(windowSize, overlapSamples, isPowerOfTwo)

            while (channelBuffer.size >= windowSize) {
                val window = channelBuffer.take(windowSize).toDoubleArray()
                val outFrame = RawFrame(channelTimestamps.getValue(frame.channel), frame.channel, window)
                try {
                    val ctx2 = currentCoroutineContext()
                    val job2 = ctx2[Job]
                    if (shouldCancelBuffering(job2, testJob2CheckOverride)) throw CancellationException("Buffer cancelled before onWindow")
                    onWindow(outFrame)
                } catch (e: CancellationException) {
                    throw e
                }
                // Slide the buffer by hop samples (windowSize - overlapSamples)
                slideBuffer(channelBuffer, hop)
            }
        } else {
            // Existing behavior: windowSize & overlap stored as ints in state
            val overlap = st.overlap
            val nfft = try {
                dataFilterGetNearestPowerOfTwo(windowSize)
            } catch (_: Throwable) {
                var p = 1
                while (p < windowSize) p = p shl 1
                p
            }
            val isPowerOfTwo = (nfft == windowSize)
            validateStoredWindow(windowSize, overlap, isPowerOfTwo)

            if (!isPowerOfTwo) {
                val logger = LoggerProvider.getLogger("Buffer")
                logger.w { "windowSize=$windowSize is not a power of two; downstream FFT will use nfft=$nfft and padding will be applied" }
            }

            val hop = windowSize - overlap  // Number of samples to advance each iteration

            while (channelBuffer.size >= windowSize) {
                val window = channelBuffer.take(windowSize).toDoubleArray()
                val outFrame = RawFrame(channelTimestamps.getValue(frame.channel), frame.channel, window)
                try {
                    val ctx2 = currentCoroutineContext()
                    val job2 = ctx2[Job]
                    if (shouldCancelBuffering(job2, testJob2CheckOverride)) throw CancellationException("Buffer cancelled before onWindow")
                    onWindow(outFrame)
                } catch (e: CancellationException) {
                    throw e
                }
                // Slide the buffer by hop samples (windowSize - overlap)
                slideBuffer(channelBuffer, hop)
            }
        }
    }
}

internal fun shouldCancelBuffering(job: Job?, overrideCheck: ((Job?) -> Boolean)?): Boolean {
    if (overrideCheck != null) {
        return overrideCheck(job)
    }
    return job != null && !job.isActive
}

internal fun slideBuffer(buffer: ArrayDeque<Double>, hop: Int) {
    repeat(hop) {
        if (buffer.isNotEmpty()) {
            buffer.removeFirst()
        }
    }
}

internal fun validateComputedWindow(windowSize: Int, overlapSamples: Int, isPowerOfTwo: Boolean) {
    require(windowSize >= 8) {
        "Invalid windowSize/overlap: windowSize=$windowSize, overlap=$overlapSamples, isPowerOfTwo=$isPowerOfTwo"
    }
}

internal fun validateStoredWindow(windowSize: Int, overlap: Int, isPowerOfTwo: Boolean) {
    require(windowSize >= 8) {
        "Invalid windowSize/overlap: windowSize=$windowSize, overlap=$overlap, isPowerOfTwo=$isPowerOfTwo"
    }
    require(overlap in 0 until windowSize) {
        "Invalid windowSize/overlap: windowSize=$windowSize, overlap=$overlap, isPowerOfTwo=$isPowerOfTwo"
    }
}
