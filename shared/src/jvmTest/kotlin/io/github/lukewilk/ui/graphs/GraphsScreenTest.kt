package io.github.lukewilk.ui.graphs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.api.BackendApi
import io.github.lukewilk.shared.model.BandPower
import io.github.lukewilk.shared.model.ChannelData
import io.github.lukewilk.shared.model.SerialPortSuggestion
import io.github.lukewilk.shared.model.SystemLogEntry
import io.github.lukewilk.ui.MainScaffold
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test

/**
 * Focused Compose tests for the standalone Graphs screen.
 */
@OptIn(ExperimentalTestApi::class)
class GraphsScreenTest {
    @Test
    fun `graphs screen shows the configuration card and waiting state before data arrives`() = runComposeUiTest {
        // Verifies the first Graphs revision exposes a collapsible configuration area even before any stream data has been received.
        setContent {
            MaterialTheme {
                GraphsScreen()
            }
        }

        onNodeWithText(GRAPHS_PAGE_TITLE).assertIsDisplayed()
        onNodeWithText(GRAPHS_CONFIGURATION_TITLE).assertIsDisplayed()
        onNodeWithContentDescription(GRAPHS_CONFIGURATION_COLLAPSE_TEXT).assertIsDisplayed()
        onNodeWithText(GRAPHS_CHANNELS_SECTION_TITLE).assertIsDisplayed()
        onNodeWithText(GRAPHS_ENABLE_CHANNELS_CONFIGURATION_MESSAGE).assertIsDisplayed()
        onNodeWithText(GRAPHS_ENABLE_CHANNELS_GRAPH_MESSAGE).assertIsDisplayed()
    }

    @Test
    fun `graphs screen collapse toggle hides configuration sections and can reopen them`() = runComposeUiTest {
        // Verifies the top configuration card collapses to a compact summary and restores the section lists when reopened.
        setContent {
            MaterialTheme {
                GraphsScreen()
            }
        }

        onNodeWithContentDescription(GRAPHS_CONFIGURATION_COLLAPSE_TEXT).performClick()
        onNodeWithContentDescription(GRAPHS_CONFIGURATION_EXPAND_TEXT).assertIsDisplayed()
        onNodeWithText(GRAPHS_CHANNELS_SECTION_TITLE).assertDoesNotExist()
        onNodeWithText("0 channels selected • 0 data sets selected").assertIsDisplayed()

        onNodeWithContentDescription(GRAPHS_CONFIGURATION_EXPAND_TEXT).performClick()
        onNodeWithText(GRAPHS_CHANNELS_SECTION_TITLE).assertIsDisplayed()
        onNodeWithText(GRAPHS_ENABLE_CHANNELS_CONFIGURATION_MESSAGE).assertIsDisplayed()
    }

    @Test
    fun `graphs screen lets users filter visible graph cards by matrix toggles`() = runComposeUiTest {
        // Verifies the compact matrix exposes row/column bulk controls and still filters rendered graph cards through per-cell toggles.
        val backend = FakeGraphsBackendApi(
            hardwareState = HardwareState(
                channels = 2,
                enabledChannels = listOf(0, 1)
            )
        ).apply {
            filteredFlowMutable.tryEmit(ChannelData(channelId = 0, payload = doubleArrayOf(0.2, -0.4, 0.8)))
            filteredFlowMutable.tryEmit(ChannelData(channelId = 1, payload = doubleArrayOf(0.1, 0.3, 0.6)))
            bandPowersFlowMutable.tryEmit(
                ChannelData(
                    channelId = 1,
                    payload = listOf(BandPower(name = "Alpha", power = 2.4))
                )
            )
        }

        setContent {
            MaterialTheme {
                GraphsScreen(backendApi = backend)
            }
        }

        waitForIdle()

        onNodeWithText(GRAPHS_MATRIX_CHANNEL_HEADER).assertIsDisplayed()
        onNodeWithText(GraphDataSetType.FilteredSignal.compactLabel).assertIsDisplayed()
        onNodeWithText(GraphDataSetType.BandPowers.compactLabel).assertIsDisplayed()
        onNodeWithContentDescription(graphsChannelBulkToggleContentDescription("Channel 2")).assertIsDisplayed()
        onNodeWithContentDescription(
            graphsDataSetBulkToggleContentDescription(GraphDataSetType.BandPowers.label)
        ).assertIsDisplayed()
        onNodeWithText("Channel 1 • Filtered Signal").assertIsDisplayed()
        onNodeWithText("Channel 2 • Filtered Signal").assertIsDisplayed()
        onNodeWithContentDescription(renderedGraphContentDescription("Channel 1 • Filtered Signal")).assertIsDisplayed()

        onNodeWithContentDescription(
            graphsMatrixToggleContentDescription("Channel 2", GraphDataSetType.BandPowers.label)
        ).performClick()
        waitForIdle()
        onNodeWithText("Channel 2 • Band Powers").assertDoesNotExist()
        onNodeWithText("Channel 2 • Filtered Signal").assertIsDisplayed()
        onNodeWithText("Channel 1 • Filtered Signal").assertIsDisplayed()

        onNodeWithContentDescription(
            graphsMatrixToggleContentDescription("Channel 2", GraphDataSetType.FilteredSignal.label)
        ).performClick()
        waitForIdle()
        onNodeWithText("Channel 2 • Filtered Signal").assertDoesNotExist()
        onNodeWithText("Channel 1 • Filtered Signal").assertIsDisplayed()
    }

