package io.github.lukewilk.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.api.BackendApi
import io.github.lukewilk.shared.model.BandPower
import io.github.lukewilk.shared.model.ChannelData
import io.github.lukewilk.shared.model.SerialPortSuggestion
import io.github.lukewilk.shared.model.SystemLogEntry
import io.github.lukewilk.shared.model.SystemLogSeverity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * JVM Compose coverage tests for the shared hardware screen orchestration.
 */
@OptIn(ExperimentalTestApi::class)
class HardwareScreenUiTest {
    @Test
    fun `hardware screen shows fallback messaging when the backend is unavailable`() {
        // Covers the compact layout path that renders board-loading failure state and the fallback system log entry.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.size(800.dp, 1200.dp)) {
                        HardwareScreen(backendApi = null)
                    }
                }
            }

            onNodeWithText("Unable to load boards").assertIsDisplayed()
            onNodeWithText("Could not scan serial devices:", substring = true).assertIsDisplayed()
        }
    }

    @Test
    fun `hardware screen shows generic serial guidance when suggestions exist but none are recommended`() {
        // Covers serialPortSupportTextFor else branch: detected ports without a recommended pick still get actionable copy.
        val backendApi = RecordingBackendApi(
            boards = listOf("CYTON_BOARD"),
            serialSuggestions = listOf(
                SerialPortSuggestion(path = "/dev/ttyS0", displayName = "Legacy", isRecommended = false),
                SerialPortSuggestion(path = "/dev/ttyUSB0", displayName = "USB", isUsbDevice = true, isRecommended = false)
            ),
            hardwareState = HardwareState()
        )

        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.size(1200.dp, 1200.dp)) {
                        HardwareScreen(backendApi = backendApi)
                    }
                }
            }

            onNodeWithText("Choose any detected port or type a custom path.").assertIsDisplayed()
        }
    }

    @Test
    fun `hardware screen renders wide layout with backend driven serial suggestions and logs`() {
        // Exercises the wide layout branch together with successful board and serial-port loading.
        val backendApi = RecordingBackendApi(
            boards = listOf("CYTON_BOARD"),
            serialSuggestions = listOf(
                SerialPortSuggestion(
                    path = "/dev/ttyACM0",
                    displayName = "Cyton Adapter",
                    isRecommended = true
                )
            ),
            hardwareState = HardwareState(channels = 2),
            logs = listOf(SystemLogEntry(1L, SystemLogSeverity.INFO, "Backend ready"))
        )

        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.size(1200.dp, 1200.dp)) {
                        HardwareScreen(backendApi = backendApi)
                    }
                }
            }

            onNodeWithText("A likely board connection was preselected for you. You can choose any detected port or type a custom path.").assertIsDisplayed()
            onNodeWithText("System Log").assertIsDisplayed()
            onNodeWithText("Connect").assertIsDisplayed()
        }
    }

    @Test
    fun `hardware screen disables connect when backend returns no boards`() {
        // Covers the no-board fallback path in the composed screen so connect remains guarded.
        val backendApi = RecordingBackendApi(
            boards = emptyList(),
            serialSuggestions = emptyList(),
            hardwareState = HardwareState()
        )

        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.size(1200.dp, 1200.dp)) {
                        HardwareScreen(backendApi = backendApi)
                    }
                }
            }

            onNodeWithText("Unable to load boards").assertIsDisplayed()
            onNodeWithText("Connect").assertIsNotEnabled()
        }
    }

    @Test
    fun `hardware screen synthetic board shows no serial required support and disabled stream controls`() {
        // Mirrors the screenshot state: synthetic board selected, no serial required text, and disconnected stream controls disabled.
        val backendApi = RecordingBackendApi(
            boards = listOf("CYTON_BOARD", "SYNTHETIC_BOARD"),
            serialSuggestions = emptyList(),
            hardwareState = HardwareState(connected = false, streaming = false, channels = 8)
        )

        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.size(1200.dp, 1200.dp)) {
                        HardwareScreen(backendApi = backendApi)
                    }
                }
            }

            onNodeWithText("Synthetic boards do not require a serial port.").assertIsDisplayed()
            onNodeWithText("No serial port required").assertIsDisplayed()
            onNodeWithText("Synthetic Control").assertIsDisplayed()
            onNodeWithText("Verify Channels").assertIsNotEnabled()
            onNodeWithText("Start Stream").assertIsNotEnabled()
            onNodeWithText("Stop Stream").assertIsNotEnabled()
        }
    }

    @Test
    fun `hardware screen compact layout updates serial guidance after changing boards`() {
        // Covers the compact DeviceSelectionCard board-selection callback and reruns serial-port loading when selectedBoardId changes.
        val backendApi = RecordingBackendApi(
            boards = listOf("SYNTHETIC_BOARD", "CYTON_BOARD"),
            serialSuggestions = listOf(
                SerialPortSuggestion(path = "/dev/ttyACM0", displayName = "Cyton Adapter", isRecommended = true)
            ),
            hardwareState = HardwareState(channels = 2)
        )

        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.size(800.dp, 1200.dp)) {
                        HardwareScreen(backendApi = backendApi)
                    }
                }
            }

            waitForIdle()
            onNodeWithText("Synthetic").performClick()
            onNodeWithText("Cyton").performClick()
            waitForIdle()

            onNodeWithText(
                "A likely board connection was preselected for you. You can choose any detected port or type a custom path."
            ).assertIsDisplayed()
            onNodeWithText("Synthetic boards do not require a serial port.").assertDoesNotExist()
        }

        assertEquals(1, backendApi.serialSuggestionRequests)
    }

    @Test
    fun `hardware screen forwards disconnect verify and stop actions to the backend`() {
        // Verifies the screen-level action lambdas dispatch to the backend when the hardware is already connected.
        val backendApi = RecordingBackendApi(
            boards = listOf("CYTON_BOARD"),
            serialSuggestions = listOf(SerialPortSuggestion(path = "/dev/ttyACM0", displayName = "Cyton Adapter")),
            hardwareState = HardwareState(connected = true, streaming = true, channels = 1)
        )

        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.size(1200.dp, 1200.dp)) {
                        HardwareScreen(backendApi = backendApi)
                    }
                }
            }

            onNodeWithText("Verify Channels").performClick()
            onNodeWithText("Stop Stream").performClick()
            onNodeWithText("Disconnect").performClick()
            waitForIdle()
        }

        assertEquals(1, backendApi.disconnectCalls)
        assertEquals(1, backendApi.verifyCalls)
        assertEquals(1, backendApi.stopStreamingCalls)
    }

    @Test
    fun `hardware screen compact layout refreshes serial ports edits the port and connects`() {
        // Exercises the compact-layout callbacks for refresh, manual serial editing, suggestion selection, timeout parsing, and connect.
        val backendApi = RecordingBackendApi(
            boards = listOf("CYTON_BOARD"),
            serialSuggestions = listOf(
                SerialPortSuggestion(
                    path = "/dev/ttyACM0",
                    displayName = "Primary Adapter",
                    isRecommended = true
                ),
                SerialPortSuggestion(
                    path = "/dev/ttyUSB0",
                    displayName = "Backup Adapter"
                )
            ),
            hardwareState = HardwareState(
                channels = 2,
                enabledChannels = listOf(1),
                rldEnabled = listOf(0)
            )
        )

        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.size(800.dp, 1200.dp)) {
                        HardwareScreen(backendApi = backendApi)
                    }
                }
            }

            onNodeWithText("Refresh Ports").performClick()
            onAllNodes(hasSetTextAction())[0].performTextClearance()
            onAllNodes(hasSetTextAction())[0].performTextInput("/dev/manual0")
            onNodeWithText("Recommended • /dev/ttyACM0 — Primary Adapter").performClick()
            onNodeWithText("/dev/ttyUSB0 — Backup Adapter").performClick()
            onAllNodes(hasSetTextAction())[1].performTextClearance()
            onAllNodes(hasSetTextAction())[1].performTextInput("9")
            onNodeWithText("Connect").performClick()
            waitForIdle()
        }

        assertEquals(2, backendApi.serialSuggestionRequests)
        assertEquals(listOf(ConnectCall("CYTON_BOARD", "/dev/ttyUSB0", 9)), backendApi.connectCalls)
    }

    @Test
    fun `hardware screen compact layout starts streaming when connected`() {
        // Exercises the compact BoxWithConstraints branch so startStreaming reaches the backend while narrow layouts stay covered.
        val backendApi = RecordingBackendApi(
            boards = listOf("CYTON_BOARD"),
            serialSuggestions = listOf(
                SerialPortSuggestion(path = "/dev/ttyACM0", displayName = "Adapter", isRecommended = true)
            ),
            hardwareState = HardwareState(connected = true, streaming = false, channels = 2)
        )

        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.size(800.dp, 1200.dp)) {
                        HardwareScreen(backendApi = backendApi)
                    }
                }
            }

            waitForIdle()
            onNodeWithText("Start Stream").performScrollTo()
            waitForIdle()
            onNodeWithText("Start Stream").performClick()
            waitForIdle()
        }

        assertEquals(1, backendApi.startStreamingCalls)
    }

    @Test
    fun `hardware screen wide layout starts streaming and toggles both channel columns`() {
        // Covers the backend-action lambdas for start-stream plus both enable/disable branches of channel and RLD toggles.
        val backendApi = RecordingBackendApi(
            boards = listOf("CYTON_BOARD"),
            serialSuggestions = listOf(
                SerialPortSuggestion(path = "/dev/ttyACM0", displayName = "Primary Adapter", isRecommended = true)
            ),
            hardwareState = HardwareState(
                connected = true,
                channels = 2,
                enabledChannels = listOf(1),
                rldEnabled = listOf(0)
            )
        )

        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.size(1200.dp, 1200.dp)) {
                        HardwareScreen(backendApi = backendApi)
                    }
                }
            }

            onNodeWithText("Start Stream").performClick()
            onAllNodes(isToggleable())[0].performClick()
            onAllNodes(isToggleable())[1].performClick()
            onAllNodes(isToggleable())[2].performClick()
            onAllNodes(isToggleable())[3].performClick()
            waitForIdle()
        }

        assertEquals(1, backendApi.startStreamingCalls)
        assertEquals(listOf(0), backendApi.enableChannelCalls)
        assertEquals(listOf(1), backendApi.disableChannelCalls)
        assertEquals(listOf(1), backendApi.enableRldCalls)
        assertEquals(listOf(0), backendApi.disableRldCalls)
    }

    @Test
    fun `hardware screen surfaces serial scan failures while boards remain selectable`() {
        // Covers the LaunchedEffect serial-loader failure branch so the composed screen shows the manual-entry hint instead of silently dropping errors.
        val backendApi = RecordingBackendApi(
            boards = listOf("CYTON_BOARD"),
            serialSuggestions = emptyList(),
            serialFailure = IllegalStateException("USB lookup blocked"),
            hardwareState = HardwareState(channels = 1)
        )

        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.size(1200.dp, 1200.dp)) {
                        HardwareScreen(backendApi = backendApi)
                    }
                }
            }

            onNodeWithText("Could not scan serial devices: USB lookup blocked. You can still enter a path manually.", substring = true)
                .assertIsDisplayed()
            onNodeWithText("Device Selection").assertIsDisplayed()
        }
    }

    @Test
    fun `hardware screen compact layout surfaces serial scan failures`() {
        // Mirrors the wide-layout serial failure test under the narrow maxWidth branch so both layout shells exercise the same error copy path.
        val backendApi = RecordingBackendApi(
            boards = listOf("CYTON_BOARD"),
            serialSuggestions = emptyList(),
            serialFailure = IllegalStateException("USB lookup blocked"),
            hardwareState = HardwareState(channels = 1)
        )

        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.size(800.dp, 1200.dp)) {
                        HardwareScreen(backendApi = backendApi)
                    }
                }
            }

            waitForIdle()
            onNodeWithText("Could not scan serial devices: USB lookup blocked. You can still enter a path manually.", substring = true)
                .assertIsDisplayed()
        }
    }

    @Test
    fun `hardware screen shows board catalog error when getBrainflowBoards throws`() {
        // Covers loadBoardState onFailure inside the board LaunchedEffect so the composed screen surfaces backend exceptions like the helper tests.
        val backendApi = RecordingBackendApi(
            boards = listOf("CYTON_BOARD"),
            serialSuggestions = emptyList(),
            hardwareState = HardwareState(),
            boardLoadFailure = IllegalStateException("Board service offline")
        )

        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.size(1200.dp, 1200.dp)) {
                        HardwareScreen(backendApi = backendApi)
                    }
                }
            }

            waitForIdle()
            onNodeWithText("Unable to load boards").assertIsDisplayed()
        }
    }

    @Test
    fun `hardware screen treats no board placeholder rows as an empty board catalog`() {
        // Covers loadBoardState filtering so a NO_BOARD-only response collapses to the same empty-board UX as a silent backend failure.
        val backendApi = RecordingBackendApi(
            boards = listOf("NO_BOARD"),
            serialSuggestions = emptyList(),
            hardwareState = HardwareState()
        )

        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.size(1200.dp, 1200.dp)) {
                        HardwareScreen(backendApi = backendApi)
                    }
                }
            }

            waitForIdle()
            onNodeWithText("Unable to load boards").assertIsDisplayed()
            onNodeWithText("Connect").assertIsNotEnabled()
        }
    }

    @Test
    fun `hardware screen wide layout lets users edit and reselect serial ports before connecting`() {
        // Covers the duplicated wide-layout serial editing and suggestion-selection branches before issuing a connect action.
        val backendApi = RecordingBackendApi(
            boards = listOf("CYTON_BOARD"),
            serialSuggestions = listOf(
                SerialPortSuggestion(
                    path = "/dev/ttyACM0",
                    displayName = "Primary Adapter",
                    isRecommended = true
                ),
                SerialPortSuggestion(
                    path = "/dev/ttyUSB0",
                    displayName = "Backup Adapter"
                )
            ),
            hardwareState = HardwareState(channels = 1)
        )

        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.size(1200.dp, 1200.dp)) {
                        HardwareScreen(backendApi = backendApi)
                    }
                }
            }

            onAllNodes(hasSetTextAction())[0].performTextClearance()
            onAllNodes(hasSetTextAction())[0].performTextInput("/dev/custom-wide")
            onNodeWithText("Recommended • /dev/ttyACM0 — Primary Adapter").performClick()
            onNodeWithText("/dev/ttyUSB0 — Backup Adapter").performClick()
            onNodeWithText("Connect").performClick()
            waitForIdle()
        }

        assertEquals(listOf(ConnectCall("CYTON_BOARD", "/dev/ttyUSB0", 0)), backendApi.connectCalls)
    }

    @Test
    fun `hardware screen wide layout updates serial guidance after changing boards`() {
        // Covers the wide DeviceSelectionCard board-selection callback and verifies board changes trigger a fresh serial-port load.
        val backendApi = RecordingBackendApi(
            boards = listOf("SYNTHETIC_BOARD", "CYTON_BOARD"),
            serialSuggestions = listOf(
                SerialPortSuggestion(path = "/dev/ttyACM0", displayName = "Cyton Adapter", isRecommended = true)
            ),
            hardwareState = HardwareState(channels = 1)
        )

        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.size(1200.dp, 1200.dp)) {
                        HardwareScreen(backendApi = backendApi)
                    }
                }
            }

            waitForIdle()
            onNodeWithText("Synthetic").performClick()
            onNodeWithText("Cyton").performClick()
            waitForIdle()

            onNodeWithText(
                "A likely board connection was preselected for you. You can choose any detected port or type a custom path."
            ).assertIsDisplayed()
            onNodeWithText("Synthetic boards do not require a serial port.").assertDoesNotExist()
        }

        assertEquals(1, backendApi.serialSuggestionRequests)
    }

    @Test
    fun `hardware screen uses compact column layout when max width is just below nine hundred dp`() {
        val backendApi = RecordingBackendApi(
            boards = listOf("CYTON_BOARD"),
            serialSuggestions = listOf(
                SerialPortSuggestion(path = "/dev/ttyACM0", displayName = "Adapter", isRecommended = true)
            ),
            hardwareState = HardwareState(channels = 1)
        )
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.size(899.dp, 900.dp)) {
                        HardwareScreen(backendApi = backendApi)
                    }
                }
            }
            waitForIdle()
            onNodeWithText("Device Selection").assertIsDisplayed()
        }
    }

    @Test
    fun `hardware screen uses wide row layout when max width reaches nine hundred dp`() {
        val backendApi = RecordingBackendApi(
            boards = listOf("CYTON_BOARD"),
            serialSuggestions = listOf(
                SerialPortSuggestion(path = "/dev/ttyACM0", displayName = "Adapter", isRecommended = true)
            ),
            hardwareState = HardwareState(channels = 1)
        )
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.size(900.dp, 900.dp)) {
                        HardwareScreen(backendApi = backendApi)
                    }
                }
            }
            waitForIdle()
            onNodeWithText("Device Selection").assertIsDisplayed()
        }
    }

    @Test
    fun `hardware screen compact layout forwards verify channels while connected`() {
        val backendApi = RecordingBackendApi(
            boards = listOf("CYTON_BOARD"),
            serialSuggestions = listOf(
                SerialPortSuggestion(path = "/dev/ttyACM0", displayName = "Adapter", isRecommended = true)
            ),
            hardwareState = HardwareState(connected = true, streaming = false, channels = 1)
        )

        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.size(800.dp, 1200.dp)) {
                        HardwareScreen(backendApi = backendApi)
                    }
                }
            }

            waitForIdle()
            onNodeWithText("Verify Channels").performScrollTo().performClick()
            waitForIdle()
        }

        assertEquals(1, backendApi.verifyCalls)
    }

    @Test
    fun `hardware screen compact layout shows synthetic serial guidance when synthetic board is default`() {
        val backendApi = RecordingBackendApi(
            boards = listOf("CYTON_BOARD", "SYNTHETIC_BOARD"),
            serialSuggestions = emptyList(),
            hardwareState = HardwareState(connected = false, streaming = false, channels = 2)
        )

        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.size(800.dp, 1200.dp)) {
                        HardwareScreen(backendApi = backendApi)
                    }
                }
            }

            waitForIdle()
            onNodeWithText("Synthetic boards do not require a serial port.").assertIsDisplayed()
        }
    }

    @Test
    fun `hardware screen connect passes zero seconds when timeout field is not numeric`() {
        // Exercises connectRequestFor(timeout.toIntOrNull() ?: 0) through the composed wide layout path.
        val backendApi = RecordingBackendApi(
            boards = listOf("CYTON_BOARD"),
            serialSuggestions = listOf(
                SerialPortSuggestion(path = "/dev/ttyACM0", displayName = "Adapter", isRecommended = true)
            ),
            hardwareState = HardwareState(channels = 1)
        )

        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.size(1200.dp, 1200.dp)) {
                        HardwareScreen(backendApi = backendApi)
                    }
                }
            }

            waitForIdle()
            onAllNodes(hasSetTextAction())[1].performTextClearance()
            onAllNodes(hasSetTextAction())[1].performTextInput("not-a-number")
            onNodeWithText("Connect").performClick()
            waitForIdle()
        }

        assertEquals(listOf(ConnectCall("CYTON_BOARD", "/dev/ttyACM0", 0)), backendApi.connectCalls)
    }
}

