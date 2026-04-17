package io.github.lukewilk.ui.elements.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import io.github.lukewilk.ui.elements.cards.CardHeader
import io.github.lukewilk.ui.elements.cards.PanelCard
import io.github.lukewilk.ui.elements.feedback.StatusIndicator
import io.github.lukewilk.ui.elements.layout.ActionButtonRow
import io.github.lukewilk.ui.elements.layout.VerticalSpacer
import io.github.lukewilk.ui.elements.tables.TableHeaderRow
import io.github.lukewilk.ui.elements.text.SectionTitle
import kotlin.test.Test

/**
 * UI smoke tests for shared presentational components.
 */
@OptIn(ExperimentalTestApi::class)
class PresentationComponentsTest {
    @Test
    fun `card header and section title render their visible text`() {
        // Verifies the shared heading components expose the intended icon and titles to the UI tree.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    androidx.compose.foundation.layout.Column {
                        CardHeader(icon = "⚡", iconColor = Color.Yellow, title = "Signal status")
                        SectionTitle(text = "Band powers")
                    }
                }
            }

            onNodeWithText("⚡").assertIsDisplayed()
            onNodeWithText("Signal status").assertIsDisplayed()
            onNodeWithText("Band powers").assertIsDisplayed()
        }
    }

    @Test
    fun `panel card and action button row render their slotted child content`() {
        // Confirms container-style shared components preserve and display the composables passed into their slots.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    androidx.compose.foundation.layout.Column {
                        PanelCard {
                            androidx.compose.material3.Text("Card body content")
                        }
                        ActionButtonRow(
                            buttons = listOf(
                                { androidx.compose.material3.Text("Primary action") },
                                { androidx.compose.material3.Text("Secondary action") }
                            )
                        )
                    }
                }
            }

            onNodeWithText("Card body content").assertIsDisplayed()
            onNodeWithText("Primary action").assertIsDisplayed()
            onNodeWithText("Secondary action").assertIsDisplayed()
        }
    }

    @Test
    fun `status indicator and table header row render their labels`() {
        // Verifies small read-only status and table-heading components surface the expected text semantics.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    androidx.compose.foundation.layout.Column {
                        StatusIndicator(color = Color.Green, text = "Connected")
                        TableHeaderRow(headers = listOf("Channel", "Enabled", "RLD"))
                    }
                }
            }

            onNodeWithText("Connected").assertIsDisplayed()
            onNodeWithText("Channel").assertIsDisplayed()
            onNodeWithText("Enabled").assertIsDisplayed()
            onNodeWithText("RLD").assertIsDisplayed()
        }
    }

    @Test
    fun `presentation components render correctly with explicit non-default parameters`() {
        // Exercises the optional parameter paths that callers use for custom colors, sizes, and layout modifiers.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    androidx.compose.foundation.layout.Column {
                        CardHeader(
                            icon = "🧠",
                            iconColor = Color.Cyan,
                            title = "Custom header",
                            titleColor = Color.Magenta
                        )
                        SectionTitle(
                            text = "Custom section",
                            modifier = Modifier
                        )
                        PanelCard(
                            containerColor = Color.LightGray,
                            modifier = Modifier
                        ) {
                            androidx.compose.material3.Text("Custom panel body")
                        }
                        StatusIndicator(
                            color = Color.Red,
                            text = "Alert",
                            modifier = Modifier,
                            iconSize = 18.dp
                        )
                        TableHeaderRow(
                            headers = listOf("Custom A", "Custom B"),
                            modifier = Modifier,
                            backgroundColor = Color.DarkGray,
                            textColor = Color.White,
                            cellModifiers = listOf(Modifier.width(140.dp), Modifier.width(100.dp))
                        )
                    }
                }
            }

            onNodeWithText("🧠").assertIsDisplayed()
            onNodeWithText("Custom header").assertIsDisplayed()
            onNodeWithText("Custom section").assertIsDisplayed()
            onNodeWithText("Custom panel body").assertIsDisplayed()
            onNodeWithText("Alert").assertIsDisplayed()
            onNodeWithText("Custom A").assertIsDisplayed()
            onNodeWithText("Custom B").assertIsDisplayed()
        }
    }

    @Test
    fun `presentation layout helpers render with explicit spacing and cell modifiers`() {
        // Covers the helper branches that use custom row spacing, a direct spacer call, and non-default table cell modifiers.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    androidx.compose.foundation.layout.Column {
                        VerticalSpacer(height = 6.dp)
                        ActionButtonRow(
                            buttons = listOf(
                                { androidx.compose.material3.Text("Only action") }
                            ),
                            spacing = 20.dp
                        )
                        TableHeaderRow(
                            headers = listOf("Primary", "Secondary"),
                            cellModifiers = listOf(Modifier.width(140.dp), Modifier)
                        )
                    }
                }
            }

            onNodeWithText("Only action").assertIsDisplayed()
            onNodeWithText("Primary").assertIsDisplayed()
            onNodeWithText("Secondary").assertIsDisplayed()
        }
    }

    @Test
    fun `action button row accepts an empty button list without rendering actions`() {
        // Covers the empty forEach path in ActionButtonRow.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    androidx.compose.foundation.layout.Column {
                        ActionButtonRow(buttons = emptyList())
                        androidx.compose.material3.Text("Sentinel")
                    }
                }
            }

            onNodeWithText("Sentinel").assertIsDisplayed()
            onNodeWithText("Primary action").assertDoesNotExist()
        }
    }

    @Test
    fun `table header row applies weighted cells when fewer custom modifiers than columns`() {
        // Verifies trailing columns fall back to the default weighted layout when no custom cell modifier is supplied.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    androidx.compose.foundation.layout.Column {
                        TableHeaderRow(
                            headers = listOf("Alpha", "Beta", "Gamma"),
                            cellModifiers = listOf(Modifier.width(120.dp))
                        )
                    }
                }
            }

            onNodeWithText("Alpha").assertIsDisplayed()
            onNodeWithText("Beta").assertIsDisplayed()
            onNodeWithText("Gamma").assertIsDisplayed()
        }
    }

    @Test
    fun `table header row handles empty headers and identity weighted modifiers`() {
        // Verifies the row handles both empty headers and the bare Modifier sentinel gracefully.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    androidx.compose.foundation.layout.Column {
                        TableHeaderRow(headers = emptyList())
                        TableHeaderRow(
                            headers = listOf("Alpha", "Beta"),
                            cellModifiers = listOf(Modifier, Modifier.padding(horizontal = 2.dp))
                        )
                    }
                }
            }

            onNodeWithText("Alpha").assertIsDisplayed()
            onNodeWithText("Beta").assertIsDisplayed()
        }
    }

    @Test
    fun `table header row cycles weighted custom and default modifiers across four columns`() {
        // Exercises repeated forEachIndexed branches so null trailing modifiers and custom widths share coverage with the default-parameter modifier chain.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    TableHeaderRow(
                        headers = listOf("A", "B", "C", "D"),
                        cellModifiers = listOf(
                            Modifier.width(40.dp),
                            Modifier,
                            Modifier.padding(horizontal = 1.dp),
                            Modifier.width(52.dp)
                        )
                    )
                }
            }

            onNodeWithText("A").assertIsDisplayed()
            onNodeWithText("B").assertIsDisplayed()
            onNodeWithText("C").assertIsDisplayed()
            onNodeWithText("D").assertIsDisplayed()
        }
    }

    @Test
    fun `panel card applies default elevation and honors custom container color`() {
        // Covers CardDefaults branches by composing PanelCard with and without a custom surface color.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    androidx.compose.foundation.layout.Column {
                        PanelCard { androidx.compose.material3.Text("Default surface panel") }
                        PanelCard(containerColor = Color(0.2f, 0.3f, 0.4f, 1f)) {
                            androidx.compose.material3.Text("Tinted panel")
                        }
                    }
                }
            }

            onNodeWithText("Default surface panel").assertIsDisplayed()
            onNodeWithText("Tinted panel").assertIsDisplayed()
        }
    }
}

