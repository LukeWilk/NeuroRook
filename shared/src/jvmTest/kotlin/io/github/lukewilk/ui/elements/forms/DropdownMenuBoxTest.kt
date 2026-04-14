package io.github.lukewilk.ui.elements.forms

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
 * UI smoke tests for the shared dropdown selection component.
 */
@OptIn(ExperimentalTestApi::class)
class DropdownMenuBoxTest {
    @Test
    fun `dropdown menu box updates the selected label after choosing an option`() {
        // Verifies the reusable dropdown keeps the selected index in sync with the callback chosen by callers.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    var selectedIndex by remember { mutableIntStateOf(0) }
                    DropdownMenuBox(
                        options = listOf("Alpha", "Beta", "Gamma"),
                        selectedIndex = selectedIndex,
                        onSelected = { selectedIndex = it },
                        enabled = true
                    )
                }
            }

            onNodeWithText("Alpha").assertIsDisplayed().performClick()
            onNodeWithText("Gamma").assertIsDisplayed().performClick()
            onNodeWithText("Gamma").assertIsDisplayed()
        }
    }

    @Test
    fun `dropdown menu box stays closed when disabled`() {
        // Confirms disabled callers cannot interact with the dropdown trigger.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    DropdownMenuBox(
                        options = listOf("Alpha", "Beta", "Gamma"),
                        selectedIndex = 0,
                        onSelected = {},
                        enabled = false
                    )
                }
            }

            onNodeWithText("Alpha").assertIsNotEnabled()
        }
    }
}


