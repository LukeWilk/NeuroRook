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
    fun `main scaffold extracted label composables render header and placeholder text directly`() {
        // Covers the tiny extracted label composables directly so their text-rendering paths are attributed outside the larger scaffold body.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    androidx.compose.foundation.layout.Column {
                        MainScaffoldHeaderLabel("Direct Header")
                        MainScaffoldPlaceholderLabelText("Protocols")
                        PlaceholderScreen("Signals")
                    }
                }
            }

            onNodeWithText("Direct Header").assertIsDisplayed()
            onNodeWithText("Protocols screen coming soon...").assertIsDisplayed()
            onNodeWithText("Signals screen coming soon...").assertIsDisplayed()
        }
    }

    @Test
    fun `main scaffold renders the built in Neuro Rook header by default`() {
        // Covers the null header-content branch so the scaffold falls back to its own branded title.
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

            onNodeWithText("Neuro Rook").assertIsDisplayed()
            onNodeWithText("Hardware screen content").assertIsDisplayed()
        }
    }

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
    fun `main scaffold collapses to the menu glyph and restores custom header content when reopened`() {
        // Covers the custom-header collapsed branch so the scaffold swaps to the compact glyph and restores the caller header on reopen.
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
            onNodeWithText("≡").assertIsDisplayed().performClick()
            onNodeWithText("Custom NeuroRook Header").assertDoesNotExist()
            onNodeWithText("≡").assertIsDisplayed().performClick()
            onNodeWithText("Custom NeuroRook Header").assertIsDisplayed()
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

            onNodeWithText("≡").performClick()
            onNodeWithText("≡").assertIsDisplayed()
        }
    }

    @Test
    fun `main scaffold shell workflow switches sections collapses and returns to hardware`() {
        // Covers the classical shell workflow so the scaffold owns section selection and sidebar expansion coherently end to end.
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

            onNodeWithText("Protocols").performClick()
            onNodeWithText("Protocols screen coming soon...").assertIsDisplayed()
            onNodeWithText("≡").performClick()
            onNodeWithText("Protocols").assertDoesNotExist()
            onNodeWithText("≡").performClick()
            onNodeWithText("Hardware").performClick()
            onNodeWithText("Hardware screen content").assertIsDisplayed()
        }
    }
}


