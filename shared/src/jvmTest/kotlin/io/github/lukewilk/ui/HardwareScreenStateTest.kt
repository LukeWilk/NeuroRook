package io.github.lukewilk.ui

import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.api.BackendApi
import io.github.lukewilk.shared.model.BandPower
import io.github.lukewilk.shared.model.SerialPortSuggestion
import io.github.lukewilk.shared.model.SystemLogEntry
import io.github.lukewilk.shared.model.SystemLogSeverity
import io.github.lukewilk.ui.hardware.boardControlCard.boardControlUiState
import io.github.lukewilk.ui.hardware.boardControlCard.channelTableRowUiState
import io.github.lukewilk.ui.hardware.deviceSelectionUiState
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * JVM state tests for the shared hardware screen logic.
 */
class HardwareScreenStateTest {
    @Test
    fun `board load state resolves default and error states`() {
        val syntheticFirst = resolveBoardLoadState(listOf("CYTON_BOARD", "SYNTHETIC_BOARD"))
        val noBoards = resolveBoardLoadState(emptyList())
        val unresolved = unresolvedBoards("Backend offline")

        assertEquals(listOf("CYTON_BOARD", "SYNTHETIC_BOARD"), syntheticFirst.availableBoards)
        assertEquals(1, syntheticFirst.selectedBoard)
        assertNull(syntheticFirst.errorMessage)
        assertEquals("No boards were returned by the backend API.", noBoards.errorMessage)
        assertEquals(emptyList(), unresolved.availableBoards)
        assertEquals(0, unresolved.selectedBoard)
        assertEquals("Backend offline", unresolved.errorMessage)
    }

    @Test
    fun `load board state handles missing backend filtered boards and backend failures`() {
        val successApi = FakeBackendApi(boards = listOf("NO_BOARD", "CYTON_BOARD", "SYNTHETIC_BOARD"))
        val failureApi = FakeBackendApi(boardFailure = IllegalStateException("Board service down"))

        assertEquals(
            "Hardware backend is unavailable on this platform.",
            loadBoardState(null).errorMessage
        )

        val successState = loadBoardState(successApi)
        assertEquals(listOf("CYTON_BOARD", "SYNTHETIC_BOARD"), successState.availableBoards)
        assertEquals(1, successState.selectedBoard)

        assertEquals("Board service down", loadBoardState(failureApi).errorMessage)
    }

    @Test
    fun `load board state falls back to an unknown error when the backend exception has no message`() {
        val failureApi = FakeBackendApi(boardFailure = IllegalStateException())
        assertEquals("Unknown error", loadBoardState(failureApi).errorMessage)
    }

    @Test
    fun `board label logic covers loading empty error and formatted ids`() {
        assertEquals(listOf("Loading boards..."), boardLabelsFor(true, null, listOf("IGNORED")))
        assertEquals(listOf("Unable to load boards"), boardLabelsFor(false, "Failed", emptyList()))
        assertEquals(listOf("No boards available"), boardLabelsFor(false, null, emptyList()))
        assertEquals(listOf("Synthetic", "Ganglion Wifi"), boardLabelsFor(false, null, listOf("SYNTHETIC_BOARD", "GANGLION_WIFI_BOARD")))
        assertEquals("Cyton Daisy", formatBoardLabel("CYTON_DAISY_BOARD"))
        assertEquals("3 Lead Ecg", formatBoardLabel("3_LEAD_ECG_BOARD"))
        assertEquals("Cyton Daisy", formatBoardLabel("CYTON__DAISY_BOARD"))
        assertEquals("Cyton", formatBoardLabel("CYTON_ _BOARD"))
        assertEquals("", formatBoardLabel("_BOARD"))
    }

    @Test
    fun `board label part logic title cases alphabetic segments and preserves numeric prefixes`() {
        assertEquals("Cyton", formatBoardLabelPart("CYTON"))
        assertEquals("Hello", formatBoardLabelPart("hello"))
        assertEquals("3lead", formatBoardLabelPart("3LEAD"))
        assertEquals("Mixedcase", formatBoardLabelPart("MIXEDCASE"))
        assertEquals("", formatBoardLabelPart(""))
    }