/** Records the board, serial port, and timeout supplied to a HardwareScreen connect action. */
private data class ConnectCall(
    val boardId: String,
    val serialPort: String,
    val timeoutSeconds: Int
)

/**
 * Recording BackendApi fake that keeps HardwareScreen UI tests deterministic while capturing invoked actions.
 */
private class RecordingBackendApi(
    private val boards: List<String>,
    private val serialSuggestions: List<SerialPortSuggestion>,
    private val serialFailure: Throwable? = null,
    private val boardLoadFailure: Throwable? = null,
    hardwareState: HardwareState = HardwareState(),
    logs: List<SystemLogEntry> = emptyList()
) : BackendApi {
    private val mutableHardwareStateFlow = MutableStateFlow(hardwareState)

    var connectCalls: List<ConnectCall> = emptyList()
        private set
    var disconnectCalls: Int = 0
        private set
    var verifyCalls: Int = 0
        private set
    var startStreamingCalls: Int = 0
        private set
    var stopStreamingCalls: Int = 0
        private set
    var enableChannelCalls: List<Int> = emptyList()
        private set
    var disableChannelCalls: List<Int> = emptyList()
        private set
    var enableRldCalls: List<Int> = emptyList()
        private set
    var disableRldCalls: List<Int> = emptyList()
        private set
    var serialSuggestionRequests: Int = 0
        private set

    override suspend fun connect(boardId: String, serialPort: String, timeoutSeconds: Int): Boolean {
        connectCalls = connectCalls + ConnectCall(boardId, serialPort, timeoutSeconds)
        mutableHardwareStateFlow.value = mutableHardwareStateFlow.value.copy(connected = true)
        return true
    }
    override suspend fun disconnect(): Boolean {
        disconnectCalls += 1
        mutableHardwareStateFlow.value = mutableHardwareStateFlow.value.copy(connected = false, streaming = false)
        return true
    }
    override suspend fun addWave(wave: WaveSpec): Boolean = true
    override suspend fun removeWave(waveIndex: Int): Boolean = true
    override suspend fun editWave(waveIndex: Int, wave: WaveSpec): Boolean = true
    override suspend fun startStreaming(): Boolean {
        startStreamingCalls += 1
        mutableHardwareStateFlow.value = mutableHardwareStateFlow.value.copy(streaming = true)
        return true
    }
    override suspend fun stopStreaming(): Boolean {
        stopStreamingCalls += 1
        mutableHardwareStateFlow.value = mutableHardwareStateFlow.value.copy(streaming = false)
        return true
    }
    override suspend fun enableChannel(channelId: Int): Boolean {
        enableChannelCalls = enableChannelCalls + channelId
        mutableHardwareStateFlow.value = mutableHardwareStateFlow.value.copy(
            enabledChannels = (mutableHardwareStateFlow.value.enabledChannels + channelId).distinct().sorted()
        )
        return true
    }
    override suspend fun disableChannel(channelId: Int): Boolean {
        disableChannelCalls = disableChannelCalls + channelId
        mutableHardwareStateFlow.value = mutableHardwareStateFlow.value.copy(
            enabledChannels = mutableHardwareStateFlow.value.enabledChannels.filterNot { it == channelId }
        )
        return true
    }
    override suspend fun enableRLD(channelId: Int): Boolean {
        enableRldCalls = enableRldCalls + channelId
        mutableHardwareStateFlow.value = mutableHardwareStateFlow.value.copy(
            rldEnabled = (mutableHardwareStateFlow.value.rldEnabled + channelId).distinct().sorted()
        )
        return true
    }
    override suspend fun disableRLD(channelId: Int): Boolean {
        disableRldCalls = disableRldCalls + channelId
        mutableHardwareStateFlow.value = mutableHardwareStateFlow.value.copy(
            rldEnabled = mutableHardwareStateFlow.value.rldEnabled.filterNot { it == channelId }
        )
        return true
    }
    override suspend fun verifyChannels(): Boolean {
        verifyCalls += 1
        return true
    }
    override suspend fun setSamplingRateHz(rate: Int): Boolean = true
    override fun getState(): HardwareState = hardwareStateFlow.value
    override fun getBrainflowBoards(): List<String> {
        boardLoadFailure?.let { throw it }
        return boards
    }
    override fun getSerialPortSuggestions(boardId: String?): List<SerialPortSuggestion> {
        serialSuggestionRequests += 1
        serialFailure?.let { throw it }
        return serialSuggestions
    }
    override val hardwareStateFlow: StateFlow<HardwareState> = mutableHardwareStateFlow
    override val systemLogFlow: StateFlow<List<SystemLogEntry>> = MutableStateFlow(logs)
    override val filteredFlow: Flow<ChannelData<DoubleArray>> = emptyFlow()
    override val bandPowersFlow: Flow<ChannelData<List<BandPower>>> = emptyFlow()
    override val fftResultFlow: Flow<ChannelData<Array<Pair<Double, Double>>>> = emptyFlow()
    override fun setOnFilteredListener(listener: ((DoubleArray) -> Unit)?) = Unit
    override fun setOnBandPowersListener(listener: ((List<BandPower>) -> Unit)?) = Unit
    override fun setOnFFTResultListener(listener: ((Array<Pair<Double, Double>>) -> Unit)?) = Unit
}
