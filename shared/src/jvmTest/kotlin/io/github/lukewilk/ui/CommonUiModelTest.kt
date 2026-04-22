package io.github.lukewilk.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.ui.text.font.FontWeight
import io.github.lukewilk.ui.elements.scroll.verticalScrollCueContentDescription
import io.github.lukewilk.ui.elements.scroll.verticalScrollCueVisibility
import io.github.lukewilk.ui.elements.navigation.menuItemHasDivider
import io.github.lukewilk.ui.elements.navigation.menuShowsTitle
import io.github.lukewilk.ui.elements.navigation.menuSidebarItemHasDivider
import io.github.lukewilk.ui.elements.navigation.menuSidebarToggleIcon
import io.github.lukewilk.ui.elements.navigation.menuSidebarSystemModeLabel
import io.github.lukewilk.ui.elements.navigation.menuSidebarToggleContentDescription
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
            selectedTab = 6,
            isSidebarOpen = true,
            hasCustomHeader = false
        )
        assertEquals(12, defaultMenuItemsState.menuItems.size)
        assertEquals("Electrodes", defaultMenuItemsState.menuItems[6].label)
        assertTrue(defaultMenuItemsState.menuItems[6].selected)

        val defaultUiState = mainScaffoldUiState(selectedTab = 0, isSidebarOpen = true, hasCustomHeader = false, menuItems = menuItems)
        val customCollapsedUiState = mainScaffoldUiState(selectedTab = 7, isSidebarOpen = false, hasCustomHeader = true, menuItems = menuItems)

        assertEquals(
            listOf(
                "Hardware",
                "Test Signal Noise",
                "Raw Data",
                "Protocols",
                "Baseline",
                "Aggregation & Reward",
                "Electrodes",
                "Signals",
                "Graphs",
                "Goals",
                "Training",
                "About"
            ),
            menuItems.map { it.label }
        )
        assertNull(mainScaffoldPlaceholderLabel(selectedTab = 0, menuItems = menuItems))
        assertNull(mainScaffoldPlaceholderLabel(selectedTab = -1, menuItems = menuItems))
        assertEquals("Protocols", mainScaffoldPlaceholderLabel(selectedTab = 3, menuItems = menuItems))
        assertEquals("Training", mainScaffoldPlaceholderLabel(selectedTab = 10, menuItems = menuItems))
        assertEquals("Test Signal Noise", mainScaffoldPlaceholderLabel(selectedTab = 1))
        assertNull(mainScaffoldPlaceholderLabel(selectedTab = 11, menuItems = menuItems))
        assertNull(mainScaffoldPlaceholderLabel(selectedTab = 999, menuItems = menuItems))
        assertNull(mainScaffoldPlaceholderLabel(selectedTab = 1, menuItems = emptyList()))
        assertEquals(200, defaultUiState.sidebarWidth)
        assertEquals("Neuro Rook", defaultUiState.defaultHeaderTitle)
        assertEquals(MainScaffoldDestination.Hardware, defaultUiState.selectedDestination)
        assertTrue(defaultUiState.menuItems.first().selected)
        assertEquals(MainScaffoldDestination.Signals, customCollapsedUiState.selectedDestination)
        assertNull(customCollapsedUiState.defaultHeaderTitle)
        assertEquals(56, customCollapsedUiState.sidebarWidth)
        assertTrue(customCollapsedUiState.menuItems[7].selected)
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
        assertEquals("Collapse sidebar", menuSidebarToggleContentDescription(true))
        assertEquals("Expand sidebar", menuSidebarToggleContentDescription(false))
        assertEquals(Icons.Outlined.ChevronLeft, menuSidebarToggleIcon(expanded = true))
        assertEquals(Icons.Outlined.ChevronRight, menuSidebarToggleIcon(expanded = false))
        assertTrue(menuSidebarItemHasDivider(index = 0, lastIndex = 2))
        assertFalse(menuSidebarItemHasDivider(index = 2, lastIndex = 2))
    }

    @Test
    fun `scroll cue model exposes top and bottom affordances only when overflow exists`() {
        val noOverflow = verticalScrollCueVisibility(scrollValue = 0, maxScrollValue = 0)
        val atTop = verticalScrollCueVisibility(scrollValue = 0, maxScrollValue = 24)
        val inMiddle = verticalScrollCueVisibility(scrollValue = 12, maxScrollValue = 24)
        val atBottom = verticalScrollCueVisibility(scrollValue = 24, maxScrollValue = 24)

        assertFalse(noOverflow.showTopCue)
        assertFalse(noOverflow.showBottomCue)
        assertFalse(atTop.showTopCue)
        assertTrue(atTop.showBottomCue)
        assertTrue(inMiddle.showTopCue)
        assertTrue(inMiddle.showBottomCue)
        assertTrue(atBottom.showTopCue)
        assertFalse(atBottom.showBottomCue)
        assertEquals("More content above", verticalScrollCueContentDescription(isTopCue = true))
        assertEquals("More content below", verticalScrollCueContentDescription(isTopCue = false))
    }

    @Test
    fun `section title model keeps the intended default typography`() {
        assertEquals(14, sectionTitleFontSizeSp())
        assertEquals(FontWeight.Medium, sectionTitleFontWeight())
    }
}
