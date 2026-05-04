package io.github.lukewilk.ui.graphs

import androidx.compose.ui.state.ToggleableState
import io.github.lukewilk.shared.model.BandPower
import io.github.lukewilk.ui.ChannelState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * JVM tests for Graphs-specific state derivation and formatting helpers.
 */
class GraphsScreenStateTest {
    @Test
    fun `graphs selection state prefers enabled and received channels while preserving manual filters`() {
        // Verifies default channel/dataset fallbacks still work and matrix selections preserve only valid manual cells.
        val channels = listOf(
            ChannelState(id = 0, name = "Channel 1", enabled = false, rld = false, status = "Not configured"),
            ChannelState(id = 1, name = "Channel 2", enabled = true, rld = false, status = "Configured"),
            ChannelState(id = 2, name = "Channel 3", enabled = true, rld = false, status = "Configured")
        )

        assertEquals(
            setOf(1, 2),
            graphsSelectedChannelIds(
                channels = channels,
                receivedChannelIds = setOf(0),
                selectedChannelIds = emptySet(),
                hasUserConfiguredChannels = false
            )
        )
        assertEquals(
            emptySet(),
            graphsSelectedChannelIds(
                channels = channels.mapIndexed { index, channel -> channel.copy(enabled = false, id = index) },
                receivedChannelIds = setOf(0),
                selectedChannelIds = emptySet(),
                hasUserConfiguredChannels = false
            )
        )
        assertEquals(
            setOf(2),
            graphsSelectedChannelIds(
                channels = channels,
                receivedChannelIds = setOf(0, 1, 2),
                selectedChannelIds = setOf(2, 9),
                hasUserConfiguredChannels = true
            )
        )
        assertEquals(
            setOf(GraphDataSetType.FilteredSignal, GraphDataSetType.BandPowers),
            graphsSelectedDataSets(
                availableDataSets = listOf(GraphDataSetType.FilteredSignal, GraphDataSetType.BandPowers),
                selectedDataSets = emptySet(),
                hasUserConfiguredDataSets = false
            )
        )
        assertEquals(
            setOf(GraphDataSetType.BandPowers),
            graphsSelectedDataSets(
                availableDataSets = listOf(GraphDataSetType.FilteredSignal, GraphDataSetType.BandPowers),
                selectedDataSets = setOf(GraphDataSetType.BandPowers, GraphDataSetType.Fft),
                hasUserConfiguredDataSets = true
            )
        )
        assertEquals(
            setOf(
                GraphSelection(channelId = 1, dataSetType = GraphDataSetType.FilteredSignal),
                GraphSelection(channelId = 1, dataSetType = GraphDataSetType.BandPowers),
                GraphSelection(channelId = 2, dataSetType = GraphDataSetType.FilteredSignal),
                GraphSelection(channelId = 2, dataSetType = GraphDataSetType.BandPowers)
            ),
            graphsSelectedGraphSelections(
                channels = channels,
                availableDataSets = listOf(GraphDataSetType.FilteredSignal, GraphDataSetType.BandPowers),
                defaultSelectedChannelIds = setOf(1, 2),
                defaultSelectedDataSets = setOf(GraphDataSetType.FilteredSignal, GraphDataSetType.BandPowers),
                selectedGraphSelections = emptySet(),
                hasUserConfiguredGraphSelections = false
            )
        )
        assertEquals(
            setOf(GraphSelection(channelId = 2, dataSetType = GraphDataSetType.BandPowers)),
            graphsSelectedGraphSelections(
                channels = channels,
                availableDataSets = listOf(GraphDataSetType.FilteredSignal, GraphDataSetType.BandPowers),
                defaultSelectedChannelIds = setOf(1, 2),
                defaultSelectedDataSets = setOf(GraphDataSetType.FilteredSignal, GraphDataSetType.BandPowers),
                selectedGraphSelections = setOf(
                    GraphSelection(channelId = 2, dataSetType = GraphDataSetType.BandPowers),
                    GraphSelection(channelId = 9, dataSetType = GraphDataSetType.FilteredSignal),
                    GraphSelection(channelId = 1, dataSetType = GraphDataSetType.Fft)
                ),
                hasUserConfiguredGraphSelections = true
            )
        )
        assertEquals(
            2 to 1,
            graphSelectionSummaryCounts(
                defaultSelectedChannelIds = setOf(1, 2),
                defaultSelectedDataSets = setOf(GraphDataSetType.FilteredSignal, GraphDataSetType.BandPowers),
                selectedGraphSelections = setOf(
                    GraphSelection(channelId = 1, dataSetType = GraphDataSetType.BandPowers),
                    GraphSelection(channelId = 2, dataSetType = GraphDataSetType.BandPowers)
                ),
                hasUserConfiguredGraphSelections = true
            )
        )
        assertEquals(ToggleableState.Off, graphSelectionToggleState(selectedCount = 0, totalCount = 2))
        assertEquals(ToggleableState.Indeterminate, graphSelectionToggleState(selectedCount = 1, totalCount = 2))
        assertEquals(ToggleableState.On, graphSelectionToggleState(selectedCount = 2, totalCount = 2))
        assertEquals(
            setOf(
                GraphSelection(channelId = 1, dataSetType = GraphDataSetType.FilteredSignal),
                GraphSelection(channelId = 1, dataSetType = GraphDataSetType.BandPowers)
            ),
            graphSelectionsForChannel(
                channelId = 1,
                availableDataSets = listOf(GraphDataSetType.FilteredSignal, GraphDataSetType.BandPowers),
                channels = channels
            )
        )
        assertEquals(
            emptySet(),
            graphSelectionsForChannel(
                channelId = 0,
                availableDataSets = listOf(GraphDataSetType.FilteredSignal, GraphDataSetType.BandPowers),
                channels = channels
            )
        )
        assertEquals(
            setOf(
                GraphSelection(channelId = 1, dataSetType = GraphDataSetType.BandPowers),
                GraphSelection(channelId = 2, dataSetType = GraphDataSetType.BandPowers)
            ),
            graphSelectionsForDataSet(channels, GraphDataSetType.BandPowers)
        )
        assertEquals(
            setOf(
                GraphSelection(channelId = 1, dataSetType = GraphDataSetType.FilteredSignal),
                GraphSelection(channelId = 1, dataSetType = GraphDataSetType.BandPowers),
                GraphSelection(channelId = 2, dataSetType = GraphDataSetType.BandPowers)
            ),
            toggledGraphSelections(
                currentSelection = setOf(
                    GraphSelection(channelId = 1, dataSetType = GraphDataSetType.FilteredSignal),
                    GraphSelection(channelId = 1, dataSetType = GraphDataSetType.BandPowers)
                ),
                values = setOf(
                    GraphSelection(channelId = 1, dataSetType = GraphDataSetType.BandPowers),
                    GraphSelection(channelId = 2, dataSetType = GraphDataSetType.BandPowers)
                ),
                selected = true
            )
        )
        assertEquals("2 channels selected • 1 data set selected", graphConfigurationSummary(2, 1))
    }

