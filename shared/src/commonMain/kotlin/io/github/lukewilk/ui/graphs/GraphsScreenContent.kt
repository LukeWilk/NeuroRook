package io.github.lukewilk.ui.graphs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lukewilk.ui.elements.cards.PanelCard
import io.github.lukewilk.ui.elements.layout.VerticalSpacer
import io.github.lukewilk.ui.elements.scroll.VerticalScrollCueBox
import io.github.lukewilk.ui.elements.text.SectionTitle

/** Width used by the left-most matrix column that holds channel names. */
private val graphMatrixChannelColumnWidth = 132.dp

/** Width used by each dataset column to keep header labels aligned with checkboxes. */
private val graphMatrixToggleColumnWidth = 60.dp

/** Smaller corner radius keeps graph surfaces visually flatter and more plot-focused. */
private val graphCardCornerShape = RoundedCornerShape(8.dp)

/** Tight graph-card padding preserves more room for chart content. */
private val graphCardContentPadding = 14.dp

/** Thin outline separates flat graph surfaces without adding bulky shadows. */
private val graphCardBorderWidth = 1.dp

/** Accessible content description for a channel/dataset matrix checkbox. */
internal fun graphsMatrixToggleContentDescription(channelName: String, dataSetLabel: String): String =
    "Toggle $dataSetLabel graph visibility for $channelName"

/** Accessible content description for a row bulk-toggle that targets every dataset for one channel. */
internal fun graphsChannelBulkToggleContentDescription(channelName: String): String =
    "Toggle all graph data sets for $channelName"

/** Accessible content description for a column bulk-toggle that targets one dataset for every channel. */
internal fun graphsDataSetBulkToggleContentDescription(dataSetLabel: String): String =
    "Toggle $dataSetLabel for all channels"

/** Renders the Graphs page body from already-derived UI state. */
@Composable
internal fun GraphsScreenContent(
    uiState: GraphsPageUiState,
    onConfigurationExpandedChange: (Boolean) -> Unit,
    onGraphSelectionChange: (Int, GraphDataSetType, Boolean) -> Unit,
    onChannelSelectionChange: (Int, Boolean) -> Unit,
    onDataSetSelectionChange: (GraphDataSetType, Boolean) -> Unit
) {
    val scrollState = rememberScrollState()
    VerticalScrollCueBox(
        scrollState = scrollState,
        clipContentToBounds = false,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(top = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = GRAPHS_PAGE_TITLE,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            GraphsConfigurationCard(
                uiState = uiState,
                onConfigurationExpandedChange = onConfigurationExpandedChange,
                onGraphSelectionChange = onGraphSelectionChange,
                onChannelSelectionChange = onChannelSelectionChange,
                onDataSetSelectionChange = onDataSetSelectionChange
            )
            GraphDisplayContent(uiState = uiState.graphDisplay)
        }
    }
}

/** Reusable graph-display section that renders either graph cards or the shared empty-state message. */
@Composable
internal fun GraphDisplayContent(uiState: GraphDisplayUiState) {
    if (uiState.graphCards.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = uiState.emptyStateMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    } else {
        uiState.graphCards.forEach { card ->
            key(card.selection) {
                GraphCard(card)
            }
        }
    }
}

/** Top card that exposes graph filtering controls and a collapsed summary state. */
@Composable
private fun GraphsConfigurationCard(
    uiState: GraphsPageUiState,
    onConfigurationExpandedChange: (Boolean) -> Unit,
    onGraphSelectionChange: (Int, GraphDataSetType, Boolean) -> Unit,
    onChannelSelectionChange: (Int, Boolean) -> Unit,
    onDataSetSelectionChange: (GraphDataSetType, Boolean) -> Unit
) {
    PanelCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = GRAPHS_CONFIGURATION_TITLE,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(
                onClick = { onConfigurationExpandedChange(!uiState.isConfigurationExpanded) },
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (uiState.isConfigurationExpanded) {
                        Icons.Outlined.KeyboardArrowUp
                    } else {
                        Icons.Outlined.KeyboardArrowDown
                    },
                    contentDescription = if (uiState.isConfigurationExpanded) {
                        GRAPHS_CONFIGURATION_COLLAPSE_TEXT
                    } else {
                        GRAPHS_CONFIGURATION_EXPAND_TEXT
                    },
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        VerticalSpacer(6.dp)
        Text(
            text = uiState.configurationSummary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (uiState.isConfigurationExpanded) {
            VerticalSpacer(10.dp)
            SectionTitle(GRAPHS_CHANNELS_SECTION_TITLE)
            VerticalSpacer(6.dp)
            if (uiState.configurationEmptyMessage != null) {
                Text(
                    text = uiState.configurationEmptyMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                GraphSelectionMatrix(
                    uiState = uiState,
                    onGraphSelectionChange = onGraphSelectionChange,
                    onChannelSelectionChange = onChannelSelectionChange,
                    onDataSetSelectionChange = onDataSetSelectionChange
                )
            }
        }
    }
}

/** Renders the graph-selection matrix with channel rows and dataset columns. */
@Composable
private fun GraphSelectionMatrix(
    uiState: GraphsPageUiState,
    onGraphSelectionChange: (Int, GraphDataSetType, Boolean) -> Unit,
    onChannelSelectionChange: (Int, Boolean) -> Unit,
    onDataSetSelectionChange: (GraphDataSetType, Boolean) -> Unit
) {
    val horizontalScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(horizontalScrollState),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = GRAPHS_MATRIX_CHANNEL_HEADER,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(graphMatrixChannelColumnWidth)
            )
            uiState.matrixColumnHeaders.forEach { header ->
                GraphMatrixColumnHeader(
                    header = header,
                    onSelectionChange = { selected ->
                        onDataSetSelectionChange(header.dataSetType, selected)
                    }
                )
            }
        }

        uiState.channelMatrixRows.forEach { row ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.heightIn(min = 28.dp)
            ) {
                GraphMatrixRowHeader(
                    row = row,
                    onSelectionChange = { selected ->
                        onChannelSelectionChange(row.channel.id, selected)
                    }
                )
                row.dataSetCells.forEach { cell ->
                    GraphMatrixToggleCell(
                        channelName = row.channel.name,
                        cell = cell,
                        onSelectionChange = { selected ->
                            onGraphSelectionChange(row.channel.id, cell.dataSetType, selected)
                        }
                    )
                }
            }
        }
    }
}

