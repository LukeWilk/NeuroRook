package io.github.lukewilk.ui.elements.forms

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

/**
 * UI smoke tests for the shared form components.
 */
@OptIn(ExperimentalTestApi::class)
class FormComponentsTest {
    @Test
    fun `styled outlined text field renders placeholder and caller provided value`() {
        // Verifies the reusable text field surfaces both its placeholder and a host-controlled current value.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    androidx.compose.foundation.layout.Column {
                        StyledOutlinedTextField(
                            value = "",
                            onValueChange = {},
                            placeholder = "Enter board name"
                        )
                        StyledOutlinedTextField(
                            value = "NeuroRook",
                            onValueChange = {},
                            placeholder = "Ignored when value exists"
                        )
                    }
                }
            }

            onNodeWithText("Enter board name").assertIsDisplayed()
            onNodeWithText("NeuroRook").assertIsDisplayed()
        }
    }

    @Test
    fun `dropdown menu renders its label and current selection`() {
        // Verifies the exposed dropdown shows the caller-provided label and currently selected item.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    DropdownMenu(
                        items = listOf("Alpha", "Beta", "Gamma"),
                        selected = "Alpha",
                        onSelected = {},
                        label = "Wave"
                    )
                }
            }

            onNodeWithText("Wave").assertIsDisplayed()
            onNodeWithText("Alpha").assertIsDisplayed()
        }
    }

    @Test
    fun `dropdown menu stays rendered after the trigger is clicked`() {
        // Exercises the exposed-menu trigger branch without depending on brittle desktop popup semantics.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    val selected = mutableStateOf("Alpha")
                    DropdownMenu(
                        items = listOf("Alpha", "Beta", "Gamma"),
                        selected = selected.value,
                        onSelected = { selected.value = it },
                        label = "Wave"
                    )
                }
            }

            onNodeWithText("Wave").performClick()
            onNodeWithText("Wave").assertIsDisplayed()
        }
    }
}



