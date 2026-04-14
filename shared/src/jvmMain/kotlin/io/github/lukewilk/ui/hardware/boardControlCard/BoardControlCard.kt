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

internal data class BoardControlUiState(
    val title: String,
    val channelsEnabled: Boolean,
    val verifyChannelsEnabled: Boolean,
    val startStreamEnabled: Boolean,
    val stopStreamEnabled: Boolean,
    val showStreamingIndicator: Boolean
)

internal fun boardControlUiState(
    availableBoards: List<String>,
    selectedBoard: Int,
    isConnected: Boolean,
    isStreaming: Boolean,
    isBusy: Boolean
): BoardControlUiState {
    val boardTitle = availableBoards.getOrNull(selectedBoard)?.let { "$it Control" } ?: "Board Control"
    val connectedAndReady = isConnected && !isBusy
    return BoardControlUiState(
        title = boardTitle,
        channelsEnabled = connectedAndReady,
        verifyChannelsEnabled = connectedAndReady,
        startStreamEnabled = isConnected && !isStreaming && !isBusy,
        stopStreamEnabled = isStreaming && !isBusy,
        showStreamingIndicator = isStreaming
    )
}

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
    val uiState = boardControlUiState(availableBoards, selectedBoard, isConnected, isStreaming, isBusy)

    PanelCard {
        CardHeader(
            icon = "\uD83D\uDD0C",
            iconColor = MaterialTheme.colorScheme.primary,
            title = uiState.title
        )
        VerticalSpacer(18.dp)
        SectionTitle("Channel Configuration")
        VerticalSpacer(12.dp)
        ChannelTable(
            channels = channels,
            enabled = uiState.channelsEnabled,
            onChannelToggle = onChannelToggle,
            onRldToggle = onRldToggle
        )
        VerticalSpacer(12.dp)
        ActionButtonRow(buttons = listOf(
            {
                SecondaryButton(
                    onClick = onVerifyChannels,
                    enabled = uiState.verifyChannelsEnabled,
                    modifier = Modifier.weight(1f),
                    text = "Verify Channels"
                )
            },
            {
                SecondaryButton(
                    onClick = onStartStreaming,
                    enabled = uiState.startStreamEnabled,
                    modifier = Modifier.weight(1f),
                    text = "Start Stream"
                )
            },
            {
                SecondaryButton(
                    onClick = onStopStreaming,
                    enabled = uiState.stopStreamEnabled,
                    modifier = Modifier.weight(1f),
                    text = "Stop Stream"
                )
            }
        ))
        if (uiState.showStreamingIndicator) {
            VerticalSpacer(8.dp)
            StatusIndicator(
                color = MaterialTheme.colorScheme.tertiary,
                text = "Streaming Active",
                iconSize = 10.dp
            )
        }
    }
}