/** Compact dataset-column header with a bulk checkbox and shortened visible label. */
@Composable
private fun GraphMatrixColumnHeader(
    header: GraphMatrixColumnHeaderUiState,
    onSelectionChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.width(graphMatrixToggleColumnWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        GraphMatrixBulkToggle(
            state = header.selectionState,
            enabled = header.enabled,
            contentDescription = graphsDataSetBulkToggleContentDescription(header.dataSetType.label),
            onSelectionChange = onSelectionChange
        )
        Text(
            text = header.dataSetType.compactLabel,
            style = MaterialTheme.typography.labelSmall,
            color = if (header.enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/** Compact channel-row header with a bulk checkbox and the channel label. */
@Composable
private fun GraphMatrixRowHeader(
    row: GraphChannelMatrixRowUiState,
    onSelectionChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.width(graphMatrixChannelColumnWidth),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        GraphMatrixBulkToggle(
            state = row.selectionState,
            enabled = row.enabled,
            contentDescription = graphsChannelBulkToggleContentDescription(row.channel.name),
            onSelectionChange = onSelectionChange
        )
        Text(
            text = row.channel.name,
            style = MaterialTheme.typography.bodySmall,
            color = if (row.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.weight(1f, fill = true))
    }
}

/** Reusable tri-state checkbox wrapper used by both matrix headers and channel rows. */
@Composable
private fun GraphMatrixBulkToggle(
    state: ToggleableState,
    enabled: Boolean,
    contentDescription: String,
    onSelectionChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .semantics {
                this.contentDescription = contentDescription
                toggleableState = state
                stateDescription = graphToggleStateDescription(state, enabled)
            }
            .triStateToggleable(
                state = state,
                enabled = enabled,
                role = Role.Checkbox,
                onClick = { onSelectionChange(state != ToggleableState.On) }
            ),
        contentAlignment = Alignment.Center
    ) {
        TriStateCheckbox(
            state = state,
            onClick = null,
            enabled = enabled,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** One checkbox cell within the graph-selection matrix. */
@Composable
private fun GraphMatrixToggleCell(
    channelName: String,
    cell: GraphDataSetMatrixCellUiState,
    onSelectionChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .width(graphMatrixToggleColumnWidth)
            .semantics {
                contentDescription = graphsMatrixToggleContentDescription(channelName, cell.dataSetType.label)
                role = Role.Checkbox
                toggleableState = ToggleableState(cell.selected)
                stateDescription = graphToggleStateDescription(ToggleableState(cell.selected), cell.enabled)
                onClick {
                    if (cell.enabled) {
                        onSelectionChange(!cell.selected)
                        true
                    } else {
                        false
                    }
                }
            }
            .toggleable(
                value = cell.selected,
                enabled = cell.enabled,
                role = Role.Checkbox,
                onValueChange = onSelectionChange
            )
            .padding(vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Checkbox(
            checked = cell.selected,
            onCheckedChange = null,
            enabled = cell.enabled,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** Human-readable state description shared by single and bulk graph toggles. */
private fun graphToggleStateDescription(state: ToggleableState, enabled: Boolean): String {
    if (!enabled) return "Unavailable"

    return when (state) {
        ToggleableState.On -> "Selected"
        ToggleableState.Indeterminate -> "Partially selected"
        ToggleableState.Off -> "Not selected"
    }
}

/** Flat graph card that hosts a reusable graph surface plus a compact supporting summary. */
@Composable
private fun GraphCard(card: GraphCardUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = graphCardCornerShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(
            width = graphCardBorderWidth,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(modifier = Modifier.padding(graphCardContentPadding)) {
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            VerticalSpacer(8.dp)
            GraphSurface(
                renderModel = card.renderModel,
                title = card.title,
                accentColor = graphAccentColor(card.selection.dataSetType)
            )
            VerticalSpacer(6.dp)
            Text(
                text = card.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Keeps dataset families visually distinct while still using the same reusable graph surface. */
@Composable
private fun graphAccentColor(dataSetType: GraphDataSetType): Color = when (dataSetType) {
    GraphDataSetType.FilteredSignal -> MaterialTheme.colorScheme.primary
    GraphDataSetType.BandPowers -> MaterialTheme.colorScheme.secondary
    GraphDataSetType.Fft -> MaterialTheme.colorScheme.tertiary
}
