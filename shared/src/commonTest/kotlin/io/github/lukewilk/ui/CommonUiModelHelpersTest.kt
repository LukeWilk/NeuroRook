package io.github.lukewilk.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import io.github.lukewilk.ui.elements.navigation.menuItemHasDivider
import io.github.lukewilk.ui.elements.navigation.menuShowsTitle
import io.github.lukewilk.ui.elements.navigation.menuSidebarItemHasDivider
import io.github.lukewilk.ui.elements.navigation.menuSidebarSystemModeLabel
import io.github.lukewilk.ui.elements.navigation.menuSidebarWidth
import io.github.lukewilk.ui.elements.tables.shouldUseWeightedHeaderCell
import io.github.lukewilk.ui.elements.text.sectionTitleFontSizeSp
import io.github.lukewilk.ui.elements.text.sectionTitleFontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Common unit tests for the extracted pure UI helpers that back shared navigation and scaffold rendering.
 */
class CommonUiModelHelpersTest {
    /** Distinguishes a caller-supplied table-cell modifier from the default empty Modifier sentinel. */
    private object CustomModifierElement : Modifier.Element

    @Test
    fun `main scaffold helpers expose menu order fallback header text and placeholder text`() {
        // Verifies the shared scaffold model stays deterministic for tab order, header fallback text, and placeholder messages.
        val menuItems = mainScaffoldMenuItems()

        assertEquals(listOf("Hardware", "Protocols", "Electrodes", "Signals", "Graphs", "Goals", "Training"), menuItems.map { it.first })
        assertEquals(200, mainScaffoldSidebarWidth(isSidebarOpen = true))
        assertEquals(56, mainScaffoldSidebarWidth(isSidebarOpen = false))
        assertEquals("Neuro Rook", mainScaffoldHeaderFallbackText(isSidebarOpen = true, hasCustomHeader = false))
        assertNull(mainScaffoldHeaderFallbackText(isSidebarOpen = true, hasCustomHeader = true))
        assertEquals("≡", mainScaffoldHeaderFallbackText(isSidebarOpen = false, hasCustomHeader = false))
        assertNull(mainScaffoldPlaceholderLabel(selectedTab = 0, menuItems = menuItems))
        assertEquals("Signals", mainScaffoldPlaceholderLabel(selectedTab = 3, menuItems = menuItems))
        assertEquals("Signals screen coming soon...", mainScaffoldPlaceholderText("Signals"))
    }

    @Test
    fun `navigation helpers cover title visibility system mode labels widths and dividers`() {
        // Confirms the extracted navigation helpers keep sidebar and menu branching readable without UI harness involvement.
        assertTrue(menuShowsTitle("Quick actions"))
        assertFalse(menuShowsTitle(null))
        assertTrue(menuItemHasDivider(index = 0, lastIndex = 1))
        assertFalse(menuItemHasDivider(index = 1, lastIndex = 1))
        assertEquals(220, menuSidebarWidth(expanded = true, collapsedWidth = 56, expandedWidth = 220))
        assertEquals(56, menuSidebarWidth(expanded = false, collapsedWidth = 56, expandedWidth = 220))
        assertEquals("System: Dark mode", menuSidebarSystemModeLabel(true))
        assertEquals("System: Light mode", menuSidebarSystemModeLabel(false))
        assertNull(menuSidebarSystemModeLabel(null))
        assertTrue(menuSidebarItemHasDivider(index = 0, lastIndex = 2))
        assertFalse(menuSidebarItemHasDivider(index = 2, lastIndex = 2))
    }

    @Test
    fun `table header and section title helpers cover default styling decisions`() {
        // Verifies the extracted table and section-title style helpers preserve the intended defaults and custom-modifier detection.
        assertTrue(shouldUseWeightedHeaderCell(null))
        assertTrue(shouldUseWeightedHeaderCell(Modifier))
        assertFalse(shouldUseWeightedHeaderCell(Modifier.then(CustomModifierElement)))
        assertEquals(14, sectionTitleFontSizeSp())
        assertEquals(FontWeight.Medium, sectionTitleFontWeight())
    }
}


