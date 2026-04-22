package io.github.lukewilk.ui.elements.navigation

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test

/**
 * Compose tests for [MenuSidebar] behavior around expansion, system mode labels, and header slots.
 */
@OptIn(ExperimentalTestApi::class)
class MenuSidebarComposeJvmTest {
    @Test
    fun `menu sidebar shows system dark label when expanded and isSystemDark is true`() = runComposeUiTest {
        // Confirms the optional system-mode label reflects a dark desktop theme when the sidebar is expanded.
        setContent {
            MaterialTheme {
                MenuSidebar(
                    title = "App",
                    items = listOf("One" to {}, "Two" to {}),
                    isSystemDark = true,
                    icons = listOf(Icons.Outlined.Memory, Icons.Outlined.Article)
                )
            }
        }
        onNodeWithText("System: Dark mode").assertIsDisplayed()
        onNodeWithText("One").assertIsDisplayed()
    }

    @Test
    fun `menu sidebar shows system light label when expanded and isSystemDark is false`() = runComposeUiTest {
        // Confirms the optional system-mode label reflects a light desktop theme when the sidebar is expanded.
        setContent {
            MaterialTheme {
                MenuSidebar(
                    title = "App",
                    items = listOf("One" to {}),
                    isSystemDark = false,
                    icons = listOf(Icons.Outlined.Memory)
                )
            }
        }
        onNodeWithText("System: Light mode").assertIsDisplayed()
    }

    @Test
    fun `menu sidebar omits system mode line when isSystemDark is null`() = runComposeUiTest {
        // Covers the absence case so callers can omit the environment label entirely while the shell stays titleless.
        setContent {
            MaterialTheme {
                MenuSidebar(
                    title = "App",
                    items = listOf("One" to {}),
                    isSystemDark = null,
                    icons = listOf(Icons.Outlined.Memory)
                )
            }
        }
        onNodeWithText("App").assertDoesNotExist()
        onNodeWithText("System: Dark mode").assertDoesNotExist()
        onNodeWithText("System: Light mode").assertDoesNotExist()
    }

    @Test
    fun `menu sidebar uses header slot divider and suppresses duplicate menu title`() = runComposeUiTest {
        // Verifies branded header content still renders while the shell itself stays titleless.
        setContent {
            MaterialTheme {
                MenuSidebar(
                    title = "ShouldNotDuplicateInMenuColumn",
                    items = listOf("Alpha" to {}),
                    icons = listOf(Icons.Outlined.Article),
                    headerContent = { Text("BrandedHeaderSlot") }
                )
            }
        }
        onNodeWithText("ShouldNotDuplicateInMenuColumn").assertDoesNotExist()
        onNodeWithText("BrandedHeaderSlot").assertIsDisplayed()
        onNodeWithText("Alpha").assertIsDisplayed()
    }

    @Test
    fun `menu sidebar keeps items visible without rendering the optional title`() = runComposeUiTest {
        // Verifies callers can still pass a title string without reintroducing header text into the shell.
        setContent {
            MaterialTheme {
                MenuSidebar(
                    title = "VisibleMenuTitle",
                    items = listOf("Row" to {}),
                    icons = listOf(Icons.Outlined.Memory)
                )
            }
        }
        onNodeWithText("VisibleMenuTitle").assertDoesNotExist()
        onNodeWithText("Row").assertIsDisplayed()
    }

    @Test
    fun `menu sidebar toggles internal expansion when expanded prop is not controlled`() = runComposeUiTest {
        // Exercises the uncontrolled expansion path so the built-in menu button can collapse and reopen the sidebar.
        setContent {
            MaterialTheme {
                MenuSidebar(
                    title = "App",
                    items = listOf("Hardware" to {}),
                    icons = listOf(Icons.Outlined.Memory)
                )
            }
        }
        onNodeWithText("Hardware").assertIsDisplayed()
        onNodeWithContentDescription("Collapse sidebar").performClick()
        waitForIdle()
        onNodeWithText("Hardware").assertDoesNotExist()
        onNodeWithContentDescription("Expand sidebar").performClick()
        waitForIdle()
        onNodeWithText("Hardware").assertIsDisplayed()
    }

    @Test
    fun `menu sidebar respects external expanded state and onExpandedChange`() = runComposeUiTest {
        // Confirms the controlled expansion path delegates state changes back to the caller.
        setContent {
            MaterialTheme {
                var expanded by remember { mutableStateOf(true) }
                MenuSidebar(
                    title = "App",
                    items = listOf("Tab" to {}),
                    icons = listOf(Icons.Outlined.Memory),
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                )
            }
        }
        onNodeWithText("Tab").assertIsDisplayed()
        onNodeWithContentDescription("Collapse sidebar").performClick()
        waitForIdle()
        onNodeWithText("Tab").assertDoesNotExist()
        onNodeWithContentDescription("Expand sidebar").performClick()
        waitForIdle()
        onNodeWithText("Tab").assertIsDisplayed()
    }

    @Test
    fun `menu sidebar uses internal expanded state when onExpandedChange is null`() = runComposeUiTest {
        // Covers the fallback internal-state branch when callers pass neither controlled expansion callbacks nor state.
        setContent {
            MaterialTheme {
                MenuSidebar(
                    title = "App",
                    items = listOf("Solo" to {}),
                    icons = listOf(Icons.Outlined.Memory),
                    expanded = null,
                    onExpandedChange = null
                )
            }
        }
        onNodeWithText("Solo").assertIsDisplayed()
        onNodeWithContentDescription("Collapse sidebar").performClick()
        waitForIdle()
        onNodeWithText("Solo").assertDoesNotExist()
    }

    @Test
    fun `menu sidebar shows overflow cues while long navigation content can scroll`() = runComposeUiTest {
        // Confirms long menus advertise additional off-screen items and update the cue once users scroll downward.
        val menuItems = (1..20).map { index -> "Item $index" to {} }
        val menuIcons = List(menuItems.size) { Icons.Outlined.Memory }

        setContent {
            MaterialTheme {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.size(width = 220.dp, height = 240.dp)
                ) {
                    MenuSidebar(
                        title = "App",
                        items = menuItems,
                        icons = menuIcons
                    )
                }
            }
        }

        waitForIdle()

        onNodeWithContentDescription("More content below", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithContentDescription("More content above", useUnmergedTree = true).assertDoesNotExist()

        onNodeWithText("Item 20").performScrollTo().assertIsDisplayed()

        onNodeWithContentDescription("More content above", useUnmergedTree = true).assertIsDisplayed()
    }
}
