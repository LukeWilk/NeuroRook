package io.github.lukewilk.ui.hardware.boardControlCard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lukewilk.ui.ChannelState
import io.github.lukewilk.ui.elements.tables.TableHeaderRow

internal data class ChannelTableRowUiState(
    val usesEvenBackground: Boolean,
    val showDivider: Boolean,
    val isConfigured: Boolean
)

internal enum class ChannelStatusTone {
    CONFIGURED,
    UNCONFIGURED
}

internal fun channelTableRowUiState(index: Int, lastIndex: Int, status: String): ChannelTableRowUiState = ChannelTableRowUiState(
    usesEvenBackground = index % 2 == 0,
    showDivider = index < lastIndex,
    isConfigured = status == "Configured"
)

internal fun channelStatusTone(isConfigured: Boolean): ChannelStatusTone =
    if (isConfigured) ChannelStatusTone.CONFIGURED else ChannelStatusTone.UNCONFIGURED

@Composable
fun ChannelTable(
    channels: List<ChannelState>,
    enabled: Boolean,
    onChannelToggle: (Int, Boolean) -> Unit,
    onRldToggle: (Int, Boolean) -> Unit
) {
    val checkboxColumnWidth = 72.dp
    val headerCellModifiers = listOf(
        Modifier,
        Modifier.width(checkboxColumnWidth),
        Modifier.width(checkboxColumnWidth),
        Modifier
    )

    Column(Modifier.fillMaxWidth()) {
        TableHeaderRow(
            headers = listOf("Channel", "Enable", "RLD", "Status"),
            cellModifiers = headerCellModifiers
        )
        channels.forEachIndexed { index, ch ->
            val rowUiState = channelTableRowUiState(index, channels.lastIndex, ch.status)
            val rowBackground = if (rowUiState.usesEvenBackground) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.18f)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .background(rowBackground, shape = RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TableCell(
                    modifier = Modifier.weight(1.15f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ch.name,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                TableCell(
                    modifier = Modifier.width(checkboxColumnWidth),
                    contentAlignment = Alignment.Center
                ) {
                    Checkbox(
                        checked = ch.enabled,
                        onCheckedChange = { checked -> onChannelToggle(ch.id, checked) },
                        enabled = enabled
                    )
                }
                TableCell(
                    modifier = Modifier.width(checkboxColumnWidth),
                    contentAlignment = Alignment.Center
                ) {
                    Checkbox(
                        checked = ch.rld,
                        onCheckedChange = { checked -> onRldToggle(ch.id, checked) },
                        enabled = enabled
                    )
                }
                TableCell(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    StatusBadge(ch.status, rowUiState.isConfigured)
                }
            }
            if (rowUiState.showDivider) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    thickness = 0.75.dp
                )
            }
        }
    }
}

@Composable
private fun TableCell(
    modifier: Modifier,
    contentAlignment: Alignment,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .defaultMinSize(minHeight = 40.dp),
        contentAlignment = contentAlignment
    ) {
        content()
    }
}

@Composable
private fun StatusBadge(status: String, isConfigured: Boolean) {
    val tone = channelStatusTone(isConfigured)
    val containerColor = if (tone == ChannelStatusTone.CONFIGURED) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (tone == ChannelStatusTone.CONFIGURED) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            textAlign = TextAlign.Center,
            fontSize = 12.sp
        )
    }
}

