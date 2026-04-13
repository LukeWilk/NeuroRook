package io.github.lukewilk
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
/**
 * Desktop smoke tests for the shared NeuroRook root composable.
 */
@OptIn(ExperimentalTestApi::class)
class AppSmokeTest {
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
    fun `app shows placeholder content when switching away from hardware`() = runComposeUiTest {
        // Confirms the shared navigation shell responds to tab clicks in the desktop host.
        setContent {
            App(backendApi = null)
        }
        onNodeWithText("Protocols").performClick()
        onNodeWithText("Protocols screen coming soon...").assertIsDisplayed()
    }
}
