package io.github.lukewilk.theming

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.math.abs
import kotlin.math.pow
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.awt.Color as AwtColor
import javax.swing.UIManager

/**
 * Verifies that the desktop theme builder maps KDE Plasma palette colors into a readable Material color scheme.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopSystemColorSchemeTest {
    @Test
    fun `desktop color scheme prefers Plasma window and view colors`() {
        // Confirms the app shell picks KDE window, view, and accent colors before falling back to Swing defaults.
        val scheme = buildDesktopSystemColorScheme(
            kdeGlobals = parseKdeGlobals(lightPlasmaConfig()),
            uiColor = { null },
            isDarkFallback = true
        )

        assertColorEquals(rgb(255, 255, 255), scheme.background)
        assertColorEquals(rgb(49, 54, 59), scheme.onBackground)
        assertColorEquals(rgb(250, 251, 252), scheme.surface)
        assertColorEquals(rgb(49, 54, 59), scheme.onSurface)
        assertColorEquals(rgb(233, 100, 58), scheme.primary)
    }

    @Test
    fun `desktop color scheme keeps tinted containers close to neutral Plasma surfaces`() {
        // Keeps selected navigation rows and containers gently tinted so the Plasma palette still feels calm and native.
        val scheme = buildDesktopSystemColorScheme(
            kdeGlobals = parseKdeGlobals(lightPlasmaConfig()),
            uiColor = { null },
            isDarkFallback = false
        )

        assertTrue(
            colorDistance(scheme.primaryContainer, scheme.background) < colorDistance(scheme.primaryContainer, scheme.primary),
            "Expected the primary container to stay closer to the neutral window background than to the raw accent color."
        )
        assertTrue(
            colorDistance(scheme.secondaryContainer, scheme.surfaceVariant) < colorDistance(scheme.secondaryContainer, scheme.secondary),
            "Expected the secondary container to stay closer to the neutral surface variant than to the accent hover color."
        )
    }

    @Test
    fun `desktop color scheme detects dark Plasma palettes and preserves readable contrast`() {
        // Verifies that dark KDE palettes remain dark while button and error text still meet readable contrast targets.
        val scheme = buildDesktopSystemColorScheme(
            kdeGlobals = parseKdeGlobals(darkPlasmaConfig()),
            uiColor = { null },
            isDarkFallback = false
        )

        assertTrue(
            relativeLuminance(scheme.background) < relativeLuminance(scheme.onBackground),
            "Expected the background to be darker than its foreground in dark Plasma mode."
        )
        assertTrue(
            contrastRatio(scheme.primary, scheme.onPrimary) >= 4.5,
            "Expected the primary accent pair to remain readable on dark Plasma palettes."
        )
        assertTrue(
            contrastRatio(scheme.error, scheme.onError) >= 4.5,
            "Expected the error pair to remain readable on dark Plasma palettes."
        )
    }

    @Test
    fun `rgb parser clamps plasma channel values into the valid compose range`() {
        // Documents that malformed kdeglobals channels are clamped instead of spilling invalid values into the theme.
        val parsed = parseRgb("300, -10, 42, 999")

        assertNotNull(parsed, "Expected the rgb parser to produce a color for numeric plasma channels.")
        assertColorEquals(rgb(255, 0, 42), parsed)
    }

    @Test
    fun `desktop color scheme falls back to Material light roles when Plasma and UI colors are absent`() {
        // Covers the light fallback path so empty Plasma and Swing palettes still produce a complete readable scheme.
        val fallback = lightColorScheme()

        val scheme = buildDesktopSystemColorScheme(
            kdeGlobals = parseKdeGlobals(""),
            uiColor = { null },
            isDarkFallback = false
        )

        assertColorEquals(fallback.background, scheme.background)
        assertColorEquals(fallback.onBackground, scheme.onBackground)
        assertColorEquals(fallback.background, scheme.surface)
        assertColorEquals(fallback.onBackground, scheme.onSurface)
        assertColorEquals(fallback.surfaceVariant, scheme.surfaceVariant)
        assertColorEquals(fallback.primary, scheme.primary)
        assertColorEquals(fallback.error, scheme.error)
    }

    @Test
    fun `desktop color scheme falls back to Material dark roles when Plasma and UI colors are absent`() {
        // Covers the dark branch so empty desktop palettes still build the expected Material dark scheme roles.
        val fallback = darkColorScheme()

        val scheme = buildDesktopSystemColorScheme(
            kdeGlobals = parseKdeGlobals(""),
            uiColor = { null },
            isDarkFallback = true
        )

        assertColorEquals(fallback.background, scheme.background)
        assertColorEquals(fallback.onBackground, scheme.onBackground)
        assertColorEquals(fallback.background, scheme.surface)
        assertColorEquals(fallback.onBackground, scheme.onSurface)
        assertColorEquals(fallback.primary, scheme.primary)
        assertColorEquals(fallback.error, scheme.error)
        assertColorEquals(scheme.primary, scheme.surfaceTint)
    }

    @Test
    fun `desktop color scheme uses the first available UI colors when Plasma keys are missing`() {
        // Verifies Swing color keys are scanned in order and fill every base, accent, and focus role when Plasma data is absent.
        val requestedKeys = mutableListOf<String>()
        val uiColors = mapOf(
            "Panel.background" to rgb(240, 241, 242),
            "Panel.foreground" to rgb(30, 34, 40),
            "TextField.background" to rgb(252, 252, 253),
            "TextField.foreground" to rgb(45, 49, 54),
            "Button.background" to rgb(235, 236, 237),
            "Button.foreground" to rgb(80, 86, 94),
            "Menu.selectionBackground" to rgb(30, 60, 120),
            "Menu.selectionForeground" to rgb(250, 251, 252),
            "Tree.selectionBackground" to rgb(120, 160, 220),
            "Focus.color" to rgb(0, 100, 180)
        )

        val scheme = buildDesktopSystemColorScheme(
            kdeGlobals = parseKdeGlobals(""),
            uiColor = { key ->
                requestedKeys += key
                uiColors[key]
            },
            isDarkFallback = false
        )

        assertColorEquals(uiColors.getValue("Panel.background"), scheme.background)
        assertColorEquals(uiColors.getValue("Panel.foreground"), scheme.onBackground)
        assertColorEquals(uiColors.getValue("TextField.background"), scheme.surface)
        assertColorEquals(uiColors.getValue("TextField.foreground"), scheme.onSurface)
        assertColorEquals(uiColors.getValue("Button.background"), scheme.surfaceVariant)
        assertColorEquals(uiColors.getValue("Button.foreground"), scheme.onSurfaceVariant)
        assertColorEquals(uiColors.getValue("Menu.selectionBackground"), scheme.primary)
        assertTrue(
            contrastRatio(scheme.primary, scheme.onPrimary) >= 4.5,
            "Expected the primary role chosen from Swing fallback colors to keep a readable foreground."
        )
        assertColorEquals(uiColors.getValue("Tree.selectionBackground"), scheme.secondary)
        assertColorEquals(uiColors.getValue("Focus.color"), scheme.tertiary)
        assertTrue(
            requestedKeys.contains("Menu.selectionBackground") &&
                requestedKeys.contains("Menu.selectionForeground") &&
                requestedKeys.contains("Tree.selectionBackground") &&
                requestedKeys.contains("Focus.color"),
            "Expected the builder to scan Swing accent, selection, and focus keys when Plasma colors are unavailable."
        )
    }

    @Test
    fun `desktop color scheme default builder tolerates unreadable kdeglobals and uses UIManager colors`() {
        // Confirms the default builder survives a broken kdeglobals path and still resolves colors from Swing UIManager values.
        val temporaryHome = createTempDirectory("desktop-theme-home")
        temporaryHome.resolve(".config").createDirectories()
        temporaryHome.resolve(".config/kdeglobals").toFile().mkdirs()

        val uiManagerColors = mapOf(
            "Panel.background" to awt(245, 246, 247),
            "Panel.foreground" to awt(28, 31, 36),
            "TextField.background" to awt(252, 252, 252),
            "TextField.foreground" to awt(38, 42, 48),
            "Button.background" to awt(230, 231, 232),
            "Button.foreground" to awt(90, 96, 104),
            "Component.accentColor" to awt(30, 60, 120),
            "TextField.selectionForeground" to awt(252, 252, 252),
            "TabbedPane.selected" to awt(120, 165, 225),
            "Button.focus" to awt(0, 105, 190)
        )

        withTemporaryUserHome(temporaryHome.toString()) {
            withUiManagerColors(uiManagerColors) {
                val scheme = buildDesktopSystemColorScheme()

                assertColorEquals(rgb(245, 246, 247), scheme.background)
                assertColorEquals(rgb(28, 31, 36), scheme.onBackground)
                assertColorEquals(rgb(252, 252, 252), scheme.surface)
                assertColorEquals(rgb(38, 42, 48), scheme.onSurface)
                assertColorEquals(rgb(30, 60, 120), scheme.primary)
                assertColorEquals(rgb(120, 165, 225), scheme.secondary)
                assertColorEquals(rgb(0, 105, 190), scheme.tertiary)
            }
        }
    }

    @Test
    fun `rgb parser rejects incomplete and non numeric inputs while defaulting invalid alpha to opaque`() {
        // Documents the parser failure matrix so malformed kdeglobals values are rejected predictably.
        assertNull(parseRgb(null), "Expected null input to produce no color.")
        assertNull(parseRgb("1,2"), "Expected fewer than three channels to be rejected.")
        assertNull(parseRgb("x,2,3"), "Expected a non-numeric red channel to be rejected.")
        assertNull(parseRgb("1,x,3"), "Expected a non-numeric green channel to be rejected.")
        assertNull(parseRgb("1,2,x"), "Expected a non-numeric blue channel to be rejected.")

        val parsedWithInvalidAlpha = parseRgb("1, 2, 3, nope")
        assertNotNull(parsedWithInvalidAlpha, "Expected invalid alpha text to fall back to an opaque color.")
        assertColorEquals(rgb(1, 2, 3), parsedWithInvalidAlpha)
    }

    @Test
    fun `compose desktop theme builder renders the light fallback summary`() = runComposeUiTest {
        // Forces the light fallback builder path through Compose so desktop Kover captures the rendered branch executions.
        val fallback = lightColorScheme()
        val expectedSummary = themeSummary("light", fallback.background, fallback.surface, fallback.primary)

        setContent {
            val scheme = buildDesktopSystemColorScheme(
                kdeGlobals = parseKdeGlobals(""),
                uiColor = { null },
                isDarkFallback = false
            )
            Text(themeSummary("light", scheme.background, scheme.surface, scheme.primary))
        }

        onNodeWithText(expectedSummary).assertIsDisplayed()
    }

    @Test
    fun `compose desktop theme builder renders the dark fallback summary`() = runComposeUiTest {
        // Forces the dark fallback builder path through Compose so Kover records the dark scheme return branch.
        val fallback = darkColorScheme()
        val expectedSummary = themeSummary("dark", fallback.background, fallback.surface, fallback.primary)

        setContent {
            val scheme = buildDesktopSystemColorScheme(
                kdeGlobals = parseKdeGlobals(""),
                uiColor = { null },
                isDarkFallback = true
            )
            Text(themeSummary("dark", scheme.background, scheme.surface, scheme.primary))
        }

        onNodeWithText(expectedSummary).assertIsDisplayed()
    }

    @Test
    fun `compose desktop theme builder renders the UI fallback summary`() = runComposeUiTest {
        // Forces the Swing fallback path through Compose so Kover records the accent and surface key selection branches.
        val uiColors = mapOf(
            "Panel.background" to rgb(240, 241, 242),
            "Panel.foreground" to rgb(30, 34, 40),
            "TextField.background" to rgb(252, 252, 253),
            "TextField.foreground" to rgb(45, 49, 54),
            "Button.background" to rgb(235, 236, 237),
            "Button.foreground" to rgb(80, 86, 94),
            "Menu.selectionBackground" to rgb(30, 60, 120)
        )
        val expectedSummary = themeSummary(
            "ui",
            uiColors.getValue("Panel.background"),
            uiColors.getValue("TextField.background"),
            uiColors.getValue("Menu.selectionBackground")
        )

        setContent {
            val scheme = buildDesktopSystemColorScheme(
                kdeGlobals = parseKdeGlobals(""),
                uiColor = { key -> uiColors[key] },
                isDarkFallback = false
            )
            Text(themeSummary("ui", scheme.background, scheme.surface, scheme.primary))
        }

        onNodeWithText(expectedSummary).assertIsDisplayed()
    }

    @Test
    fun `compose desktop theme builder renders the unreadable kdeglobals fallback summary`() = runComposeUiTest {
        // Forces the default builder through Compose with a broken kdeglobals path so Kover records the read failure fallback branch.
        val temporaryHome = createTempDirectory("desktop-theme-compose-home")
        temporaryHome.resolve(".config").createDirectories()
        temporaryHome.resolve(".config/kdeglobals").toFile().mkdirs()

        val uiManagerColors = mapOf(
            "Panel.background" to awt(245, 246, 247),
            "Panel.foreground" to awt(28, 31, 36),
            "TextField.background" to awt(252, 252, 252),
            "TextField.foreground" to awt(38, 42, 48),
            "Button.background" to awt(230, 231, 232),
            "Button.foreground" to awt(90, 96, 104),
            "Component.accentColor" to awt(30, 60, 120)
        )
        val expectedSummary = themeSummary("broken", rgb(245, 246, 247), rgb(252, 252, 252), rgb(30, 60, 120))

        withTemporaryUserHome(temporaryHome.toString()) {
            withUiManagerColors(uiManagerColors) {
                setContent {
                    val scheme = buildDesktopSystemColorScheme()
                    Text(themeSummary("broken", scheme.background, scheme.surface, scheme.primary))
                }
            }
        }

        onNodeWithText(expectedSummary).assertIsDisplayed()
    }

    @Test
    fun `preferred on primary helper uses UI selection foreground before falling back to on surface`() {
        // Covers the selection-foreground helper branches directly so the desktop theme fallback order stays explicit and covered.
        val uiPreferred = rgb(250, 251, 252)
        val onSurface = rgb(40, 44, 48)

        val uiResolved = resolvePreferredOnPrimary(
            kdeGlobals = parseKdeGlobals(""),
            uiColor = { key -> if (key == "Menu.selectionForeground") uiPreferred else null },
            onSurface = onSurface
        )
        val fallbackResolved = resolvePreferredOnPrimary(
            kdeGlobals = parseKdeGlobals(""),
            uiColor = { null },
            onSurface = onSurface
        )

        assertColorEquals(uiPreferred, uiResolved)
        assertColorEquals(onSurface, fallbackResolved)
    }

    @Test
    fun `secondary helper uses window selection ui and tinted fallbacks in order`() {
        // Covers the remaining secondary-role branches so Plasma hover and UI selection fallbacks stay covered in order.
        val surfaceVariant = rgb(235, 236, 237)
        val primary = rgb(30, 60, 120)
        val windowHover = rgb(90, 140, 210)
        val selectionHover = rgb(120, 150, 220)
        val uiSecondary = rgb(150, 170, 230)

        val windowResolved = resolveSecondary(
            kdeGlobals = parseKdeGlobals(
                """
                [Colors:Window]
                DecorationHover=90,140,210
                """.trimIndent()
            ),
            uiColor = { null },
            surfaceVariant = surfaceVariant,
            primary = primary,
            isDark = false
        )
        val selectionResolved = resolveSecondary(
            kdeGlobals = parseKdeGlobals(
                """
                [Colors:Selection]
                DecorationHover=120,150,220
                """.trimIndent()
            ),
            uiColor = { null },
            surfaceVariant = surfaceVariant,
            primary = primary,
            isDark = false
        )
        val uiResolved = resolveSecondary(
            kdeGlobals = parseKdeGlobals(""),
            uiColor = { key -> if (key == "Tree.selectionBackground") uiSecondary else null },
            surfaceVariant = surfaceVariant,
            primary = primary,
            isDark = false
        )
        val fallbackResolved = resolveSecondary(
            kdeGlobals = parseKdeGlobals(""),
            uiColor = { null },
            surfaceVariant = surfaceVariant,
            primary = primary,
            isDark = false
        )

        assertColorEquals(windowHover, windowResolved)
        assertColorEquals(selectionHover, selectionResolved)
        assertColorEquals(uiSecondary, uiResolved)
        assertColorEquals(tinted(surfaceVariant, primary, 0.28f), fallbackResolved)
    }

    @Test
    fun `tertiary helper uses window ui and tinted fallbacks in order`() {
        // Covers the remaining tertiary-role branches so Plasma links and UI focus colors fall back predictably.
        val surface = rgb(252, 252, 253)
        val primary = rgb(30, 60, 120)
        val windowLink = rgb(40, 120, 185)
        val uiFocus = rgb(0, 100, 180)

        val windowResolved = resolveTertiary(
            kdeGlobals = parseKdeGlobals(
                """
                [Colors:Window]
                ForegroundLink=40,120,185
                """.trimIndent()
            ),
            uiColor = { null },
            surface = surface,
            primary = primary,
            isDark = false
        )
        val uiResolved = resolveTertiary(
            kdeGlobals = parseKdeGlobals(""),
            uiColor = { key -> if (key == "Focus.color") uiFocus else null },
            surface = surface,
            primary = primary,
            isDark = false
        )
        val fallbackResolved = resolveTertiary(
            kdeGlobals = parseKdeGlobals(""),
            uiColor = { null },
            surface = surface,
            primary = primary,
            isDark = false
        )

        assertColorEquals(windowLink, windowResolved)
        assertColorEquals(uiFocus, uiResolved)
        assertColorEquals(tinted(surface, primary, 0.32f), fallbackResolved)
    }

    @Test
    fun `error helper falls back to the provided material error color when plasma roles are missing`() {
        // Covers the final error-role fallback so missing Plasma negative colors still map to Material error safely.
        val fallbackError = rgb(186, 26, 26)

        val resolved = resolveError(
            kdeGlobals = parseKdeGlobals(""),
            fallback = fallbackError
        )

        assertColorEquals(fallbackError, resolved)
    }
}

/** Returns a light Plasma-inspired config sample so tests exercise the window, view, and selection groups together. */
private fun lightPlasmaConfig(): String = """
    [Colors:Button]
    BackgroundNormal=255,255,255
    ForegroundNormal=49,54,59
    DecorationFocus=251,132,65
    DecorationHover=251,132,65

    [Colors:Selection]
    BackgroundNormal=251,132,65
    ForegroundNormal=250,251,252

    [Colors:View]
    BackgroundNormal=250,251,252
    BackgroundAlternate=248,248,248
    ForegroundLink=0,87,174
    ForegroundNegative=191,3,3
    ForegroundNormal=49,54,59

    [Colors:Window]
    BackgroundNormal=255,255,255
    ForegroundInactive=136,135,134
    ForegroundNormal=49,54,59
    DecorationFocus=251,132,65
    DecorationHover=251,132,65

    [General]
    LastUsedCustomAccentColor=233,100,58
""".trimIndent()