    @Test
    fun `graphs page state exposes received dataset toggles summaries and reusable graph models`() {
        // Verifies the matrix columns and rows reflect available datasets while graph cards expose reusable line/bar models.
        val channels = listOf(
            ChannelState(id = 0, name = "Channel 1", enabled = true, rld = false, status = "Configured"),
            ChannelState(id = 1, name = "Channel 2", enabled = false, rld = false, status = "Configured")
        )
        val receivedData = GraphsReceivedData(
            filteredSignals = mapOf(0 to doubleArrayOf(-0.5, 0.25, 0.75)),
            bandPowers = mapOf(1 to listOf(BandPower("Alpha", 2.4))),
            fftResults = mapOf(0 to arrayOf(8.0 to 0.8, 10.0 to 1.2))
        )
        val bandPowersReceivedData = receivedData.copy(
            bandPowers = mapOf(0 to listOf(BandPower("Alpha", 2.4), BandPower("Beta", 1.2)))
        )
        val displayState = graphDisplayUiState(
            channels = channels,
            selectedGraphSelections = setOf(
                GraphSelection(channelId = 0, dataSetType = GraphDataSetType.FilteredSignal),
                GraphSelection(channelId = 1, dataSetType = GraphDataSetType.BandPowers)
            ),
            samplingRateHz = 2,
            receivedData = receivedData
        )
        val bandPowersCard = graphDisplayUiState(
            channels = channels,
            selectedGraphSelections = setOf(GraphSelection(channelId = 0, dataSetType = GraphDataSetType.BandPowers)),
            samplingRateHz = 2,
            receivedData = bandPowersReceivedData
        ).graphCards.first()
        val fftCard = graphDisplayUiState(
            channels = channels,
            selectedGraphSelections = setOf(GraphSelection(channelId = 0, dataSetType = GraphDataSetType.Fft)),
            samplingRateHz = 2,
            receivedData = receivedData
        ).graphCards.first()

        val uiState = graphsPageUiState(
            isConfigurationExpanded = true,
            channels = channels,
            selectedGraphSelections = setOf(
                GraphSelection(channelId = 0, dataSetType = GraphDataSetType.FilteredSignal),
                GraphSelection(channelId = 1, dataSetType = GraphDataSetType.BandPowers)
            ),
            selectedChannelCount = 2,
            selectedDataSetCount = 2,
            samplingRateHz = 2,
            graphViewOptions = GraphViewOptions(
                showDataPoints = false,
                useBlackBackground = true,
                showGridLines = false,
                fillFilteredArea = false,
                refreshInterval = GraphRefreshInterval.Relaxed
            ),
            receivedData = receivedData
        )

        assertEquals(
            listOf(GraphDataSetType.FilteredSignal, GraphDataSetType.BandPowers, GraphDataSetType.Fft),
            receivedData.availableDataSets()
        )
        assertEquals(setOf(0, 1), receivedData.availableChannelIds())
        assertEquals(1, displayState.graphCards.size)
        val filteredCard = displayState.graphCards.first()
        assertEquals(GraphSelection(channelId = 0, dataSetType = GraphDataSetType.FilteredSignal), filteredCard.selection)
        assertEquals("Channel 1 • Filtered Signal", filteredCard.title)
        assertEquals(filteredSignalSummary(doubleArrayOf(-0.5, 0.25, 0.75)), filteredCard.summary)
        val filteredRenderModel = assertIs<LineGraphRenderModel>(filteredCard.renderModel)
        assertEquals(3, filteredRenderModel.points.size)
        assertEquals(
            GraphAxisLabels(
                ticks = listOf("0.75", "0.44", "0.12", "-0.19", "-0.5"),
                unitLabel = "Signal (a.u.)"
            ),
            filteredRenderModel.yAxisLabels
        )
        assertEquals(
            GraphAxisLabels(
                ticks = listOf("-1 s", "-750 ms", "-500 ms", "-250 ms", "0 s"),
                unitLabel = "Time"
            ),
            filteredRenderModel.xAxisLabels
        )
        val bandPowersRenderModel = assertIs<BarGraphRenderModel>(bandPowersCard.renderModel)
        assertEquals(listOf("Alpha", "Beta"), bandPowersRenderModel.bars.map(GraphBarEntry::label))
        assertEquals(
            GraphAxisLabels(
                ticks = listOf("2.4", "1.8", "1.2", "0.6", "0"),
                unitLabel = "Power (a.u.)"
            ),
            bandPowersRenderModel.yAxisLabels
        )
        val fftRenderModel = assertIs<LineGraphRenderModel>(fftCard.renderModel)
        assertEquals(
            GraphAxisLabels(
                ticks = listOf("1.2", "0.9", "0.6", "0.3", "0"),
                unitLabel = "Power (a.u.)"
            ),
            fftRenderModel.yAxisLabels
        )
        assertEquals(
            GraphAxisLabels(
                ticks = listOf("8 Hz", "8.5 Hz", "9 Hz", "9.5 Hz", "10 Hz"),
                unitLabel = "Frequency"
            ),
            fftRenderModel.xAxisLabels
        )
        assertEquals(
            GRAPHS_NO_MATCHING_DATA_MESSAGE,
            graphDisplayUiState(
                channels = channels,
                selectedGraphSelections = setOf(GraphSelection(channelId = 0, dataSetType = GraphDataSetType.BandPowers)),
                samplingRateHz = 2,
                receivedData = receivedData
            ).emptyStateMessage
        )
        assertEquals(3, uiState.matrixColumnHeaders.size)
        assertEquals(true, uiState.matrixColumnHeaders.first().enabled)
        assertEquals(ToggleableState.On, uiState.matrixColumnHeaders.first().selectionState)
        assertEquals(null, uiState.configurationEmptyMessage)
        assertEquals(1, uiState.channelMatrixRows.size)
        assertEquals(true, uiState.channelMatrixRows.first().enabled)
        assertEquals(ToggleableState.Indeterminate, uiState.channelMatrixRows.first().selectionState)
        assertEquals(3, uiState.channelMatrixRows.first().dataSetCells.size)
        assertEquals(false, uiState.graphViewOptions.showDataPoints)
        assertEquals(true, uiState.graphViewOptions.useBlackBackground)
        assertEquals(false, uiState.graphViewOptions.showGridLines)
        assertEquals(false, uiState.graphViewOptions.fillFilteredArea)
        assertEquals(GraphRefreshInterval.Relaxed, uiState.graphViewOptions.refreshInterval)
        assertEquals(displayState.graphCards, uiState.graphCards)
        assertEquals(displayState.emptyStateMessage, uiState.emptyStateMessage)
        assertEquals("Channel 1 • Filtered Signal", uiState.graphCards.first().title)
        assertEquals(1, uiState.graphCards.size)
        assertIs<LineGraphRenderModel>(uiState.graphCards.first().renderModel)
        assertEquals("Alpha: 2.4", bandPowersSummary(listOf(BandPower("Alpha", 2.4))))
        assertEquals("Latest 3 samples • min -0.5 • max 0.75 • last 0.75", filteredSignalSummary(doubleArrayOf(-0.5, 0.25, 0.75)))
        assertEquals("Latest 2 bins • peak 10 Hz @ 1.2", fftSummary(arrayOf(8.0 to 0.8, 10.0 to 1.2)))
        assertEquals(
            "Select at least one channel and data set combination to show graphs.",
            graphsPageUiState(
                isConfigurationExpanded = false,
                channels = channels,
                selectedGraphSelections = emptySet(),
                selectedChannelCount = 0,
                selectedDataSetCount = 0,
                samplingRateHz = 2,
                graphViewOptions = GraphViewOptions(),
                receivedData = receivedData
            ).emptyStateMessage
        )
        val noEnabledChannelsState = graphsPageUiState(
            isConfigurationExpanded = true,
            channels = channels.map { it.copy(enabled = false) },
            selectedGraphSelections = emptySet(),
            selectedChannelCount = 0,
            selectedDataSetCount = 0,
            samplingRateHz = 2,
            graphViewOptions = GraphViewOptions(),
            receivedData = receivedData
        )
        assertEquals(GRAPHS_ENABLE_CHANNELS_CONFIGURATION_MESSAGE, noEnabledChannelsState.configurationEmptyMessage)
        assertEquals(GRAPHS_ENABLE_CHANNELS_GRAPH_MESSAGE, noEnabledChannelsState.emptyStateMessage)
        assertEquals(0, noEnabledChannelsState.channelMatrixRows.size)

        val waitingForDataState = graphsPageUiState(
            isConfigurationExpanded = true,
            channels = listOf(channels.first()),
            selectedGraphSelections = emptySet(),
            selectedChannelCount = 1,
            selectedDataSetCount = 0,
            samplingRateHz = 2,
            graphViewOptions = GraphViewOptions(refreshInterval = GraphRefreshInterval.Balanced),
            receivedData = GraphsReceivedData()
        )
        assertEquals(GRAPHS_WAITING_FOR_DATA_MESSAGE, waitingForDataState.configurationEmptyMessage)
        assertEquals(GRAPHS_WAITING_FOR_GRAPHS_MESSAGE, waitingForDataState.emptyStateMessage)
        assertEquals(GraphRefreshInterval.Balanced, waitingForDataState.graphViewOptions.refreshInterval)
        assertEquals("Immediate", GraphRefreshInterval.Immediate.label)
    }

