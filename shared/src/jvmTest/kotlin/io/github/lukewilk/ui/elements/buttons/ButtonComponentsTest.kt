package io.github.lukewilk.ui.elements.buttons

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

/**
 * UI smoke tests for the shared button components.
 */
@OptIn(ExperimentalTestApi::class)
class ButtonComponentsTest {
    @Test
    fun `primary button displays its label and forwards clicks`() {
        // Verifies the primary action button exposes its label and executes the supplied click callback.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    var clicks by remember { mutableIntStateOf(0) }
                    androidx.compose.foundation.layout.Column {
                        PrimaryButton(
                            onClick = { clicks += 1 },
                            text = "Start session"
                        )
                        androidx.compose.material3.Text("Clicks: $clicks")
                    }
                }
            }

            onNodeWithText("Start session").assertIsDisplayed().performClick()
            onNodeWithText("Clicks: 1").assertIsDisplayed()
        }
    }

    @Test
    fun `secondary button shows disabled state when callers turn it off`() {
        // Confirms the secondary action button exposes the disabled interaction state to accessibility semantics.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    SecondaryButton(
                        onClick = {},
                        text = "Stop session",
                        enabled = false
                    )
                }
            }

            onNodeWithText("Stop session").assertIsNotEnabled()
        }
    }
}

