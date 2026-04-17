package io.github.lukewilk.ui.elements.forms

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Compose UI tests for the shared ExposedDropdownMenu-based [DropdownMenu] wrapper.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalTestApi::class)
class DropdownMenuComposeJvmTest {

    @Test
    fun `dropdown menu expands on field click selects another item and collapses`() = runComposeUiTest {
        val selections = mutableListOf<String>()
        setContent {
            MaterialTheme {
                var selected by remember { mutableStateOf("Alpha") }
                DropdownMenu(
                    items = listOf("Alpha", "Beta", "Gamma"),
                    selected = selected,
                    onSelected = {
                        selected = it
                        selections += it
                    },
                    label = "Option"
                )
            }
        }

        onNodeWithText("Option").assertIsDisplayed()
        onNodeWithText("Alpha").performClick()
        waitForIdle()
        onNodeWithText("Gamma").assertIsDisplayed().performClick()
        waitForIdle()

        assertEquals(listOf("Gamma"), selections)
    }

    @Test
    fun `dropdown menu renders read only field when item list is empty`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                DropdownMenu(
                    items = emptyList(),
                    selected = "",
                    onSelected = {},
                    label = "Empty catalog"
                )
            }
        }

        onNodeWithText("Empty catalog").assertIsDisplayed()
        onNodeWithText("Alpha").assertDoesNotExist()
    }

    @Test
    fun `dropdown menu dismisses when focus moves outside the popup`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                androidx.compose.foundation.layout.Column {
                    DropdownMenu(
                        items = listOf("Alpha", "Beta", "Gamma"),
                        selected = "Alpha",
                        onSelected = {},
                        label = "Dismissible"
                    )
                    androidx.compose.material3.Text("Outside target")
                }
            }
        }

        onNodeWithText("Alpha").performClick()
        waitForIdle()
        onNodeWithText("Gamma", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Outside target").performClick()
        waitForIdle()
        onNodeWithText("Gamma", useUnmergedTree = true).assertDoesNotExist()
    }
}
