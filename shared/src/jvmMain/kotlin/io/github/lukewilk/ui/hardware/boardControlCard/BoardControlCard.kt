package io.github.lukewilk.ui.hardware.boardControlCard

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.lukewilk.ui.ChannelState
import io.github.lukewilk.ui.elements.buttons.SecondaryButton
import io.github.lukewilk.ui.elements.cards.CardHeader
import io.github.lukewilk.ui.elements.cards.PanelCard
import io.github.lukewilk.ui.elements.feedback.StatusIndicator
import io.github.lukewilk.ui.elements.layout.ActionButtonRow
import io.github.lukewilk.ui.elements.layout.VerticalSpacer
import io.github.lukewilk.ui.elements.text.SectionTitle

@Composable
fun BoardControlCard(
    availableBoards: List<String>,
    selectedBoard: Int,
    isConnected: Boolean,
    isStreaming: Boolean,
    isBusy: Boolean,
    channels: List<ChannelState>,
    onChannelToggle: (Int, Boolean) -> Unit,
    onRldToggle: (Int, Boolean) -> Unit,
    onVerifyChannels: () -> Unit,
    onStartStreaming: () -> Unit,
    onStopStreaming: () -> Unit
) {
    PanelCard {
        CardHeader(
            icon = "\uD83D\uDD0C",
            iconColor = MaterialTheme.colorScheme.primary,
            title = availableBoards.getOrNull(selectedBoard)?.let { "$it Control" } ?: "Board Control"
        )
        VerticalSpacer(18.dp)
        SectionTitle("Channel Configuration")
        VerticalSpacer(12.dp)
        ChannelTable(
            channels = channels,
            enabled = isConnected && !isBusy,
            onChannelToggle = onChannelToggle,
            onRldToggle = onRldToggle
        )
        VerticalSpacer(12.dp)
        ActionButtonRow(buttons = listOf(
            {
                SecondaryButton(
                    onClick = onVerifyChannels,
                    enabled = isConnected && !isBusy,
                    modifier = Modifier.weight(1f),
                    text = "Verify Channels"
                )
            },
            {
                SecondaryButton(
                    onClick = onStartStreaming,
                    enabled = isConnected && !isStreaming && !isBusy,
                    modifier = Modifier.weight(1f),
                    text = "Start Stream"
                )
            },
            {
                SecondaryButton(
                    onClick = onStopStreaming,
                    enabled = isStreaming && !isBusy,
                    modifier = Modifier.weight(1f),
                    text = "Stop Stream"
                )
            }
        ))
        if (isStreaming) {
            VerticalSpacer(8.dp)
            StatusIndicator(
                color = MaterialTheme.colorScheme.tertiary,
                text = "Streaming Active",
                iconSize = 10.dp
            )
        }
    }
}