    @Test
    fun `system log merge keeps backend logs and appends unique fallback entries`() {
        val backendLog = SystemLogEntry(1L, SystemLogSeverity.INFO, "Connected")
        val fallbackLog = SystemLogEntry(2L, SystemLogSeverity.ERROR, "Backend offline")

        assertEquals(listOf(backendLog), mergeSystemLogs(listOf(backendLog), null))
        assertEquals(listOf(fallbackLog), mergeSystemLogs(emptyList(), fallbackLog))
        assertEquals(listOf(backendLog, fallbackLog), mergeSystemLogs(listOf(backendLog), fallbackLog))
        assertEquals(
            listOf(backendLog.copy(message = "Backend offline")),
            mergeSystemLogs(listOf(backendLog.copy(message = "Backend offline")), fallbackLog)
        )
    }

    @Test
    fun `fallback log entry logic covers backend unavailable error and success states`() {
        val unavailable = fallbackLogEntryFor(true, null, 10L)
        val error = fallbackLogEntryFor(false, "Board lookup failed", 11L)
        val success = fallbackLogEntryFor(false, null, 12L)
        val blankErrorIgnored = fallbackLogEntryFor(false, "   ", 13L)

        assertEquals(SystemLogSeverity.WARN, unavailable?.severity)
        assertEquals("Hardware backend is unavailable on this platform.", unavailable?.message)
        assertEquals(SystemLogSeverity.ERROR, error?.severity)
        assertEquals("Board lookup failed", error?.message)
        assertNull(success)
        assertNull(blankErrorIgnored)
    }

    @Test
    fun `merge system logs appends fallback when backend list is non empty and message differs`() {
        val backendLog = SystemLogEntry(1L, SystemLogSeverity.INFO, "Live backend")
        val fallback = SystemLogEntry(2L, SystemLogSeverity.WARN, "Extra notice")
        assertEquals(
            listOf(backendLog, fallback),
            mergeSystemLogs(listOf(backendLog), fallback)
        )
    }

    @Test
    fun `serial port selection prefers recommended usb and first available suggestions`() {
        val suggestions = listOf(
            SerialPortSuggestion(path = "/dev/ttyS0", displayName = "Legacy"),
            SerialPortSuggestion(path = "/dev/ttyUSB0", displayName = "USB", isUsbDevice = true),
            SerialPortSuggestion(path = "/dev/ttyACM0", displayName = "Recommended", isRecommended = true)
        )

        assertEquals("", defaultSerialPortFor("SYNTHETIC_BOARD", suggestions))
        assertEquals("/dev/ttyACM0", defaultSerialPortFor("CYTON_BOARD", suggestions))
        assertEquals("/dev/ttyUSB0", defaultSerialPortFor("CYTON_BOARD", suggestions.dropLast(1)))
        assertEquals("/dev/ttyS0", defaultSerialPortFor("CYTON_BOARD", suggestions.take(1)))
        assertEquals("", defaultSerialPortFor("CYTON_BOARD", emptyList()))
    }

    @Test
    fun `preferred serial port path covers recommended usb first and empty fallback ordering`() {
        val recommended = listOf(
            SerialPortSuggestion(path = "/dev/ttyS0", displayName = "Legacy"),
            SerialPortSuggestion(path = "/dev/ttyACM0", displayName = "Board", isRecommended = true)
        )
        val usbOnly = listOf(
            SerialPortSuggestion(path = "/dev/ttyUSB0", displayName = "USB", isUsbDevice = true)
        )
        val genericOnly = listOf(
            SerialPortSuggestion(path = "/dev/onlypath", displayName = "Port")
        )

        assertEquals("/dev/ttyACM0", preferredSerialPortPath(recommended))
        assertEquals("/dev/ttyUSB0", preferredSerialPortPath(usbOnly))
        assertEquals("/dev/onlypath", preferredSerialPortPath(genericOnly))
        assertEquals("", preferredSerialPortPath(emptyList()))
    }

