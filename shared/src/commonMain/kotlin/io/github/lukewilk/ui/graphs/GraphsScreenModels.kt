package io.github.lukewilk.ui.graphs

import io.github.lukewilk.shared.model.BandPower
import io.github.lukewilk.ui.ChannelState

/** Visible page title for the Graphs destination. */
internal const val GRAPHS_PAGE_TITLE = "Graphs"

/** Header label for the collapsible graph-configuration card. */
internal const val GRAPHS_CONFIGURATION_TITLE = "Graph Configuration"

/** Button label shown when the configuration card is collapsed. */
internal const val GRAPHS_CONFIGURATION_EXPAND_TEXT = "Show configuration"

/** Button label shown when the configuration card is expanded. */
internal const val GRAPHS_CONFIGURATION_COLLAPSE_TEXT = "Hide configuration"

/** Section title for channel graph-selection controls. */
internal const val GRAPHS_CHANNELS_SECTION_TITLE = "Channels"

/** Section title for received-dataset graph-selection controls. */
internal const val GRAPHS_DATASETS_SECTION_TITLE = "Received Data Sets"

/** Prompt shown inside the configuration card before any dataset arrives. */
internal const val GRAPHS_WAITING_FOR_DATA_MESSAGE = "Start streaming to receive graph data sets."

/** Body empty-state text shown before any graph cards can be rendered. */
internal const val GRAPHS_WAITING_FOR_GRAPHS_MESSAGE = "Graphs will appear here after data starts streaming."

/** Empty-state text shown when users deselect every channel or dataset. */
internal const val GRAPHS_EMPTY_SELECTION_MESSAGE = "Select at least one channel and one received data set to show graphs."

/** Empty-state text shown when selected filters do not match any received payload. */
internal const val GRAPHS_NO_MATCHING_DATA_MESSAGE = "No received graph data matches the current channel and data set filters yet."

/** Supported graph dataset families derived from backend streaming flows. */
internal enum class GraphDataSetType(val label: String) {
    FilteredSignal("Filtered Signal"),
    BandPowers("Band Powers"),
    Fft("FFT")
}

/** UI option representing whether a received dataset type is currently selected for display. */
internal data class GraphDataSetOption(
    val dataSetType: GraphDataSetType,
    val selected: Boolean
)

/** Minimal card model for a rendered graph section on the page body. */
internal data class GraphCardUiState(
    val title: String,
    val summary: String
)

/**
 * Snapshot of the latest received graph payloads keyed by source channel.
 *
 * Each dataset family keeps only the most recent payload per channel so the UI can
 * render a stable summary/card without accumulating an unbounded history in memory.
 */
internal data class GraphsReceivedData(
    val filteredSignals: Map<Int, DoubleArray> = emptyMap(),
    val bandPowers: Map<Int, List<BandPower>> = emptyMap(),
    val fftResults: Map<Int, Array<Pair<Double, Double>>> = emptyMap()
)

/**
 * Derived UI state for the Graphs page.
 *
 * This combines remembered selection state with currently received backend payloads so
 * the page can render one collapsible configuration surface and a filtered list of graph cards.
 */
internal data class GraphsPageUiState(
    val isConfigurationExpanded: Boolean,
    val configurationSummary: String,
    val channelOptions: List<ChannelState>,
    val dataSetOptions: List<GraphDataSetOption>,
    val graphCards: List<GraphCardUiState>,
    val emptyStateMessage: String
)


