package io.github.lukewilk

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.api.BackendApi
import io.github.lukewilk.shared.model.BandPower
import io.github.lukewilk.shared.model.SerialPortSuggestion
import io.github.lukewilk.shared.model.SystemLogEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.StateFlow
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Window
import java.awt.event.WindowEvent
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import org.junit.Assume.assumeTrue
/**
 * Desktop smoke tests for the shared NeuroRook root composable.
 */
@OptIn(ExperimentalTestApi::class)
class AppSmokeTest {
    @Test
    fun `app uses the default null backend parameter when callers omit it`() = runComposeUiTest {
        // Covers the default-argument entry path so desktop callers can render the app without supplying a backend.
        setContent {
            App()
        }
        onNodeWithText("Neuro Rook").assertIsDisplayed()
        onNodeWithText("Hardware").assertIsDisplayed()
    }

    @Test
    fun `app renders the shared scaffold header and default hardware tab`() = runComposeUiTest {
        // Verifies the desktop host can render the shared root UI without a backend implementation.
        setContent {
            App(backendApi = null)
        }
        onNodeWithText("Neuro Rook").assertIsDisplayed()
        onNodeWithText("Hardware").assertIsDisplayed()
    }

    @Test
    fun `app renders the shared scaffold with an explicit backend instance`() = runComposeUiTest {
        // Covers the non-null backend path so the root app composable is exercised with both injected and default host wiring.
        setContent {
            App(backendApi = FakeAppBackendApi())
        }
        onNodeWithText("Neuro Rook").assertIsDisplayed()
        onNodeWithText("Hardware").assertIsDisplayed()
    }

    @Test
    fun `app shows placeholder content when switching away from hardware`() = runComposeUiTest {
        // Confirms the shared navigation shell responds to tab clicks in the desktop host.
        setContent {
            App(backendApi = null)
        }
        onNodeWithText("Protocols").performClick()
        onNodeWithText("Protocols screen coming soon...").assertIsDisplayed()
    }

    @Test
    fun `app shell workflow can switch sections collapse and return to hardware`() = runComposeUiTest {
        // Covers the root app shell as a real navigation workflow instead of isolated one-step renders.
        setContent {
            App(backendApi = null)
        }

        onNodeWithText("Signals").performClick()
        onNodeWithText("Signals screen coming soon...").assertIsDisplayed()
        onNodeWithText("≡").performClick()
        onNodeWithText("Signals").assertDoesNotExist()
        onNodeWithText("≡").performClick()
        onNodeWithText("Hardware").performClick()
        onNodeWithText("Device Selection").assertIsDisplayed()
    }

    @Test
    fun `desktop main launches and closes the real application window`() {
        // Verifies the desktop entrypoint can create its real window shell and respond to a close request.
        assumeTrue(
            "Real desktop window smoke tests require a non-headless environment with a display server.",
            canShowRealDesktopWindow()
        )

        val mainThread = thread(start = true, isDaemon = true, name = "desktop-main-test") {
            main()
        }

        val window = waitForDesktopWindow(title = "NeuroRook")
        assertNotNull(window, "Expected the desktop main entrypoint to open a visible NeuroRook window")

        try {
            java.awt.EventQueue.invokeAndWait {
                window.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING))
            }
            mainThread.join(10_000)
            assertFalse(mainThread.isAlive, "Expected the desktop main thread to exit after the window close request")
        } finally {
            java.awt.EventQueue.invokeLater {
                Window.getWindows().forEach { existingWindow ->
                    runCatching { existingWindow.dispose() }
                }
            }
        }
    }

    /** Detects whether this process can actually show a real AWT/Compose desktop window. */
    private fun canShowRealDesktopWindow(): Boolean {
        if (GraphicsEnvironment.isHeadless()) {
            return false
        }

        val isLinux = System.getProperty("os.name")?.contains("Linux", ignoreCase = true) == true
        if (!isLinux) {
            return true
        }

        return !System.getenv("DISPLAY").isNullOrBlank() || !System.getenv("WAYLAND_DISPLAY").isNullOrBlank()
    }

    /** Waits for the real desktop entrypoint to publish a visible top-level window with the requested title. */
    private fun waitForDesktopWindow(title: String, timeoutMs: Long = 10_000): Frame? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val matchingWindow = Window.getWindows()
                .filterIsInstance<Frame>()
                .firstOrNull { it.isShowing && it.title == title }
            if (matchingWindow != null) {
                return matchingWindow
            }
            Thread.sleep(100)
        }
        return null
    }
}

/** Tiny desktop backend fake used to cover the explicit non-null App backend path without external hardware dependencies. */
private class FakeAppBackendApi : BackendApi {
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
    override fun getBrainflowBoards(): List<String> = emptyList()
    override fun getSerialPortSuggestions(boardId: String?): List<SerialPortSuggestion> = emptyList()
    override val hardwareStateFlow: StateFlow<HardwareState> = MutableStateFlow(HardwareState())
    override val systemLogFlow: StateFlow<List<SystemLogEntry>> = MutableStateFlow(emptyList())
    override val filteredFlow: Flow<DoubleArray> = emptyFlow()
    override val bandPowersFlow: Flow<List<BandPower>> = emptyFlow()
    override val fftResultFlow: Flow<Array<Pair<Double, Double>>> = emptyFlow()
    override fun setOnFilteredListener(listener: ((DoubleArray) -> Unit)?) = Unit
    override fun setOnBandPowersListener(listener: ((List<BandPower>) -> Unit)?) = Unit
    override fun setOnFFTResultListener(listener: ((Array<Pair<Double, Double>>) -> Unit)?) = Unit
}