    @Test
    fun `serial port logic covers placeholder support text and auto selection rules`() {
        val recommended = listOf(
            SerialPortSuggestion(path = "/dev/ttyACM0", displayName = "Board", isRecommended = true)
        )
        val usbOnly = listOf(
            SerialPortSuggestion(path = "/dev/ttyUSB0", displayName = "USB", isUsbDevice = true)
        )

        assertEquals("No serial port required", serialPortPlaceholderFor("SYNTHETIC_BOARD", recommended))
        assertEquals("/dev/ttyACM0", serialPortPlaceholderFor("CYTON_BOARD", recommended))
        assertEquals("Auto-detect active USB serial device", serialPortPlaceholderFor("CYTON_BOARD", emptyList()))

        assertEquals(
            "Synthetic boards do not require a serial port.",
            serialPortSupportTextFor("SYNTHETIC_BOARD", false, null, emptyList())
        )
        assertEquals(
            "Scanning active serial devices on this machine...",
            serialPortSupportTextFor("CYTON_BOARD", true, null, emptyList())
        )
        assertEquals(
            "Could not scan serial devices: Permission denied. You can still enter a path manually.",
            serialPortSupportTextFor("CYTON_BOARD", false, "Permission denied", emptyList())
        )
        assertEquals(
            "No active serial devices were detected. Plug in your board or enter a port manually.",
            serialPortSupportTextFor("CYTON_BOARD", false, "   ", emptyList())
        )
        assertEquals(
            "No active serial devices were detected. Plug in your board or enter a port manually.",
            serialPortSupportTextFor("CYTON_BOARD", false, null, emptyList())
        )
        assertEquals(
            "A likely board connection was preselected for you. You can choose any detected port or type a custom path.",
            serialPortSupportTextFor("CYTON_BOARD", false, null, recommended)
        )
        assertEquals(
            "Choose any detected port or type a custom path.",
            serialPortSupportTextFor("CYTON_BOARD", false, null, usbOnly)
        )

        assertTrue(shouldAutoSelectSerialPort(serialPort = "", lastAutoSelectedSerialPort = null, hasManualSerialPortSelection = false))
        assertTrue(shouldAutoSelectSerialPort(serialPort = "", lastAutoSelectedSerialPort = "/dev/ttyACM0", hasManualSerialPortSelection = true))
        assertTrue(shouldAutoSelectSerialPort(serialPort = "/dev/ttyACM0", lastAutoSelectedSerialPort = "/dev/ttyACM0", hasManualSerialPortSelection = true))
        assertFalse(shouldAutoSelectSerialPort(serialPort = "/dev/custom", lastAutoSelectedSerialPort = "/dev/ttyACM0", hasManualSerialPortSelection = true))
    }

    @Test
    fun `load serial port state handles unavailable blank synthetic success manual and failure branches`() {
        val suggestions = listOf(
            SerialPortSuggestion(path = "/dev/ttyUSB0", displayName = "USB", isUsbDevice = true),
            SerialPortSuggestion(path = "/dev/ttyACM0", displayName = "Recommended", isRecommended = true)
        )
        val successApi = FakeBackendApi(serialSuggestions = suggestions)
        val emptySuggestionsApi = FakeBackendApi(serialSuggestions = emptyList())
        val failureApi = FakeBackendApi(serialFailure = IllegalStateException("Permission denied"))

        val unavailableState = loadSerialPortUiState(
            backendApi = null,
            selectedBoardId = "CYTON_BOARD",
            serialPort = "/dev/manual",
            lastAutoSelectedSerialPort = "/dev/ttyACM0",
            hasManualSerialPortSelection = false
        )
        assertEquals("Hardware backend is unavailable on this platform.", unavailableState.serialPortLoadError)
        assertEquals("", unavailableState.serialPort)
        assertNull(unavailableState.lastAutoSelectedSerialPort)

        val blankBoardState = loadSerialPortUiState(successApi, null, "/dev/manual", "/dev/ttyACM0", true)
        assertEquals(emptyList(), blankBoardState.serialPortSuggestions)
        assertNull(blankBoardState.serialPortLoadError)
        assertEquals("/dev/manual", blankBoardState.serialPort)

        val syntheticBoardState = loadSerialPortUiState(successApi, "SYNTHETIC_BOARD", "/dev/manual", "/dev/ttyACM0", false)
        assertEquals("", syntheticBoardState.serialPort)
        assertNull(syntheticBoardState.lastAutoSelectedSerialPort)

        val successState = loadSerialPortUiState(successApi, "CYTON_BOARD", "", null, false)
        assertEquals(suggestions, successState.serialPortSuggestions)
        assertEquals("/dev/ttyACM0", successState.serialPort)
        assertEquals("/dev/ttyACM0", successState.lastAutoSelectedSerialPort)

        val manualState = loadSerialPortUiState(successApi, "CYTON_BOARD", "/dev/custom", "/dev/ttyACM0", true)
        assertEquals("/dev/custom", manualState.serialPort)
        assertEquals("/dev/ttyACM0", manualState.lastAutoSelectedSerialPort)

        val retainedAutoSelectionState = loadSerialPortUiState(
            emptySuggestionsApi,
            "CYTON_BOARD",
            "/dev/custom",
            "/dev/ttyACM0",
            true
        )
        assertEquals(emptyList(), retainedAutoSelectionState.serialPortSuggestions)
        assertEquals("/dev/custom", retainedAutoSelectionState.serialPort)
        assertEquals("/dev/ttyACM0", retainedAutoSelectionState.lastAutoSelectedSerialPort)

        val failureState = loadSerialPortUiState(failureApi, "CYTON_BOARD", "/dev/custom", "/dev/ttyACM0", true)
        assertEquals(emptyList(), failureState.serialPortSuggestions)
        assertEquals("Permission denied", failureState.serialPortLoadError)
        assertEquals("/dev/custom", failureState.serialPort)
    }

