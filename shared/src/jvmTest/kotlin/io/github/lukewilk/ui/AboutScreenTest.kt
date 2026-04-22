package io.github.lukewilk.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
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
}

