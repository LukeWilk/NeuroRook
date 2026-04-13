package io.github.lukewilk.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

/**
 * UI smoke tests for the shared navigation scaffold.
 */
@OptIn(ExperimentalTestApi::class)
class MainScaffoldTest {
    @Test
    fun `main scaffold renders custom header content and the hardware screen slot`() {
        // Verifies the shared scaffold renders caller-provided header and content without any platform backend.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    MainScaffold(
                        hardwareScreen = {
                            androidx.compose.material3.Text("Hardware screen content")
                        },
                        headerContent = {
                            androidx.compose.material3.Text("Custom NeuroRook Header")
                        }
                    )
                }
            }

            onNodeWithText("Custom NeuroRook Header").assertIsDisplayed()
            onNodeWithText("Hardware screen content").assertIsDisplayed()
            onNodeWithText("Hardware").assertIsDisplayed()
        }
    }

    @Test
    fun `main scaffold shows placeholder content after selecting another tab`() {
        // Confirms the shared sidebar navigation swaps the hardware slot for placeholder content on non-hardware tabs.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    MainScaffold(
                        hardwareScreen = {
                            androidx.compose.material3.Text("Hardware screen content")
                        }
                    )
                }
            }

            onNodeWithText("Signals").performClick()
            onNodeWithText("Signals screen coming soon...").assertIsDisplayed()
        }
    }

    @Test
    fun `main scaffold shows the collapsed menu glyph after closing the sidebar`() {
        // Exercises the collapsed-sidebar header branch so the compact menu affordance stays visible.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    MainScaffold(
                        hardwareScreen = {
                            androidx.compose.material3.Text("Hardware screen content")
                        }
                    )
                }
            }

            onNodeWithText("Neuro Rook").performClick()
            onNodeWithText("≡").assertIsDisplayed()
        }
    }
}


