package io.github.lukewilk.hardware.pipeline
import io.github.lukewilk.hardware.RawFrame
import io.github.lukewilk.hardware.pipeline.signal.computeOptimalFFTWindow
import io.github.lukewilk.shared.Band
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
/** Creates a deterministic list of single-sample frames for buffering scenarios. */
internal fun sampleFrames(range: IntRange, channel: Int = 0): List<RawFrame> = range.map { value ->
    RawFrame(
        timestampMs = value.toLong(),
        channel = channel,
        data = doubleArrayOf(value.toDouble())
    )
}
/** Creates a stored-window buffer state with explicit window size and overlap values. */
internal fun storedBufferState(
    windowSize: Int,
    overlap: Int,
    samplingRateHz: Int = 250,
    channelCount: Int = 1
): StateStore<HardwareState> = StateStore(
    HardwareState(
        windowSize = windowSize,
        overlap = overlap,
        samplingRateHz = samplingRateHz,
        channels = channelCount
    )
)
/** Creates a computed-window buffer state that derives the window from bands and overlap preferences. */
internal fun computedBufferState(
    samplingRateHz: Int,
    bands: List<Band>,
    preferredOverlap: Double? = null,
    windowSize: Int = 0
): StateStore<HardwareState> = StateStore(
    HardwareState(
        windowSize = windowSize,
        overlap = 0,
        samplingRateHz = samplingRateHz,
        bands = bands,
        preferredOverlap = preferredOverlap,
        channels = 1
    )
)
/** Runs `buffer()` and collects every emitted output window for focused assertions. */
internal suspend fun collectBufferedWindows(
    stateStore: StateStore<HardwareState>,
    frames: List<RawFrame>,
    onWindow: suspend (RawFrame) -> Unit = {}
): List<RawFrame> {
    val windows = mutableListOf<RawFrame>()
    buffer(flowOf(*frames.toTypedArray()), stateStore) { frame ->
        windows += frame
        onWindow(frame)
    }
    return windows
}
/** Computes the expected window sample count used by the computed buffering branch. */
internal fun computedWindowSamples(
    samplingRateHz: Double,
    bands: List<Band>,
    preferredOverlap: Double? = null
): Int = computeOptimalFFTWindow(
    samplingRateHz = samplingRateHz,
    bandsHz = bands.map { it.lowHz to it.highHz },
    preferredOverlap = preferredOverlap
).windowSamples
/** Temporarily overrides the BrainFlow power-of-two helper for deterministic branch coverage. */
internal inline fun <T> withNearestPowerOfTwoOverride(
    crossinline override: (Int) -> Int,
    block: () -> T
): T {
    val original = dataFilterGetNearestPowerOfTwo
    return try {
        dataFilterGetNearestPowerOfTwo = { value -> override(value) }
        block()
    } finally {
        dataFilterGetNearestPowerOfTwo = original
    }
}
/** Temporarily overrides buffer cancellation hooks so tests can target each cancellation branch directly. */
internal suspend fun withBufferCancellationOverrides(
    topLevelOverride: ((Job?) -> Boolean)? = testJobCheckOverride,
    preWindowOverride: ((Job?) -> Boolean)? = testJob2CheckOverride,
    block: suspend () -> Unit
) {
    val originalTopLevel = testJobCheckOverride
    val originalPreWindow = testJob2CheckOverride
    try {
        testJobCheckOverride = topLevelOverride
        testJob2CheckOverride = preWindowOverride
        block()
    } finally {
        testJobCheckOverride = originalTopLevel
        testJob2CheckOverride = originalPreWindow
    }
}
/** Runs a suspend block in the current coroutine context after removing any Job element. */
internal suspend fun withoutJobInContext(block: suspend () -> Unit) {
    withContext(currentCoroutineContext().minusKey(Job)) {
        block()
    }
}
