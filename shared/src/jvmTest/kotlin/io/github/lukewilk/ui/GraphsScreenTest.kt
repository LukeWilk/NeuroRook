package io.github.lukewilk.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

/**
 * Focused Compose tests for the standalone Graphs screen.
 */
@OptIn(ExperimentalTestApi::class)
class GraphsScreenTest {
    @Test
    fun `graphs screen shows an explicit empty state for the new tab`() = runComposeUiTest {
        // Verifies the initial Graphs page is intentionally blank-state content instead of the generic coming-soon placeholder.
        setContent {
            MaterialTheme {
                GraphsScreen()
            }
        }

        onNodeWithText("Graphs").assertIsDisplayed()
        onNodeWithText(GRAPHS_EMPTY_STATE_MESSAGE).assertIsDisplayed()
    }
}

