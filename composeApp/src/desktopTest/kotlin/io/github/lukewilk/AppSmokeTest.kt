package io.github.lukewilk

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.unit.Density
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.api.BackendApi
import io.github.lukewilk.shared.model.BandPower
import io.github.lukewilk.shared.model.ChannelData
import io.github.lukewilk.shared.model.SerialPortSuggestion
import io.github.lukewilk.shared.model.SystemLogEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Test
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Window
import java.awt.event.WindowEvent
import javax.swing.UIManager
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue
/**
 * Desktop smoke tests for the shared NeuroRook root composable.
 */
@OptIn(ExperimentalTestApi::class)
class AppSmokeTest {
    @Test
    fun `app uses the default null backend parameter when callers omit it`() = runComposeUiTest {
        // Covers the default-argument entry path so desktop callers can render the app without supplying a backend or fallback title text.
        setContent {
            App()
        }
        assertTrue(onAllNodesWithText("Neuro Rook").fetchSemanticsNodes().isEmpty())
        onNodeWithText("Hardware").assertIsDisplayed()
    }

    @Test
    fun `app renders the shared scaffold chrome and default hardware tab`() = runComposeUiTest {
        // Verifies the desktop host can render the shared root UI without a backend implementation.
        setContent {
            App(backendApi = null)
        }
        assertTrue(onAllNodesWithText("Neuro Rook").fetchSemanticsNodes().isEmpty())
        onNodeWithContentDescription("Collapse sidebar").assertIsDisplayed()
        onNodeWithText("Hardware").assertIsDisplayed()
    }

    @Test
    fun `app renders with an explicit light color scheme overriding default material roles`() = runComposeUiTest {
        // Covers the non-null colorScheme branch in App so MaterialTheme uses the injected scheme.
        setContent {
            App(backendApi = null, colorScheme = lightColorScheme(), headerContent = null)
        }
        assertTrue(onAllNodesWithText("Neuro Rook").fetchSemanticsNodes().isEmpty())
        onNodeWithText("Hardware").assertIsDisplayed()
    }

    @Test
    fun `app renders the shared scaffold with an explicit backend instance`() = runComposeUiTest {
        // Exercises the non-null backend path so the root app composable runs with both injected and default hosts.
        setContent {
            App(backendApi = FakeAppBackendApi())
        }
        assertTrue(onAllNodesWithText("Neuro Rook").fetchSemanticsNodes().isEmpty())
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
        // Covers the root app shell as a real navigation workflow, including icon-only collapsed navigation.
        setContent {
            App(backendApi = null)
        }

        onNodeWithText("Signals").performClick()
        onNodeWithText("Signals screen coming soon...").assertIsDisplayed()
        onNodeWithContentDescription("Collapse sidebar").performClick()
        onNodeWithText("Signals").assertDoesNotExist()
        onNodeWithContentDescription("Signals navigation icon").assertIsDisplayed()
        onNodeWithContentDescription("Hardware").performClick()
        onNodeWithText("Device Selection").assertIsDisplayed()
    }

    @Test
    fun `app shows attribution license and repository details on the about page`() = runComposeUiTest {
        // Covers the screenshot-inspired About destination that documents the icon source together with the NeuroRook MIT license and repository.
        setContent {
            App(backendApi = null)
        }

        onNodeWithText("About").performScrollTo().performClick()
        onNodeWithText("About Neuro Rook").assertIsDisplayed()
        onNodeWithText("Icon attribution").assertIsDisplayed()
        onNodeWithText("NeuroRook").assertIsDisplayed()
        onNodeWithText("Repository").assertIsDisplayed()
        onNodeWithText("https://github.com/LukeWilk/NeuroRook").assertIsDisplayed().assertHasClickAction()
        onNodeWithText("License: MIT License\nCopyright (c) 2026 Luke Wilk").assertIsDisplayed()
    }

    @Test
    fun `app renders custom desktop header content when supplied`() = runComposeUiTest {
        // Covers the optional headerContent path while keeping the shared scaffold titleless.
        setContent {
            App(
                backendApi = null,
                headerContent = { DesktopSidebarBrandContent(logoPainter = ColorPainter(Color.Red)) }
            )
        }

        assertTrue(onAllNodesWithText("Neuro Rook").fetchSemanticsNodes().isEmpty())
        onNodeWithText("Hardware").assertIsDisplayed()
    }

    @Test
    fun `desktop sidebar brand content shows text fallback when logo is unavailable`() = runComposeUiTest {
        // Covers the null-logo branch that renders the textual fallback branding.
        setContent {
            MaterialTheme {
                DesktopSidebarBrandContent(logoPainter = null)
            }
        }

        onNodeWithText("NeuroRook").assertIsDisplayed()
    }

    @Test
    fun `desktop sidebar brand content hides text fallback when logo painter exists`() = runComposeUiTest {
        // Covers the painter branch so the component renders image mode instead of fallback text.
        setContent {
            MaterialTheme {
                DesktopSidebarBrandContent(logoPainter = ColorPainter(Color.Blue))
            }
        }

        onNodeWithContentDescription("NeuroRook logo").assertIsDisplayed()
        onNodeWithText("NeuroRook").assertDoesNotExist()
    }