    @Test
    fun `load serial port state preserves manual custom paths and falls back to unknown errors`() {
        val emptySuggestionsApi = FakeBackendApi(serialSuggestions = emptyList())
        val blankFailureApi = FakeBackendApi(serialFailure = IllegalStateException())

        val unavailableManualState = loadSerialPortUiState(
            backendApi = null,
            selectedBoardId = "CYTON_BOARD",
            serialPort = "/dev/custom-manual",
            lastAutoSelectedSerialPort = "/dev/ttyACM0",
            hasManualSerialPortSelection = true
        )
        assertEquals("/dev/custom-manual", unavailableManualState.serialPort)
        assertEquals("/dev/ttyACM0", unavailableManualState.lastAutoSelectedSerialPort)

        val syntheticManualState = loadSerialPortUiState(
            backendApi = emptySuggestionsApi,
            selectedBoardId = "SYNTHETIC_BOARD",
            serialPort = "/dev/custom-manual",
            lastAutoSelectedSerialPort = "/dev/ttyACM0",
            hasManualSerialPortSelection = true
        )
        assertEquals("/dev/custom-manual", syntheticManualState.serialPort)
        assertEquals("/dev/ttyACM0", syntheticManualState.lastAutoSelectedSerialPort)

        val emptyAutoSelectionState = loadSerialPortUiState(
            backendApi = emptySuggestionsApi,
            selectedBoardId = "CYTON_BOARD",
            serialPort = "",
            lastAutoSelectedSerialPort = null,
            hasManualSerialPortSelection = false
        )
        assertEquals("", emptyAutoSelectionState.serialPort)
        assertNull(emptyAutoSelectionState.lastAutoSelectedSerialPort)

        val blankFailureState = loadSerialPortUiState(
            backendApi = blankFailureApi,
            selectedBoardId = "CYTON_BOARD",
            serialPort = "/dev/custom-manual",
            lastAutoSelectedSerialPort = "/dev/ttyACM0",
            hasManualSerialPortSelection = true
        )
        assertEquals("Unknown error", blankFailureState.serialPortLoadError)
    }

    @Test
    fun `load serial port state retains last auto selection when suggestions empty and user keeps manual port`() {
        val emptySuggestionsApi = FakeBackendApi(serialSuggestions = emptyList())
        val state = loadSerialPortUiState(
            backendApi = emptySuggestionsApi,
            selectedBoardId = "CYTON_BOARD",
            serialPort = "/dev/manual-only",
            lastAutoSelectedSerialPort = "/dev/prior-auto",
            hasManualSerialPortSelection = true
        )
        assertEquals("/dev/manual-only", state.serialPort)
        assertEquals("/dev/prior-auto", state.lastAutoSelectedSerialPort)
        assertEquals(emptyList(), state.serialPortSuggestions)
        assertNull(state.serialPortLoadError)
    }

    @Test
    fun `load serial port state clears last auto marker when auto select yields blank default path`() {
        val emptyPathSuggestions = listOf(SerialPortSuggestion(path = "", displayName = "Placeholder port"))
        val api = FakeBackendApi(serialSuggestions = emptyPathSuggestions)
        val state = loadSerialPortUiState(
            backendApi = api,
            selectedBoardId = "CYTON_BOARD",
            serialPort = "",
            lastAutoSelectedSerialPort = null,
            hasManualSerialPortSelection = false
        )
        assertEquals("", state.serialPort)
        assertNull(state.lastAutoSelectedSerialPort)
        assertEquals(emptyPathSuggestions, state.serialPortSuggestions)
    }

