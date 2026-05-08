package io.github.lukewilk.ui.graphs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import io.github.lukewilk.shared.api.BackendApi
import io.github.lukewilk.shared.model.BandPower
import io.github.lukewilk.shared.model.ChannelData
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Collects the latest received graph payload for each dataset family and channel.
 *
 * The three backend flows are collected concurrently so a fresh payload in one family does not
 * block updates from the others. Filtered signals keep a bounded append-only history per channel
 * so the newest plotted sample stays on the right without older displayed samples being recomputed.
 */
@Composable
internal fun rememberGraphsReceivedData(
    backendApi: BackendApi?,
    filteredHistorySize: Int,
    filteredOverlap: Int
): GraphsReceivedData {
    val receivedDataState = produceState(initialValue = GraphsReceivedData(), backendApi, filteredHistorySize, filteredOverlap) {
        if (backendApi == null) {
            value = GraphsReceivedData()
            return@produceState
        }

        var filteredSignals = emptyMap<Int, DoubleArray>()
        var bandPowers = emptyMap<Int, List<BandPower>>()
        var fftResults = emptyMap<Int, Array<Pair<Double, Double>>>()

        coroutineScope {
            launch {
                backendApi.filteredFlow.collect { channelData: ChannelData<DoubleArray> ->
                    val previousSamples = filteredSignals[channelData.channelId]
                    filteredSignals = filteredSignals + (
                        channelData.channelId to updatedFilteredSignalHistory(
                            previousSamples = previousSamples,
                            incomingSamples = channelData.payload,
                            filteredHistorySize = filteredHistorySize,
                            filteredOverlap = filteredOverlap
                        )
                    )
                    value = GraphsReceivedData(
                        filteredSignals = filteredSignals,
                        bandPowers = bandPowers,
                        fftResults = fftResults
                    )
                }
            }
            launch {
                backendApi.bandPowersFlow.collect { channelData: ChannelData<List<BandPower>> ->
                    bandPowers = bandPowers + (channelData.channelId to channelData.payload.toList())
                    value = GraphsReceivedData(
                        filteredSignals = filteredSignals,
                        bandPowers = bandPowers,
                        fftResults = fftResults
                    )
                }
            }
            launch {
                backendApi.fftResultFlow.collect { channelData: ChannelData<Array<Pair<Double, Double>>> ->
                    fftResults = fftResults + (channelData.channelId to channelData.payload.copyOf())
                    value = GraphsReceivedData(
                        filteredSignals = filteredSignals,
                        bandPowers = bandPowers,
                        fftResults = fftResults
                    )
                }
            }
        }
    }
    return receivedDataState.value
}

/**
 * Appends only the newest filtered samples so the graph behaves like a stable scrolling history.
 *
 * The backend emits overlapping filtered windows; keeping the whole latest window would let already-visible
 * samples change when they reappear in the next overlap region. Instead we keep the first full payload, then
 * append only the newly arrived tail and trim to the configured history size.
 */
internal fun updatedFilteredSignalHistory(
    previousSamples: DoubleArray?,
    incomingSamples: DoubleArray,
    filteredHistorySize: Int,
    filteredOverlap: Int
): DoubleArray {
    if (incomingSamples.isEmpty()) return previousSamples?.copyOf() ?: doubleArrayOf()

    val resolvedHistorySize = filteredHistorySize.coerceAtLeast(1)
    if (previousSamples == null || previousSamples.isEmpty()) {
        return incomingSamples.takeLastSamples(resolvedHistorySize)
    }

    val resolvedOverlap = resolveFilteredOverlap(
        previousSamples = previousSamples,
        incomingSamples = incomingSamples,
        configuredOverlap = filteredOverlap
    )
    val appendedSampleCount = (incomingSamples.size - resolvedOverlap).coerceIn(0, incomingSamples.size)
    if (appendedSampleCount == 0) {
        return previousSamples.takeLastSamples(resolvedHistorySize)
    }
    val newestSamples = incomingSamples.takeLastSamples(appendedSampleCount)
    val smoothedNewestSamples = smoothSeamIfNeeded(
        previousSamples = previousSamples,
        newestSamples = newestSamples,
        filteredHistorySize = resolvedHistorySize,
        incomingWindowSize = incomingSamples.size,
        filteredOverlap = filteredOverlap
    )
    val merged = DoubleArray(previousSamples.size + newestSamples.size)
    previousSamples.copyInto(merged, endIndex = previousSamples.size)
    smoothedNewestSamples.copyInto(merged, destinationOffset = previousSamples.size)
    return merged.takeLastSamples(resolvedHistorySize)
}

