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
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.Test
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
        assertNull(readDesktopSidebarResourceBytes(null, "any.svg"))
        assertNull(readDesktopSidebarLogoBytes(null))

        val missingStreamLoader = object : ClassLoader() {
            override fun getResourceAsStream(name: String?): java.io.InputStream? = null
        }
        assertNull(readDesktopSidebarResourceBytes(missingStreamLoader, "neuroRook.svg"))
    }

    @Test
    fun `desktop sidebar brand uses injected painter provider`() = runComposeUiTest {
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
        setContent {
            MaterialTheme {
                DesktopSidebarBrand(logoPainterProvider = { null })
            }
        }
        onNodeWithText("NeuroRook").assertIsDisplayed()
        onNodeWithContentDescription("NeuroRook logo").assertDoesNotExist()
    }

    fun `desktop sidebar brand content renders vector slot when painter is non null`() = runComposeUiTest {
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
        setContent {
            MaterialTheme {
                DesktopSidebarBrandContent(logoPainter = null)
            }
        }
        onNodeWithText("NeuroRook").assertIsDisplayed()
        onNodeWithContentDescription("NeuroRook logo").assertDoesNotExist()
    }

    @Test
    fun `sidebar logo painter loader returns null for invalid svg bytes and parses bundled svg bytes`() {
        val density = Density(1f)
        assertNull(loadDesktopSidebarLogoPainter(byteArrayOf(0, 1, 2, 3), density))
        assertNull(loadDesktopSidebarLogoPainter(null, density))
        val bytes = assertNotNull(readDesktopSidebarLogoBytes(Thread.currentThread().contextClassLoader))
        assertTrue(bytes.isNotEmpty())
        assertNotNull(loadDesktopSidebarLogoPainter(bytes, Density(1f)))
    }

    @Test
    fun `read desktop sidebar resource bytes returns stream payload when class loader resolves resource`() {
        val loader = object : ClassLoader() {
            override fun getResourceAsStream(name: String?): InputStream? =
                if (name == "custom.svg") ByteArrayInputStream(byteArrayOf(7, 8, 9)) else null
        }
        assertContentEquals(byteArrayOf(7, 8, 9), readDesktopSidebarResourceBytes(loader, "custom.svg"))
    }

    @Test
    fun `desktop sidebar brand default painter path shows either the vector logo or text fallback`() = runComposeUiTest {
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
