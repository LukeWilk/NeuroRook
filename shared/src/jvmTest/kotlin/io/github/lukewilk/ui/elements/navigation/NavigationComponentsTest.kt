package io.github.lukewilk.ui.elements.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

/**
 * UI smoke tests for the shared navigation components.
 */
@OptIn(ExperimentalTestApi::class)
class NavigationComponentsTest {
    @Test
    fun `menu item displays its label and invokes the click action`() {
        // Verifies the reusable menu item forwards user clicks to the provided callback.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    var clicks by remember { mutableIntStateOf(0) }
                    androidx.compose.foundation.layout.Column {
                        MenuItem(label = "Overview", onClick = { clicks += 1 })
                        androidx.compose.material3.Text("Clicks: $clicks")
                    }
                }
            }

            onNodeWithText("Overview").assertIsDisplayed().performClick()
            onNodeWithText("Clicks: 1").assertIsDisplayed()
        }
    }

    @Test
    fun `menu renders its title and forwards clicks from nested items`() {
        // Confirms the composed menu keeps its optional title visible while delegating actions through MenuItem children.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    var clicks by remember { mutableIntStateOf(0) }
                    androidx.compose.foundation.layout.Column {
                        Menu(
                            title = "Quick actions",
                            items = listOf(
                                "Connect" to { clicks += 1 },
                                "Disconnect" to { clicks += 10 }
                            )
                        )
                        androidx.compose.material3.Text("Clicks: $clicks")
                    }
                }
            }

            onNodeWithText("Quick actions").assertIsDisplayed()
            onNodeWithText("Disconnect").assertIsDisplayed().performClick()
            onNodeWithText("Clicks: 10").assertIsDisplayed()
        }
    }

    @Test
    fun `menu sidebar renders system mode title and items`() {
        // Verifies the sidebar surfaces its optional system-mode hint alongside the provided title and entries.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    MenuSidebar(
                        title = "Navigation",
                        items = listOf(
                            "Hardware" to {},
                            "Signals" to {}
                        ),
                        isSystemDark = true
                    )
                }
            }

            onNodeWithText("System: Dark mode").assertIsDisplayed()
            onNodeWithText("Navigation").assertIsDisplayed()
            onNodeWithText("Signals").assertIsDisplayed()
        }
    }

    @Test
    fun `menu sidebar renders the light mode hint when requested`() {
        // Covers the alternate system-mode label so both light and dark sidebar hints stay wired correctly.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    MenuSidebar(
                        title = "Navigation",
                        items = listOf("Hardware" to {}),
                        isSystemDark = false
                    )
                }
            }

            onNodeWithText("System: Light mode").assertIsDisplayed()
            onNodeWithText("Navigation").assertIsDisplayed()
        }
    }
}