    @Test
    fun `graphs screen hides disabled hardware channels from the graph matrix`() = runComposeUiTest {
        // Verifies Graphs respects Hardware page enablement by omitting disabled channels from the visible matrix entirely.
        val backend = FakeGraphsBackendApi(
            hardwareState = HardwareState(
                channels = 2,
                enabledChannels = listOf(0)
            )
        ).apply {
            filteredFlowMutable.tryEmit(ChannelData(channelId = 0, payload = doubleArrayOf(0.2, -0.4, 0.8)))
            filteredFlowMutable.tryEmit(ChannelData(channelId = 1, payload = doubleArrayOf(0.1, 0.3, 0.6)))
        }

        setContent {
            MaterialTheme {
                GraphsScreen(backendApi = backend)
            }
        }

        waitForIdle()

        onNodeWithText("Channel 1").assertIsDisplayed()
        onNodeWithText("Channel 2").assertDoesNotExist()
        onNodeWithContentDescription(graphsChannelBulkToggleContentDescription("Channel 2")).assertDoesNotExist()
        onNodeWithContentDescription(
            graphsMatrixToggleContentDescription("Channel 2", GraphDataSetType.FilteredSignal.label)
        ).assertDoesNotExist()
        onNodeWithText("Channel 1 • Filtered Signal").assertIsDisplayed()
        onNodeWithText("Channel 2 • Filtered Signal").assertDoesNotExist()
    }

    @Test
    fun `graphs destination keeps custom scaffold header visible when selected`() = runComposeUiTest {
        // Verifies the shared shell can route into Graphs without hiding caller-provided header content or falling back to placeholder text.
        setContent {
            MaterialTheme {
                MainScaffold(
                    hardwareScreen = {
                        androidx.compose.material3.Text("Hardware screen content")
                    },
                    headerContent = {
                        androidx.compose.material3.Text("Branded Shell Header")
                    }
                )
            }
        }

        onNodeWithText("Neuro Rook").assertDoesNotExist()
        onNodeWithText("Branded Shell Header").assertIsDisplayed()
        onNodeWithText("Graphs").performClick()
        onNodeWithText(GRAPHS_CONFIGURATION_TITLE).assertIsDisplayed()
        onNodeWithText(GRAPHS_ENABLE_CHANNELS_CONFIGURATION_MESSAGE).assertIsDisplayed()
        onNodeWithContentDescription(GRAPHS_CONFIGURATION_COLLAPSE_TEXT).assertIsDisplayed()
        onNodeWithText("Neuro Rook").assertDoesNotExist()
        onNodeWithText("Branded Shell Header").assertIsDisplayed()
        onNodeWithText("Hardware screen content").assertDoesNotExist()
    }
}

/** Tiny backend fake that publishes replayed graph payloads without depending on hardware runtime state. */
private class FakeGraphsBackendApi(
    hardwareState: HardwareState
) : BackendApi {
    private val hardwareStateMutable = MutableStateFlow(hardwareState)
    // Keeps a small replay window so tests can seed multiple channels before the UI starts collecting the flows.
    val filteredFlowMutable = MutableSharedFlow<ChannelData<DoubleArray>>(replay = 8, extraBufferCapacity = 1)
    val bandPowersFlowMutable = MutableSharedFlow<ChannelData<List<BandPower>>>(replay = 8, extraBufferCapacity = 1)
    val fftResultFlowMutable = MutableSharedFlow<ChannelData<Array<Pair<Double, Double>>>>(replay = 8, extraBufferCapacity = 1)

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
    override fun getBrainflowBoards(): List<String> = emptyList()
    override fun getSerialPortSuggestions(boardId: String?): List<SerialPortSuggestion> = emptyList()
    override val hardwareStateFlow: StateFlow<HardwareState> = hardwareStateMutable
    override val systemLogFlow: StateFlow<List<SystemLogEntry>> = MutableStateFlow(emptyList())
    override val filteredFlow: Flow<ChannelData<DoubleArray>> = filteredFlowMutable
    override val bandPowersFlow: Flow<ChannelData<List<BandPower>>> = bandPowersFlowMutable
    override val fftResultFlow: Flow<ChannelData<Array<Pair<Double, Double>>>> = fftResultFlowMutable
    override fun setOnFilteredListener(listener: ((DoubleArray) -> Unit)?) = Unit
    override fun setOnBandPowersListener(listener: ((List<BandPower>) -> Unit)?) = Unit
    override fun setOnFFTResultListener(listener: ((Array<Pair<Double, Double>>) -> Unit)?) = Unit
}










