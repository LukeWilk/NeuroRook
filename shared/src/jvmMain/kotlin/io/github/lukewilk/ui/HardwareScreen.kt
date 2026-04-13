package io.github.lukewilk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.api.BackendApi
import io.github.lukewilk.shared.model.SerialPortSuggestion
import io.github.lukewilk.shared.model.SystemLogEntry
import io.github.lukewilk.shared.model.SystemLogSeverity
import io.github.lukewilk.ui.hardware.DeviceSelectionCard
import io.github.lukewilk.ui.hardware.SystemLogCard
import io.github.lukewilk.ui.hardware.boardControlCard.BoardControlCard
import kotlinx.coroutines.launch

@Composable
actual fun HardwareScreen(backendApi: BackendApi?) {
    val compactSpacing = 24.dp
    val wideSpacing = 32.dp
    val compactHorizontalPadding = 16.dp
    val wideHorizontalPadding = 32.dp
    val cardOuterTopPadding = 32.dp
    val cardOuterMidPadding = 12.dp
    val cardOuterBottomPadding = 32.dp

    val coroutineScope = rememberCoroutineScope()
    val hardwareState by (backendApi?.hardwareStateFlow?.collectAsState()
        ?: remember { mutableStateOf(HardwareState()) })
    val backendLogs by (backendApi?.systemLogFlow?.collectAsState()
        ?: remember { mutableStateOf(emptyList<SystemLogEntry>()) })

    var availableBoards by remember { mutableStateOf(emptyList<String>()) }
    var isLoadingBoards by remember { mutableStateOf(true) }
    var boardLoadError by remember { mutableStateOf<String?>(null) }
    var selectedBoard by remember { mutableStateOf(0) }
    var serialPort by remember { mutableStateOf("") }
    var serialPortSuggestions by remember { mutableStateOf(emptyList<SerialPortSuggestion>()) }
    var isLoadingSerialPorts by remember { mutableStateOf(false) }
    var serialPortLoadError by remember { mutableStateOf<String?>(null) }
    var serialPortRefreshToken by remember { mutableStateOf(0) }
    var hasManualSerialPortSelection by remember { mutableStateOf(false) }
    var lastAutoSelectedSerialPort by remember { mutableStateOf<String?>(null) }
    var timeout by remember { mutableStateOf("0") }
    var isBusy by remember { mutableStateOf(false) }

    LaunchedEffect(backendApi) {
        isLoadingBoards = true
        val boardLoadState = loadBoardState(backendApi)
        availableBoards = boardLoadState.availableBoards
        selectedBoard = boardLoadState.selectedBoard
        boardLoadError = boardLoadState.errorMessage
        isLoadingBoards = false
    }

    val fallbackLogEntry = remember(backendApi, boardLoadError) {
        fallbackLogEntryFor(
            backendUnavailable = backendApi == null,
            boardLoadError = boardLoadError,
            timestampEpochMillis = System.currentTimeMillis()
        )
    }

    val displayedLogs = mergeSystemLogs(
        backendLogs = backendLogs,
        fallbackLogEntry = fallbackLogEntry
    )

    val boardLabels = boardLabelsFor(
        isLoadingBoards = isLoadingBoards,
        boardLoadError = boardLoadError,
        availableBoards = availableBoards
    )
    val resolvedSelectedBoard = selectedBoard.coerceIn(0, boardLabels.lastIndex)
    val selectedBoardId = availableBoards.getOrNull(resolvedSelectedBoard)
    val boardsReady = !isLoadingBoards && boardLoadError == null && availableBoards.isNotEmpty()
    val selectedSerialPortSuggestion = selectedSerialPortSuggestionIndex(
        serialPort = serialPort,
        serialPortSuggestions = serialPortSuggestions
    )
    val serialPortPlaceholder = serialPortPlaceholderFor(
        selectedBoardId = selectedBoardId,
        serialPortSuggestions = serialPortSuggestions
    )
    val serialPortSupportText = serialPortSupportTextFor(
        selectedBoardId = selectedBoardId,
        isLoadingSerialPorts = isLoadingSerialPorts,
        serialPortLoadError = serialPortLoadError,
        serialPortSuggestions = serialPortSuggestions
    )
    val channels = channelStatesFor(hardwareState)

    LaunchedEffect(backendApi, selectedBoardId, serialPortRefreshToken) {
        val serialPortUiState = loadSerialPortUiState(
            backendApi = backendApi,
            selectedBoardId = selectedBoardId,
            serialPort = serialPort,
            lastAutoSelectedSerialPort = lastAutoSelectedSerialPort,
            hasManualSerialPortSelection = hasManualSerialPortSelection
        )
        serialPortSuggestions = serialPortUiState.serialPortSuggestions
        serialPortLoadError = serialPortUiState.serialPortLoadError
        isLoadingSerialPorts = serialPortUiState.isLoadingSerialPorts
        serialPort = serialPortUiState.serialPort
        lastAutoSelectedSerialPort = serialPortUiState.lastAutoSelectedSerialPort
    }

    fun runBackendAction(block: suspend BackendApi.() -> Unit) {
        val api = backendApi ?: return
        coroutineScope.launch {
            isBusy = true
            try {
                api.block()
            } finally {
                isBusy = false
            }
        }
    }

    val connectToSelectedBoard: () -> Unit = {
        val boardId = availableBoards.getOrNull(resolvedSelectedBoard)
        if (boardId != null) {
            val timeoutSeconds = timeout.toIntOrNull() ?: 0
            runBackendAction { connect(boardId, serialPort.trim(), timeoutSeconds) }
        }
    }

    val refreshSerialPorts = {
        serialPortRefreshToken += 1
    }

    val disconnectFromBoard = {
        runBackendAction { disconnect() }
    }

    val toggleChannel: (Int, Boolean) -> Unit = { id, enabled ->
        runBackendAction {
            if (enabled) enableChannel(id) else disableChannel(id)
        }
    }

    val toggleRld: (Int, Boolean) -> Unit = { id, enabled ->
        runBackendAction {
            if (enabled) enableRLD(id) else disableRLD(id)
        }
    }

    val verifyChannels = {
        runBackendAction { verifyChannels() }
    }

    val startStreaming = {
        runBackendAction { startStreaming() }
    }

    val stopStreaming = {
        runBackendAction { stopStreaming() }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scrollState = rememberScrollState()
        if (maxWidth < 900.dp) {
            Column(
                verticalArrangement = Arrangement.spacedBy(compactSpacing),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = compactHorizontalPadding)
                    .verticalScroll(scrollState)
            ) {
                Box(Modifier.padding(top = cardOuterTopPadding, bottom = cardOuterMidPadding)) {
                    DeviceSelectionCard(
                        availableBoards = boardLabels,
                        selectedBoard = resolvedSelectedBoard,
                        onBoardSelected = { selectedBoard = it },
                        serialPort = serialPort,
                        onSerialPortChange = {
                            serialPort = it
                            hasManualSerialPortSelection = true
                        },
                        serialPortPlaceholder = serialPortPlaceholder,
                        serialPortSuggestions = serialPortSuggestions,
                        selectedSerialPortSuggestion = selectedSerialPortSuggestion,
                        onSerialPortSuggestionSelected = { suggestionIndex ->
                            serialPort = serialPortSuggestions.getOrNull(suggestionIndex)?.path.orEmpty()
                            hasManualSerialPortSelection = true
                        },
                        onRefreshSerialPorts = refreshSerialPorts,
                        isLoadingSerialPorts = isLoadingSerialPorts,
                        serialPortSupportText = serialPortSupportText,
                        timeout = timeout,
                        onTimeoutChange = { timeout = it },
                        isConnected = hardwareState.connected,
                        isBusy = isBusy,
                        onConnect = connectToSelectedBoard,
                        onDisconnect = disconnectFromBoard,
                        canSelectBoard = boardsReady,
                        canConnect = boardsReady
                    )
                }
                Box(Modifier.padding(vertical = cardOuterMidPadding)) {
                    SystemLogCard(displayedLogs)
                }
                Box(Modifier.padding(top = cardOuterMidPadding, bottom = cardOuterBottomPadding)) {
                    BoardControlCard(
                        availableBoards = boardLabels,
                        selectedBoard = resolvedSelectedBoard,
                        isConnected = hardwareState.connected,
                        isStreaming = hardwareState.streaming,
                        isBusy = isBusy,
                        channels = channels,
                        onChannelToggle = toggleChannel,
                        onRldToggle = toggleRld,
                        onVerifyChannels = verifyChannels,
                        onStartStreaming = startStreaming,
                        onStopStreaming = stopStreaming
                    )
                }
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = wideHorizontalPadding)
                    .verticalScroll(scrollState)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(wideSpacing)
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(wideSpacing)
                    ) {
                        Box(Modifier.padding(top = cardOuterTopPadding, bottom = cardOuterMidPadding)) {
                            DeviceSelectionCard(
                                availableBoards = boardLabels,
                                selectedBoard = resolvedSelectedBoard,
                                onBoardSelected = { selectedBoard = it },
                                serialPort = serialPort,
                                onSerialPortChange = {
                                    serialPort = it
                                    hasManualSerialPortSelection = true
                                },
                                serialPortPlaceholder = serialPortPlaceholder,
                                serialPortSuggestions = serialPortSuggestions,
                                selectedSerialPortSuggestion = selectedSerialPortSuggestion,
                                onSerialPortSuggestionSelected = { suggestionIndex ->
                                    serialPort = serialPortSuggestions.getOrNull(suggestionIndex)?.path.orEmpty()
                                    hasManualSerialPortSelection = true
                                },
                                onRefreshSerialPorts = refreshSerialPorts,
                                isLoadingSerialPorts = isLoadingSerialPorts,
                                serialPortSupportText = serialPortSupportText,
                                timeout = timeout,
                                onTimeoutChange = { timeout = it },
                                isConnected = hardwareState.connected,
                                isBusy = isBusy,
                                onConnect = connectToSelectedBoard,
                                onDisconnect = disconnectFromBoard,
                                canSelectBoard = boardsReady,
                                canConnect = boardsReady
                            )
                        }
                        Box(Modifier.padding(top = cardOuterMidPadding, bottom = cardOuterBottomPadding)) {
                            SystemLogCard(displayedLogs)
                        }
                    }
                    Box(
                        Modifier
                            .weight(1f, fill = false)
                            .padding(top = cardOuterTopPadding, bottom = cardOuterBottomPadding)
                    ) {
                        BoardControlCard(
                            availableBoards = boardLabels,
                            selectedBoard = resolvedSelectedBoard,
                            isConnected = hardwareState.connected,
                            isStreaming = hardwareState.streaming,
                            isBusy = isBusy,
                            channels = channels,
                            onChannelToggle = toggleChannel,
                            onRldToggle = toggleRld,
                            onVerifyChannels = verifyChannels,
                            onStartStreaming = startStreaming,
                            onStopStreaming = stopStreaming
                        )
                    }
                }
            }
        }
    }
}