/**
 * Applies a short boundary blend only for long-history rendering when a large seam jump is detected.
 */
private fun smoothSeamIfNeeded(
    previousSamples: DoubleArray,
    newestSamples: DoubleArray,
    filteredHistorySize: Int,
    incomingWindowSize: Int,
    filteredOverlap: Int
): DoubleArray {
    if (newestSamples.isEmpty()) return newestSamples

    // Keep short history behavior unchanged; seam blending targets long retained windows.
    val shouldBlendForLongHistory = filteredHistorySize >= incomingWindowSize * 3
    if (!shouldBlendForLongHistory) return newestSamples

    val seamJump = kotlin.math.abs(previousSamples.last() - newestSamples.first())
    val seamBlendActivationJump = 0.25
    if (seamJump < seamBlendActivationJump) return newestSamples

    val blendCount = minOf(
        newestSamples.size,
        filteredOverlap.coerceAtLeast(1).coerceAtMost(8)
    )
    val adjusted = newestSamples.copyOf()
    val seamOffset = previousSamples.last() - newestSamples.first()
    for (index in 0 until blendCount) {
        val blendWeight = (blendCount - index).toDouble() / blendCount.toDouble()
        adjusted[index] += seamOffset * blendWeight
    }
    return adjusted
}

/**
 * Resolves overlap used for filtered-history stitching, validating against actual payload continuity.
 */
private fun resolveFilteredOverlap(
    previousSamples: DoubleArray,
    incomingSamples: DoubleArray,
    configuredOverlap: Int
): Int {
    val maxComparableOverlap = minOf(previousSamples.size, incomingSamples.size)
    if (maxComparableOverlap == 0) return 0

    val preferredOverlap = configuredOverlap.coerceIn(0, maxComparableOverlap)
    if (overlapMatches(previousSamples, incomingSamples, preferredOverlap)) {
        return preferredOverlap
    }

    for (candidate in preferredOverlap - 1 downTo 1) {
        if (overlapMatches(previousSamples, incomingSamples, candidate)) return candidate
    }
    for (candidate in preferredOverlap + 1..maxComparableOverlap) {
        if (overlapMatches(previousSamples, incomingSamples, candidate)) return candidate
    }

    return preferredOverlap
}

/** Returns true when the previous suffix and incoming prefix align for [overlapSize] samples. */
private fun overlapMatches(
    previousSamples: DoubleArray,
    incomingSamples: DoubleArray,
    overlapSize: Int
): Boolean {
    if (overlapSize == 0) return true
    if (overlapSize > previousSamples.size || overlapSize > incomingSamples.size) return false

    val previousStart = previousSamples.size - overlapSize
    for (index in 0 until overlapSize) {
        if (!samplesAreClose(previousSamples[previousStart + index], incomingSamples[index])) {
            return false
        }
    }
    return true
}

/**
 * Tolerates tiny numeric jitter from floating-point processing so overlap matching stays stable.
 */
private fun samplesAreClose(
    first: Double,
    second: Double,
    absoluteTolerance: Double = 1e-4,
    relativeTolerance: Double = 1e-3
): Boolean {
    val diff = kotlin.math.abs(first - second)
    if (diff <= absoluteTolerance) return true
    val scale = maxOf(kotlin.math.abs(first), kotlin.math.abs(second), 1.0)
    return diff <= scale * relativeTolerance
}

/**
 * Resolves the effective filtered-signal history buffer size.
 *
 * Enforces a minimum of 5 seconds of samples so the graph always shows a meaningful window
 * regardless of the configured DSP window size. At a 256 Hz sampling rate this yields at least
 * 1 280 samples; at 125 Hz at least 625 samples.
 */
internal fun resolveFilteredHistorySize(
    requestedHistorySize: Int,
    samplingRateHz: Int,
    minimumDurationSeconds: Int
): Int {
    val minimumSamples = (samplingRateHz * minimumDurationSeconds).coerceAtLeast(1)
    return requestedHistorySize.coerceAtLeast(minimumSamples)
}

/** Returns the last [count] samples from an array while preserving sample order. */
private fun DoubleArray.takeLastSamples(count: Int): DoubleArray {
    val resolvedCount = count.coerceIn(0, size)
    return copyOfRange(size - resolvedCount, size)
}