/** Returns a dark Plasma-inspired config sample so tests cover dark palette detection and contrast fallback behavior. */
private fun darkPlasmaConfig(): String = """
    [Colors:Button]
    BackgroundNormal=47,52,63
    ForegroundNormal=211,218,227
    DecorationFocus=61,174,233
    DecorationHover=61,174,233

    [Colors:Selection]
    BackgroundNormal=61,174,233
    ForegroundNormal=255,255,255

    [Colors:View]
    BackgroundNormal=47,52,63
    BackgroundAlternate=40,42,51
    ForegroundLink=41,128,185
    ForegroundNegative=218,68,83
    ForegroundNormal=211,218,227

    [Colors:Window]
    BackgroundNormal=40,42,51
    ForegroundInactive=102,106,115
    ForegroundNormal=211,218,227
    DecorationFocus=61,174,233
    DecorationHover=61,174,233

    [General]
    LastUsedCustomAccentColor=61,174,233
""".trimIndent()

/** Builds an opaque compose color from the same 0-255 channel values Plasma stores inside `kdeglobals`. */
private fun rgb(red: Int, green: Int, blue: Int): Color {
    return Color(red / 255f, green / 255f, blue / 255f, 1f)
}

/** Builds an AWT color for the default Swing UIManager fallback tests. */
private fun awt(red: Int, green: Int, blue: Int): AwtColor {
    return AwtColor(red, green, blue)
}

