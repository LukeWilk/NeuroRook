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
 * block updates from the others; each emission replaces only the latest value for its channel.
 */
@Composable
internal fun rememberGraphsReceivedData(backendApi: BackendApi?): GraphsReceivedData {
    val receivedDataState = produceState(initialValue = GraphsReceivedData(), backendApi) {
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
                    filteredSignals = filteredSignals + (channelData.channelId to channelData.payload)
                    value = GraphsReceivedData(
                        filteredSignals = filteredSignals,
                        bandPowers = bandPowers,
                        fftResults = fftResults
                    )
                }
            }
            launch {
                backendApi.bandPowersFlow.collect { channelData: ChannelData<List<BandPower>> ->
                    bandPowers = bandPowers + (channelData.channelId to channelData.payload)
                    value = GraphsReceivedData(
                        filteredSignals = filteredSignals,
                        bandPowers = bandPowers,
                        fftResults = fftResults
                    )
                }
            }
            launch {
                backendApi.fftResultFlow.collect { channelData: ChannelData<Array<Pair<Double, Double>>> ->
                    fftResults = fftResults + (channelData.channelId to channelData.payload)
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

