package io.github.lukewilk.ui.graphs

import androidx.compose.ui.state.ToggleableState
import io.github.lukewilk.shared.model.BandPower
import io.github.lukewilk.ui.ChannelState
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Dataset display order used by both configuration controls and rendered graph cards. */
private val graphDataSetDisplayOrder = listOf(
    GraphDataSetType.FilteredSignal,
    GraphDataSetType.BandPowers,
    GraphDataSetType.Fft
)

/** Returns dataset types that have already received at least one payload. */
internal fun GraphsReceivedData.availableDataSets(): List<GraphDataSetType> = graphDataSetDisplayOrder.filter { dataSetType ->
    when (dataSetType) {
        GraphDataSetType.FilteredSignal -> filteredSignals.isNotEmpty()
        GraphDataSetType.BandPowers -> bandPowers.isNotEmpty()
        GraphDataSetType.Fft -> fftResults.isNotEmpty()
    }
}

/** Returns only channels that are currently enabled on the Hardware page and therefore selectable for graphs. */
private fun selectableGraphChannels(channels: List<ChannelState>): List<ChannelState> = channels.filter(ChannelState::enabled)

/** Returns the union of channel ids observed across every received dataset family. */
internal fun GraphsReceivedData.availableChannelIds(): Set<Int> = buildSet {
    addAll(filteredSignals.keys)
    addAll(bandPowers.keys)
    addAll(fftResults.keys)
}

/**
 * Resolves the effective selected channels for the page.
 *
 * Fallback order matters:
 * 1. preserve explicit user choices,
 * 2. otherwise prefer enabled channels from hardware state,
 * 3. otherwise prefer channels that have already produced data,
 * 4. otherwise keep at least the first known channel selected.
 */
internal fun graphsSelectedChannelIds(
    channels: List<ChannelState>,
    receivedChannelIds: Set<Int>,
    selectedChannelIds: Set<Int>,
    hasUserConfiguredChannels: Boolean
): Set<Int> {
    val availableChannelIds = selectableGraphChannels(channels).map { it.id }.toSet()
    if (availableChannelIds.isEmpty()) return emptySet()

    if (hasUserConfiguredChannels) {
        return selectedChannelIds.filterTo(mutableSetOf()) { it in availableChannelIds }
    }

    val enabledChannelIds = channels.filter { it.enabled }.mapTo(mutableSetOf()) { it.id }
    if (enabledChannelIds.isNotEmpty()) return enabledChannelIds

    val receivedAvailableChannelIds = receivedChannelIds.filterTo(mutableSetOf()) { it in availableChannelIds }
    if (receivedAvailableChannelIds.isNotEmpty()) return receivedAvailableChannelIds

    return setOf(channels.first().id)
}

/** Resolves the effective selected dataset types while preserving manual filters when possible. */
internal fun graphsSelectedDataSets(
    availableDataSets: List<GraphDataSetType>,
    selectedDataSets: Set<GraphDataSetType>,
    hasUserConfiguredDataSets: Boolean
): Set<GraphDataSetType> {
    val availableDataSetIds = availableDataSets.toSet()
    if (availableDataSetIds.isEmpty()) return emptySet()

    return if (hasUserConfiguredDataSets) {
        selectedDataSets.filterTo(mutableSetOf()) { it in availableDataSetIds }
    } else {
        availableDataSetIds
    }
}

/** Resolves the effective selected channel/dataset pairs for the matrix UI. */
internal fun graphsSelectedGraphSelections(
    channels: List<ChannelState>,
    availableDataSets: List<GraphDataSetType>,
    defaultSelectedChannelIds: Set<Int>,
    defaultSelectedDataSets: Set<GraphDataSetType>,
    selectedGraphSelections: Set<GraphSelection>,
    hasUserConfiguredGraphSelections: Boolean
): Set<GraphSelection> {
    val availableChannelIds = selectableGraphChannels(channels).mapTo(mutableSetOf()) { it.id }
    val availableDataSetIds = availableDataSets.toSet()
    if (availableChannelIds.isEmpty() || availableDataSetIds.isEmpty()) return emptySet<GraphSelection>()

    return if (hasUserConfiguredGraphSelections) {
        selectedGraphSelections.filterTo(mutableSetOf<GraphSelection>()) { selection ->
            selection.channelId in availableChannelIds && selection.dataSetType in availableDataSetIds
        }
    } else {
        defaultSelectedChannelIds
            .filter { it in availableChannelIds }
            .flatMapTo(mutableSetOf<GraphSelection>()) { channelId ->
                defaultSelectedDataSets
                    .filter { it in availableDataSetIds }
                    .map { dataSetType -> GraphSelection(channelId = channelId, dataSetType = dataSetType) }
            }
    }
}

