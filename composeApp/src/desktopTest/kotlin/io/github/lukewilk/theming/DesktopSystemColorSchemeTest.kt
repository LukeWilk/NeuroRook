package io.github.lukewilk.theming

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
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
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.io.File
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
        assertColorEquals(rgb(251, 132, 65), scheme.primary)
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
    fun `fallback desktop color scheme switches between light and dark material palettes`() {
        assertColorEquals(darkColorScheme().background, fallbackDesktopColorScheme(isDark = true).background)
        assertColorEquals(lightColorScheme().background, fallbackDesktopColorScheme(isDark = false).background)
    }

    @Test
    fun `dark desktop color scheme copies every provided role verbatim`() {
        val roles = DesktopColorSchemeRoles(
            primary = rgb(1, 2, 3),
            onPrimary = rgb(4, 5, 6),
            secondary = rgb(7, 8, 9),
            onSecondary = rgb(10, 11, 12),
            tertiary = rgb(13, 14, 15),
            onTertiary = rgb(16, 17, 18),
            primaryContainer = rgb(19, 20, 21),
            onPrimaryContainer = rgb(22, 23, 24),
            secondaryContainer = rgb(25, 26, 27),
            onSecondaryContainer = rgb(28, 29, 30),
            tertiaryContainer = rgb(31, 32, 33),
            onTertiaryContainer = rgb(34, 35, 36),
            background = rgb(37, 38, 39),
            onBackground = rgb(40, 41, 42),
            surface = rgb(43, 44, 45),
            onSurface = rgb(46, 47, 48),
            error = rgb(49, 50, 51),
            onError = rgb(52, 53, 54),
            errorContainer = rgb(55, 56, 57),
            onErrorContainer = rgb(58, 59, 60),
            surfaceVariant = rgb(61, 62, 63),
            onSurfaceVariant = rgb(64, 65, 66),
            outline = rgb(67, 68, 69),
            outlineVariant = rgb(70, 71, 72),
            surfaceTint = rgb(73, 74, 75)
        )

        val scheme = darkDesktopColorScheme(roles)

        assertColorEquals(roles.primary, scheme.primary)
        assertColorEquals(roles.onPrimary, scheme.onPrimary)
        assertColorEquals(roles.secondaryContainer, scheme.secondaryContainer)
        assertColorEquals(roles.errorContainer, scheme.errorContainer)
        assertColorEquals(roles.surfaceTint, scheme.surfaceTint)
    }

    @Test
    fun `ui key helper lists stay in declared lookup order and first available color picks earliest match`() {
        assertEquals(
            listOf(
                "Component.accentColor",
                "Menu.selectionBackground",
                "List.selectionBackground",
                "TextField.selectionBackground",
                "Button.default.background",
                "Button.background"
            ),
            primaryUiColorKeys()
        )
        assertEquals(
            listOf(
                "TextField.selectionForeground",
                "Button.default.foreground",
                "Menu.selectionForeground",
                "List.selectionForeground",
                "Button.foreground"
            ),
            onPrimaryUiColorKeys()
        )
        assertEquals(
            listOf("TabbedPane.selected", "ComboBox.selectionBackground", "Tree.selectionBackground"),
            secondaryUiColorKeys()
        )
        assertEquals(
            listOf("Button.focus", "Focus.color", "nimbusFocus"),
            tertiaryUiColorKeys()
        )

        val requested = mutableListOf<String>()
        val resolved = firstAvailableUiColor(
            uiColor = { key ->
                requested += key
                when (key) {
                    "Component.accentColor" -> null
                    "Menu.selectionBackground" -> rgb(90, 100, 110)
                    "List.selectionBackground" -> rgb(120, 130, 140)
                    else -> null
                }
            },
            keys = primaryUiColorKeys()
        )

        assertColorEquals(rgb(90, 100, 110), checkNotNull(resolved))
        assertEquals(listOf("Component.accentColor", "Menu.selectionBackground"), requested)
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
    fun `desktop color scheme uses late swing fallback keys when earlier accent keys are absent`() {
        // Covers later UI fallback positions so the desktop theme builder still resolves accent roles after skipping earlier missing keys.
        val requestedKeys = mutableListOf<String>()
        val uiColors = mapOf(
            "Panel.background" to rgb(241, 242, 243),
            "Panel.foreground" to rgb(21, 22, 23),
            "TextField.background" to rgb(251, 252, 253),
            "TextField.foreground" to rgb(31, 32, 33),
            "Button.background" to rgb(231, 232, 233),
            "Button.foreground" to rgb(81, 82, 83),
            "Button.default.background" to rgb(20, 40, 80),
            "TextField.selectionForeground" to rgb(250, 250, 250),
            "ComboBox.selectionBackground" to rgb(101, 102, 103),
            "nimbusFocus" to rgb(111, 112, 113)
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
        assertColorEquals(uiColors.getValue("Button.default.background"), scheme.primary)
        assertColorEquals(
            pickBestContrastOnBackground(
                scheme.primary,
                listOf(
                    uiColors.getValue("TextField.selectionForeground"),
                    uiColors.getValue("TextField.foreground"),
                    uiColors.getValue("Panel.foreground")
                )
            ),
            scheme.onPrimary
        )
        assertColorEquals(uiColors.getValue("ComboBox.selectionBackground"), scheme.secondary)
        assertColorEquals(uiColors.getValue("nimbusFocus"), scheme.tertiary)
        assertTrue(
            requestedKeys.containsAll(
                listOf(
                    "Component.accentColor",
                    "Menu.selectionBackground",
                    "List.selectionBackground",
                    "TextField.selectionBackground",
                    "Button.default.background",
                    "TabbedPane.selected",
                    "ComboBox.selectionBackground",
                    "Button.focus",
                    "Focus.color",
                    "nimbusFocus"
                )
            ),
            "Expected the builder to keep scanning later Swing fallback keys when earlier accent keys are absent."
        )
    }

    @Test
    fun `desktop color scheme uses plasma selection window and inactive fallback roles when higher priority accent sources are absent`() {
        // Covers the middle KDE fallback chain: selection accents, window hover/link roles, and inactive foreground fallback.
        val scheme = buildDesktopSystemColorScheme(
            kdeGlobals = parseKdeGlobals(
                """
                [Colors:Window]
                BackgroundNormal=245,246,247
                ForegroundNormal=20,21,22
                ForegroundInactive=100,101,102
                DecorationHover=66,77,88
                ForegroundLink=99,109,119
                ForegroundNegative=150,10,20
                [Colors:View]
                BackgroundNormal=250,251,252
                ForegroundNormal=30,31,32
                [Colors:Selection]
                BackgroundNormal=40,50,60
                ForegroundNormal=250,250,250
                """.trimIndent()
            ),
            uiColor = { null },
            isDarkFallback = false
        )

        assertColorEquals(rgb(40, 50, 60), scheme.primary)
        assertColorEquals(
            pickBestContrastOnBackground(
                scheme.primary,
                listOf(rgb(250, 250, 250), rgb(30, 31, 32), rgb(20, 21, 22))
            ),
            scheme.onPrimary
        )
        assertColorEquals(rgb(66, 77, 88), scheme.secondary)
        assertColorEquals(rgb(99, 109, 119), scheme.tertiary)
        assertColorEquals(rgb(150, 10, 20), scheme.error)
        assertColorEquals(rgb(100, 101, 102), scheme.onSurfaceVariant)
    }

    @Test
    fun `base desktop color resolver falls back to explicit colors and computed contrast when optional roles are absent`() {
        // Covers the deepest base-color fallback path so missing KDE and Swing colors still produce a readable surface pair.
        val base = resolveBaseDesktopColors(
            kdeGlobals = parseKdeGlobals(""),
            uiColor = { null },
            fallbackBackground = rgb(11, 12, 13),
            fallbackOnBackground = rgb(21, 22, 23),
            fallbackSurfaceVariant = rgb(240, 241, 242)
        )

        assertColorEquals(rgb(11, 12, 13), base.background)
        assertColorEquals(rgb(21, 22, 23), base.onBackground)
        assertColorEquals(base.background, base.surface)
        assertColorEquals(base.onBackground, base.onSurface)
        assertColorEquals(rgb(240, 241, 242), base.surfaceVariant)
        assertColorEquals(
            pickBestContrastOnBackground(base.surfaceVariant, listOf(base.onSurface, base.onBackground)),
            base.onSurfaceVariant
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

    @Test
    fun `resolve secondary returns plasma button decoration hover when present`() {
        val kde = parseKdeGlobals(
            """
            [Colors:Button]
            DecorationHover=50,50,60
            """.trimIndent()
        )
        val surfaceVariant = rgb(200, 200, 200)
        val primary = rgb(10, 10, 10)
        val resolved = resolveSecondary(kde, { null }, surfaceVariant, primary, isDark = false)
        assertColorEquals(rgb(50, 50, 60), resolved)
    }

    @Test
    fun `resolve error prefers view foreground negative over window`() {
        val kde = parseKdeGlobals(
            """
            [Colors:View]
            ForegroundNegative=200,0,0
            [Colors:Window]
            ForegroundNegative=0,200,0
            """.trimIndent()
        )
        val resolved = resolveError(kde, rgb(50, 50, 50))
        assertColorEquals(rgb(200, 0, 0), resolved)
    }

    @Test
    fun `resolve error uses window foreground negative when view role is absent`() {
        val kde = parseKdeGlobals(
            """
            [Colors:Window]
            ForegroundNegative=0,180,0
            """.trimIndent()
        )
        val resolved = resolveError(kde, rgb(1, 2, 3))
        assertColorEquals(rgb(0, 180, 0), resolved)
    }

    @Test
    fun `extract kde section name only recognizes balanced bracket lines`() {
        assertEquals("Valid", extractKdeSectionName("[Valid]"))
        assertNull(extractKdeSectionName("[unclosed"))
        assertNull(extractKdeSectionName("not-open]"))
        assertNull(extractKdeSectionName(""))
    }

    @Test
    fun `should ignore kde globals line treats comments empties and trimmed blanks`() {
        assertTrue(shouldIgnoreKdeGlobalsLine(""))
        assertFalse(shouldIgnoreKdeGlobalsLine("   "), "Whitespace-only raw lines are trimmed before this helper runs in parseKdeGlobals.")
        assertTrue(shouldIgnoreKdeGlobalsLine("   ".trim()), "Trimmed blank lines are ignored the same as empty lines.")
        assertTrue(shouldIgnoreKdeGlobalsLine("# comment"))
        assertTrue(shouldIgnoreKdeGlobalsLine("; also comment"))
        assertFalse(shouldIgnoreKdeGlobalsLine("[Real]"))
        assertFalse(shouldIgnoreKdeGlobalsLine("Key=value"))
    }

    @Test
    fun `parse kde globals ignores comment blank malformed and continuation lines`() {
        val kde = parseKdeGlobals(
            """
            # leading comment
            [Colors:View]
            ForegroundNormal=1,2,3
            
            ; semicolon comment
            notAKeyLine
            =noKeyBeforeEquals
            [Second]
            ValidKey=10,20,30
            """.trimIndent()
        )
        assertNotNull(kde.color("Colors:View", "ForegroundNormal"))
        assertColorEquals(rgb(1, 2, 3), kde.color("Colors:View", "ForegroundNormal")!!)
        assertNotNull(kde.color("Second", "ValidKey"))
        assertColorEquals(rgb(10, 20, 30), kde.color("Second", "ValidKey")!!)
        assertNull(kde.color("Second", "MissingKey"))
    }

    @Test
    fun `remember desktop system color scheme composable returns a scheme`() = runComposeUiTest {
        // Smoke-tests the default desktop theme hook so the Compose entry point still produces a color scheme.
        setContent {
            MaterialTheme {
                val scheme = rememberDesktopSystemColorScheme()
                Text("primary:${colorToken(scheme.primary)}")
            }
        }
        onNodeWithText("primary:", substring = true).assertIsDisplayed()
    }

    @Test
    fun `kde globals color returns null when stored rgb is invalid`() {
        val kde = parseKdeGlobals(
            """
            [Bad]
            Key=1,x,3
            """.trimIndent()
        )
        assertNull(kde.color("Bad", "Key"))
    }

    @Test
    fun `resolve preferred on primary uses kde selection foreground when present`() {
        val kde = parseKdeGlobals(
            """
            [Colors:Selection]
            ForegroundNormal=100,150,200
            """.trimIndent()
        )
        val resolved = resolvePreferredOnPrimary(kde, { null }, onSurface = rgb(40, 40, 40))
        assertColorEquals(rgb(100, 150, 200), resolved)
    }

    @Test
    fun `resolve secondary uses selection decoration hover when button and window hovers absent`() {
        val kde = parseKdeGlobals(
            """
            [Colors:Selection]
            DecorationHover=70,80,90
            """.trimIndent()
        )
        val resolved = resolveSecondary(kde, { null }, rgb(200, 200, 200), rgb(10, 10, 10), isDark = false)
        assertColorEquals(rgb(70, 80, 90), resolved)
    }

    @Test
    fun `resolve secondary uses swing tab selection when plasma hovers are absent`() {
        val kde = parseKdeGlobals("")
        val tabSel = rgb(55, 66, 77)
        val resolved = resolveSecondary(
            kdeGlobals = kde,
            uiColor = { key -> if (key == "TabbedPane.selected") tabSel else null },
            surfaceVariant = rgb(200, 200, 200),
            primary = rgb(10, 10, 10),
            isDark = false
        )
        assertColorEquals(tabSel, resolved)
    }

    @Test
    fun `resolve secondary uses darker tint fallback when isDark is true`() {
        val kde = parseKdeGlobals("")
        val surfaceVariant = rgb(200, 200, 200)
        val primary = rgb(10, 10, 10)
        val resolved = resolveSecondary(kde, { null }, surfaceVariant, primary, isDark = true)
        assertColorEquals(tinted(surfaceVariant, primary, 0.36f), resolved)
    }

    @Test
    fun `resolve tertiary prefers view foreground link over window`() {
        val kde = parseKdeGlobals(
            """
            [Colors:View]
            ForegroundLink=1,2,3
            [Colors:Window]
            ForegroundLink=250,250,250
            """.trimIndent()
        )
        val resolved = resolveTertiary(kde, { null }, rgb(200, 200, 200), rgb(10, 10, 10), isDark = false)
        assertColorEquals(rgb(1, 2, 3), resolved)
    }

    @Test
    fun `resolve tertiary uses darker tint fallback when isDark is true`() {
        val kde = parseKdeGlobals("")
        val surface = rgb(252, 252, 253)
        val primary = rgb(30, 60, 120)
        val resolved = resolveTertiary(kde, { null }, surface, primary, isDark = true)
        assertColorEquals(tinted(surface, primary, 0.42f), resolved)
    }

    @Test
    fun `desktop color scheme maps surface variant from button background when view alternate is absent`() {
        val cfg = """
            [Colors:View]
            BackgroundNormal=250,251,252
            ForegroundNormal=49,54,59
            [Colors:Window]
            BackgroundNormal=255,255,255
            ForegroundNormal=49,54,59
            [Colors:Button]
            BackgroundNormal=235,236,237
            ForegroundNormal=80,86,94
            [General]
            LastUsedCustomAccentColor=40,60,120
        """.trimIndent()
        val scheme = buildDesktopSystemColorScheme(
            kdeGlobals = parseKdeGlobals(cfg),
            uiColor = { null },
            isDarkFallback = false
        )
        assertColorEquals(rgb(235, 236, 237), scheme.surfaceVariant)
    }

    @Test
    fun `base desktop color resolver uses ui fallbacks when plasma roles are missing`() {
        val base = resolveBaseDesktopColors(
            kdeGlobals = parseKdeGlobals(""),
            uiColor = { key ->
                when (key) {
                    "Panel.background" -> rgb(241, 242, 243)
                    "Panel.foreground" -> rgb(21, 22, 23)
                    "TextField.background" -> rgb(251, 252, 253)
                    "TextField.foreground" -> rgb(31, 32, 33)
                    "Button.background" -> rgb(233, 234, 235)
                    "Button.foreground" -> rgb(77, 78, 79)
                    else -> null
                }
            },
            fallbackBackground = rgb(1, 1, 1),
            fallbackOnBackground = rgb(2, 2, 2),
            fallbackSurfaceVariant = rgb(3, 3, 3)
        )
        assertColorEquals(rgb(241, 242, 243), base.background)
        assertColorEquals(rgb(21, 22, 23), base.onBackground)
        assertColorEquals(rgb(251, 252, 253), base.surface)
        assertColorEquals(rgb(31, 32, 33), base.onSurface)
        assertColorEquals(rgb(233, 234, 235), base.surfaceVariant)
        assertColorEquals(rgb(77, 78, 79), base.onSurfaceVariant)
    }

    @Test
    fun `primary resolver walks plasma and ui fallback order`() {
        val plasma = parseKdeGlobals(
            """
            [Colors:Window]
            DecorationFocus=77,88,99
            [General]
            LastUsedCustomAccentColor=11,22,33
            [Colors:Selection]
            BackgroundNormal=44,55,66
            """.trimIndent()
        )
        val fromPlasma = resolvePrimary(plasma, { null }, rgb(1, 1, 1))
        assertColorEquals(rgb(44, 55, 66), fromPlasma)

        val fromGeneralAccent = resolvePrimary(
            parseKdeGlobals(
                """
                [General]
                LastUsedCustomAccentColor=12,34,56
                """.trimIndent()
            ),
            { null },
            rgb(2, 2, 2)
        )
        assertColorEquals(rgb(12, 34, 56), fromGeneralAccent)

        val fromUi = resolvePrimary(parseKdeGlobals(""), { key ->
            if (key == "Menu.selectionBackground") rgb(77, 88, 99) else null
        }, rgb(5, 5, 5))
        assertColorEquals(rgb(77, 88, 99), fromUi)

        val fallback = resolvePrimary(parseKdeGlobals(""), { null }, rgb(9, 9, 9))
        assertColorEquals(rgb(9, 9, 9), fallback)
    }

    @Test
    fun `desktop accent and container role resolvers return contrast and tinted containers`() {
        val accents = resolveDesktopAccentRoles(
            kdeGlobals = parseKdeGlobals(""),
            uiColor = { null },
            surface = rgb(250, 250, 250),
            surfaceVariant = rgb(236, 236, 236),
            onSurface = rgb(40, 40, 40),
            onBackground = rgb(30, 30, 30),
            fallbackPrimary = rgb(10, 80, 160),
            fallbackError = rgb(186, 26, 26),
            isDark = false
        )
        val containers = resolveDesktopContainerRoles(
            isDark = false,
            background = rgb(245, 245, 245),
            surface = rgb(250, 250, 250),
            surfaceVariant = rgb(236, 236, 236),
            onSurface = rgb(40, 40, 40),
            onBackground = rgb(30, 30, 30),
            onSurfaceVariant = rgb(90, 90, 90),
            primary = accents.primary,
            secondary = accents.secondary,
            tertiary = accents.tertiary,
            error = accents.error,
            onError = accents.onError
        )
        assertTrue(contrastRatio(accents.primary, accents.onPrimary) >= 4.5)
        assertColorEquals(tinted(rgb(245, 245, 245), accents.primary, 0.14f), containers.primaryContainer)
        assertColorEquals(tinted(rgb(250, 250, 250), accents.error, 0.10f), containers.errorContainer)
    }

    @Test
    fun `base desktop color resolver falls back to background when view background is missing`() {
        val base = resolveBaseDesktopColors(
            kdeGlobals = parseKdeGlobals(
                """
                [Colors:Window]
                BackgroundNormal=240,241,242
                ForegroundNormal=40,41,42
                """.trimIndent()
            ),
            uiColor = { null },
            fallbackBackground = rgb(1, 1, 1),
            fallbackOnBackground = rgb(2, 2, 2),
            fallbackSurfaceVariant = rgb(3, 3, 3)
        )
        assertColorEquals(base.background, base.surface)
        assertColorEquals(base.onBackground, base.onSurface)
    }

    @Test
    fun `accent role resolver uses dark secondary tint and fallback error when no theme hints exist`() {
        val accents = resolveDesktopAccentRoles(
            kdeGlobals = parseKdeGlobals(""),
            uiColor = { null },
            surface = rgb(250, 250, 250),
            surfaceVariant = rgb(236, 236, 236),
            onSurface = rgb(40, 40, 40),
            onBackground = rgb(30, 30, 30),
            fallbackPrimary = rgb(10, 80, 160),
            fallbackError = rgb(186, 26, 26),
            isDark = true
        )
        assertColorEquals(tinted(rgb(236, 236, 236), accents.primary, 0.36f), accents.secondary)
        assertColorEquals(rgb(186, 26, 26), accents.error)
    }

    @Test
    fun `base resolver falls back to contrast color when button and inactive foreground are missing`() {
        val base = resolveBaseDesktopColors(
            kdeGlobals = parseKdeGlobals(
                """
                [Colors:Window]
                BackgroundNormal=255,255,255
                ForegroundNormal=25,25,25
                [Colors:View]
                BackgroundNormal=250,250,250
                ForegroundNormal=20,20,20
                """.trimIndent()
            ),
            uiColor = { key ->
                when (key) {
                    "Panel.background" -> rgb(240, 240, 240)
                    "Panel.foreground" -> rgb(30, 30, 30)
                    "TextField.background" -> rgb(245, 245, 245)
                    "TextField.foreground" -> rgb(35, 35, 35)
                    else -> null
                }
            },
            fallbackBackground = rgb(1, 1, 1),
            fallbackOnBackground = rgb(2, 2, 2),
            fallbackSurfaceVariant = rgb(3, 3, 3)
        )
        assertTrue(contrastRatio(base.surfaceVariant, base.onSurfaceVariant) >= 4.5)
    }

    @Test
    fun `primary resolver honors focus precedence over ui accents`() {
        val kde = parseKdeGlobals(
            """
            [Colors:Window]
            DecorationFocus=12,34,56
            [Colors:Button]
            DecorationFocus=200,201,202
            """.trimIndent()
        )
        val resolved = resolvePrimary(
            kdeGlobals = kde,
            uiColor = { key -> if (key == "Component.accentColor") rgb(90, 91, 92) else null },
            fallbackPrimary = rgb(9, 9, 9)
        )
        assertColorEquals(rgb(12, 34, 56), resolved)
    }

    @Test
    fun `primary resolver falls through each source in order`() {
        val selection = resolvePrimary(
            kdeGlobals = parseKdeGlobals(
                """
                [Colors:Selection]
                BackgroundNormal=31,41,51
                """.trimIndent()
            ),
            uiColor = { null },
            fallbackPrimary = rgb(1, 1, 1)
        )
        assertColorEquals(rgb(31, 41, 51), selection)

        val buttonFocus = resolvePrimary(
            kdeGlobals = parseKdeGlobals(
                """
                [Colors:Button]
                DecorationFocus=61,71,81
                """.trimIndent()
            ),
            uiColor = { null },
            fallbackPrimary = rgb(1, 1, 1)
        )
        assertColorEquals(rgb(61, 71, 81), buttonFocus)

        val fallback = resolvePrimary(
            kdeGlobals = parseKdeGlobals(""),
            uiColor = { null },
            fallbackPrimary = rgb(91, 92, 93)
        )
        assertColorEquals(rgb(91, 92, 93), fallback)
    }

    @Test
    fun `preferred on primary resolver scans ui keys in declared order`() {
        val requests = mutableListOf<String>()
        val resolved = resolvePreferredOnPrimary(
            kdeGlobals = parseKdeGlobals(""),
            uiColor = { key ->
                requests += key
                if (key == "Menu.selectionForeground") rgb(111, 112, 113) else null
            },
            onSurface = rgb(10, 10, 10)
        )
        assertColorEquals(rgb(111, 112, 113), resolved)
        assertTrue(
            requests.indexOf("TextField.selectionForeground") < requests.indexOf("Menu.selectionForeground"),
            "Expected resolver to query UI keys in declared fallback order."
        )
    }

    @Test
    fun `secondary resolver scans ui selection keys in declared order`() {
        val requests = mutableListOf<String>()
        val resolved = resolveSecondary(
            kdeGlobals = parseKdeGlobals(""),
            uiColor = { key ->
                requests += key
                if (key == "Tree.selectionBackground") rgb(141, 142, 143) else null
            },
            surfaceVariant = rgb(220, 220, 220),
            primary = rgb(10, 80, 160),
            isDark = false
        )
        assertColorEquals(rgb(141, 142, 143), resolved)
        assertEquals(
            listOf("TabbedPane.selected", "ComboBox.selectionBackground", "Tree.selectionBackground"),
            requests
        )
    }

    @Test
    fun `secondary resolver returns window hover before selection hover`() {
        val resolved = resolveSecondary(
            kdeGlobals = parseKdeGlobals(
                """
                [Colors:Window]
                DecorationHover=71,81,91
                [Colors:Selection]
                DecorationHover=101,111,121
                """.trimIndent()
            ),
            uiColor = { null },
            surfaceVariant = rgb(220, 220, 220),
            primary = rgb(10, 80, 160),
            isDark = false
        )
        assertColorEquals(rgb(71, 81, 91), resolved)
    }

    @Test
    fun `tertiary resolver scans ui focus keys in declared order`() {
        val requests = mutableListOf<String>()
        val resolved = resolveTertiary(
            kdeGlobals = parseKdeGlobals(""),
            uiColor = { key ->
                requests += key
                if (key == "nimbusFocus") rgb(201, 202, 203) else null
            },
            surface = rgb(245, 245, 245),
            primary = rgb(20, 70, 160),
            isDark = false
        )
        assertColorEquals(rgb(201, 202, 203), resolved)
        assertEquals(listOf("Button.focus", "Focus.color", "nimbusFocus"), requests)
    }

    @Test
    fun `tertiary resolver returns window link when view link missing`() {
        val resolved = resolveTertiary(
            kdeGlobals = parseKdeGlobals(
                """
                [Colors:Window]
                ForegroundLink=87,97,107
                """.trimIndent()
            ),
            uiColor = { null },
            surface = rgb(245, 245, 245),
            primary = rgb(20, 70, 160),
            isDark = false
        )
        assertColorEquals(rgb(87, 97, 107), resolved)
    }

    @Test
    fun `container resolver applies dark and light container tint factors`() {
        val dark = resolveDesktopContainerRoles(
            isDark = true,
            background = rgb(30, 30, 30),
            surface = rgb(40, 40, 40),
            surfaceVariant = rgb(50, 50, 50),
            onSurface = rgb(230, 230, 230),
            onBackground = rgb(220, 220, 220),
            onSurfaceVariant = rgb(180, 180, 180),
            primary = rgb(100, 140, 220),
            secondary = rgb(90, 120, 200),
            tertiary = rgb(80, 110, 190),
            error = rgb(220, 80, 80),
            onError = rgb(255, 255, 255)
        )
        val light = resolveDesktopContainerRoles(
            isDark = false,
            background = rgb(240, 240, 240),
            surface = rgb(250, 250, 250),
            surfaceVariant = rgb(235, 235, 235),
            onSurface = rgb(30, 30, 30),
            onBackground = rgb(25, 25, 25),
            onSurfaceVariant = rgb(90, 90, 90),
            primary = rgb(40, 80, 160),
            secondary = rgb(35, 70, 150),
            tertiary = rgb(30, 60, 140),
            error = rgb(180, 30, 30),
            onError = rgb(255, 255, 255)
        )
        assertColorEquals(tinted(rgb(30, 30, 30), rgb(100, 140, 220), 0.28f), dark.primaryContainer)
        assertColorEquals(tinted(rgb(240, 240, 240), rgb(40, 80, 160), 0.14f), light.primaryContainer)
    }

    @Test
    fun `base resolver prefers plasma roles over ui and fallback`() {
        val base = resolveBaseDesktopColors(
            kdeGlobals = parseKdeGlobals(
                """
                [Colors:Window]
                BackgroundNormal=210,211,212
                ForegroundNormal=10,11,12
                ForegroundInactive=13,14,15
                [Colors:View]
                BackgroundNormal=213,214,215
                BackgroundAlternate=216,217,218
                ForegroundNormal=16,17,18
                [Colors:Button]
                ForegroundNormal=19,20,21
                """.trimIndent()
            ),
            uiColor = { key ->
                when (key) {
                    "Panel.background" -> rgb(240, 240, 240)
                    "Panel.foreground" -> rgb(30, 30, 30)
                    "TextField.background" -> rgb(245, 245, 245)
                    "TextField.foreground" -> rgb(35, 35, 35)
                    "Button.background" -> rgb(233, 233, 233)
                    "Button.foreground" -> rgb(77, 77, 77)
                    else -> null
                }
            },
            fallbackBackground = rgb(1, 1, 1),
            fallbackOnBackground = rgb(2, 2, 2),
            fallbackSurfaceVariant = rgb(3, 3, 3)
        )

        assertColorEquals(rgb(210, 211, 212), base.background)
        assertColorEquals(rgb(10, 11, 12), base.onBackground)
        assertColorEquals(rgb(213, 214, 215), base.surface)
        assertColorEquals(rgb(16, 17, 18), base.onSurface)
        assertColorEquals(rgb(216, 217, 218), base.surfaceVariant)
        assertColorEquals(rgb(19, 20, 21), base.onSurfaceVariant)
    }

    @Test
    fun `remember desktop system color scheme honors custom scheme builder`() = runComposeUiTest {
        // Confirms callers can still inject a prebuilt scheme without losing the public builder override seam.
        val marker = rgb(12, 34, 56)
        val customScheme = lightColorScheme(primary = marker)

        setContent {
            val scheme = rememberDesktopSystemColorScheme { customScheme }
            Text("marker:${colorToken(scheme.primary)}")
        }

        onNodeWithText("marker:${colorToken(marker)}").assertIsDisplayed()
    }

    @Test
    fun `remember desktop system color scheme recomputes when tracked desktop theme state changes`() = runComposeUiTest {
        // Simulates a runtime system theme flip so Material colors refresh without restarting the desktop app.
        val light = lightColorScheme(primary = rgb(200, 40, 40))
        val dark = darkColorScheme(primary = rgb(40, 90, 200))
        val refreshKey = androidx.compose.runtime.mutableStateOf(
            DesktopSystemThemeSignature(
                kdeGlobals = parseKdeGlobals(lightPlasmaConfig()),
                uiColors = emptyMap(),
                isDarkFallback = false,
                activeLookAndFeelClassName = "light"
            )
        )

        setContent {
            val scheme = rememberDesktopSystemColorScheme(
                schemeBuilder = {
                    if (refreshKey.value.activeLookAndFeelClassName == "light") light else dark
                },
                refreshKeyProvider = { refreshKey.value },
                refreshIntervalMillis = 1L
            )
            Text(
                "primary:${if (scheme.primary == light.primary) "light" else "dark"}"
            )
        }

        onNodeWithText("primary:light").assertIsDisplayed()

        refreshKey.value = refreshKey.value.copy(
            kdeGlobals = parseKdeGlobals(darkPlasmaConfig()),
            isDarkFallback = true,
            activeLookAndFeelClassName = "dark"
        )

        Thread.sleep(50)
        waitForIdle()
        onNodeWithText("primary:dark").assertIsDisplayed()
    }

    @Test
    fun `remember desktop system color scheme can build through default dependency seams`() = runComposeUiTest {
        // Exercises the injected dependency seam used by the desktop theme builder when external color sources are supplied.
        val injectedKde = parseKdeGlobals(
            """
            [Colors:Window]
            BackgroundNormal=9,10,11
            ForegroundNormal=240,241,242
            [Colors:View]
            BackgroundNormal=12,13,14
            ForegroundNormal=230,231,232
            """.trimIndent()
        )

        setContent {
            val scheme = rememberDesktopSystemColorScheme {
                buildDesktopSystemColorScheme(
                    defaultDesktopSystemColorSchemeDependencies(
                        kdeGlobalsProvider = { injectedKde },
                        swingUiColor = { null },
                        desktopDarkFallback = { false }
                    )
                )
            }
            Text("bg:${colorToken(scheme.background)}")
        }

        onNodeWithText("bg:9-10-11").assertIsDisplayed()
    }

    @Test
    fun `tracked desktop ui color keys are unique and include every resolver lookup`() {
        // Guards the polling signature so runtime theme refreshes continue monitoring every Swing color key we resolve.
        val trackedKeys = trackedDesktopUiColorKeys()

        assertEquals(trackedKeys.size, trackedKeys.distinct().size)
        assertTrue(trackedKeys.containsAll(primaryUiColorKeys()))
        assertTrue(trackedKeys.containsAll(onPrimaryUiColorKeys()))
        assertTrue(trackedKeys.containsAll(secondaryUiColorKeys()))
        assertTrue(trackedKeys.containsAll(tertiaryUiColorKeys()))
        assertTrue(trackedKeys.containsAll(listOf("Panel.background", "Panel.foreground", "TextField.background", "TextField.foreground", "Button.background", "Button.foreground")))
    }

    @Test
    fun `desktop ui color maps swing manager colors and returns null for unknown keys`() {
        withUiManagerColors(mapOf("Panel.background" to awt(10, 20, 30))) {
            val panelBackground = desktopUiColor("Panel.background")
            assertNotNull(panelBackground)
            assertColorEquals(rgb(10, 20, 30), panelBackground)
            assertNull(desktopUiColor("DefinitelyMissingColorKey"))
        }
    }

    @Test
    fun `container tint resolver covers every role for light and dark palettes`() {
        for (role in DesktopContainerTintRole.entries) {
            val light = resolveDesktopContainerTintAmount(role, isDark = false)
            val dark = resolveDesktopContainerTintAmount(role, isDark = true)
            assertTrue(light in 0f..1f && dark in 0f..1f)
        }
    }

    @Test
    fun `first available ui color returns null when every candidate is missing`() {
        assertNull(firstAvailableUiColor({ null }, primaryUiColorKeys()))
    }

    @Test
    fun `is dark from pair handles awt null channels and luminance ordering`() {
        assertFalse(isDarkFromPair(null, awt(255, 255, 255)))
        assertFalse(isDarkFromPair(awt(0, 0, 0), null))
        assertTrue(isDarkFromPair(awt(0, 0, 0), awt(255, 255, 255)))
        assertFalse(isDarkFromPair(awt(255, 255, 255), awt(0, 0, 0)))
    }

    @Test
    fun `is dark from pair or null returns null when plasma pair is incomplete`() {
        assertNull(isDarkFromPairOrNull(null, rgb(1, 2, 3)))
        assertNull(isDarkFromPairOrNull(rgb(1, 2, 3), null))
    }

    @Test
    fun `plasma dark theme from kde returns null when window color pair is incomplete`() {
        assertNull(plasmaDarkThemeFromKde(parseKdeGlobals("")))
        val onlyBackground = parseKdeGlobals(
            """
            [Colors:Window]
            BackgroundNormal=0,0,0
            """.trimIndent()
        )
        assertNull(plasmaDarkThemeFromKde(onlyBackground))
    }

    @Test
    fun `plasma dark theme from kde follows window luminance ordering`() {
        val lightWindow = parseKdeGlobals(
            """
            [Colors:Window]
            BackgroundNormal=255,255,255
            ForegroundNormal=0,0,0
            """.trimIndent()
        )
        assertEquals(false, plasmaDarkThemeFromKde(lightWindow))
        val darkWindow = parseKdeGlobals(
            """
            [Colors:Window]
            BackgroundNormal=0,0,0
            ForegroundNormal=255,255,255
            """.trimIndent()
        )
        assertEquals(true, plasmaDarkThemeFromKde(darkWindow))
    }

    @Test
    fun `compose relative luminance and contrast ratio stay ordered for light and dark grays`() {
        val dark = rgb(12, 14, 16)
        val light = rgb(230, 232, 234)
        assertTrue(relativeLuminanceCompose(dark) < relativeLuminanceCompose(light))
        assertTrue(contrastRatio(dark, light) > 4.5)
    }

    @Test
    fun `read kde globals from sources parses existing config files`() {
        val root = createTempDirectory("kde-read-ok")
        val configHome = root.resolve(".config").createDirectories()
        configHome.resolve("kdeglobals").writeText(
            """
            [Section]
            Key=1,2,3
            """.trimIndent()
        )

        val kde = readKdeGlobalsFromSources(
            xdgConfigHome = { configHome.toString() },
            userHome = { "/unused" },
            fileExists = File::exists,
            readText = File::readText
        )

        assertColorEquals(rgb(1, 2, 3), kde.color("Section", "Key")!!)
    }

    @Test
    fun `parse kde globals safely falls back to empty sections when parsing throws`() {
        val kde = parseKdeGlobalsSafely { error("unreadable file") }
        assertNull(kde.color("Any", "Key"))
    }

    @Test
    fun `parse kde globals ignores malformed key lines and blank keys`() {
        val kde = parseKdeGlobals(
            """
            [S]
            =onlyValue
            noEqualsSign
            Good=4,5,6
            """.trimIndent()
        )
        assertNull(kde.color("S", ""))
        assertColorEquals(rgb(4, 5, 6), kde.color("S", "Good")!!)
    }

    @Test
    fun `resolve kde config home treats blank xdg as user config directory`() {
        assertEquals("/users/example/.config", resolveKdeConfigHome("   ", "/users/example"))
    }

    @Test
    fun `resolve kde config home preserves non blank xdg path`() {
        assertEquals("/tmp/custom-config", resolveKdeConfigHome("/tmp/custom-config", "/users/example"))
    }

    @Test
    fun `linearize srgb channel covers piecewise gamma segments`() {
        val low = linearizeSrgbChannel(0.02f)
        val high = linearizeSrgbChannel(0.5f)
        assertTrue(low < high)
    }

    @Test
    fun `resolve primary walks ui accent list when plasma palette lacks accents`() {
        val keys = primaryUiColorKeys()
        for (targetKey in keys) {
            val resolved = resolvePrimary(
                kdeGlobals = parseKdeGlobals(""),
                uiColor = { key -> if (key == targetKey) rgb(9, 8, 7) else null },
                fallbackPrimary = rgb(1, 1, 1)
            )
            assertColorEquals(rgb(9, 8, 7), resolved, tolerance = 0.01f)
        }
    }

    @Test
    fun `resolve primary uses button decoration focus when window focus is absent`() {
        val kde = parseKdeGlobals(
            """
            [Colors:Button]
            DecorationFocus=55,66,77
            """.trimIndent()
        )
        assertColorEquals(rgb(55, 66, 77), resolvePrimary(kde, { null }, rgb(1, 1, 1)))
    }

    @Test
    fun `default desktop dependencies inject kde globals swing ui colors and dark fallback`() {
        val injectedKde = parseKdeGlobals(
            """
            [Colors:Window]
            BackgroundNormal=10,10,10
            ForegroundNormal=240,240,240
            [Colors:View]
            BackgroundNormal=20,20,20
            ForegroundNormal=230,230,230
            """.trimIndent()
        )
        val swingHits = mutableListOf<String>()
        val deps = defaultDesktopSystemColorSchemeDependencies(
            kdeGlobalsProvider = { injectedKde },
            swingUiColor = { key ->
                swingHits += key
                when (key) {
                    "Panel.background" -> rgb(5, 5, 5)
                    else -> null
                }
            },
            desktopDarkFallback = { false }
        )
        assertEquals(injectedKde, deps.kdeGlobals)
        assertFalse(deps.isDarkFallback)
        val scheme = buildDesktopSystemColorScheme(deps)
        assertColorEquals(rgb(10, 10, 10), scheme.background)
        assertTrue(swingHits.isNotEmpty())
    }

    @Test
    fun `build desktop system color scheme default dependency overload matches explicit kde and fallback`() {
        val kde = parseKdeGlobals(
            """
            [Colors:Window]
            BackgroundNormal=255,255,255
            ForegroundNormal=20,20,20
            """.trimIndent()
        )
        val explicit = buildDesktopSystemColorScheme(kdeGlobals = kde, uiColor = { null }, isDarkFallback = true)
        val viaDeps = buildDesktopSystemColorScheme(
            dependencies = defaultDesktopSystemColorSchemeDependencies(
                kdeGlobalsProvider = { kde },
                swingUiColor = { null },
                desktopDarkFallback = { true }
            )
        )
        assertColorEquals(explicit.background, viaDeps.background)
        assertColorEquals(explicit.primary, viaDeps.primary)
    }

    @Test
    fun `read kde globals uses xdg config home when non blank and reads file`() {
        val root = createTempDirectory("kde-xdg")
        val xdgHome = root.resolve("xdg").createDirectories()
        xdgHome.resolve("kdeglobals").writeText(
            """
            [XDG]
            Key=5,6,7
            """.trimIndent()
        )
        val kde = readKdeGlobals(
            xdgConfigHome = { xdgHome.toString() },
            userHome = { "/unused" },
            fileExists = File::exists,
            readText = File::readText
        )
        assertColorEquals(rgb(5, 6, 7), kde.color("XDG", "Key")!!)
    }

    @Test
    fun `read kde globals uses user home config when xdg is null`() {
        val root = createTempDirectory("kde-user")
        val cfg = root.resolve(".config").createDirectories()
        cfg.resolve("kdeglobals").writeText("[U]\nK=1,2,3\n")
        val kde = readKdeGlobals(
            xdgConfigHome = { null },
            userHome = { root.toString() },
            fileExists = File::exists,
            readText = File::readText
        )
        assertColorEquals(rgb(1, 2, 3), kde.color("U", "K")!!)
    }

    @Test
    fun `read kde globals uses user home when xdg is blank`() {
        val root = createTempDirectory("kde-blank-xdg")
        val cfg = root.resolve(".config").createDirectories()
        cfg.resolve("kdeglobals").writeText("[B]\nK=8,9,10\n")
        val kde = readKdeGlobals(
            xdgConfigHome = { "   " },
            userHome = { root.toString() },
            fileExists = File::exists,
            readText = File::readText
        )
        assertColorEquals(rgb(8, 9, 10), kde.color("B", "K")!!)
    }

    @Test
    fun `read kde globals returns empty map when kdeglobals file missing`() {
        val root = createTempDirectory("kde-missing")
        val kde = readKdeGlobals(
            xdgConfigHome = { root.resolve("no-kde").createDirectories().toString() },
            userHome = { root.toString() },
            fileExists = File::exists,
            readText = File::readText
        )
        assertNull(kde.color("Any", "Key"))
    }

    @Test
    fun `read kde globals returns empty map when read text throws`() {
        val root = createTempDirectory("kde-read-throw")
        val fakeFile = root.resolve("kdeglobals").toFile()
        val kde = readKdeGlobals(
            xdgConfigHome = { root.toString() },
            userHome = { "/unused" },
            fileExists = { it == fakeFile },
            readText = { error("simulated io failure") }
        )
        assertNull(kde.color("S", "K"))
    }

    @Test
    fun `is likely dark theme uses injected panel colors`() {
        assertTrue(
            isLikelyDarkTheme(
                panelBackground = { AwtColor(0, 0, 0) },
                panelForeground = { AwtColor(255, 255, 255) }
            )
        )
        assertFalse(
            isLikelyDarkTheme(
                panelBackground = { AwtColor(250, 250, 250) },
                panelForeground = { AwtColor(20, 20, 20) }
            )
        )
        assertFalse(
            isLikelyDarkTheme(
                panelBackground = { null },
                panelForeground = { AwtColor(0, 0, 0) }
            )
        )
    }

    @Test
    fun `is dark from pair or null resolves luminance ordering when both colors present`() {
        assertTrue(isDarkFromPairOrNull(rgb(0, 0, 0), rgb(255, 255, 255)) == true)
        assertTrue(isDarkFromPairOrNull(rgb(255, 255, 255), rgb(0, 0, 0)) == false)
    }

    @Test
    fun `resolve desktop dark mode prefers plasma when present otherwise uses fallback`() {
        assertTrue(resolveDesktopDarkMode(plasmaDarkMode = true, fallbackDarkMode = false))
        assertFalse(resolveDesktopDarkMode(plasmaDarkMode = false, fallbackDarkMode = true))
        assertTrue(resolveDesktopDarkMode(plasmaDarkMode = null, fallbackDarkMode = true))
        assertFalse(resolveDesktopDarkMode(plasmaDarkMode = null, fallbackDarkMode = false))
    }

    @Test
    fun `default dependencies propagate is likely dark theme through desktop dark fallback seam`() {
        val lightKde = parseKdeGlobals(
            """
            [Colors:Window]
            BackgroundNormal=255,255,255
            ForegroundNormal=30,30,30
            """.trimIndent()
        )
        val schemeLight = buildDesktopSystemColorScheme(
            defaultDesktopSystemColorSchemeDependencies(
                kdeGlobalsProvider = { lightKde },
                swingUiColor = { null },
                desktopDarkFallback = { false }
            )
        )
        val explicitLight = buildDesktopSystemColorScheme(
            kdeGlobals = lightKde,
            uiColor = { null },
            isDarkFallback = false
        )
        assertColorEquals(explicitLight.background, schemeLight.background)
        assertColorEquals(explicitLight.primary, schemeLight.primary)

        val darkKdeIncomplete = parseKdeGlobals("")
        val schemeFromFallback = buildDesktopSystemColorScheme(
            defaultDesktopSystemColorSchemeDependencies(
                kdeGlobalsProvider = { darkKdeIncomplete },
                swingUiColor = { null },
                desktopDarkFallback = {
                    isLikelyDarkTheme(
                        panelBackground = { AwtColor(5, 5, 5) },
                        panelForeground = { AwtColor(240, 240, 240) }
                    )
                }
            )
        )
        val darkFallback = darkColorScheme()
        assertColorEquals(darkFallback.background, schemeFromFallback.background)
    }

    @Test
    fun `material desktop scheme factory switches light and dark builders`() {
        val roles = DesktopColorSchemeRoles(
            primary = rgb(10, 10, 10),
            onPrimary = rgb(250, 250, 250),
            secondary = rgb(20, 20, 20),
            onSecondary = rgb(240, 240, 240),
            tertiary = rgb(30, 30, 30),
            onTertiary = rgb(230, 230, 230),
            primaryContainer = rgb(40, 40, 40),
            onPrimaryContainer = rgb(220, 220, 220),
            secondaryContainer = rgb(50, 50, 50),
            onSecondaryContainer = rgb(210, 210, 210),
            tertiaryContainer = rgb(60, 60, 60),
            onTertiaryContainer = rgb(200, 200, 200),
            background = rgb(70, 70, 70),
            onBackground = rgb(190, 190, 190),
            surface = rgb(80, 80, 80),
            onSurface = rgb(180, 180, 180),
            error = rgb(90, 90, 90),
            onError = rgb(170, 170, 170),
            errorContainer = rgb(100, 100, 100),
            onErrorContainer = rgb(160, 160, 160),
            surfaceVariant = rgb(110, 110, 110),
            onSurfaceVariant = rgb(150, 150, 150),
            outline = rgb(120, 120, 120),
            outlineVariant = rgb(130, 130, 130),
            surfaceTint = rgb(140, 140, 140)
        )
        val lightScheme = desktopMaterialSchemeFactory(false)(roles)
        val darkScheme = desktopMaterialSchemeFactory(true)(roles)
        assertColorEquals(roles.primary, lightScheme.primary)
        assertColorEquals(roles.primary, darkScheme.primary)
        assertColorEquals(roles.errorContainer, lightScheme.errorContainer)
        assertColorEquals(roles.errorContainer, darkScheme.errorContainer)
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







