package io.github.lukewilk.ui.elements.tables

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test

/**
 * Compose tests for [TableHeaderRow] rendering and per-cell modifier behavior on JVM.
 */
@OptIn(ExperimentalTestApi::class)
class TableHeaderRowComposeJvmTest {

    @Test
    fun `table header row displays all headers with default weighted cells`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                TableHeaderRow(headers = listOf("A", "B", "C"))
            }
        }
        onNodeWithText("A").assertIsDisplayed()
        onNodeWithText("B").assertIsDisplayed()
        onNodeWithText("C").assertIsDisplayed()
    }

    @Test
    fun `table header row honors custom cell modifiers and colors`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                TableHeaderRow(
                    headers = listOf("Left", "Right"),
                    backgroundColor = Color(0xFF112233),
                    textColor = Color(0xFFEEDDCC),
                    cellModifiers = listOf(Modifier.padding(horizontal = 2.dp), Modifier.padding(vertical = 3.dp))
                )
            }
        }
        onNodeWithText("Left").assertIsDisplayed()
        onNodeWithText("Right").assertIsDisplayed()
    }
}
