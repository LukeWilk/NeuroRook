package io.github.lukewilk.graphs

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.github.lukewilk.App
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
        onNodeWithText("Channel Graph Matrix").assertIsDisplayed()
        onNodeWithText("Enable channels on the Hardware page to configure graphs.").assertIsDisplayed()
        onNodeWithText("Enable channels on the Hardware page to show graphs.").assertIsDisplayed()
        onNodeWithText("Graphs screen coming soon...").assertDoesNotExist()
    }
}