internal data class BoardLoadState(
    val availableBoards: List<String>,
    val selectedBoard: Int,
    val errorMessage: String? = null
)

internal data class SerialPortUiState(
    val serialPortSuggestions: List<SerialPortSuggestion>,
    val serialPortLoadError: String?,
    val isLoadingSerialPorts: Boolean,
    val serialPort: String,
    val lastAutoSelectedSerialPort: String?
)

internal fun loadBoardState(backendApi: BackendApi?): BoardLoadState {
    if (backendApi == null) {
        return unresolvedBoards("Hardware backend is unavailable on this platform.")
    }

    return runCatching {
        backendApi.getBrainflowBoards()
            .filterNot { it.equals("NO_BOARD", ignoreCase = true) }
    }.fold(
        onSuccess = ::resolveBoardLoadState,
        onFailure = { error -> unresolvedBoards(error.message ?: "Unknown error") }
    )
}

internal fun resolveBoardLoadState(boards: List<String>): BoardLoadState = BoardLoadState(
    availableBoards = boards,
    selectedBoard = boards.indexOfFirst { it == "SYNTHETIC_BOARD" }
        .takeIf { it >= 0 }
        ?: 0,
    errorMessage = if (boards.isEmpty()) "No boards were returned by the backend API." else null
)

internal fun unresolvedBoards(errorMessage: String): BoardLoadState = BoardLoadState(
    availableBoards = emptyList(),
    selectedBoard = 0,
    errorMessage = errorMessage
)

