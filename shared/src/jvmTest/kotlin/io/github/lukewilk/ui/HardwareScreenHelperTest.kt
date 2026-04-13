package io.github.lukewilk.ui

import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.api.BackendApi
import io.github.lukewilk.shared.model.BandPower
import io.github.lukewilk.shared.model.SerialPortSuggestion
import io.github.lukewilk.shared.model.SystemLogEntry
import io.github.lukewilk.shared.model.SystemLogSeverity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * JVM unit tests for the pure helper logic that backs the shared hardware screen.
 */
class HardwareScreenHelperTest {
    @Test
    fun `board load helpers resolve default and error states`() {
        // Confirms normal and error board-loading branches pick the expected selected board and fallback text.
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
        // Verifies the extracted board-loading helper covers unavailable, filtered-success, and exception branches.
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
    fun `board label helpers cover loading empty error and formatted ids`() {
        // Verifies the screen-level board labels stay readable across loading, failure, empty, and normal states.
        assertEquals(listOf("Loading boards..."), boardLabelsFor(true, null, listOf("IGNORED")))
        assertEquals(listOf("Unable to load boards"), boardLabelsFor(false, "Failed", emptyList()))
        assertEquals(listOf("No boards available"), boardLabelsFor(false, null, emptyList()))
        assertEquals(listOf("Synthetic", "Ganglion Wifi"), boardLabelsFor(false, null, listOf("SYNTHETIC_BOARD", "GANGLION_WIFI_BOARD")))
        assertEquals("Cyton Daisy", formatBoardLabel("CYTON_DAISY_BOARD"))
        assertEquals("3 Lead Ecg", formatBoardLabel("3_LEAD_ECG_BOARD"))
    }

    @Test
    fun `system log merge keeps backend logs and appends unique fallback entries`() {
        // Covers the helper branches that avoid duplicates while still surfacing fallback warnings and errors.
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
    fun `fallback log entry helper covers backend unavailable error and success states`() {
        // Verifies the screen-level fallback log helper emits warning, error, or null depending on the load state.
        val unavailable = fallbackLogEntryFor(true, null, 10L)
        val error = fallbackLogEntryFor(false, "Board lookup failed", 11L)
        val success = fallbackLogEntryFor(false, null, 12L)

        assertEquals(SystemLogSeverity.WARN, unavailable?.severity)
        assertEquals("Hardware backend is unavailable on this platform.", unavailable?.message)
        assertEquals(SystemLogSeverity.ERROR, error?.severity)
        assertEquals("Board lookup failed", error?.message)
        assertNull(success)
    }

    @Test
    fun `serial port helpers prefer recommended usb and first available suggestions`() {
        // Documents how automatic serial-port selection prioritizes recommended and USB devices.
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
    fun `serial port helper functions cover placeholder support text and auto selection rules`() {
        // Verifies placeholder/support-text branches and the auto-selection guard used by the hardware screen.
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
        // Confirms the extracted serial-port loading helper preserves all of the UI-side state transitions.
        val suggestions = listOf(
            SerialPortSuggestion(path = "/dev/ttyUSB0", displayName = "USB", isUsbDevice = true),
            SerialPortSuggestion(path = "/dev/ttyACM0", displayName = "Recommended", isRecommended = true)
        )
        val successApi = FakeBackendApi(serialSuggestions = suggestions)
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

        val failureState = loadSerialPortUiState(failureApi, "CYTON_BOARD", "/dev/custom", "/dev/ttyACM0", true)
        assertEquals(emptyList(), failureState.serialPortSuggestions)
        assertEquals("Permission denied", failureState.serialPortLoadError)
        assertEquals("/dev/custom", failureState.serialPort)
    }

    @Test
    fun `selected serial port suggestion index prefers explicit path then recommended then zero`() {
        // Covers the index ranking helper used to preselect the best matching serial suggestion in the UI.
        val suggestions = listOf(
            SerialPortSuggestion(path = "/dev/ttyS0", displayName = "Legacy"),
            SerialPortSuggestion(path = "/dev/ttyUSB0", displayName = "Recommended", isRecommended = true)
        )

        assertEquals(0, selectedSerialPortSuggestionIndex("/dev/ttyS0", suggestions))
        assertEquals(1, selectedSerialPortSuggestionIndex("/dev/missing", suggestions))
        assertEquals(0, selectedSerialPortSuggestionIndex("/dev/missing", emptyList()))
    }

    @Test
    fun `channel states helper uses hardware counts and falls back to eight channels`() {
        // Verifies the channel-row derivation keeps ids, labels, and status flags aligned with the hardware state.
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
}

/**
 * Small BackendApi fake used to drive the extracted HardwareScreen loader helpers deterministically.
 */
private class FakeBackendApi(
    private val boards: List<String> = emptyList(),
    private val boardFailure: Throwable? = null,
    private val serialSuggestions: List<SerialPortSuggestion> = emptyList(),
    private val serialFailure: Throwable? = null
) : BackendApi {
    override suspend fun connect(boardId: String, serialPort: String, timeoutSeconds: Int): Boolean = true
    override suspend fun disconnect(): Boolean = true
    override suspend fun addWave(wave: WaveSpec): Boolean = true
    override suspend fun removeWave(waveIndex: Int): Boolean = true
    override suspend fun editWave(waveIndex: Int, wave: WaveSpec): Boolean = true
    override suspend fun startStreaming(): Boolean = true
    override suspend fun stopStreaming(): Boolean = true
    override suspend fun enableChannel(channelId: Int): Boolean = true
    override suspend fun disableChannel(channelId: Int): Boolean = true
    override suspend fun enableRLD(channelId: Int): Boolean = true
    override suspend fun disableRLD(channelId: Int): Boolean = true
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