/** Returns summary counts for the collapsed configuration card. */
internal fun graphSelectionSummaryCounts(
    defaultSelectedChannelIds: Set<Int>,
    defaultSelectedDataSets: Set<GraphDataSetType>,
    selectedGraphSelections: Set<GraphSelection>,
    hasUserConfiguredGraphSelections: Boolean
): Pair<Int, Int> = if (hasUserConfiguredGraphSelections) {
    selectedGraphSelections.mapTo(mutableSetOf<Int>()) { it.channelId }.size to
        selectedGraphSelections.mapTo(mutableSetOf<GraphDataSetType>()) { it.dataSetType }.size
} else {
    defaultSelectedChannelIds.size to defaultSelectedDataSets.size
}

/** Converts selected-vs-available counts into the tri-state used by bulk matrix checkboxes. */
internal fun graphSelectionToggleState(selectedCount: Int, totalCount: Int): ToggleableState = when {
    totalCount <= 0 || selectedCount <= 0 -> ToggleableState.Off
    selectedCount >= totalCount -> ToggleableState.On
    else -> ToggleableState.Indeterminate
}

/** Returns all matrix cells belonging to one channel row. */
internal fun graphSelectionsForChannel(
    channelId: Int,
    availableDataSets: List<GraphDataSetType>,
    channels: List<ChannelState>
): Set<GraphSelection> {
    val isSelectable = selectableGraphChannels(channels).any { it.id == channelId }
    if (!isSelectable) return emptySet<GraphSelection>()

    return availableDataSets.mapTo(mutableSetOf<GraphSelection>()) { dataSetType ->
        GraphSelection(channelId = channelId, dataSetType = dataSetType)
    }
}

/** Returns all matrix cells belonging to one dataset column. */
internal fun graphSelectionsForDataSet(channels: List<ChannelState>, dataSetType: GraphDataSetType): Set<GraphSelection> =
    selectableGraphChannels(channels).mapTo(mutableSetOf<GraphSelection>()) { channel ->
        GraphSelection(channelId = channel.id, dataSetType = dataSetType)
    }

/** Applies a single-cell or grouped matrix toggle to the current graph-selection set. */
internal fun toggledGraphSelections(
    currentSelection: Set<GraphSelection>,
    values: Set<GraphSelection>,
    selected: Boolean
): Set<GraphSelection> = if (selected) currentSelection + values else currentSelection - values

/** Builds the compact summary shown in the configuration card header. */
internal fun graphConfigurationSummary(selectedChannelCount: Int, selectedDataSetCount: Int): String = buildString {
    append(selectedChannelCount)
    append(if (selectedChannelCount == 1) " channel selected" else " channels selected")
    append(" • ")
    append(selectedDataSetCount)
    append(if (selectedDataSetCount == 1) " data set selected" else " data sets selected")
}

/** Upper bound for streamed line/spectrum points so graph rendering stays lightweight during frequent updates. */
private const val maxRenderedLinePoints = 720

/** Upper bound for streamed FFT bins because spectra can contain significantly more buckets than the viewport. */
private const val maxRenderedSpectrumPoints = 560

/** Builds a bounded line-graph model from a raw filtered signal payload. */
private fun filteredSignalRenderModel(samples: DoubleArray): LineGraphRenderModel {
    val sampled = sampleSignal(samples, maxRenderedLinePoints)
    val graphBounds = graphBounds(
        minimum = sampled.minOrNull() ?: 0f,
        maximum = sampled.maxOrNull() ?: 0f,
        includeZero = true
    )

    return LineGraphRenderModel(
        points = sampled.toGraphPoints(),
        minY = graphBounds.first,
        maxY = graphBounds.second,
        showZeroLine = true,
        fillArea = true,
        startLabel = "Oldest",
        endLabel = "Newest"
    )
}

/** Builds a compact categorical bar graph for band-power payloads. */
private fun bandPowersRenderModel(bands: List<BandPower>): BarGraphRenderModel {
    val bars = bands.map { band ->
        GraphBarEntry(label = band.name, value = band.power.toFloat())
    }
    val graphBounds = graphBounds(
        minimum = bars.minOfOrNull(GraphBarEntry::value) ?: 0f,
        maximum = bars.maxOfOrNull(GraphBarEntry::value) ?: 0f,
        includeZero = true
    )

    return BarGraphRenderModel(
        bars = bars,
        minY = graphBounds.first,
        maxY = graphBounds.second
    )
}

