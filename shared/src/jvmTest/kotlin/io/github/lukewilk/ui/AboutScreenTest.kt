package io.github.lukewilk.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Focused Compose tests for the standalone About screen.
 */
@OptIn(ExperimentalTestApi::class)
class AboutScreenTest {
    @Test
    fun `about screen opens the repository URL when the link is clicked`() = runComposeUiTest {
        // Verifies the emphasized repository link delegates to the provided opener instead of requiring a real browser in tests.
        var openedUrl: String? = null

        setContent {
            MaterialTheme {
                AboutScreen(onOpenRepositoryUrl = { openedUrl = it })
            }
        }

        onNodeWithText(NEUROROOK_REPOSITORY_URL).performClick()
        assertEquals(NEUROROOK_REPOSITORY_URL, openedUrl)
    }

    @Test
    fun `about screen shows overflow cues when its content extends beyond the viewport`() = runComposeUiTest {
        // Verifies long page content advertises additional content below and flips the cue after scrolling to the footer.
        setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(width = 320.dp, height = 120.dp)) {
                    AboutScreen()
                }
            }
        }

        waitForIdle()

        onNodeWithContentDescription("More content below", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithContentDescription("More content above", useUnmergedTree = true).assertDoesNotExist()

        onNodeWithText("License: MIT License\nCopyright (c) 2026 Luke Wilk").performScrollTo().assertIsDisplayed()

        onNodeWithContentDescription("More content above", useUnmergedTree = true).assertIsDisplayed()
    }
}

