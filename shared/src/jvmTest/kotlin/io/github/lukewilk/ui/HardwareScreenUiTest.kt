package io.github.lukewilk.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.api.BackendApi
import io.github.lukewilk.shared.model.BandPower
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

            onNodeWithText("Disconnect").performClick()
            onNodeWithText("Verify Channels").performClick()
            onNodeWithText("Stop Stream").performClick()
            waitForIdle()
        }

        assertEquals(1, backendApi.disconnectCalls)
        assertEquals(1, backendApi.verifyCalls)
        assertEquals(1, backendApi.stopStreamingCalls)
    }
}

/**
 * Recording BackendApi fake that keeps HardwareScreen UI tests deterministic while capturing invoked actions.
 */
private class RecordingBackendApi(
    private val boards: List<String>,
    private val serialSuggestions: List<SerialPortSuggestion>,
    hardwareState: HardwareState = HardwareState(),
    logs: List<SystemLogEntry> = emptyList()
) : BackendApi {
    var disconnectCalls: Int = 0
        private set
    var verifyCalls: Int = 0
        private set
    var stopStreamingCalls: Int = 0
        private set

    override suspend fun connect(boardId: String, serialPort: String, timeoutSeconds: Int): Boolean = true
    override suspend fun disconnect(): Boolean {
        disconnectCalls += 1
        return true
    }
    override suspend fun addWave(wave: WaveSpec): Boolean = true
    override suspend fun removeWave(waveIndex: Int): Boolean = true
    override suspend fun editWave(waveIndex: Int, wave: WaveSpec): Boolean = true
    override suspend fun startStreaming(): Boolean = true
    override suspend fun stopStreaming(): Boolean {
        stopStreamingCalls += 1
        return true
    }
    override suspend fun enableChannel(channelId: Int): Boolean = true
    override suspend fun disableChannel(channelId: Int): Boolean = true
    override suspend fun enableRLD(channelId: Int): Boolean = true
    override suspend fun disableRLD(channelId: Int): Boolean = true
    override suspend fun verifyChannels(): Boolean {
        verifyCalls += 1
        return true
    }
    override suspend fun setSamplingRateHz(rate: Int): Boolean = true
    override fun getState(): HardwareState = hardwareStateFlow.value
    override fun getBrainflowBoards(): List<String> = boards
    override fun getSerialPortSuggestions(boardId: String?): List<SerialPortSuggestion> = serialSuggestions
    override val hardwareStateFlow: StateFlow<HardwareState> = MutableStateFlow(hardwareState)
    override val systemLogFlow: StateFlow<List<SystemLogEntry>> = MutableStateFlow(logs)
    override val filteredFlow: Flow<DoubleArray> = emptyFlow()
    override val bandPowersFlow: Flow<List<BandPower>> = emptyFlow()
    override val fftResultFlow: Flow<Array<Pair<Double, Double>>> = emptyFlow()
    override fun setOnFilteredListener(listener: ((DoubleArray) -> Unit)?) = Unit
    override fun setOnBandPowersListener(listener: ((List<BandPower>) -> Unit)?) = Unit
    override fun setOnFFTResultListener(listener: ((Array<Pair<Double, Double>>) -> Unit)?) = Unit
}