/** Compares colors with a tiny tolerance so tests stay stable across float conversions. */
private fun assertColorEquals(expected: Color, actual: Color, tolerance: Float = 0.001f) {
    assertTrue(abs(expected.red - actual.red) <= tolerance, "Expected red=${expected.red} but was ${actual.red}")
    assertTrue(abs(expected.green - actual.green) <= tolerance, "Expected green=${expected.green} but was ${actual.green}")
    assertTrue(abs(expected.blue - actual.blue) <= tolerance, "Expected blue=${expected.blue} but was ${actual.blue}")
    assertTrue(abs(expected.alpha - actual.alpha) <= tolerance, "Expected alpha=${expected.alpha} but was ${actual.alpha}")
}

/** Measures how far two colors are apart so tests can assert subtle container tinting. */
private fun colorDistance(first: Color, second: Color): Float {
    val red = first.red - second.red
    val green = first.green - second.green
    val blue = first.blue - second.blue
    return red * red + green * green + blue * blue
}

/** Mirrors the production tint calculation so helper tests can assert the exact fallback color produced for uncovered branches. */
private fun tinted(base: Color, accent: Color, amount: Float): Color {
    val ratio = amount.coerceIn(0f, 1f)
    return Color(
        red = base.red * (1f - ratio) + accent.red * ratio,
        green = base.green * (1f - ratio) + accent.green * ratio,
        blue = base.blue * (1f - ratio) + accent.blue * ratio,
        alpha = base.alpha * (1f - ratio) + accent.alpha * ratio
    )
}

