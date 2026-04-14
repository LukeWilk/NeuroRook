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

internal fun isBoardSelectionEnabled(isConnected: Boolean, isBusy: Boolean, canSelectBoard: Boolean): Boolean =
    !isConnected && !isBusy && canSelectBoard

internal fun isSerialSuggestionSelectionEnabled(
    serialPortSuggestions: List<SerialPortSuggestion>,
    isConnected: Boolean,
    isBusy: Boolean,
    isLoadingSerialPorts: Boolean
): Boolean = serialPortSuggestions.isNotEmpty() && !isConnected && !isBusy && !isLoadingSerialPorts

internal fun isRefreshPortsEnabled(isConnected: Boolean, isBusy: Boolean, isLoadingSerialPorts: Boolean): Boolean =
    !isConnected && !isBusy && !isLoadingSerialPorts

internal fun isConnectEnabled(isConnected: Boolean, isBusy: Boolean, canConnect: Boolean): Boolean =
    !isConnected && !isBusy && canConnect

internal fun isDisconnectEnabled(isConnected: Boolean, isBusy: Boolean): Boolean =
    isConnected && !isBusy

internal fun areSerialInputFieldsEnabled(isConnected: Boolean, isBusy: Boolean): Boolean =
    !isConnected && !isBusy

internal fun selectedSerialPortSuggestionIndexValue(
    selectedSerialPortSuggestion: Int,
    serialPortSuggestions: List<SerialPortSuggestion>
): Int = selectedSerialPortSuggestion.coerceIn(
    minimumValue = 0,
    maximumValue = serialPortSuggestions.lastIndex.coerceAtLeast(0)
)

internal fun refreshPortsButtonText(isLoadingSerialPorts: Boolean): String =
    if (isLoadingSerialPorts) "Refreshing..." else "Refresh Ports"

internal fun visibleSerialPortSupportText(serialPortSupportText: String?): String? =
    serialPortSupportText?.takeIf { it.isNotBlank() }

internal fun deviceConnectionStatusText(isConnected: Boolean): String =
    if (isConnected) "Device Connected" else "No Device Connected"

internal fun serialPortSuggestionDescriptor(suggestion: SerialPortSuggestion): String = suggestion.details.ifBlank {
    suggestion.displayName.takeIf { it.isNotBlank() && it != suggestion.path }.orEmpty()
}

internal data class DeviceSelectionUiState(
    val boardSelectionEnabled: Boolean,
    val serialSuggestionSelectionEnabled: Boolean,
    val selectedSerialPortSuggestion: Int,
    val refreshPortsEnabled: Boolean,
    val refreshPortsText: String,
    val visibleSerialPortSupportText: String?,
    val serialInputFieldsEnabled: Boolean,
    val connectionStatusText: String,
    val connectEnabled: Boolean,
    val disconnectEnabled: Boolean
)

internal fun deviceSelectionUiState(
    serialPortSuggestions: List<SerialPortSuggestion>,
    selectedSerialPortSuggestion: Int,
    isConnected: Boolean,
    isBusy: Boolean,
    isLoadingSerialPorts: Boolean,
    serialPortSupportText: String?,
    canSelectBoard: Boolean,
    canConnect: Boolean
): DeviceSelectionUiState = DeviceSelectionUiState(
    boardSelectionEnabled = isBoardSelectionEnabled(isConnected, isBusy, canSelectBoard),
    serialSuggestionSelectionEnabled = isSerialSuggestionSelectionEnabled(serialPortSuggestions, isConnected, isBusy, isLoadingSerialPorts),
    selectedSerialPortSuggestion = selectedSerialPortSuggestionIndexValue(selectedSerialPortSuggestion, serialPortSuggestions),
    refreshPortsEnabled = isRefreshPortsEnabled(isConnected, isBusy, isLoadingSerialPorts),
    refreshPortsText = refreshPortsButtonText(isLoadingSerialPorts),
    visibleSerialPortSupportText = visibleSerialPortSupportText(serialPortSupportText),
    serialInputFieldsEnabled = areSerialInputFieldsEnabled(isConnected, isBusy),
    connectionStatusText = deviceConnectionStatusText(isConnected),
    connectEnabled = isConnectEnabled(isConnected, isBusy, canConnect),
    disconnectEnabled = isDisconnectEnabled(isConnected, isBusy)
)

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
    val uiState = deviceSelectionUiState(
        serialPortSuggestions = serialPortSuggestions,
        selectedSerialPortSuggestion = selectedSerialPortSuggestion,
        isConnected = isConnected,
        isBusy = isBusy,
        isLoadingSerialPorts = isLoadingSerialPorts,
        serialPortSupportText = serialPortSupportText,
        canSelectBoard = canSelectBoard,
        canConnect = canConnect
    )

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
            enabled = uiState.boardSelectionEnabled
        )
        VerticalSpacer(14.dp)
        SectionTitle("Detected Serial Devices")
        DropdownMenuBox(
            options = serialPortSuggestionLabelsFor(serialPortSuggestions),
            selectedIndex = uiState.selectedSerialPortSuggestion,
            onSelected = onSerialPortSuggestionSelected,
            enabled = uiState.serialSuggestionSelectionEnabled
        )
        VerticalSpacer(10.dp)
        SecondaryButton(
            onClick = onRefreshSerialPorts,
            enabled = uiState.refreshPortsEnabled,
            text = uiState.refreshPortsText,
            modifier = Modifier
        )
        if (uiState.visibleSerialPortSupportText != null) {
            VerticalSpacer(8.dp)
            Text(
                text = uiState.visibleSerialPortSupportText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        VerticalSpacer(14.dp)
        SectionTitle("Serial Port")
        StyledOutlinedTextField(
            value = serialPort,
            onValueChange = onSerialPortChange,
            enabled = uiState.serialInputFieldsEnabled,
            placeholder = serialPortPlaceholder,
            fontFamily = FontFamily.Monospace
        )
        VerticalSpacer(14.dp)
        SectionTitle("Timeout (seconds)")
        StyledOutlinedTextField(
            value = timeout,
            onValueChange = onTimeoutChange,
            enabled = uiState.serialInputFieldsEnabled,
            placeholder = "0"
        )
        VerticalSpacer(14.dp)
        StatusIndicator(
            color = if (isConnected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
            text = uiState.connectionStatusText
        )
        VerticalSpacer(18.dp)
        ActionButtonRow(buttons = listOf(
            {
                PrimaryButton(
                    onClick = onConnect,
                    enabled = uiState.connectEnabled,
                    modifier = Modifier.weight(1f),
                    text = "Connect"
                )
            },
            {
                SecondaryButton(
                    onClick = onDisconnect,
                    enabled = uiState.disconnectEnabled,
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
            val descriptor = serialPortSuggestionDescriptor(suggestion)
            if (descriptor.isNotBlank()) {
                append(" — ")
                append(descriptor)
            }
        }
    }
}