    @Test
    fun `graphs render models cap oversized filtered and fft inputs at the denser point budgets`() {
        // Verifies the renderer now receives the larger filtered/FFT point budgets instead of the earlier sparse caps.
        val channels = listOf(
            ChannelState(id = 0, name = "Channel 1", enabled = true, rld = false, status = "Configured")
        )
        val receivedData = GraphsReceivedData(
            filteredSignals = mapOf(0 to DoubleArray(1_500) { index -> index.toDouble() / 10.0 }),
            fftResults = mapOf(0 to Array(1_200) { index -> index.toDouble() to (index % 17).toDouble() })
        )

        val filteredCard = graphDisplayUiState(
            channels = channels,
            selectedGraphSelections = setOf(GraphSelection(channelId = 0, dataSetType = GraphDataSetType.FilteredSignal)),
            samplingRateHz = 250,
            receivedData = receivedData
        ).graphCards.first()
        val fftCard = graphDisplayUiState(
            channels = channels,
            selectedGraphSelections = setOf(GraphSelection(channelId = 0, dataSetType = GraphDataSetType.Fft)),
            samplingRateHz = 250,
            receivedData = receivedData
        ).graphCards.first()

        assertEquals(720, assertIs<LineGraphRenderModel>(filteredCard.renderModel).points.size)
        assertEquals(560, assertIs<LineGraphRenderModel>(fftCard.renderModel).points.size)
    }
}