internal fun loadSerialPortUiState(
    backendApi: BackendApi?,
    selectedBoardId: String?,
    serialPort: String,
    lastAutoSelectedSerialPort: String?,
    hasManualSerialPortSelection: Boolean
): SerialPortUiState {
    if (backendApi == null) {
        val shouldReset = shouldAutoSelectSerialPort(serialPort, lastAutoSelectedSerialPort, hasManualSerialPortSelection)
        return SerialPortUiState(
            serialPortSuggestions = emptyList(),
            serialPortLoadError = "Hardware backend is unavailable on this platform.",
            isLoadingSerialPorts = false,
            serialPort = if (shouldReset) "" else serialPort,
            lastAutoSelectedSerialPort = if (shouldReset) null else lastAutoSelectedSerialPort
        )
    }

    if (selectedBoardId.isNullOrBlank()) {
        return SerialPortUiState(
            serialPortSuggestions = emptyList(),
            serialPortLoadError = null,
            isLoadingSerialPorts = false,
            serialPort = serialPort,
            lastAutoSelectedSerialPort = lastAutoSelectedSerialPort
        )
    }

    if (selectedBoardId.equals("SYNTHETIC_BOARD", ignoreCase = true)) {
        val shouldReset = shouldAutoSelectSerialPort(serialPort, lastAutoSelectedSerialPort, hasManualSerialPortSelection)
        return SerialPortUiState(
            serialPortSuggestions = emptyList(),
            serialPortLoadError = null,
            isLoadingSerialPorts = false,
            serialPort = if (shouldReset) "" else serialPort,
            lastAutoSelectedSerialPort = if (shouldReset) null else lastAutoSelectedSerialPort
        )
    }

    return runCatching {
        backendApi.getSerialPortSuggestions(selectedBoardId)
    }.fold(
        onSuccess = { suggestions ->
            val suggestedSerialPort = defaultSerialPortFor(selectedBoardId, suggestions)
            val shouldAutoSelect = shouldAutoSelectSerialPort(serialPort, lastAutoSelectedSerialPort, hasManualSerialPortSelection)
            SerialPortUiState(
                serialPortSuggestions = suggestions,
                serialPortLoadError = null,
                isLoadingSerialPorts = false,
                serialPort = if (shouldAutoSelect) suggestedSerialPort else serialPort,
                lastAutoSelectedSerialPort = when {
                    shouldAutoSelect -> suggestedSerialPort.takeIf { it.isNotBlank() }
                    suggestedSerialPort.isNotBlank() -> suggestedSerialPort
                    else -> lastAutoSelectedSerialPort
                }
            )
        },
        onFailure = { error ->
            SerialPortUiState(
                serialPortSuggestions = emptyList(),
                serialPortLoadError = error.message ?: "Unknown error",
                isLoadingSerialPorts = false,
                serialPort = serialPort,
                lastAutoSelectedSerialPort = lastAutoSelectedSerialPort
            )
        }
    )
}

