package io.github.lukewilk.ui.hardware

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.lukewilk.shared.model.SerialPortSuggestion
import io.github.lukewilk.ui.elements.buttons.PrimaryButton
import io.github.lukewilk.ui.elements.buttons.SecondaryButton
import io.github.lukewilk.ui.elements.cards.CardHeader
import io.github.lukewilk.ui.elements.cards.PanelCard
import io.github.lukewilk.ui.elements.feedback.StatusIndicator
import io.github.lukewilk.ui.elements.forms.DropdownMenuBox
import io.github.lukewilk.ui.elements.forms.StyledOutlinedTextField
import io.github.lukewilk.ui.elements.layout.ActionButtonRow
import io.github.lukewilk.ui.elements.layout.VerticalSpacer
import io.github.lukewilk.ui.elements.text.SectionTitle

@Composable
fun DeviceSelectionCard(
    availableBoards: List<String>,
    selectedBoard: Int,
    onBoardSelected: (Int) -> Unit,
    serialPort: String,
    onSerialPortChange: (String) -> Unit,
    serialPortPlaceholder: String,
    serialPortSuggestions: List<SerialPortSuggestion>,
    selectedSerialPortSuggestion: Int,
    onSerialPortSuggestionSelected: (Int) -> Unit,
    onRefreshSerialPorts: () -> Unit,
    isLoadingSerialPorts: Boolean,
    serialPortSupportText: String? = null,
    timeout: String,
    onTimeoutChange: (String) -> Unit,
    isConnected: Boolean,
    isBusy: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    canSelectBoard: Boolean = true,
    canConnect: Boolean = true
) {
    PanelCard {
        CardHeader(
            icon = "\uD83D\uDD27",
            iconColor = MaterialTheme.colorScheme.primary,
            title = "Device Selection"
        )
        VerticalSpacer(18.dp)
        SectionTitle("Board Type")
        DropdownMenuBox(
            options = availableBoards,
            selectedIndex = selectedBoard,
            onSelected = onBoardSelected,
            enabled = !isConnected && !isBusy && canSelectBoard
        )
        VerticalSpacer(14.dp)
        SectionTitle("Detected Serial Devices")
        DropdownMenuBox(
            options = serialPortSuggestionLabelsFor(serialPortSuggestions),
            selectedIndex = selectedSerialPortSuggestion.coerceIn(
                minimumValue = 0,
                maximumValue = serialPortSuggestions.lastIndex.coerceAtLeast(0)
            ),
            onSelected = onSerialPortSuggestionSelected,
            enabled = serialPortSuggestions.isNotEmpty() && !isConnected && !isBusy && !isLoadingSerialPorts
        )
        VerticalSpacer(10.dp)
        SecondaryButton(
            onClick = onRefreshSerialPorts,
            enabled = !isConnected && !isBusy && !isLoadingSerialPorts,
            text = if (isLoadingSerialPorts) "Refreshing..." else "Refresh Ports",
            modifier = Modifier
        )
        if (!serialPortSupportText.isNullOrBlank()) {
            VerticalSpacer(8.dp)
            Text(
                text = serialPortSupportText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        VerticalSpacer(14.dp)
        SectionTitle("Serial Port")
        StyledOutlinedTextField(
            value = serialPort,
            onValueChange = onSerialPortChange,
            enabled = !isConnected && !isBusy,
            placeholder = serialPortPlaceholder,
            fontFamily = FontFamily.Monospace
        )
        VerticalSpacer(14.dp)
        SectionTitle("Timeout (seconds)")
        StyledOutlinedTextField(
            value = timeout,
            onValueChange = onTimeoutChange,
            enabled = !isConnected && !isBusy,
            placeholder = "0"
        )
        VerticalSpacer(14.dp)
        StatusIndicator(
            color = if (isConnected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
            text = if (isConnected) "Device Connected" else "No Device Connected"
        )
        VerticalSpacer(18.dp)
        ActionButtonRow(buttons = listOf(
            {
                PrimaryButton(
                    onClick = onConnect,
                    enabled = !isConnected && !isBusy && canConnect,
                    modifier = Modifier.weight(1f),
                    text = "Connect"
                )
            },
            {
                SecondaryButton(
                    onClick = onDisconnect,
                    enabled = isConnected && !isBusy,
                    modifier = Modifier.weight(1f),
                    text = "Disconnect"
                )
            }
        ))
    }
}

private fun serialPortSuggestionLabelsFor(suggestions: List<SerialPortSuggestion>): List<String> {
    if (suggestions.isEmpty()) return listOf("No active serial devices detected")

    return suggestions.map { suggestion ->
        buildString {
            if (suggestion.isRecommended) {
                append("Recommended • ")
            }
            append(suggestion.path)
            val descriptor = suggestion.details.ifBlank {
                suggestion.displayName.takeIf { it.isNotBlank() && it != suggestion.path }.orEmpty()
            }
            if (descriptor.isNotBlank()) {
                append(" — ")
                append(descriptor)
            }
        }
    }
}

