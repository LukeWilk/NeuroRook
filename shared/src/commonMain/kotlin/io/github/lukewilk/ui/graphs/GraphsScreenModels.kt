package io.github.lukewilk.ui.graphs

import androidx.compose.runtime.Immutable
import androidx.compose.ui.state.ToggleableState
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

/** Label shown above the left-most matrix column that lists graph channels. */
internal const val GRAPHS_MATRIX_CHANNEL_HEADER = "Channel"

/** Prompt shown inside the configuration card when graph selection is unavailable because hardware channels are disabled. */
internal const val GRAPHS_ENABLE_CHANNELS_CONFIGURATION_MESSAGE =
    "Enable at least one hardware channel on the Hardware page to configure graph visibility."

/** Body empty-state text shown when graph rendering is unavailable because hardware channels are disabled. */
internal const val GRAPHS_ENABLE_CHANNELS_GRAPH_MESSAGE =
    "Enable at least one hardware channel on the Hardware page to show graphs."

/** Prompt shown inside the configuration card before any dataset arrives. */
internal const val GRAPHS_WAITING_FOR_DATA_MESSAGE = "Start streaming to receive graph data sets."

/** Body empty-state text shown before any graph cards can be rendered. */
internal const val GRAPHS_WAITING_FOR_GRAPHS_MESSAGE = "Graphs will appear here after data starts streaming."

/** Empty-state text shown when users deselect every channel or dataset. */
internal const val GRAPHS_EMPTY_SELECTION_MESSAGE = "Select at least one channel and data set combination to show graphs."

/** Empty-state text shown when selected filters do not match any received payload. */
internal const val GRAPHS_NO_MATCHING_DATA_MESSAGE = "No received graph data matches the current channel and data set filters yet."

/** Supported graph dataset families derived from backend streaming flows. */
internal enum class GraphDataSetType(val label: String, val compactLabel: String) {
    FilteredSignal(label = "Filtered Signal", compactLabel = "Filtered"),
    BandPowers(label = "Band Powers", compactLabel = "Bands"),
    Fft(label = "FFT", compactLabel = "FFT")
}

/** Stable identifier for a single graph card / matrix cell pairing a channel with one dataset family. */
internal data class GraphSelection(
    val channelId: Int,
    val dataSetType: GraphDataSetType
)

/** Reusable graph point with a normalized horizontal position and a raw vertical value. */
@Immutable
internal data class GraphPoint(
    val x: Float,
    val y: Float
)

/** Reusable bar entry for categorical graph renderers such as band powers. */
@Immutable
internal data class GraphBarEntry(
    val label: String,
    val value: Float
)

/** Stable shared render model for all graph surfaces shown by the Graphs feature. */
@Immutable
internal sealed interface GraphRenderModel

/** Shared line/spectrum renderer input used by filtered-signal and FFT cards. */
@Immutable
internal data class LineGraphRenderModel(
    val points: List<GraphPoint>,
    val minY: Float,
    val maxY: Float,
    val showZeroLine: Boolean,
    val fillArea: Boolean,
    val startLabel: String? = null,
    val endLabel: String? = null
) : GraphRenderModel

/** Shared bar renderer input used by categorical graph cards such as band powers. */
@Immutable
internal data class BarGraphRenderModel(
    val bars: List<GraphBarEntry>,
    val minY: Float,
    val maxY: Float
) : GraphRenderModel

/** Minimal card model for a rendered graph section on the page body. */
@Immutable
internal data class GraphCardUiState(
    val selection: GraphSelection,
    val title: String,
    val summary: String,
    val renderModel: GraphRenderModel
)

/** Reusable body-display state for the visible graph cards and their shared empty state. */
@Immutable
internal data class GraphDisplayUiState(
    val graphCards: List<GraphCardUiState>,
    val emptyStateMessage: String
)

/** UI state for one dataset column header in the graph-selection matrix. */
@Immutable
internal data class GraphMatrixColumnHeaderUiState(
    val dataSetType: GraphDataSetType,
    val enabled: Boolean,
    val selectionState: ToggleableState
)

/** UI state for one matrix checkbox cell pairing a selectable channel with a dataset family. */
@Immutable
internal data class GraphDataSetMatrixCellUiState(
    val dataSetType: GraphDataSetType,
    val enabled: Boolean,
    val selected: Boolean
)

/** UI state for one channel row in the graph-selection matrix. */
@Immutable
internal data class GraphChannelMatrixRowUiState(
    val channel: ChannelState,
    val enabled: Boolean,
    val selectionState: ToggleableState,
    val dataSetCells: List<GraphDataSetMatrixCellUiState>
)

/**
 * Snapshot of the latest received graph payloads keyed by source channel.
 *
 * Filtered signals keep a bounded rendered history per channel so the graph can scroll without
 * previously displayed samples changing, while band powers and FFT results keep only their latest payload.
 */
@Immutable
internal data class GraphsReceivedData(
    val filteredSignals: Map<Int, DoubleArray> = emptyMap(),
    val bandPowers: Map<Int, List<BandPower>> = emptyMap(),
    val fftResults: Map<Int, Array<Pair<Double, Double>>> = emptyMap()
)

/**
 * Derived UI state for the Graphs page.
 *
 * This combines remembered selection state with currently received backend payloads so
 * the page can render one collapsible configuration surface plus a reusable graph-display section.
 */
@Immutable
internal data class GraphsPageUiState(
    val isConfigurationExpanded: Boolean,
    val configurationSummary: String,
    val configurationEmptyMessage: String?,
    val matrixColumnHeaders: List<GraphMatrixColumnHeaderUiState>,
    val channelMatrixRows: List<GraphChannelMatrixRowUiState>,
    val graphDisplay: GraphDisplayUiState
) {
    /** Backwards-compatible accessor for callers/tests that only need the rendered card list. */
    val graphCards: List<GraphCardUiState>
        get() = graphDisplay.graphCards

    /** Backwards-compatible accessor for callers/tests that only need the rendered empty-state text. */
    val emptyStateMessage: String
        get() = graphDisplay.emptyStateMessage
}