    @Test
    fun `load serial port state records suggested default path as last auto marker when manual selection blocks auto apply`() {
        val suggestions = listOf(SerialPortSuggestion(path = "/dev/ttyACM0", displayName = "Adapter", isRecommended = true))
        val api = FakeBackendApi(serialSuggestions = suggestions)
        val state = loadSerialPortUiState(
            backendApi = api,
            selectedBoardId = "CYTON_BOARD",
            serialPort = "/dev/my-custom",
            lastAutoSelectedSerialPort = "/dev/old",
            hasManualSerialPortSelection = true
        )
        assertEquals("/dev/my-custom", state.serialPort)
        assertEquals("/dev/ttyACM0", state.lastAutoSelectedSerialPort)
    }

    @Test
    fun `selected serial port suggestion index prefers explicit path then recommended then zero`() {
        val suggestions = listOf(
            SerialPortSuggestion(path = "/dev/ttyS0", displayName = "Legacy"),
            SerialPortSuggestion(path = "/dev/ttyUSB0", displayName = "Recommended", isRecommended = true)
        )

        assertEquals(0, selectedSerialPortSuggestionIndex("/dev/ttyS0", suggestions))
        assertEquals(1, selectedSerialPortSuggestionIndex("/dev/missing", suggestions))
        assertEquals(0, selectedSerialPortSuggestionIndex("/dev/missing", emptyList()))
        val genericOnly = listOf(
            SerialPortSuggestion(path = "/dev/ttyS0", displayName = "Legacy"),
            SerialPortSuggestion(path = "/dev/ttyS1", displayName = "Other")
        )
        assertEquals(0, selectedSerialPortSuggestionIndex("/dev/unknown", genericOnly))
    }

    @Test
    fun `connect request and selected serial path logic cover missing board trimming and fallback selection`() {
        val suggestions = listOf(
            SerialPortSuggestion(path = "/dev/ttyUSB0", displayName = "USB"),
            SerialPortSuggestion(path = "/dev/ttyACM0", displayName = "Recommended")
        )

        assertEquals(
            ConnectRequest(boardId = "CYTON_BOARD", serialPort = "/dev/manual", timeoutSeconds = 9),
            connectRequestFor(listOf("CYTON_BOARD"), resolvedSelectedBoard = 0, serialPort = " /dev/manual ", timeout = "9")
        )
        assertEquals(
            ConnectRequest(boardId = "CYTON_BOARD", serialPort = "/dev/manual", timeoutSeconds = 0),
            connectRequestFor(listOf("CYTON_BOARD"), resolvedSelectedBoard = 0, serialPort = "/dev/manual", timeout = "invalid")
        )
        assertNull(connectRequestFor(emptyList(), resolvedSelectedBoard = 0, serialPort = "/dev/manual", timeout = "9"))

        assertEquals("/dev/ttyACM0", selectedSerialPortPathFor(suggestions, 1))
        assertEquals("", selectedSerialPortPathFor(suggestions, 99))
    }

    @Test
    fun `channel and rld backend logic dispatch enable and disable calls to the api`() = runBlocking {
        val backendApi = FakeBackendApi()

        backendApi.setChannelEnabled(channelId = 7, enabled = true)
        backendApi.setChannelEnabled(channelId = 3, enabled = false)
        backendApi.setRldEnabled(channelId = 4, enabled = true)
        backendApi.setRldEnabled(channelId = 2, enabled = false)

        assertEquals(listOf(7), backendApi.enabledChannelCalls)
        assertEquals(listOf(3), backendApi.disabledChannelCalls)
        assertEquals(listOf(4), backendApi.enabledRldCalls)
        assertEquals(listOf(2), backendApi.disabledRldCalls)
    }

    @Test
    fun `channel state logic uses hardware counts and falls back to eight channels`() {
        val explicitChannels = channelStatesFor(
            HardwareState(
                channels = 2,
                enabledChannels = listOf(1),
                rldEnabled = listOf(0),
                verifiedChannels = listOf(1)
            )
        )
        val fallbackChannels = channelStatesFor(HardwareState(channels = 0))

        assertEquals(2, explicitChannels.size)
        assertEquals("Channel 1", explicitChannels[0].name)
        assertEquals(false, explicitChannels[0].enabled)
        assertEquals(true, explicitChannels[0].rld)
        assertEquals("Not configured", explicitChannels[0].status)
        assertEquals(true, explicitChannels[1].enabled)
        assertEquals("Configured", explicitChannels[1].status)
        assertEquals(8, fallbackChannels.size)
    }

