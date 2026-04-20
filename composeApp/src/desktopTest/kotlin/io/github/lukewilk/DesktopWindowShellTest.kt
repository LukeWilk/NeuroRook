package io.github.lukewilk

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for the extracted desktop window shell defaults.
 */
class DesktopWindowShellTest {
    @Test
    fun `default desktop window spec matches the published shell geometry`() {
        // Verifies the extracted shell defaults still point to the same title, size, and bundled app icon.
        val spec = defaultDesktopWindowSpec()

        assertEquals("NeuroRook", spec.title)
        assertEquals(1440, spec.widthDp)
        assertEquals(960, spec.heightDp)
        assertEquals(DESKTOP_APP_ICON_RESOURCE, spec.iconResourcePath)
    }
}

