package io.github.lukewilk.ui.graphs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.api.BackendApi
import io.github.lukewilk.ui.channelStatesFor

/**
 * Entry point for the Graphs page.
 *
 * This file intentionally keeps only orchestration concerns: collecting hardware-backed state,
 * remembering user selections, and wiring callbacks into the extracted UI/state helpers.
 */
@Composable
fun GraphsScreen(backendApi: BackendApi? = null) {
    val hardwareState by (backendApi?.hardwareStateFlow?.collectAsState() ?: remember { mutableStateOf(HardwareState()) })
    val receivedData = rememberGraphsReceivedData(backendApi)
    val channels = remember(hardwareState) { channelStatesFor(hardwareState) }

    var isConfigurationExpanded by remember { mutableStateOf(true) }
    var selectedGraphSelections by remember { mutableStateOf(emptySet<GraphSelection>()) }
    var hasUserConfiguredGraphSelections by remember { mutableStateOf(false) }

    val effectiveSelectedChannelIds = remember(channels, receivedData) {
        graphsSelectedChannelIds(
            channels = channels,
            receivedChannelIds = receivedData.availableChannelIds(),
            selectedChannelIds = emptySet(),
            hasUserConfiguredChannels = false
        )
    }
    val availableDataSets = remember(receivedData) { receivedData.availableDataSets() }
    val effectiveSelectedDataSets = remember(availableDataSets) {
        graphsSelectedDataSets(
            availableDataSets = availableDataSets,
            selectedDataSets = emptySet(),
            hasUserConfiguredDataSets = false
        )
    }
    val effectiveSelectedGraphSelections = remember(
        channels,
        availableDataSets,
        effectiveSelectedChannelIds,
        effectiveSelectedDataSets,
        selectedGraphSelections,
        hasUserConfiguredGraphSelections
    ) {
        graphsSelectedGraphSelections(
            channels = channels,
            availableDataSets = availableDataSets,
            defaultSelectedChannelIds = effectiveSelectedChannelIds,
            defaultSelectedDataSets = effectiveSelectedDataSets,
            selectedGraphSelections = selectedGraphSelections,
            hasUserConfiguredGraphSelections = hasUserConfiguredGraphSelections
        )
    }
    val (selectedChannelCount, selectedDataSetCount) = remember(
        effectiveSelectedChannelIds,
        effectiveSelectedDataSets,
        effectiveSelectedGraphSelections,
        hasUserConfiguredGraphSelections
    ) {
        graphSelectionSummaryCounts(
            defaultSelectedChannelIds = effectiveSelectedChannelIds,
            defaultSelectedDataSets = effectiveSelectedDataSets,
            selectedGraphSelections = effectiveSelectedGraphSelections,
            hasUserConfiguredGraphSelections = hasUserConfiguredGraphSelections
        )
    }

    val uiState = remember(
        isConfigurationExpanded,
        channels,
        effectiveSelectedGraphSelections,
        selectedChannelCount,
        selectedDataSetCount,
        receivedData
    ) {
        graphsPageUiState(
            isConfigurationExpanded = isConfigurationExpanded,
            channels = channels,
            selectedGraphSelections = effectiveSelectedGraphSelections,
            selectedChannelCount = selectedChannelCount,
            selectedDataSetCount = selectedDataSetCount,
            receivedData = receivedData
        )
    }

    GraphsScreenContent(
        uiState = uiState,
        onConfigurationExpandedChange = { isConfigurationExpanded = it },
        onGraphSelectionChange = { channelId, dataSetType, selected ->
            hasUserConfiguredGraphSelections = true
            selectedGraphSelections = toggledGraphSelections(
                currentSelection = effectiveSelectedGraphSelections,
                values = setOf(GraphSelection(channelId = channelId, dataSetType = dataSetType)),
                selected = selected
            )
        },
        onChannelSelectionChange = { channelId, selected ->
            hasUserConfiguredGraphSelections = true
            selectedGraphSelections = toggledGraphSelections(
                currentSelection = effectiveSelectedGraphSelections,
                values = graphSelectionsForChannel(
                    channelId = channelId,
                    availableDataSets = availableDataSets,
                    channels = channels
                ),
                selected = selected
            )
        },
        onDataSetSelectionChange = { dataSetType, selected ->
            hasUserConfiguredGraphSelections = true
            selectedGraphSelections = toggledGraphSelections(
                currentSelection = effectiveSelectedGraphSelections,
                values = graphSelectionsForDataSet(channels, dataSetType),
                selected = selected
            )
        }
    )
}


