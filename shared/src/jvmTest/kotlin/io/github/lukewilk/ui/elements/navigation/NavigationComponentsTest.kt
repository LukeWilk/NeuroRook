package io.github.lukewilk.ui.elements.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
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
    fun `menu item renders optional icon and selected styling while still invoking clicks`() {
        // Covers the richer shell-navigation row path that includes an icon and selected state without changing callback behavior.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    var clicks by remember { mutableIntStateOf(0) }
                    androidx.compose.foundation.layout.Column {
                        MenuItem(label = "Hardware", onClick = { clicks += 1 }, icon = "🔧", selected = true)
                        androidx.compose.material3.Text("Clicks: $clicks")
                    }
                }
            }

            onNodeWithText("🔧").assertIsDisplayed()
            onNodeWithText("Hardware").assertIsDisplayed().performClick()
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
    fun `menu renders icons and preserves selected item state for shell navigation`() {
        // Covers the richer shell-menu path where items include icons and one selected destination.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Menu(
                        title = "Shell",
                        items = listOf("Hardware" to {}, "Protocols" to {}),
                        icons = listOf("🔧", "📡"),
                        selectedIndex = 1
                    )
                }
            }

            onNodeWithText("Shell").assertIsDisplayed()
            onNodeWithText("🔧").assertIsDisplayed()
            onNodeWithText("📡").assertIsDisplayed()
            onNodeWithText("Protocols").assertIsDisplayed()
        }
    }

    @Test
    fun `menu renders its title even when callers provide no items`() {
        // Covers the zero-item path so the menu can render only its optional heading without producing child entries.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Menu(
                        title = "Quick actions",
                        items = emptyList()
                    )
                }
            }

            onNodeWithText("Quick actions").assertIsDisplayed()
            onNodeWithText("Connect").assertDoesNotExist()
        }
    }

    @Test
    fun `menu omits its title block when callers pass null`() {
        // Covers the nullable title branch while keeping the nested menu action path active.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Menu(
                        title = null,
                        items = listOf("Connect" to {}),
                        modifier = Modifier.width(180.dp)
                    )
                }
            }

            onNodeWithText("Connect").assertIsDisplayed()
            onNodeWithText("Quick actions").assertDoesNotExist()
        }
    }

    @Test
    fun `menu forwards clicks from a titleless single item when using the default modifier`() {
        // Covers the default-modifier single-item path so the menu body still delegates clicks without a heading or custom sizing.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    var clicks by remember { mutableIntStateOf(0) }
                    androidx.compose.foundation.layout.Column {
                        Menu(
                            title = null,
                            items = listOf("Only action" to { clicks += 1 })
                        )
                        androidx.compose.material3.Text("Clicks: $clicks")
                    }
                }
            }

            onNodeWithText("Only action").assertIsDisplayed().performClick()
            onNodeWithText("Clicks: 1").assertIsDisplayed()
            onNodeWithText("Quick actions").assertDoesNotExist()
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

    @Test
    fun `menu sidebar omits the system mode hint when no system theme is provided`() {
        // Verifies the nullable system-theme branch leaves the sidebar title and items intact without rendering a hint line.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    MenuSidebar(
                        title = "Navigation",
                        items = listOf("Hardware" to {}, "Signals" to {}),
                        isSystemDark = null
                    )
                }
            }

            onNodeWithText("Navigation").assertIsDisplayed()
            onNodeWithText("Hardware").assertIsDisplayed()
            onNodeWithText("System: Dark mode").assertDoesNotExist()
            onNodeWithText("System: Light mode").assertDoesNotExist()
        }
    }

    @Test
    fun `menu sidebar supports a null title and empty item list while toggling the system label`() {
        // Covers the titleless and empty-item sidebar path so the expanded and collapsed shells remain stable without navigation entries.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    MenuSidebar(
                        title = null,
                        items = emptyList(),
                        isSystemDark = true
                    )
                }
            }

            onNodeWithText("≡").assertIsDisplayed()
            onNodeWithText("System: Dark mode").assertIsDisplayed()
            onNodeWithText("Navigation").assertDoesNotExist()

            onNodeWithText("≡").performClick()
            onNodeWithText("System: Dark mode").assertDoesNotExist()

            onNodeWithText("≡").assertIsDisplayed().performClick()
            onNodeWithText("System: Dark mode").assertIsDisplayed()
        }
    }

    @Test
    fun `menu sidebar supports controlled expansion custom header content and selected icons`() {
        // Covers the controlled shell-sidebar path so parent-owned expansion and custom header content route through Menu and MenuItem.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    var expanded by remember { mutableStateOf(true) }
                    MenuSidebar(
                        title = null,
                        headerContent = { androidx.compose.material3.Text("Shell Header") },
                        items = listOf("Hardware" to {}, "Protocols" to {}),
                        icons = listOf("🔧", "📡"),
                        selectedIndex = 1,
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    )
                }
            }

            onNodeWithText("Shell Header").assertIsDisplayed()
            onNodeWithText("🔧").assertIsDisplayed()
            onNodeWithText("📡").assertIsDisplayed()
            onNodeWithText("Protocols").assertIsDisplayed()
            onNodeWithText("≡").performClick()
            onNodeWithText("Shell Header").assertDoesNotExist()
            onNodeWithText("Protocols").assertDoesNotExist()
            onNodeWithText("≡").assertIsDisplayed().performClick()
            onNodeWithText("Shell Header").assertIsDisplayed()
        }
    }

    @Test
    fun `menu sidebar collapses to the menu glyph and can be expanded again`() {
        // Covers the collapsed-width branch so the sidebar can hide and then restore its title and items deterministically.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    MenuSidebar(
                        title = "Navigation",
                        items = listOf("Hardware" to {}, "Signals" to {}),
                        collapsedWidth = 48,
                        expandedWidth = 180,
                        isSystemDark = true
                    )
                }
            }

            onNodeWithText("≡").performClick()
            onNodeWithText("Navigation").assertDoesNotExist()
            onNodeWithText("Signals").assertDoesNotExist()
            onNodeWithText("≡").assertIsDisplayed().performClick()
            onNodeWithText("Navigation").assertIsDisplayed()
            onNodeWithText("Signals").assertIsDisplayed()
        }
    }
}


