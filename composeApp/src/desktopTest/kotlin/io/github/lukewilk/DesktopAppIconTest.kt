package io.github.lukewilk

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
/**
 * Tests for the extracted desktop app icon resource and aspect-ratio helpers.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopAppIconTest {
    @Test
    fun `desktop app icon loader reads resource bytes from the class loader and tolerates invalid payloads`() {
        // Verifies the dedicated app-icon resource path can read raw bytes and reject malformed image data safely.
        assertNull(readDesktopAppIconBytes(null, "any.png"))
        val loader = object : ClassLoader() {
            override fun getResourceAsStream(name: String?): InputStream? =
                if (name == "custom-icon.png") ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)) else null
        }
        assertEquals(byteArrayOf(1, 2, 3, 4).toList(), readDesktopAppIconBytes(loader, "custom-icon.png")?.toList())
        assertNull(readDesktopAppIconBytes(loader, "missing.png"))
        assertNull(loadDesktopAppIconPainter(null))
        assertNull(loadDesktopAppIconPainter(byteArrayOf(0, 1, 2, 3)))
        val bundledBytes = assertNotNull(readDesktopAppIconBytes(Thread.currentThread().contextClassLoader, DESKTOP_APP_ICON_RESOURCE))
        assertTrue(bundledBytes.isNotEmpty())
        assertNotNull(loadDesktopAppIconPainter(bundledBytes))
    }
    @Test
    fun `desktop app icon fitting keeps portrait art centered inside square icon bounds`() {
        // Prevents tall app icons from stretching by fitting them inside the square titlebar target with centered padding.
        val fitted = fitDesktopIconRect(
            sourceWidth = 300f,
            sourceHeight = 424f,
            destinationWidth = 64f,
            destinationHeight = 64f
        )
        assertEquals(0f, fitted.top)
        assertEquals(64f, fitted.height)
        assertEquals(45.28302f, fitted.width, 0.001f)
        assertEquals(9.35849f, fitted.left, 0.001f)
    }
    @Test
    fun `desktop app icon fitting returns an empty rectangle when source or destination dimensions are missing`() {
        // Covers every invalid-dimension guard branch so broken icon metadata never crashes the painter math.
        val cases = listOf(
            fitDesktopIconRect(0f, 424f, 64f, 64f),
            fitDesktopIconRect(300f, 0f, 64f, 64f),
            fitDesktopIconRect(300f, 424f, 0f, 64f),
            fitDesktopIconRect(300f, 424f, 64f, 0f),
            fitDesktopIconRect(-1f, 424f, 64f, 64f)
        )
        cases.forEach { fitted ->
            assertEquals(DesktopIconDrawRect(0f, 0f, 0f, 0f), fitted)
        }
    }
    @Test
    fun `remember desktop app icon painter returns a fitted painter for the bundled resource and a transparent fallback for missing files`() = runComposeUiTest {
        // Ensures the titlebar icon path preserves aspect ratio for the real asset while still degrading safely when a resource is missing.
        setContent {
            MaterialTheme {
                val bundledPainter = rememberDesktopAppIconPainter(DESKTOP_APP_ICON_RESOURCE)
                val missingPainter = rememberDesktopAppIconPainter("missing-icon.png")
                Text(
                    "bundled:${bundledPainter::class.simpleName}|missing:${missingPainter::class.simpleName}"
                )
            }
        }
        onNodeWithText(
            "bundled:DesktopFittedBitmapPainter|missing:TransparentDesktopIconPainter"
        ).assertIsDisplayed()
    }

    @Test
    fun `transparent desktop icon painter reports an unspecified intrinsic size`() {
        // Documents the transparent fallback contract so missing icons stay layout-neutral when Compose queries their size.
        assertEquals(Size.Unspecified, TransparentDesktopIconPainter.intrinsicSize)
    }
}
