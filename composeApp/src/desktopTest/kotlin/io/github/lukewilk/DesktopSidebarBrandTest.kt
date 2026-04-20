package io.github.lukewilk

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.lukewilk.theming.contrastRatio
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavior and loader tests for the desktop sidebar brand.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopSidebarBrandTest {
    @Test
    fun `sidebar resource readers return null for missing loader or missing resource`() {
        // Verifies the classpath reader stays null-safe when the sidebar logo cannot be resolved.
        assertNull(readDesktopSidebarResourceBytes(null, "any.svg"))
        assertNull(readDesktopSidebarLogoBytes(null))

        val missingStreamLoader = object : ClassLoader() {
            override fun getResourceAsStream(name: String?): java.io.InputStream? = null
        }
        assertNull(readDesktopSidebarResourceBytes(missingStreamLoader, "neuroRook.svg"))
    }

    @Test
    fun `desktop sidebar brand uses injected painter provider`() = runComposeUiTest {
        // Confirms callers can inject a custom painter and still get the branded image slot instead of the text fallback.
        setContent {
            MaterialTheme {
                DesktopSidebarBrand(logoPainterProvider = { ColorPainter(Color.Magenta) })
            }
        }
        onNodeWithContentDescription("NeuroRook logo").assertIsDisplayed()
        onNodeWithText("NeuroRook").assertDoesNotExist()
    }

    @Test
    fun `desktop sidebar brand shows text fallback when painter provider returns null`() = runComposeUiTest {
        // Covers the null-provider branch so branding remains visible even when the SVG asset cannot be loaded.
        setContent {
            MaterialTheme {
                DesktopSidebarBrand(logoPainterProvider = { null })
            }
        }
        onNodeWithText("NeuroRook").assertIsDisplayed()
        onNodeWithContentDescription("NeuroRook logo").assertDoesNotExist()
    }

    @Test
    fun `desktop sidebar brand content renders vector slot when painter is non null`() = runComposeUiTest {
        // Exercises the direct content helper with a concrete painter so the image branch stays independently testable.
        setContent {
            MaterialTheme {
                DesktopSidebarBrandContent(logoPainter = ColorPainter(Color.Red))
            }
        }
        onNodeWithContentDescription("NeuroRook logo").assertIsDisplayed()
        onNodeWithText("NeuroRook").assertDoesNotExist()
    }

    @Test
    fun `desktop sidebar brand content renders text fallback when painter is null`() = runComposeUiTest {
        // Exercises the direct content helper fallback path without going through the outer loader wrapper.
        setContent {
            MaterialTheme {
                DesktopSidebarBrandContent(logoPainter = null)
            }
        }
        onNodeWithText("NeuroRook").assertIsDisplayed()
        onNodeWithContentDescription("NeuroRook logo").assertDoesNotExist()
    }

    @Test
    fun `desktop sidebar brand tint keeps the preferred on-surface color when contrast is already sufficient`() {
        // Preserves the current theme foreground on light and dark sidebars when it already meets the readability target.
        val tint = desktopSidebarBrandTintColor(
            containerColor = Color(0.15f, 0.16f, 0.18f, 1f),
            preferredContentColor = Color(0.94f, 0.95f, 0.97f, 1f)
        )

        assertEquals(Color(0.94f, 0.95f, 0.97f, 1f), tint)
    }

    @Test
    fun `desktop sidebar brand tint falls back to a higher-contrast neutral when preferred content is too close to the surface`() {
        // Prevents the bundled monochrome logo from disappearing when a dark sidebar receives an overly dark foreground color.
        val container = Color(0.12f, 0.13f, 0.14f, 1f)
        val preferred = Color(0.18f, 0.19f, 0.20f, 1f)

        val tint = desktopSidebarBrandTintColor(
            containerColor = container,
            preferredContentColor = preferred
        )

        assertTrue(contrastRatio(container, tint) >= 4.5)
        assertTrue(tint == Color.White || tint == Color.Black)
    }

    @Test
    fun `sidebar logo painter loader returns null for invalid svg bytes and parses bundled svg bytes`() {
        // Verifies the raw SVG loader rejects malformed data but still decodes the shipped sidebar asset.
        val density = Density(1f)
        assertNull(loadDesktopSidebarLogoPainter(byteArrayOf(0, 1, 2, 3), density))
        assertNull(loadDesktopSidebarLogoPainter(null, density))
        val bytes = assertNotNull(readDesktopSidebarLogoBytes(Thread.currentThread().contextClassLoader))
        assertTrue(bytes.isNotEmpty())
        assertNotNull(loadDesktopSidebarLogoPainter(bytes, Density(1f)))
    }

    @Test
    fun `read desktop sidebar resource bytes returns stream payload when class loader resolves resource`() {
        // Confirms the lower-level resource reader returns the exact byte payload exposed by a custom class loader.
        val loader = object : ClassLoader() {
            override fun getResourceAsStream(name: String?): InputStream? =
                if (name == "custom.svg") ByteArrayInputStream(byteArrayOf(7, 8, 9)) else null
        }
        assertContentEquals(byteArrayOf(7, 8, 9), readDesktopSidebarResourceBytes(loader, "custom.svg"))
    }

    @Test
    fun `desktop sidebar brand default painter path shows either the vector logo or text fallback`() = runComposeUiTest {
        // Guards the production path where the sidebar picks whichever branding representation the environment can decode.
        setContent {
            MaterialTheme {
                DesktopSidebarBrand()
            }
        }
        waitForIdle()
        val showsVectorLogo = runCatching {
            onNodeWithContentDescription("NeuroRook logo").assertIsDisplayed()
        }.isSuccess
        val showsTextFallback = runCatching {
            onNodeWithText("NeuroRook").assertIsDisplayed()
        }.isSuccess
        assertTrue(showsVectorLogo xor showsTextFallback)
    }
}