/** Builds a spectrum-style line graph from the latest FFT bins. */
private fun fftRenderModel(values: Array<Pair<Double, Double>>): LineGraphRenderModel {
    val sampled = samplePairs(values, maxRenderedSpectrumPoints)
    val minFrequency = sampled.minOfOrNull { it.first.toFloat() } ?: 0f
    val maxFrequency = sampled.maxOfOrNull { it.first.toFloat() } ?: 0f
    val graphBounds = graphBounds(
        minimum = sampled.minOfOrNull { it.second.toFloat() } ?: 0f,
        maximum = sampled.maxOfOrNull { it.second.toFloat() } ?: 0f,
        includeZero = true
    )

    return LineGraphRenderModel(
        points = sampled.toGraphPoints(minimumX = minFrequency, maximumX = maxFrequency),
        minY = graphBounds.first,
        maxY = graphBounds.second,
        showZeroLine = true,
        fillArea = false,
        startLabel = "${formatGraphNumber(minFrequency.toDouble())} Hz",
        endLabel = "${formatGraphNumber(maxFrequency.toDouble())} Hz"
    )
}

/** Evenly samples a streamed signal down to a render-friendly point count while keeping the newest value. */
private fun sampleSignal(values: DoubleArray, maxPoints: Int): List<Float> {
    if (values.isEmpty()) return emptyList()
    if (values.size <= maxPoints) return values.map(Double::toFloat)
    if (maxPoints <= 1) return listOf(values.last().toFloat())

    val step = values.lastIndex.toDouble() / (maxPoints - 1).toDouble()
    return List(maxPoints) { index ->
        values[(index * step).toInt().coerceIn(values.indices)].toFloat()
    }
}

/** Evenly samples FFT bins down to a bounded render-friendly list while preserving spectral ordering. */
private fun samplePairs(values: Array<Pair<Double, Double>>, maxPoints: Int): List<Pair<Double, Double>> {
    if (values.isEmpty()) return emptyList()
    val sorted = values.sortedBy { it.first }
    if (sorted.size <= maxPoints) return sorted
    if (maxPoints <= 1) return listOf(sorted.last())

    val step = sorted.lastIndex.toDouble() / (maxPoints - 1).toDouble()
    return List(maxPoints) { index ->
        sorted[(index * step).toInt().coerceIn(sorted.indices)]
    }
}

/** Maps uniformly spaced values to normalized graph points for the shared line renderer. */
private fun List<Float>.toGraphPoints(): List<GraphPoint> {
    if (isEmpty()) return emptyList()
    if (size == 1) return listOf(GraphPoint(x = 0.5f, y = first()))

    return mapIndexed { index, value ->
        GraphPoint(
            x = index.toFloat() / lastIndex.toFloat(),
            y = value
        )
    }
}

/** Maps frequency/magnitude pairs to normalized graph points for the shared line renderer. */
private fun List<Pair<Double, Double>>.toGraphPoints(minimumX: Float, maximumX: Float): List<GraphPoint> {
    if (isEmpty()) return emptyList()
    if (size == 1) return listOf(GraphPoint(x = 0.5f, y = first().second.toFloat()))

    val xRange = (maximumX - minimumX).takeIf { abs(it) > 0f } ?: 1f
    return map { (x, y) ->
        GraphPoint(
            x = ((x.toFloat() - minimumX) / xRange).coerceIn(0f, 1f),
            y = y.toFloat()
        )
    }
}

/** Expands flat data ranges into safe graph bounds and optionally keeps zero visible as a baseline. */
private fun graphBounds(minimum: Float, maximum: Float, includeZero: Boolean): Pair<Float, Float> {
    var lowerBound = if (includeZero) min(minimum, 0f) else minimum
    var upperBound = if (includeZero) max(maximum, 0f) else maximum

    if (lowerBound == upperBound) {
        val padding = (abs(lowerBound).takeIf { it > 0f } ?: 1f) * 0.1f
        lowerBound -= padding
        upperBound += padding
    }

    return lowerBound to upperBound
}

/**
 * Derives the reusable graph-display section from the latest data plus current matrix selections.
 *
 * This state is intentionally page-agnostic so other graph surfaces can reuse the same card/empty-state logic.
 */
