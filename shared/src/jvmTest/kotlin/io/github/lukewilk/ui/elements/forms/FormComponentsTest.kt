package io.github.lukewilk.ui.elements.forms

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
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
    fun `styled outlined text field exposes disabled state with explicit monospace styling`() {
        // Covers the non-default font-family branch while keeping the field visibly disabled for semantics checks.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    StyledOutlinedTextField(
                        value = "/dev/ttyUSB0",
                        onValueChange = {},
                        enabled = false,
                        placeholder = "Serial path",
                        modifier = Modifier.width(240.dp),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            onNodeWithText("/dev/ttyUSB0").assertIsDisplayed().assertIsNotEnabled()
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

    @Test
    fun `dropdown menu item selection updates the host state`() {
        // Verifies the popup item click invokes the selection callback with the chosen item.
        val selected = mutableStateOf("Alpha")

        runComposeUiTest {
            setContent {
                MaterialTheme {
                    DropdownMenu(
                        items = listOf("Alpha", "Beta", "Gamma"),
                        selected = selected.value,
                        onSelected = { selected.value = it },
                        label = "Wave"
                    )
                }
            }

            onNodeWithText("Wave").performClick()
            onNodeWithText("Beta", useUnmergedTree = true).performClick()
            onNodeWithText("Beta").assertIsDisplayed()
        }

        assertEquals("Beta", selected.value)
    }

    @Test
    fun `dropdown menu renders safely with no items and an explicit modifier`() {
        // Covers the empty-items branch so the shared dropdown still renders a stable trigger without popup entries.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    DropdownMenu(
                        items = emptyList(),
                        selected = "No selection",
                        onSelected = {},
                        label = "Wave",
                        modifier = Modifier.width(240.dp)
                    )
                }
            }

            onNodeWithText("Wave").performClick()
            onNodeWithText("No selection").assertIsDisplayed()
            onNodeWithText("Alpha").assertDoesNotExist()
        }
    }

}