/** Computes WCAG luminance for compose colors so the tests can reason about light and dark Plasma palettes. */
private fun relativeLuminance(color: Color): Double {
    fun Float.linearize(): Double {
        return if (this <= 0.04045f) {
            this / 12.92
        } else {
            ((this + 0.055f) / 1.055f).toDouble().pow(2.4)
        }
    }

    val red = color.red.linearize()
    val green = color.green.linearize()
    val blue = color.blue.linearize()
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue
}

/** Computes WCAG contrast so tests can verify accent and error roles stay readable. */
private fun contrastRatio(background: Color, foreground: Color): Double {
    val lighter = maxOf(relativeLuminance(background), relativeLuminance(foreground))
    val darker = minOf(relativeLuminance(background), relativeLuminance(foreground))
    return (lighter + 0.05) / (darker + 0.05)
}

/** Reduces a color scheme snapshot to stable text so Compose desktop tests can assert that the target builder path rendered. */
private fun themeSummary(prefix: String, background: Color, surface: Color, primary: Color): String {
    return "$prefix:${colorToken(background)}:${colorToken(surface)}:${colorToken(primary)}"
}

/** Converts a compose color to a stable rgb token for compact desktop theme assertions. */
private fun colorToken(color: Color): String {
    fun channel(value: Float): Int = (value * 255f).toInt()
    return "${channel(color.red)}-${channel(color.green)}-${channel(color.blue)}"
}

/** Temporarily overrides the desktop `user.home` system property so tests can safely control the kdeglobals lookup path. */
private fun <T> withTemporaryUserHome(value: String, block: () -> T): T {
    val previous = System.getProperty("user.home")
    return try {
        System.setProperty("user.home", value)
        block()
    } finally {
        if (previous == null) {
            System.clearProperty("user.home")
        } else {
            System.setProperty("user.home", previous)
        }
    }
}

/** Temporarily seeds Swing UIManager colors so the default desktop theme builder can exercise its runtime fallback path. */
private fun <T> withUiManagerColors(colors: Map<String, AwtColor>, block: () -> T): T {
    val previousValues = colors.keys.associateWith { key -> UIManager.get(key) }
    return try {
        colors.forEach { (key, value) -> UIManager.put(key, value) }
        block()
    } finally {
        previousValues.forEach { (key, value) -> UIManager.put(key, value) }
    }
}