internal fun graphDisplayUiState(
    channels: List<ChannelState>,
    selectedGraphSelections: Set<GraphSelection>,
    receivedData: GraphsReceivedData
): GraphDisplayUiState {
    val availableDataSets = receivedData.availableDataSets()
    val selectableChannels = selectableGraphChannels(channels)
    val graphCards = selectableChannels
        .flatMap { channel ->
            availableDataSets.mapNotNull { dataSetType ->
                graphCardUiState(
                    channel = channel,
                    dataSetType = dataSetType,
                    isSelected = GraphSelection(channel.id, dataSetType) in selectedGraphSelections,
                    receivedData = receivedData
                )
            }
        }

    val emptyStateMessage = when {
        selectableChannels.isEmpty() -> GRAPHS_ENABLE_CHANNELS_GRAPH_MESSAGE
        availableDataSets.isEmpty() -> GRAPHS_WAITING_FOR_GRAPHS_MESSAGE
        selectedGraphSelections.isEmpty() -> GRAPHS_EMPTY_SELECTION_MESSAGE
        else -> GRAPHS_NO_MATCHING_DATA_MESSAGE
    }

    return GraphDisplayUiState(
        graphCards = graphCards,
        emptyStateMessage = emptyStateMessage
    )
}

/** Derives the full page UI state from selections plus latest backend payloads. */
internal fun graphsPageUiState(
    isConfigurationExpanded: Boolean,
    channels: List<ChannelState>,
    selectedGraphSelections: Set<GraphSelection>,
    selectedChannelCount: Int,
    selectedDataSetCount: Int,
    receivedData: GraphsReceivedData
): GraphsPageUiState {
    val availableDataSets = receivedData.availableDataSets()
    val selectableChannels = selectableGraphChannels(channels)
    val graphDisplay = graphDisplayUiState(
        channels = channels,
        selectedGraphSelections = selectedGraphSelections,
        receivedData = receivedData
    )

    val configurationEmptyMessage = when {
        selectableChannels.isEmpty() -> GRAPHS_ENABLE_CHANNELS_CONFIGURATION_MESSAGE
        availableDataSets.isEmpty() -> GRAPHS_WAITING_FOR_DATA_MESSAGE
        else -> null
    }

    return GraphsPageUiState(
        isConfigurationExpanded = isConfigurationExpanded,
        configurationSummary = graphConfigurationSummary(selectedChannelCount, selectedDataSetCount),
        configurationEmptyMessage = configurationEmptyMessage,
        matrixColumnHeaders = availableDataSets.map { dataSetType ->
            GraphMatrixColumnHeaderUiState(
                dataSetType = dataSetType,
                enabled = selectableChannels.isNotEmpty(),
                selectionState = graphSelectionToggleState(
                    selectedCount = selectableChannels.count { channel ->
                        GraphSelection(channel.id, dataSetType) in selectedGraphSelections
                    },
                    totalCount = selectableChannels.size
                )
            )
        },
        channelMatrixRows = selectableChannels.map { channel ->
            GraphChannelMatrixRowUiState(
                channel = channel,
                enabled = channel.enabled,
                selectionState = graphSelectionToggleState(
                    selectedCount = availableDataSets.count { dataSetType ->
                        GraphSelection(channel.id, dataSetType) in selectedGraphSelections
                    },
                    totalCount = if (channel.enabled) availableDataSets.size else 0
                ),
                dataSetCells = availableDataSets.map { dataSetType ->
                    GraphDataSetMatrixCellUiState(
                        dataSetType = dataSetType,
                        enabled = channel.enabled,
                        selected = GraphSelection(channel.id, dataSetType) in selectedGraphSelections
                    )
                }
            )
        },
        graphDisplay = graphDisplay
    )
}

/** Builds one graph-card model for a channel/dataset pair when data exists for that pair. */
private fun graphCardUiState(
    channel: ChannelState,
    dataSetType: GraphDataSetType,
    isSelected: Boolean,
    receivedData: GraphsReceivedData
): GraphCardUiState? {
    if (!isSelected) return null

    val title = "${channel.name} • ${dataSetType.label}"

    val (summary, renderModel) = when (dataSetType) {
        GraphDataSetType.FilteredSignal -> receivedData.filteredSignals[channel.id]
            ?.takeIf { it.isNotEmpty() }
            ?.let { samples ->
                filteredSignalSummary(samples) to filteredSignalRenderModel(samples)
            }

        GraphDataSetType.BandPowers -> receivedData.bandPowers[channel.id]
            ?.takeIf { it.isNotEmpty() }
            ?.let { bands ->
                bandPowersSummary(bands) to bandPowersRenderModel(bands)
            }

        GraphDataSetType.Fft -> receivedData.fftResults[channel.id]
            ?.takeIf { it.isNotEmpty() }
            ?.let { values ->
                fftSummary(values) to fftRenderModel(values)
            }
    } ?: return null

    return GraphCardUiState(
        selection = GraphSelection(channelId = channel.id, dataSetType = dataSetType),
        title = title,
        summary = summary,
        renderModel = renderModel
    )
}

