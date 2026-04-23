package io.github.lukewilk.ui.graphs

import androidx.compose.ui.state.ToggleableState
import io.github.lukewilk.ui.ChannelState

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
    if (availableChannelIds.isEmpty() || availableDataSetIds.isEmpty()) return emptySet()

    return if (hasUserConfiguredGraphSelections) {
        selectedGraphSelections.filterTo(mutableSetOf()) { selection ->
            selection.channelId in availableChannelIds && selection.dataSetType in availableDataSetIds
        }
    } else {
        defaultSelectedChannelIds
            .filter { it in availableChannelIds }
            .flatMapTo(mutableSetOf()) { channelId ->
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
    selectedGraphSelections.mapTo(mutableSetOf()) { it.channelId }.size to
        selectedGraphSelections.mapTo(mutableSetOf()) { it.dataSetType }.size
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
    if (!isSelectable) return emptySet()

    return availableDataSets.mapTo(mutableSetOf()) { dataSetType ->
        GraphSelection(channelId = channelId, dataSetType = dataSetType)
    }
}

/** Returns all matrix cells belonging to one dataset column. */
internal fun graphSelectionsForDataSet(channels: List<ChannelState>, dataSetType: GraphDataSetType): Set<GraphSelection> =
    selectableGraphChannels(channels).mapTo(mutableSetOf()) { channel ->
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

    val configurationEmptyMessage = when {
        selectableChannels.isEmpty() -> GRAPHS_ENABLE_CHANNELS_CONFIGURATION_MESSAGE
        availableDataSets.isEmpty() -> GRAPHS_WAITING_FOR_DATA_MESSAGE
        else -> null
    }

    val emptyStateMessage = when {
        selectableChannels.isEmpty() -> GRAPHS_ENABLE_CHANNELS_GRAPH_MESSAGE
        availableDataSets.isEmpty() -> GRAPHS_WAITING_FOR_GRAPHS_MESSAGE
        selectedGraphSelections.isEmpty() -> GRAPHS_EMPTY_SELECTION_MESSAGE
        else -> GRAPHS_NO_MATCHING_DATA_MESSAGE
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
        graphCards = graphCards,
        emptyStateMessage = emptyStateMessage
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

    val summary = when (dataSetType) {
        GraphDataSetType.FilteredSignal -> receivedData.filteredSignals[channel.id]?.let(::filteredSignalSummary)
        GraphDataSetType.BandPowers -> receivedData.bandPowers[channel.id]?.let(::bandPowersSummary)
        GraphDataSetType.Fft -> receivedData.fftResults[channel.id]?.let(::fftSummary)
    } ?: return null

    return GraphCardUiState(
        title = "${channel.name} • ${dataSetType.label}",
        summary = summary
    )
}









