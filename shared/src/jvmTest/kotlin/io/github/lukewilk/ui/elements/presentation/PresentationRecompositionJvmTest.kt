package io.github.lukewilk.ui.elements.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.lukewilk.ui.elements.cards.CardHeader
import io.github.lukewilk.ui.elements.cards.PanelCard
import io.github.lukewilk.ui.elements.tables.TableHeaderRow
import kotlin.test.Test

/**
 * Recomposition-focused smoke tests for presentational Compose wrappers.
 *
 * These components already have static render coverage; this file drives caller-owned state changes
 * so Compose-generated change-mask branches execute too.
 */
@OptIn(ExperimentalTestApi::class)
class PresentationRecompositionJvmTest {
    @Test
    fun `card header and panel card update when caller state changes`() = runComposeUiTest {
        val icon = mutableStateOf("A")
        val title = mutableStateOf("Alpha header")
        val titleColor = mutableStateOf(Color.Red)
        val panelText = mutableStateOf("Alpha body")
        val panelColor = mutableStateOf(Color(0.95f, 0.95f, 0.95f, 1f))
        val panelModifier = mutableStateOf<Modifier>(Modifier)

        setContent {
            MaterialTheme {
                Column {
                    CardHeader(
                        icon = icon.value,
                        iconColor = Color.Yellow,
                        title = title.value,
                        titleColor = titleColor.value
                    )
                    PanelCard(
                        modifier = panelModifier.value,
                        containerColor = panelColor.value
                    ) {
                        Text(panelText.value)
                    }
                }
            }
        }

        onNodeWithText("A").assertIsDisplayed()
        onNodeWithText("Alpha header").assertIsDisplayed()
        onNodeWithText("Alpha body").assertIsDisplayed()

        icon.value = "B"
        title.value = "Beta header"
        titleColor.value = Color.Cyan
        panelText.value = "Beta body"
        panelColor.value = Color(0.2f, 0.3f, 0.4f, 1f)
        panelModifier.value = Modifier.padding(6.dp)
        waitForIdle()

        onNodeWithText("B").assertIsDisplayed()
        onNodeWithText("Beta header").assertIsDisplayed()
        onNodeWithText("Beta body").assertIsDisplayed()
    }

    @Test
    fun `table header row updates headers colors and modifiers across recomposition`() = runComposeUiTest {
        val headers = mutableStateOf(listOf("Left", "Right"))
        val background = mutableStateOf(Color.DarkGray)
        val textColor = mutableStateOf(Color.White)
        val cellModifiers = mutableStateOf(listOf(Modifier.padding(horizontal = 2.dp), Modifier))

        setContent {
            MaterialTheme {
                TableHeaderRow(
                    headers = headers.value,
                    backgroundColor = background.value,
                    textColor = textColor.value,
                    cellModifiers = cellModifiers.value
                )
            }
        }

        onNodeWithText("Left").assertIsDisplayed()
        onNodeWithText("Right").assertIsDisplayed()

        headers.value = listOf("First", "Second", "Third")
        background.value = Color(0.2f, 0.25f, 0.3f, 1f)
        textColor.value = Color(0.95f, 0.95f, 0.95f, 1f)
        cellModifiers.value = listOf(Modifier, Modifier.padding(horizontal = 1.dp))
        waitForIdle()

        onNodeWithText("First").assertIsDisplayed()
        onNodeWithText("Second").assertIsDisplayed()
        onNodeWithText("Third").assertIsDisplayed()
    }
}
