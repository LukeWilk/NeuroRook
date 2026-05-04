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

    val appendedSampleCount = (incomingSamples.size - filteredOverlap).coerceIn(1, incomingSamples.size)
    val newestSamples = incomingSamples.takeLastSamples(appendedSampleCount)
    val merged = DoubleArray(previousSamples.size + newestSamples.size)
    previousSamples.copyInto(merged, endIndex = previousSamples.size)
    newestSamples.copyInto(merged, destinationOffset = previousSamples.size)
    return merged.takeLastSamples(resolvedHistorySize)
}

/** Returns the last [count] samples from an array while preserving sample order. */
private fun DoubleArray.takeLastSamples(count: Int): DoubleArray {
    val resolvedCount = count.coerceIn(0, size)
    return copyOfRange(size - resolvedCount, size)
}