    @Test
    fun `useCompactHardwareLayout is true strictly below the breakpoint`() {
        assertTrue(useCompactHardwareLayout(899.dp))
        assertFalse(useCompactHardwareLayout(900.dp))
        assertFalse(useCompactHardwareLayout(1200.dp))
        assertTrue(useCompactHardwareLayout(499.dp, compactBreakpoint = 500.dp))
        assertFalse(useCompactHardwareLayout(500.dp, compactBreakpoint = 500.dp))
    }

    @Test
    fun `hardware screen display state derives logs boards serial ui and channels together`() {
        val hardwareState = HardwareState(
            channels = 2,
            enabledChannels = listOf(1),
            rldEnabled = listOf(0),
            verifiedChannels = listOf(1)
        )
        val suggestions = listOf(
            SerialPortSuggestion(path = "/dev/ttyUSB0", displayName = "USB Adapter", isUsbDevice = true),
            SerialPortSuggestion(path = "/dev/ttyACM0", displayName = "Recommended Adapter", isRecommended = true)
        )
        val backendLog = SystemLogEntry(10L, SystemLogSeverity.INFO, "Backend ready")

        val displayState = hardwareScreenDisplayState(
            backendApiAvailable = true,
            backendLogs = listOf(backendLog),
            boardLoadError = null,
            isLoadingBoards = false,
            availableBoards = listOf("CYTON_BOARD", "SYNTHETIC_BOARD"),
            selectedBoard = 0,
            serialPort = "/dev/ttyACM0",
            serialPortSuggestions = suggestions,
            isLoadingSerialPorts = false,
            serialPortLoadError = null,
            hardwareState = hardwareState,
            timestampEpochMillis = 20L
        )

        assertEquals(listOf(backendLog), displayState.displayedLogs)
        assertEquals(listOf("Cyton", "Synthetic"), displayState.boardLabels)
        assertEquals(0, displayState.resolvedSelectedBoard)
        assertEquals("CYTON_BOARD", displayState.selectedBoardId)
        assertTrue(displayState.boardsReady)
        assertEquals(1, displayState.selectedSerialPortSuggestion)
        assertEquals("/dev/ttyACM0", displayState.serialPortPlaceholder)
        assertEquals(
            "A likely board connection was preselected for you. You can choose any detected port or type a custom path.",
            displayState.serialPortSupportText
        )
        assertEquals(2, displayState.channels.size)
        assertEquals("Not configured", displayState.channels[0].status)
        assertEquals("Configured", displayState.channels[1].status)
    }

    @Test
    fun `hardware screen display state falls back for unavailable backend loading boards and empty serial suggestions`() {
        val displayState = hardwareScreenDisplayState(
            backendApiAvailable = false,
            backendLogs = emptyList(),
            boardLoadError = "Board service offline",
            isLoadingBoards = true,
            availableBoards = emptyList(),
            selectedBoard = 5,
            serialPort = "",
            serialPortSuggestions = emptyList(),
            isLoadingSerialPorts = true,
            serialPortLoadError = "Permission denied",
            hardwareState = HardwareState(channels = 0),
            timestampEpochMillis = 99L
        )

        assertEquals(1, displayState.displayedLogs.size)
        assertEquals("Hardware backend is unavailable on this platform.", displayState.displayedLogs.single().message)
        assertEquals(listOf("Loading boards..."), displayState.boardLabels)
        assertEquals(0, displayState.resolvedSelectedBoard)
        assertNull(displayState.selectedBoardId)
        assertFalse(displayState.boardsReady)
        assertEquals(0, displayState.selectedSerialPortSuggestion)
        assertEquals("Auto-detect active USB serial device", displayState.serialPortPlaceholder)
        assertEquals("Scanning active serial devices on this machine...", displayState.serialPortSupportText)
        assertEquals(8, displayState.channels.size)
    }

    @Test
    fun `hardware screen display state coerces selected board index into catalog range`() {
        val displayState = hardwareScreenDisplayState(
            backendApiAvailable = true,
            backendLogs = emptyList(),
            boardLoadError = null,
            isLoadingBoards = false,
            availableBoards = listOf("A_BOARD", "B_BOARD"),
            selectedBoard = 99,
            serialPort = "",
            serialPortSuggestions = emptyList(),
            isLoadingSerialPorts = false,
            serialPortLoadError = null,
            hardwareState = HardwareState(channels = 1),
            timestampEpochMillis = 1L
        )
        assertEquals(1, displayState.resolvedSelectedBoard)
        assertEquals("B_BOARD", displayState.selectedBoardId)
    }

