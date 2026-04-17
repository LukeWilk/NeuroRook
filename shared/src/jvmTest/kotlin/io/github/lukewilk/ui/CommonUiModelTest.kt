package io.github.lukewilk.ui

import androidx.compose.ui.text.font.FontWeight
import io.github.lukewilk.ui.elements.navigation.menuItemHasDivider
import io.github.lukewilk.ui.elements.navigation.menuShowsTitle
import io.github.lukewilk.ui.elements.navigation.menuSidebarItemHasDivider
import io.github.lukewilk.ui.elements.navigation.menuSidebarSystemModeLabel
import io.github.lukewilk.ui.elements.navigation.menuSidebarWidth
import io.github.lukewilk.ui.elements.text.sectionTitleFontSizeSp
import io.github.lukewilk.ui.elements.text.sectionTitleFontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * JVM tests for shared UI model logic that is easier to validate outside a Compose harness.
 */
class CommonUiModelTest {
    @Test
    fun `main scaffold state exposes menu order selection and placeholder state`() {
        val menuItems = mainScaffoldMenuItems()
        val defaultMenuItemsState = mainScaffoldUiState(
            selectedTab = 2,
            isSidebarOpen = true,
            hasCustomHeader = false
        )
        assertEquals(7, defaultMenuItemsState.menuItems.size)
        assertEquals("Electrodes", defaultMenuItemsState.menuItems[2].label)
        assertTrue(defaultMenuItemsState.menuItems[2].selected)

        val defaultUiState = mainScaffoldUiState(selectedTab = 0, isSidebarOpen = true, hasCustomHeader = false, menuItems = menuItems)
        val customCollapsedUiState = mainScaffoldUiState(selectedTab = 3, isSidebarOpen = false, hasCustomHeader = true, menuItems = menuItems)

        assertEquals(listOf("Hardware", "Protocols", "Electrodes", "Signals", "Graphs", "Goals", "Training"), menuItems.map { it.first })
        assertNull(mainScaffoldPlaceholderLabel(selectedTab = 0, menuItems = menuItems))
        assertNull(mainScaffoldPlaceholderLabel(selectedTab = -1, menuItems = menuItems))
        assertEquals("Signals", mainScaffoldPlaceholderLabel(selectedTab = 3, menuItems = menuItems))
        assertEquals("Training", mainScaffoldPlaceholderLabel(selectedTab = 6, menuItems = menuItems))
        assertEquals("Protocols", mainScaffoldPlaceholderLabel(selectedTab = 1))
        assertNull(mainScaffoldPlaceholderLabel(selectedTab = 999, menuItems = menuItems))
        assertNull(mainScaffoldPlaceholderLabel(selectedTab = 1, menuItems = emptyList()))
        assertEquals(200, defaultUiState.sidebarWidth)
        assertEquals("Neuro Rook", defaultUiState.defaultHeaderTitle)
        assertNull(defaultUiState.placeholderLabel)
        assertTrue(defaultUiState.menuItems.first().selected)
        assertEquals("Signals", customCollapsedUiState.placeholderLabel)
        assertNull(customCollapsedUiState.defaultHeaderTitle)
        assertEquals(56, customCollapsedUiState.sidebarWidth)
        assertTrue(customCollapsedUiState.menuItems[3].selected)
    }

    @Test
    fun `navigation state covers title visibility system mode labels widths and dividers`() {
        assertTrue(menuShowsTitle("Quick actions"))
        assertFalse(menuShowsTitle(null))
        assertTrue(menuItemHasDivider(index = 0, lastIndex = 1))
        assertFalse(menuItemHasDivider(index = 1, lastIndex = 1))
        assertEquals(220, menuSidebarWidth(expanded = true, collapsedWidth = 56, expandedWidth = 220))
        assertEquals(56, menuSidebarWidth(expanded = false, collapsedWidth = 56, expandedWidth = 220))
        assertEquals(180, menuSidebarWidth(expanded = true, collapsedWidth = 48, expandedWidth = 180))
        assertEquals(48, menuSidebarWidth(expanded = false, collapsedWidth = 48, expandedWidth = 180))
        assertEquals("System: Dark mode", menuSidebarSystemModeLabel(true))
        assertEquals("System: Light mode", menuSidebarSystemModeLabel(false))
        assertNull(menuSidebarSystemModeLabel(null))
        assertTrue(menuSidebarItemHasDivider(index = 0, lastIndex = 2))
        assertFalse(menuSidebarItemHasDivider(index = 2, lastIndex = 2))
    }

    @Test
    fun `section title model keeps the intended default typography`() {
        assertEquals(14, sectionTitleFontSizeSp())
        assertEquals(FontWeight.Medium, sectionTitleFontWeight())
    }
}