internal fun boardLabelsFor(
    isLoadingBoards: Boolean,
    boardLoadError: String?,
    availableBoards: List<String>
): List<String> = when {
    isLoadingBoards -> listOf("Loading boards...")
    boardLoadError != null -> listOf("Unable to load boards")
    availableBoards.isEmpty() -> listOf("No boards available")
    else -> availableBoards.map(::formatBoardLabel)
}

internal fun mergeSystemLogs(
    backendLogs: List<SystemLogEntry>,
    fallbackLogEntry: SystemLogEntry?
): List<SystemLogEntry> {
    if (fallbackLogEntry == null) return backendLogs
    if (backendLogs.any { it.message == fallbackLogEntry.message }) return backendLogs
    return if (backendLogs.isEmpty()) listOf(fallbackLogEntry) else backendLogs + fallbackLogEntry
}

internal fun fallbackLogEntryFor(
    backendUnavailable: Boolean,
    boardLoadError: String?,
    timestampEpochMillis: Long
): SystemLogEntry? = when {
    backendUnavailable -> SystemLogEntry(
        timestampEpochMillis = timestampEpochMillis,
        severity = SystemLogSeverity.WARN,
        message = "Hardware backend is unavailable on this platform."
    )

    !boardLoadError.isNullOrBlank() -> SystemLogEntry(
        timestampEpochMillis = timestampEpochMillis,
        severity = SystemLogSeverity.ERROR,
        message = boardLoadError
    )

    else -> null
}

