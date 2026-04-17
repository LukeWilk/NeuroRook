package io.github.lukewilk.ui.elements.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

/**
 * Compose tests for [MenuSidebar] behavior around expansion, system mode labels, and header slots.
 */
@OptIn(ExperimentalTestApi::class)
class MenuSidebarComposeJvmTest {
    @Test
    fun `menu sidebar shows system dark label when expanded and isSystemDark is true`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MenuSidebar(
                    title = "App",
                    items = listOf("One" to {}, "Two" to {}),
                    isSystemDark = true,
                    icons = listOf("1", "2")
                )
            }
        }
        onNodeWithText("System: Dark mode").assertIsDisplayed()
        onNodeWithText("One").assertIsDisplayed()
    }

    @Test
    fun `menu sidebar shows system light label when expanded and isSystemDark is false`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MenuSidebar(
                    title = "App",
                    items = listOf("One" to {}),
                    isSystemDark = false,
                    icons = listOf("1")
                )
            }
        }
        onNodeWithText("System: Light mode").assertIsDisplayed()
    }

    @Test
    fun `menu sidebar omits system mode line when isSystemDark is null`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MenuSidebar(
                    title = "App",
                    items = listOf("One" to {}),
                    isSystemDark = null,
                    icons = listOf("1")
                )
            }
        }
        onNodeWithText("App").assertIsDisplayed()
        onNodeWithText("System: Dark mode").assertDoesNotExist()
        onNodeWithText("System: Light mode").assertDoesNotExist()
    }

    @Test
    fun `menu sidebar uses header slot divider and suppresses duplicate menu title`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MenuSidebar(
                    title = "ShouldNotDuplicateInMenuColumn",
                    items = listOf("Alpha" to {}),
                    icons = listOf("🔤"),
                    headerContent = { Text("BrandedHeaderSlot") }
                )
            }
        }
        onNodeWithText("BrandedHeaderSlot").assertIsDisplayed()
        onNodeWithText("Alpha").assertIsDisplayed()
        onNodeWithText("ShouldNotDuplicateInMenuColumn").assertDoesNotExist()
    }

    @Test
    fun `menu sidebar passes title into menu when header content is absent`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MenuSidebar(
                    title = "VisibleMenuTitle",
                    items = listOf("Row" to {}),
                    icons = listOf("🔧")
                )
            }
        }
        onNodeWithText("VisibleMenuTitle").assertIsDisplayed()
        onNodeWithText("Row").assertIsDisplayed()
    }

    @Test
    fun `menu sidebar toggles internal expansion when expanded prop is not controlled`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MenuSidebar(
                    title = "App",
                    items = listOf("Hardware" to {}),
                    icons = listOf("🔧")
                )
            }
        }
        onNodeWithText("Hardware").assertIsDisplayed()
        onNodeWithText("≡").performClick()
        waitForIdle()
        onNodeWithText("Hardware").assertDoesNotExist()
        onNodeWithText("≡").performClick()
        waitForIdle()
        onNodeWithText("Hardware").assertIsDisplayed()
    }

    @Test
    fun `menu sidebar respects external expanded state and onExpandedChange`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                var expanded by remember { mutableStateOf(true) }
                MenuSidebar(
                    title = "App",
                    items = listOf("Tab" to {}),
                    icons = listOf("🔧"),
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                )
            }
        }
        onNodeWithText("Tab").assertIsDisplayed()
        onNodeWithText("≡").performClick()
        waitForIdle()
        onNodeWithText("Tab").assertDoesNotExist()
        onNodeWithText("≡").performClick()
        waitForIdle()
        onNodeWithText("Tab").assertIsDisplayed()
    }

    @Test
    fun `menu sidebar uses internal expanded state when onExpandedChange is null`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MenuSidebar(
                    title = "App",
                    items = listOf("Solo" to {}),
                    icons = listOf("🔧"),
                    expanded = null,
                    onExpandedChange = null
                )
            }
        }
        onNodeWithText("Solo").assertIsDisplayed()
        onNodeWithText("≡").performClick()
        waitForIdle()
        onNodeWithText("Solo").assertDoesNotExist()
    }
}
