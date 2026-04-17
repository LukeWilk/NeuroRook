package io.github.lukewilk.theming

import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import java.awt.Color as AwtColor
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests the extracted desktop color math and fallback selection logic directly.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopSystemColorSchemeColorMathTest {
    @Test
    fun `blend desktop colors clamps ratio to zero one`() {
        val a = Color.Red
        val b = Color.Blue
        assertColorsClose(blendDesktopColors(a, b, -2f), blendDesktopColors(a, b, 0f))
        assertColorsClose(blendDesktopColors(a, b, 3f), blendDesktopColors(a, b, 1f))
    }

    @Test
    fun `tint desktop colors matches blend with same amount`() {
        val base = Color(0.9f, 0.9f, 0.9f, 1f)
        val accent = Color(0.1f, 0.2f, 0.3f, 1f)
        assertColorsClose(
            tintDesktopColors(base, accent, 0.25f),
            blendDesktopColors(base, accent, 0.25f)
        )
    }

    @Test
    fun `pick best contrast on background prefers strongest readable candidate`() {
        val background = Color.White
        val weak = Color(0.85f, 0.85f, 0.85f, 1f)
        val strong = Color.Black
        assertEquals(strong, pickBestContrastOnBackground(background, listOf(weak, strong)))
    }

    @Test
    fun `pick best contrast on background injects black and white so weak palettes still pick a readable foreground`() {
        val background = Color.White
        val weakA = Color(0.9f, 0.9f, 0.91f, 1f)
        val weakB = Color(0.88f, 0.88f, 0.89f, 1f)
        val picked = pickBestContrastOnBackground(background, listOf(weakA, weakB))
        assertEquals(Color.Black, picked)
        assertTrue(contrastRatio(background, picked) > contrastRatio(background, weakA))
    }

    @Test
    fun `pick best contrast on background falls back to white when there are no candidates`() {
        assertEquals(Color.White, pickBestContrastOnBackground(Color.Black, emptyList()))
    }

    @Test
    fun `awt to compose color preserves rgb channels`() {
        val awt = AwtColor(12, 34, 56)
        val compose = awtColorToComposeColor(awt)
        assertChannel(12, compose.red)
        assertChannel(34, compose.green)
        assertChannel(56, compose.blue)
    }

    @Test
    fun `relative luminance awt matches compose luminance of converted color`() {
        val awt = AwtColor(40, 50, 60)
        val compose = awtColorToComposeColor(awt)
        assertEquals(relativeLuminanceCompose(compose), relativeLuminanceAwt(awt), absoluteTolerance = 1e-9)
    }

    @Test
    fun `contrast ratio returns one when foreground and background share luminance`() {
        val gray = Color(0.5f, 0.5f, 0.5f, 1f)
        assertEquals(1.0, contrastRatio(gray, gray), absoluteTolerance = 1e-9)
    }

    @Test
    fun `resolve desktop dark mode prefers plasma flag when present`() {
        assertTrue(resolveDesktopDarkMode(plasmaDarkMode = true, fallbackDarkMode = false))
        assertFalse(resolveDesktopDarkMode(plasmaDarkMode = false, fallbackDarkMode = true))
        assertTrue(resolveDesktopDarkMode(plasmaDarkMode = null, fallbackDarkMode = true))
    }

    @Test
    fun `is likely dark theme returns false when panel foreground is missing`() {
        assertFalse(
            isLikelyDarkTheme(
                panelBackground = { AwtColor(0, 0, 0) },
                panelForeground = { null }
            )
        )
    }

    @Test
    fun `fallback desktop color scheme delegates to material light and dark builders`() {
        assertEquals(lightColorScheme().background, fallbackDesktopColorScheme(isDark = false).background)
        assertEquals(darkColorScheme().background, fallbackDesktopColorScheme(isDark = true).background)
    }

    @Test
    fun `desktop color scheme routes dark and light role packs through material factories`() {
        val roles = DesktopColorSchemeRoles(
            primary = Color.Black,
            onPrimary = Color.White,
            secondary = Color.DarkGray,
            onSecondary = Color.White,
            tertiary = Color.Gray,
            onTertiary = Color.White,
            primaryContainer = Color(0.2f, 0.2f, 0.2f, 1f),
            onPrimaryContainer = Color.White,
            secondaryContainer = Color(0.25f, 0.25f, 0.25f, 1f),
            onSecondaryContainer = Color.White,
            tertiaryContainer = Color(0.3f, 0.3f, 0.3f, 1f),
            onTertiaryContainer = Color.White,
            background = Color.White,
            onBackground = Color.Black,
            surface = Color.White,
            onSurface = Color.Black,
            error = Color.Red,
            onError = Color.White,
            errorContainer = Color(0.4f, 0f, 0f, 1f),
            onErrorContainer = Color.White,
            surfaceVariant = Color.LightGray,
            onSurfaceVariant = Color.Black,
            outline = Color.Gray,
            outlineVariant = Color.LightGray,
            surfaceTint = Color.Black
        )
        val light = desktopColorScheme(roles, isDark = false)
        val dark = desktopColorScheme(roles, isDark = true)
        assertEquals(roles.primary, light.primary)
        assertEquals(roles.primary, dark.primary)
        assertEquals(desktopMaterialSchemeFactory(false)(roles).background, light.background)
        assertEquals(desktopMaterialSchemeFactory(true)(roles).background, dark.background)
        assertEquals(roles.outline, darkDesktopColorScheme(roles).outline)
        assertEquals(roles.outline, lightDesktopColorScheme(roles).outline)
    }

    @Test
    fun `is dark from pair or null requires both plasma colors`() {
        val dark = Color.Black
        val light = Color.White
        assertNull(isDarkFromPairOrNull(null, light))
        assertNull(isDarkFromPairOrNull(dark, null))
        assertEquals(true, isDarkFromPairOrNull(dark, light))
        assertEquals(false, isDarkFromPairOrNull(light, dark))
    }

    @Test
    fun `linearize srgb channel uses linear segment for dark values`() {
        val low = linearizeSrgbChannel(0.02f)
        val high = linearizeSrgbChannel(0.5f)
        assertTrue(low < high)
        assertTrue(low < 0.05)
    }

    @Test
    fun `remember desktop system color scheme recomputes when builder identity changes`() = runComposeUiTest {
        val light = lightColorScheme(primary = Color.Red)
        val dark = darkColorScheme(primary = Color.Blue)
        val activeBuilder = mutableStateOf<() -> androidx.compose.material3.ColorScheme>({ light })

        setContent {
            val scheme = rememberDesktopSystemColorScheme(activeBuilder.value)
            Text("primary:${if (scheme.primary == light.primary) "light" else "dark"}")
        }

        onNodeWithText("primary:light").assertIsDisplayed()
        activeBuilder.value = { dark }
        waitForIdle()
        onNodeWithText("primary:dark").assertIsDisplayed()
    }

    private fun assertColorsClose(expected: Color, actual: Color, tolerance: Float = 0.001f) {
        assertTrue(abs(expected.red - actual.red) <= tolerance)
        assertTrue(abs(expected.green - actual.green) <= tolerance)
        assertTrue(abs(expected.blue - actual.blue) <= tolerance)
        assertTrue(abs(expected.alpha - actual.alpha) <= tolerance)
    }

    private fun assertChannel(expected255: Int, channelFloat: Float) {
        val asInt = (channelFloat * 255f).toInt().coerceIn(0, 255)
        assertEquals(expected255, asInt)
    }
}
