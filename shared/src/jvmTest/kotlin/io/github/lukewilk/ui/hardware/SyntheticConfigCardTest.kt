package io.github.lukewilk.ui.hardware

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.github.lukewilk.shared.WaveSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class)
class SyntheticConfigCardTest {
    @Test
    fun `add wave button calls callback`() {
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    var clicks by remember { mutableIntStateOf(0) }
                    androidx.compose.foundation.layout.Column {
                        SyntheticConfigCard(
                            waveSpecs = emptyList(),
                            onAddWave = { clicks += 1 },
                            onRemoveWave = {},
                            onToggleEnabled = { _, _ -> },
                            onEditWave = { _, _ -> }
                        )
                        androidx.compose.material3.Text("Clicks: $clicks")
                    }
                }
            }

            onNodeWithText("Add Wave").assertIsDisplayed().performClick()
            onNodeWithText("Clicks: 1").assertIsDisplayed()
        }
    }

    @Test
    fun `remove and toggle forward callbacks`() {
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    var removed by remember { mutableIntStateOf(-1) }
                    var toggles by remember { mutableIntStateOf(0) }
                    val waves = listOf(WaveSpec(enabled = true, amplitude = 2.0, frequencyHz = 5.0))
                    androidx.compose.foundation.layout.Column {
                        SyntheticConfigCard(
                            waveSpecs = waves,
                            onAddWave = {},
                            onRemoveWave = { idx -> removed = idx },
                            onToggleEnabled = { idx, _ -> toggles += 1 },
                            onEditWave = { _, _ -> }
                        )
                        androidx.compose.material3.Text("Removed: $removed")
                        androidx.compose.material3.Text("Toggles: $toggles")
                    }
                }
            }

            onNodeWithContentDescription("Disable").assertIsDisplayed().performClick()
            onNodeWithText("Toggles: 1").assertIsDisplayed()

            onNodeWithContentDescription("Remove").assertIsDisplayed().performClick()
            onNodeWithText("Removed: 0").assertIsDisplayed()
        }
    }

    @Test
    fun `edit wave opens dialog and save calls callback`() {
        runComposeUiTest {
            val edited = arrayOfNulls<io.github.lukewilk.shared.WaveSpec>(1)
            val editedIdx = IntArray(1) { -1 }
            setContent {
                MaterialTheme {
                    val waves = listOf(io.github.lukewilk.shared.WaveSpec(enabled = false, amplitude = 3.0, frequencyHz = 2.0))
                    androidx.compose.foundation.layout.Column {
                        SyntheticConfigCard(
                            waveSpecs = waves,
                            onAddWave = {},
                            onRemoveWave = {},
                            onToggleEnabled = { _, _ -> },
                            onEditWave = { idx, wave ->
                                editedIdx[0] = idx
                                edited[0] = wave
                            }
                        )
                    }
                }
            }

            // Open edit dialog
            onNodeWithContentDescription("Edit").assertIsDisplayed().performClick()
            waitForIdle()

            // Save without changing values
            onNodeWithText("Save").assertIsDisplayed().performClick()
            waitForIdle()

            // verify callback was invoked
            assertNotNull(edited[0])
            assertEquals(0, editedIdx[0])
        }
    }

    @Test
    fun `channel selector controls are not rendered`() {
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    val waves = listOf(io.github.lukewilk.shared.WaveSpec(enabled = false))
                    androidx.compose.foundation.layout.Column {
                        SyntheticConfigCard(
                            waveSpecs = waves,
                            onAddWave = {},
                            onRemoveWave = {},
                            onToggleEnabled = { _, _ -> },
                            onEditWave = { _, _ -> }
                        )
                    }
                }
            }

            assertEquals(0, onAllNodesWithText("Channels").fetchSemanticsNodes().size)
            assertEquals(0, onAllNodesWithText("Emit to channels:").fetchSemanticsNodes().size)
            assertEquals(0, onAllNodesWithText("Select Channels").fetchSemanticsNodes().size)
        }
    }
}