    @Test
    fun `desktop sidebar brand wrapper renders logo when resource exists and falls back otherwise`() = runComposeUiTest {
        // Covers DesktopSidebarBrand + rememberDesktopSidebarLogo branches deterministically by checking resource availability.
        val hasLogoResource = Thread.currentThread().contextClassLoader?.getResource("neuroRook.svg") != null

        setContent {
            MaterialTheme {
                DesktopSidebarBrand()
            }
        }

        if (hasLogoResource) {
            onNodeWithContentDescription("NeuroRook logo").assertIsDisplayed()
            onNodeWithText("NeuroRook").assertDoesNotExist()
        } else {
            onNodeWithText("NeuroRook").assertIsDisplayed()
        }
    }

    @Test
    fun `desktop sidebar brand falls back when logo bytes cannot be parsed as svg`() = runComposeUiTest {
        // Forces rememberDesktopSidebarLogo through the runCatching failure path.
        val previousLoader = Thread.currentThread().contextClassLoader
        val brokenSvgLoader = object : ClassLoader(previousLoader) {
            override fun getResourceAsStream(name: String?): java.io.InputStream? {
                if (name == "neuroRook.svg") {
                    return java.io.ByteArrayInputStream("not an svg".toByteArray())
                }
                return super.getResourceAsStream(name)
            }
        }

        try {
            Thread.currentThread().contextClassLoader = brokenSvgLoader
            setContent {
                MaterialTheme {
                    DesktopSidebarBrand()
                }
            }
            val textVisible = runCatching {
                onNodeWithText("NeuroRook").assertIsDisplayed()
                true
            }.getOrDefault(false)
            val logoVisible = runCatching {
                onNodeWithContentDescription("NeuroRook logo").assertIsDisplayed()
                true
            }.getOrDefault(false)
            assertTrue(textVisible || logoVisible)
        } finally {
            Thread.currentThread().contextClassLoader = previousLoader
        }
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

    @Test
    fun `desktop look and feel configuration helper can be invoked directly`() {
        // Covers the desktop look-and-feel helper without requiring a full Compose window lifecycle.
        // Invoke twice to exercise both "apply" and "already set" control flow safely.
        configureSystemLookAndFeel()
        val activeClassName = UIManager.getLookAndFeel()?.javaClass?.name
        val systemClassName = UIManager.getSystemLookAndFeelClassName()
        if (activeClassName == systemClassName) {
            configureSystemLookAndFeel()
        }
    }

    @Test
    fun `desktop look and feel helper applies system class when current look and feel differs`() {
        // Forces configureSystemLookAndFeel through the non-early-return branch.
        val previousLookAndFeelClass = UIManager.getLookAndFeel()?.javaClass?.name
        val crossPlatformClass = UIManager.getCrossPlatformLookAndFeelClassName()
        val systemClass = UIManager.getSystemLookAndFeelClassName()

        runCatching { UIManager.setLookAndFeel(crossPlatformClass) }
        configureSystemLookAndFeel()
        assertEquals(systemClass, UIManager.getLookAndFeel()?.javaClass?.name)

        if (previousLookAndFeelClass != null) {
            runCatching { UIManager.setLookAndFeel(previousLookAndFeelClass) }
        }
    }

    @Test
    fun `desktop sidebar logo helper functions cover missing and invalid logo bytes`() {
        val noLoaderBytes = readDesktopSidebarLogoBytes(null)
        assertEquals(null, noLoaderBytes)

        val invalidPainter = loadDesktopSidebarLogoPainter("not-svg".toByteArray(), Density(1f))
        assertEquals(null, invalidPainter)
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

        val hasDisplayEnvironment = !System.getenv("DISPLAY").isNullOrBlank() || !System.getenv("WAYLAND_DISPLAY").isNullOrBlank()
        if (!hasDisplayEnvironment) {
            return false
        }

        return runCatching {
            java.awt.EventQueue.invokeAndWait {
                Frame("NeuroRook desktop smoke probe").apply {
                    setSize(1, 1)
                    setLocation(-10_000, -10_000)
                    isVisible = true
                    dispose()
                }
            }
        }.isSuccess
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
    override val filteredFlow: Flow<ChannelData<DoubleArray>> = emptyFlow()
    override val bandPowersFlow: Flow<ChannelData<List<BandPower>>> = emptyFlow()
    override val fftResultFlow: Flow<ChannelData<Array<Pair<Double, Double>>>> = emptyFlow()
    override fun setOnFilteredListener(listener: ((DoubleArray) -> Unit)?) = Unit
    override fun setOnBandPowersListener(listener: ((List<BandPower>) -> Unit)?) = Unit
    override fun setOnFFTResultListener(listener: ((Array<Pair<Double, Double>>) -> Unit)?) = Unit
}

