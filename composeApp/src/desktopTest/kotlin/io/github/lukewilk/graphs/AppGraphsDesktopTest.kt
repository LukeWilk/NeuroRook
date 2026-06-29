package io.github.lukewilk.graphs

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.github.lukewilk.App
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.api.BackendApi
import io.github.lukewilk.shared.model.BandPower
import io.github.lukewilk.shared.model.ChannelData
import io.github.lukewilk.shared.model.SerialPortSuggestion
import io.github.lukewilk.shared.model.SystemLogEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test

/**
 * Desktop host behavior tests focused specifically on the Graphs feature.
 */
@OptIn(ExperimentalTestApi::class)
class AppGraphsDesktopTest {
    @Test
    fun `app shows the configurable graphs page when the graphs tab is selected`() = runComposeUiTest {
        // Verifies the desktop host routes Graphs to a configurable screen with filter controls instead of the generic placeholder surface.
        setContent {
            App(backendApi = null)
        }

        onNodeWithText("Graphs").performClick()
        onNodeWithText("Graph Configuration").assertIsDisplayed()
        onNodeWithContentDescription("Hide configuration").assertIsDisplayed()
        onNodeWithText("Channels").assertIsDisplayed()
        onNodeWithText("Enable at least one hardware channel on the Hardware page to configure graph visibility.").assertIsDisplayed()
        onNodeWithText("Enable at least one hardware channel on the Hardware page to show graphs.").assertIsDisplayed()
        onNodeWithText("Graphs screen coming soon...").assertDoesNotExist()
    }

    @Test
    fun `app graphs page uses the shared hardware state from the backend store`() = runComposeUiTest {
        // Verifies the app host passes the same backend-backed hardware state into Graphs instead of the empty fallback state.
        val backend = FakeAppGraphsBackendApi(
            hardwareState = HardwareState(
                channels = 2,
                enabledChannels = listOf(0, 1)
            )
        ).apply {
            filteredFlowMutable.tryEmit(ChannelData(channelId = 0, payload = doubleArrayOf(0.25, -0.1, 0.5)))
        }

        setContent {
            App(backendApi = backend)
        }

        onNodeWithText("Graphs").performClick()
        waitForIdle()

        onNodeWithText("Channel 1").assertIsDisplayed()
        onNodeWithText("Channel 2").assertIsDisplayed()
        onNodeWithText("Enable at least one hardware channel on the Hardware page to configure graph visibility.").assertDoesNotExist()
    }
}

/** Tiny backend fake that lets the desktop app test verify shared hardware-to-graphs state propagation. */
private class FakeAppGraphsBackendApi(
    hardwareState: HardwareState
) : BackendApi {
    private val hardwareStateMutable = MutableStateFlow(hardwareState)
    val filteredFlowMutable = MutableSharedFlow<ChannelData<DoubleArray>>(replay = 8, extraBufferCapacity = 1)
    private val bandPowersFlowMutable = MutableSharedFlow<ChannelData<List<BandPower>>>(replay = 8, extraBufferCapacity = 1)
    private val fftResultFlowMutable = MutableSharedFlow<ChannelData<Array<Pair<Double, Double>>>>(replay = 8, extraBufferCapacity = 1)

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
    override fun getState(): HardwareState = hardwareStateMutable.value
    override suspend fun getBrainflowBoards(): List<String> = emptyList()
    override suspend fun getSerialPortSuggestions(boardId: String?): List<SerialPortSuggestion> = emptyList()
    override val hardwareStateFlow: StateFlow<HardwareState> = hardwareStateMutable
    override val systemLogFlow: StateFlow<List<SystemLogEntry>> = MutableStateFlow(emptyList())
    override val filteredFlow: Flow<ChannelData<DoubleArray>> = filteredFlowMutable
    override val bandPowersFlow: Flow<ChannelData<List<BandPower>>> = bandPowersFlowMutable
    override val fftResultFlow: Flow<ChannelData<Array<Pair<Double, Double>>>> = fftResultFlowMutable
    override fun setOnFilteredListener(listener: ((DoubleArray) -> Unit)?) = Unit
    override fun setOnBandPowersListener(listener: ((List<BandPower>) -> Unit)?) = Unit
    override fun setOnFFTResultListener(listener: ((Array<Pair<Double, Double>>) -> Unit)?) = Unit
}




