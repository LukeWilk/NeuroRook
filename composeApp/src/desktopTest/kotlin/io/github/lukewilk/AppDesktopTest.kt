package io.github.lukewilk

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

/**
 * Desktop-facing behavior tests for the shared root app composable.
 */
@OptIn(ExperimentalTestApi::class)
class AppDesktopTest {
    @Test
    fun `app renders through a custom scaffold renderer`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                App(
                    backendApi = null,
                    colorScheme = lightColorScheme(),
                    headerContent = null,
                    renderScaffold = { _, _ ->
                        Text("CustomShell")
                    }
                )
            }
        }
        onNodeWithText("CustomShell").assertIsDisplayed()
        onNodeWithText("Neuro Rook").assertDoesNotExist()
    }

    fun `default app scaffold renderer wires hardware body and header slots`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                defaultAppScaffoldRenderer(
                    hardwareScreen = { Text("HardwareBodyMarker") },
                    headerContent = { Text("HeaderSlotMarker") }
                )
            }
        }
        onNodeWithText("HardwareBodyMarker").assertIsDisplayed()
        onNodeWithText("HeaderSlotMarker").assertIsDisplayed()
    }

    @Test
    fun `default app scaffold renderer uses main scaffold header fallback when header content is null`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                defaultAppScaffoldRenderer(
                    hardwareScreen = { Text("HardwareBodyOnly") },
                    headerContent = null
                )
            }
        }
        onNodeWithText("HardwareBodyOnly").assertIsDisplayed()
        onNodeWithText("Neuro Rook").assertIsDisplayed()
    }

    @Test
    fun `app composable applies explicit material theme and default scaffold rendering`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                App(backendApi = null, colorScheme = lightColorScheme(primary = Color.Red))
            }
        }
        onNodeWithText("Neuro Rook").assertIsDisplayed()
        onNodeWithText("Hardware").assertIsDisplayed()
    }

    @Test
    fun `default app hardware screen provider renders hardware shell`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                appHardwareScreen(null).invoke()
            }
        }
        onNodeWithText("Unable to load boards").assertIsDisplayed()
    }

    @Test
    fun `app uses outer material color scheme when explicit scheme argument is null`() = runComposeUiTest {
        val outer = lightColorScheme(primary = Color(0.11f, 0.62f, 0.33f))
        setContent {
            MaterialTheme(colorScheme = outer) {
                App(
                    backendApi = null,
                    colorScheme = null,
                    hardwareScreenProvider = { _ ->
                        @Composable {
                            val p = MaterialTheme.colorScheme.primary
                            Text("primary:${p.red}-${p.green}-${p.blue}")
                        }
                    }
                )
            }
        }
        onNodeWithText("primary:${outer.primary.red}-${outer.primary.green}-${outer.primary.blue}").assertIsDisplayed()
    }

    @Test
    fun `app renders custom header content through the default scaffold`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                App(
                    backendApi = null,
                    colorScheme = lightColorScheme(),
                    headerContent = { Text("AppHeaderSlot") }
                )
            }
        }
        onNodeWithText("AppHeaderSlot").assertIsDisplayed()
        onNodeWithText("Hardware").assertIsDisplayed()
    }

}