    @Test
    fun `hardware screen display state shows board-load errors serial scan failures and plain preferred serial fallback`() {
        val fallbackSuggestions = listOf(
            SerialPortSuggestion(path = "/dev/ttyS0", displayName = "Legacy Adapter"),
            SerialPortSuggestion(path = "/dev/ttyS1", displayName = "Backup Adapter")
        )

        val displayState = hardwareScreenDisplayState(
            backendApiAvailable = true,
            backendLogs = emptyList(),
            boardLoadError = "Board service offline",
            isLoadingBoards = false,
            availableBoards = emptyList(),
            selectedBoard = 0,
            serialPort = "/dev/ttyS0",
            serialPortSuggestions = fallbackSuggestions,
            isLoadingSerialPorts = false,
            serialPortLoadError = "Permission denied",
            hardwareState = HardwareState(),
            timestampEpochMillis = 123L
        )

        assertEquals("Board service offline", displayState.displayedLogs.single().message)
        assertEquals(listOf("Unable to load boards"), displayState.boardLabels)
        assertEquals(0, displayState.selectedSerialPortSuggestion)
        assertEquals("/dev/ttyS0", displayState.serialPortPlaceholder)
        assertEquals(
            "Could not scan serial devices: Permission denied. You can still enter a path manually.",
            displayState.serialPortSupportText
        )
        assertEquals("/dev/ttyS0", preferredSerialPortPath(fallbackSuggestions))
    }

    @Test
    fun `device selection board control and channel row state builders derive feature behavior`() {
        val setupState = deviceSelectionUiState(
            serialPortSuggestions = listOf(SerialPortSuggestion(path = "/dev/ttyUSB0", displayName = "USB Adapter")),
            selectedSerialPortSuggestion = 9,
            isConnected = false,
            isBusy = false,
            isLoadingSerialPorts = true,
            serialPortSupportText = "Use the primary adapter",
            canSelectBoard = true,
            canConnect = false
        )
        val boardControlState = boardControlUiState(
            availableBoards = listOf("Cyton"),
            selectedBoard = 0,
            isConnected = true,
            isStreaming = false,
            isBusy = false
        )
        val configuredRowState = channelTableRowUiState(index = 0, lastIndex = 1, status = "Configured")
        val finalRowState = channelTableRowUiState(index = 1, lastIndex = 1, status = "Not configured")

        assertTrue(setupState.boardSelectionEnabled)
        assertFalse(setupState.serialSuggestionSelectionEnabled)
        assertEquals(0, setupState.selectedSerialPortSuggestion)
        assertFalse(setupState.refreshPortsEnabled)
        assertEquals("Refreshing...", setupState.refreshPortsText)
        assertEquals("Use the primary adapter", setupState.visibleSerialPortSupportText)
        assertTrue(setupState.serialInputFieldsEnabled)
        assertEquals("No Device Connected", setupState.connectionStatusText)
        assertFalse(setupState.connectEnabled)
        assertFalse(setupState.disconnectEnabled)

        assertEquals("Cyton Control", boardControlState.title)
        assertTrue(boardControlState.channelsEnabled)
        assertTrue(boardControlState.verifyChannelsEnabled)
        assertTrue(boardControlState.startStreamEnabled)
        assertFalse(boardControlState.stopStreamEnabled)
        assertFalse(boardControlState.showStreamingIndicator)

        assertTrue(configuredRowState.usesEvenBackground)
        assertTrue(configuredRowState.showDivider)
        assertTrue(configuredRowState.isConfigured)
        assertFalse(finalRowState.usesEvenBackground)
        assertFalse(finalRowState.showDivider)
        assertFalse(finalRowState.isConfigured)
    }