internal fun selectedSerialPortSuggestionIndex(
    serialPort: String,
    serialPortSuggestions: List<SerialPortSuggestion>
): Int = serialPortSuggestions.indexOfFirst { it.path == serialPort }
    .takeIf { it >= 0 }
    ?: serialPortSuggestions.indexOfFirst { it.isRecommended }
        .takeIf { it >= 0 }
    ?: 0

internal fun channelStatesFor(hardwareState: HardwareState): List<ChannelState> {
    val channelCount = hardwareState.channels.takeIf { it > 0 } ?: 8
    return List(channelCount) { index ->
        ChannelState(
            id = index,
            name = "Channel ${index + 1}",
            enabled = index in hardwareState.enabledChannels,
            rld = index in hardwareState.rldEnabled,
            status = if (index in hardwareState.verifiedChannels) "Configured" else "Not configured"
        )
    }
}

internal fun formatBoardLabel(boardId: String): String = boardId
    .removeSuffix("_BOARD")
    .split('_')
    .filter { it.isNotBlank() }
    .joinToString(" ") { part ->
        part.lowercase().replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase() else char.toString()
        }
    }

internal fun defaultSerialPortFor(
    selectedBoardId: String?,
    serialPortSuggestions: List<SerialPortSuggestion>
): String {
    if (selectedBoardId.equals("SYNTHETIC_BOARD", ignoreCase = true)) return ""

    return serialPortSuggestions.firstOrNull { it.isRecommended }?.path
        ?: serialPortSuggestions.firstOrNull { it.isUsbDevice }?.path
        ?: serialPortSuggestions.firstOrNull()?.path
        .orEmpty()
}

internal fun shouldAutoSelectSerialPort(
    serialPort: String,
    lastAutoSelectedSerialPort: String?,
    hasManualSerialPortSelection: Boolean
): Boolean = !hasManualSerialPortSelection ||
    serialPort.isBlank() ||
    serialPort == lastAutoSelectedSerialPort

internal fun serialPortPlaceholderFor(
    selectedBoardId: String?,
    serialPortSuggestions: List<SerialPortSuggestion>
): String = when {
    selectedBoardId.equals("SYNTHETIC_BOARD", ignoreCase = true) -> "No serial port required"
    serialPortSuggestions.isNotEmpty() -> defaultSerialPortFor(selectedBoardId, serialPortSuggestions)
    else -> "Auto-detect active USB serial device"
}

internal fun serialPortSupportTextFor(
    selectedBoardId: String?,
    isLoadingSerialPorts: Boolean,
    serialPortLoadError: String?,
    serialPortSuggestions: List<SerialPortSuggestion>
): String = when {
    selectedBoardId.equals("SYNTHETIC_BOARD", ignoreCase = true) ->
        "Synthetic boards do not require a serial port."

    isLoadingSerialPorts -> "Scanning active serial devices on this machine..."

    !serialPortLoadError.isNullOrBlank() ->
        "Could not scan serial devices: $serialPortLoadError. You can still enter a path manually."

    serialPortSuggestions.isEmpty() ->
        "No active serial devices were detected. Plug in your board or enter a port manually."

    serialPortSuggestions.any { it.isRecommended } ->
        "A likely board connection was preselected for you. You can choose any detected port or type a custom path."

    else -> "Choose any detected port or type a custom path."
}

