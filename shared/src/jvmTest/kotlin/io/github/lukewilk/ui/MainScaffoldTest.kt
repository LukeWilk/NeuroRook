package io.github.lukewilk.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

/**
 * UI smoke tests for the shared navigation scaffold.
 */
@OptIn(ExperimentalTestApi::class)
class MainScaffoldTest {
    @Test
    fun `placeholder screen renders placeholder text directly`() {
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    PlaceholderScreen("Signals")
                }
            }

            onNodeWithText("Signals screen coming soon...").assertIsDisplayed()
        }
    }

    @Test
    fun `main scaffold renders the default shell without a built in title`() {
        // Covers the null header-content branch so the scaffold exposes the collapse toggle without rendering fallback title text.
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

            onNodeWithContentDescription("Collapse sidebar").assertIsDisplayed()
            onNodeWithText("Neuro Rook").assertDoesNotExist()
            onNodeWithText("Hardware screen content").assertIsDisplayed()
        }
    }

    @Test
    fun `main scaffold renders custom header content and the hardware screen slot`() {
        // Verifies the shared scaffold renders caller-provided header content without reintroducing fallback title text.
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

            onNodeWithText("Neuro Rook").assertDoesNotExist()
            onNodeWithText("Custom NeuroRook Header").assertIsDisplayed()
            onNodeWithText("Hardware screen content").assertIsDisplayed()
            onNodeWithText("Hardware").assertIsDisplayed()
        }
    }

    @Test
    fun `main scaffold collapses to the icon toggle and restores custom header content when reopened`() {
        // Covers the custom-header collapsed branch so the scaffold swaps to the compact toggle affordance and restores the caller header on reopen.
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
            onNodeWithContentDescription("Collapse sidebar").assertIsDisplayed().performClick()
            onNodeWithText("Custom NeuroRook Header").assertDoesNotExist()
            onNodeWithContentDescription("Expand sidebar").assertIsDisplayed().performClick()
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
    fun `main scaffold shows the collapsed expand icon after closing the sidebar`() {
        // Exercises the collapsed-sidebar header branch so the compact expand affordance and icon-only navigation stay visible.
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

            onNodeWithContentDescription("Collapse sidebar").performClick()
            onNodeWithContentDescription("Expand sidebar").assertIsDisplayed()
            onNodeWithContentDescription("Hardware navigation icon").assertIsDisplayed()
        }
    }

    @Test
    fun `main scaffold keeps the default header title absent after collapsing and reopening`() {
        // Covers the titleless default-header branch when isSidebarOpen flips false then true without custom header content.
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

            onNodeWithText("Neuro Rook").assertDoesNotExist()
            onNodeWithContentDescription("Collapse sidebar").performClick()
            onNodeWithText("Neuro Rook").assertDoesNotExist()
            onNodeWithContentDescription("Expand sidebar").performClick()
            onNodeWithText("Neuro Rook").assertDoesNotExist()
            onNodeWithText("Hardware screen content").assertIsDisplayed()
        }
    }

    @Test
    fun `main scaffold shell workflow switches sections collapses and returns to hardware`() {
        // Covers the classical shell workflow so the scaffold keeps collapsed icon-only navigation interactive.
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
            onNodeWithContentDescription("Collapse sidebar").performClick()
            onNodeWithText("Protocols").assertDoesNotExist()
            onNodeWithContentDescription("Protocols navigation icon").assertIsDisplayed()
            onNodeWithContentDescription("Hardware").performClick()
            onNodeWithText("Hardware screen content").assertIsDisplayed()
        }
    }

    @Test
    fun `main scaffold shows attribution license and repository details on the about page`() {
        // Covers the dedicated About destination that documents icon attribution together with the NeuroRook repository and MIT license.
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

            onNodeWithText("About").performScrollTo().performClick()
            onNodeWithText("About Neuro Rook").assertIsDisplayed()
            onNodeWithText("Icon attribution").assertIsDisplayed()
            onNodeWithText("• Material Symbols / Material Icons by Google\n• Licensed under the Apache License 2.0\n• Source: https://fonts.google.com/icons").assertIsDisplayed()
            onNodeWithText("NeuroRook").assertIsDisplayed()
            onNodeWithText("Repository").assertIsDisplayed()
            onNodeWithText(NEUROROOK_REPOSITORY_URL).assertIsDisplayed().assertHasClickAction()
            onNodeWithText("License: MIT License\nCopyright (c) 2026 Luke Wilk").assertIsDisplayed()
        }
    }
}