    @Test
    fun `device selection and board control state builders cover connected busy and fallback title paths`() {
        val connectedSetupState = deviceSelectionUiState(
            serialPortSuggestions = emptyList(),
            selectedSerialPortSuggestion = 2,
            isConnected = true,
            isBusy = true,
            isLoadingSerialPorts = false,
            serialPortSupportText = "   ",
            canSelectBoard = false,
            canConnect = true
        )
        val fallbackBoardControlState = boardControlUiState(
            availableBoards = emptyList(),
            selectedBoard = 4,
            isConnected = false,
            isStreaming = true,
            isBusy = true
        )

        assertFalse(connectedSetupState.boardSelectionEnabled)
        assertFalse(connectedSetupState.serialSuggestionSelectionEnabled)
        assertEquals(0, connectedSetupState.selectedSerialPortSuggestion)
        assertFalse(connectedSetupState.refreshPortsEnabled)
        assertEquals("Refresh Ports", connectedSetupState.refreshPortsText)
        assertNull(connectedSetupState.visibleSerialPortSupportText)
        assertFalse(connectedSetupState.serialInputFieldsEnabled)
        assertEquals("Device Connected", connectedSetupState.connectionStatusText)
        assertFalse(connectedSetupState.connectEnabled)
        assertFalse(connectedSetupState.disconnectEnabled)

        assertEquals("Board Control", fallbackBoardControlState.title)
        assertFalse(fallbackBoardControlState.channelsEnabled)
        assertFalse(fallbackBoardControlState.verifyChannelsEnabled)
        assertFalse(fallbackBoardControlState.startStreamEnabled)
        assertFalse(fallbackBoardControlState.stopStreamEnabled)
        assertTrue(fallbackBoardControlState.showStreamingIndicator)
    }

    @Test
    fun `board control state enables the stop action when streaming remains active and not busy`() {
        val streamingState = boardControlUiState(
            availableBoards = listOf("Cyton"),
            selectedBoard = 0,
            isConnected = true,
            isStreaming = true,
            isBusy = false
        )

        assertFalse(streamingState.startStreamEnabled)
        assertTrue(streamingState.stopStreamEnabled)
        assertTrue(streamingState.showStreamingIndicator)
    }
}

private class FakeBackendApi(
    private val boards: List<String> = emptyList(),
    private val boardFailure: Throwable? = null,
    private val serialSuggestions: List<SerialPortSuggestion> = emptyList(),
    private val serialFailure: Throwable? = null
) : BackendApi {
    val enabledChannelCalls = mutableListOf<Int>()
    val disabledChannelCalls = mutableListOf<Int>()
    val enabledRldCalls = mutableListOf<Int>()
    val disabledRldCalls = mutableListOf<Int>()

    override suspend fun connect(boardId: String, serialPort: String, timeoutSeconds: Int): Boolean = true
    override suspend fun disconnect(): Boolean = true
    override suspend fun addWave(wave: WaveSpec): Boolean = true
    override suspend fun removeWave(waveIndex: Int): Boolean = true
    override suspend fun editWave(waveIndex: Int, wave: WaveSpec): Boolean = true
    override suspend fun startStreaming(): Boolean = true
    override suspend fun stopStreaming(): Boolean = true
    override suspend fun enableChannel(channelId: Int): Boolean {
        enabledChannelCalls += channelId
        return true
    }
    override suspend fun disableChannel(channelId: Int): Boolean {
        disabledChannelCalls += channelId
        return true
    }
    override suspend fun enableRLD(channelId: Int): Boolean {
        enabledRldCalls += channelId
        return true
    }
    override suspend fun disableRLD(channelId: Int): Boolean {
        disabledRldCalls += channelId
        return true
    }
    override suspend fun verifyChannels(): Boolean = true
    override suspend fun setSamplingRateHz(rate: Int): Boolean = true
    override fun getState(): HardwareState = HardwareState()
    override fun getBrainflowBoards(): List<String> = boardFailure?.let { throw it } ?: boards
    override fun getSerialPortSuggestions(boardId: String?): List<SerialPortSuggestion> = serialFailure?.let { throw it } ?: serialSuggestions
    override val hardwareStateFlow: StateFlow<HardwareState> = MutableStateFlow(HardwareState())
    override val systemLogFlow: StateFlow<List<SystemLogEntry>> = MutableStateFlow(emptyList())
    override val filteredFlow: Flow<DoubleArray> = emptyFlow()
    override val bandPowersFlow: Flow<List<BandPower>> = emptyFlow()
    override val fftResultFlow: Flow<Array<Pair<Double, Double>>> = emptyFlow()
    override fun setOnFilteredListener(listener: ((DoubleArray) -> Unit)?) = Unit
    override fun setOnBandPowersListener(listener: ((List<BandPower>) -> Unit)?) = Unit
    override fun setOnFFTResultListener(listener: ((Array<Pair<Double, Double>>) -> Unit)?) = Unit
}
