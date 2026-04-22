package io.github.lukewilk.ui.elements.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * UI smoke tests for the shared navigation components.
 */
@OptIn(ExperimentalTestApi::class)
class NavigationComponentsTest {
    @Test
    fun `menu item ui state data class supports copy and destructuring`() {
        var clicks = 0
        val original = MenuItemUiState(label = "Hardware", onClick = { clicks += 1 })
        val updated = original.copy(icon = Icons.Outlined.Memory, selected = true)
        val (label, onClick, icon, selected) = updated

        assertEquals("Hardware", label)
        assertEquals(Icons.Outlined.Memory, icon)
        assertTrue(selected)

        onClick()
        assertEquals(1, clicks)
    }

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
                        MenuItem(label = "Hardware", onClick = { clicks += 1 }, icon = Icons.Outlined.Memory, selected = true)
                        androidx.compose.material3.Text("Clicks: $clicks")
                    }
                }
            }

            onNodeWithContentDescription("Hardware navigation icon").assertIsDisplayed()
            onNodeWithText("Hardware").assertIsDisplayed().performClick()
            onNodeWithText("Clicks: 1").assertIsDisplayed()
        }
    }

    @Test
    fun `menu item ui state overload renders and delegates click`() {
        // Covers the MenuItem(MenuItemUiState) overload used by Menu mapping.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    var clicks by remember { mutableIntStateOf(0) }
                    androidx.compose.foundation.layout.Column {
                        MenuItem(
                            item = MenuItemUiState(
                                label = "Overview",
                                onClick = { clicks += 1 },
                                icon = null,
                                selected = false
                            )
                        )
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
    fun `menu renders icons and preserves selected item state for shell navigation`() {
        // Covers the richer shell-menu path where items include icons and one selected destination.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Menu(
                        title = "Shell",
                        items = listOf("Hardware" to {}, "Protocols" to {}),
                        icons = listOf(Icons.Outlined.Memory, Icons.Outlined.Article),
                        selectedIndex = 1
                    )
                }
            }

            onNodeWithText("Shell").assertIsDisplayed()
            onNodeWithContentDescription("Hardware navigation icon").assertIsDisplayed()
            onNodeWithContentDescription("Protocols navigation icon").assertIsDisplayed()
            onNodeWithText("Protocols").assertIsDisplayed()
        }
    }

    @Test
    fun `menu can render compact icon-only navigation rows`() {
        // Verifies the collapsed-sidebar menu mode hides labels while keeping the navigation icons visible.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Menu(
                        title = null,
                        items = listOf("Hardware" to {}, "Protocols" to {}),
                        icons = listOf(Icons.Outlined.Memory, Icons.Outlined.Article),
                        compact = true
                    )
                }
            }

            onNodeWithText("Hardware").assertDoesNotExist()
            onNodeWithText("Protocols").assertDoesNotExist()
            onNodeWithContentDescription("Hardware navigation icon").assertIsDisplayed()
            onNodeWithContentDescription("Protocols navigation icon").assertIsDisplayed()
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
    fun `menu sidebar renders system mode hint and items without a header title`() {
        // Verifies the sidebar surfaces its optional system-mode hint alongside entries while the chevron row stays titleless.
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
            onNodeWithText("Navigation").assertDoesNotExist()
            onNodeWithText("Signals").assertIsDisplayed()
        }
    }

    @Test
    fun `menu sidebar renders the light mode hint when requested`() {
        // Covers the alternate system-mode label so both light and dark sidebar hints stay wired correctly without showing header text.
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
            onNodeWithText("Navigation").assertDoesNotExist()
        }
    }

    @Test
    fun `menu sidebar omits the system mode hint when no system theme is provided`() {
        // Verifies the nullable system-theme branch leaves the sidebar items intact without rendering a hint line or title text.
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

            onNodeWithText("Navigation").assertDoesNotExist()
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

            onNodeWithContentDescription("Collapse sidebar").assertIsDisplayed()
            onNodeWithText("System: Dark mode").assertIsDisplayed()
            onNodeWithText("Navigation").assertDoesNotExist()

            onNodeWithContentDescription("Collapse sidebar").performClick()
            onNodeWithText("System: Dark mode").assertDoesNotExist()

            onNodeWithContentDescription("Expand sidebar").assertIsDisplayed().performClick()
            onNodeWithText("System: Dark mode").assertIsDisplayed()
        }
    }

    @Test
    fun `menu sidebar omits the menu title when header content owns the heading slot`() {
        // Verifies branded header content owns the visible heading while the chevron row stays titleless.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    MenuSidebar(
                        title = "Hidden Shell Title",
                        headerContent = { androidx.compose.material3.Text("Visible Shell Header") },
                        items = listOf("Hardware" to {}),
                        icons = listOf(Icons.Outlined.Memory),
                        selectedIndex = 0
                    )
                }
            }

            onNodeWithText("Hidden Shell Title").assertDoesNotExist()
            onNodeWithText("Visible Shell Header").assertIsDisplayed()
            onNodeWithText("Hardware").assertIsDisplayed()
        }
    }

    @Test
    fun `menu sidebar shows custom header and divider without system hint when theme is unknown`() {
        // Covers the expanded branch where systemModeLabel is null and the custom header renders below a titleless toggle row.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    MenuSidebar(
                        title = "Hidden Shell Title",
                        headerContent = { androidx.compose.material3.Text("Brand Strip") },
                        items = listOf("Hardware" to {}),
                        isSystemDark = null
                    )
                }
            }

            onNodeWithText("Brand Strip").assertIsDisplayed()
            onNodeWithText("Hardware").assertIsDisplayed()
            onNodeWithText("System: Dark mode").assertDoesNotExist()
            onNodeWithText("System: Light mode").assertDoesNotExist()
            onNodeWithText("Hidden Shell Title").assertDoesNotExist()
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
                        icons = listOf(Icons.Outlined.Memory, Icons.Outlined.Article),
                        selectedIndex = 1,
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    )
                }
            }

            onNodeWithText("Shell Header").assertIsDisplayed()
            onNodeWithContentDescription("Hardware navigation icon").assertIsDisplayed()
            onNodeWithContentDescription("Protocols navigation icon").assertIsDisplayed()
            onNodeWithText("Protocols").assertIsDisplayed()
            onNodeWithContentDescription("Collapse sidebar").performClick()
            onNodeWithText("Shell Header").assertDoesNotExist()
            onNodeWithText("Protocols").assertDoesNotExist()
            onNodeWithContentDescription("Hardware navigation icon").assertIsDisplayed()
            onNodeWithContentDescription("Protocols navigation icon").assertIsDisplayed()
            onNodeWithContentDescription("Expand sidebar").assertIsDisplayed().performClick()
            onNodeWithText("Shell Header").assertIsDisplayed()
        }
    }

    @Test
    fun `menu maps items when icons list is shorter than item count`() {
        // Covers the icon indexing path so trailing menu rows still render when callers omit optional icons.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Menu(
                        title = "Nav",
                        items = listOf(
                            "Alpha" to {},
                            "Beta" to {},
                            "Gamma" to {}
                        ),
                        icons = listOf(Icons.Outlined.Article, Icons.Outlined.Info)
                    )
                }
            }

            onNodeWithText("Nav").assertIsDisplayed()
            onNodeWithText("Alpha").assertIsDisplayed()
            onNodeWithText("Beta").assertIsDisplayed()
            onNodeWithText("Gamma").assertIsDisplayed()
        }
    }

    @Test
    fun `menu sidebar keeps parent expanded true when expansion callback is omitted`() {
        // Covers expanded != null with onExpandedChange == null so updateExpanded only mutates internal state while isExpanded stays pinned open.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    MenuSidebar(
                        title = "Pinned Open",
                        items = listOf("Hardware" to {}),
                        expanded = true,
                        onExpandedChange = null
                    )
                }
            }

            onNodeWithText("Pinned Open").assertDoesNotExist()
            onNodeWithContentDescription("Collapse sidebar").performClick()
            onNodeWithText("Pinned Open").assertDoesNotExist()
            onNodeWithText("Hardware").assertIsDisplayed()
        }
    }

    @Test
    fun `menu sidebar stays collapsed when pinned closed without an expansion callback`() {
        // Covers expanded == false with onExpandedChange == null so internal toggles cannot override the forced collapsed width branch.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    MenuSidebar(
                        title = "Hidden While Collapsed",
                        items = listOf("Hardware" to {}),
                        expanded = false,
                        onExpandedChange = null
                    )
                }
            }

            onNodeWithContentDescription("Expand sidebar").assertIsDisplayed()
            onNodeWithText("Hidden While Collapsed").assertDoesNotExist()
            onNodeWithText("Hardware").assertDoesNotExist()
            onNodeWithContentDescription("Expand sidebar").performClick()
            onNodeWithText("Hidden While Collapsed").assertDoesNotExist()
            onNodeWithText("Hardware").assertDoesNotExist()
        }
    }

    @Test
    fun `menu sidebar collapses to the menu glyph and can be expanded again`() {
        // Covers the collapsed-width branch so the sidebar can hide and then restore its items deterministically without header text.
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

            onNodeWithContentDescription("Collapse sidebar").performClick()
            onNodeWithText("Navigation").assertDoesNotExist()
            onNodeWithText("Signals").assertDoesNotExist()
            onNodeWithContentDescription("Hardware").assertIsDisplayed()
            onNodeWithContentDescription("Expand sidebar").assertIsDisplayed().performClick()
            onNodeWithText("Navigation").assertDoesNotExist()
            onNodeWithText("Signals").assertIsDisplayed()
        }
    }
}


