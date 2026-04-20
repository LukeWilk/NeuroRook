package io.github.lukewilk.theming

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import java.io.File
import java.awt.Color as AwtColor
import javax.swing.UIManager
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.pow

internal const val DesktopSystemThemePollingIntervalMillis = 2000L

@Composable
fun rememberDesktopSystemColorScheme(
    schemeBuilder: () -> ColorScheme = ::buildDesktopSystemColorScheme
): ColorScheme = rememberDesktopSystemColorScheme(
    schemeBuilder = schemeBuilder,
    refreshKeyProvider = ::defaultDesktopSystemThemeSignature
)

@Composable
internal fun rememberDesktopSystemColorScheme(
    schemeBuilder: () -> ColorScheme,
    refreshKeyProvider: () -> DesktopSystemThemeSignature,
    refreshIntervalMillis: Long = DesktopSystemThemePollingIntervalMillis
): ColorScheme {
    val latestRefreshKeyProvider by rememberUpdatedState(refreshKeyProvider)
    val pollIntervalMillis = refreshIntervalMillis.coerceAtLeast(1L)
    val refreshKey by produceState(
        initialValue = latestRefreshKeyProvider(),
        key1 = pollIntervalMillis
    ) {
        while (currentCoroutineContext().isActive) {
            delay(pollIntervalMillis)
            val next = latestRefreshKeyProvider()
            if (next != value) {
                value = next
            }
        }
    }

    return remember(refreshKey, schemeBuilder) {
        schemeBuilder()
    }
}

internal data class DesktopSystemColorSchemeDependencies(
    val kdeGlobals: KdeGlobals,
    val uiColor: (String) -> Color?,
    val isDarkFallback: Boolean
)

internal fun defaultDesktopSystemColorSchemeDependencies(
    kdeGlobalsProvider: () -> KdeGlobals = { readKdeGlobals() },
    swingUiColor: (String) -> Color? = ::desktopUiColor,
    desktopDarkFallback: () -> Boolean = { isLikelyDarkTheme() }
): DesktopSystemColorSchemeDependencies =
    DesktopSystemColorSchemeDependencies(
        kdeGlobals = kdeGlobalsProvider(),
        uiColor = swingUiColor,
        isDarkFallback = desktopDarkFallback()
    )

internal data class DesktopSystemThemeSignature(
    val kdeGlobals: KdeGlobals,
    val uiColors: Map<String, Color?>,
    val isDarkFallback: Boolean,
    val activeLookAndFeelClassName: String?
)

internal fun defaultDesktopSystemThemeSignature(
    kdeGlobalsProvider: () -> KdeGlobals = { readKdeGlobals() },
    swingUiColor: (String) -> Color? = ::desktopUiColor,
    desktopDarkFallback: () -> Boolean = { isLikelyDarkTheme() },
    activeLookAndFeelClassName: () -> String? = { UIManager.getLookAndFeel()?.javaClass?.name }
): DesktopSystemThemeSignature = DesktopSystemThemeSignature(
    kdeGlobals = kdeGlobalsProvider(),
    uiColors = trackedDesktopUiColorKeys().associateWith(swingUiColor),
    isDarkFallback = desktopDarkFallback(),
    activeLookAndFeelClassName = activeLookAndFeelClassName()
)

internal fun trackedDesktopUiColorKeys(): List<String> = listOf(
    "Panel.background",
    "Panel.foreground",
    "TextField.background",
    "TextField.foreground",
    "Button.background",
    "Button.foreground"
)
    .plus(primaryUiColorKeys())
    .plus(onPrimaryUiColorKeys())
    .plus(secondaryUiColorKeys())
    .plus(tertiaryUiColorKeys())
    .distinct()

internal fun buildDesktopSystemColorScheme(
    dependencies: DesktopSystemColorSchemeDependencies = defaultDesktopSystemColorSchemeDependencies()
): ColorScheme = buildDesktopSystemColorScheme(
    kdeGlobals = dependencies.kdeGlobals,
    uiColor = dependencies.uiColor,
    isDarkFallback = dependencies.isDarkFallback
)

internal fun buildDesktopSystemColorScheme(
    kdeGlobals: KdeGlobals,
    uiColor: (String) -> Color?,
    isDarkFallback: Boolean
): ColorScheme {
    val isDark = resolveDesktopDarkMode(plasmaDarkThemeFromKde(kdeGlobals), isDarkFallback)
    val fallback = fallbackDesktopColorScheme(isDark)

    val base = resolveBaseDesktopColors(kdeGlobals, uiColor, fallback.background, fallback.onBackground, fallback.surfaceVariant)
    val accents = resolveDesktopAccentRoles(
        kdeGlobals = kdeGlobals,
        uiColor = uiColor,
        surface = base.surface,
        surfaceVariant = base.surfaceVariant,
        onSurface = base.onSurface,
        onBackground = base.onBackground,
        fallbackPrimary = fallback.primary,
        fallbackError = fallback.error,
        isDark = isDark
    )
    val containers = resolveDesktopContainerRoles(
        isDark = isDark,
        background = base.background,
        surface = base.surface,
        surfaceVariant = base.surfaceVariant,
        onSurface = base.onSurface,
        onBackground = base.onBackground,
        onSurfaceVariant = base.onSurfaceVariant,
        primary = accents.primary,
        secondary = accents.secondary,
        tertiary = accents.tertiary,
        error = accents.error,
        onError = accents.onError
    )

    return desktopColorScheme(
        roles = DesktopColorSchemeRoles(
            primary = accents.primary,
            onPrimary = accents.onPrimary,
            secondary = accents.secondary,
            onSecondary = accents.onSecondary,
            tertiary = accents.tertiary,
            onTertiary = accents.onTertiary,
            primaryContainer = containers.primaryContainer,
            onPrimaryContainer = containers.onPrimaryContainer,
            secondaryContainer = containers.secondaryContainer,
            onSecondaryContainer = containers.onSecondaryContainer,
            tertiaryContainer = containers.tertiaryContainer,
            onTertiaryContainer = containers.onTertiaryContainer,
            background = base.background,
            onBackground = base.onBackground,
            surface = base.surface,
            onSurface = base.onSurface,
            error = accents.error,
            onError = accents.onError,
            errorContainer = containers.errorContainer,
            onErrorContainer = containers.onErrorContainer,
            surfaceVariant = base.surfaceVariant,
            onSurfaceVariant = base.onSurfaceVariant,
            outline = containers.outline,
            outlineVariant = containers.outlineVariant,
            surfaceTint = accents.primary
        ),
        isDark = isDark
    )
}

internal fun desktopUiColor(key: String): Color? =
    UIManager.getColor(key)?.let(::awtColorToComposeColor)

internal fun resolveDesktopDarkMode(plasmaDarkMode: Boolean?, fallbackDarkMode: Boolean): Boolean =
    plasmaDarkMode ?: fallbackDarkMode

internal fun fallbackDesktopColorScheme(isDark: Boolean): ColorScheme =
    if (isDark) darkColorScheme() else lightColorScheme()

internal data class DesktopBaseColors(
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color
)

internal data class DesktopAccentRoles(
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val error: Color,
    val onError: Color
)

internal data class DesktopContainerRoles(
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val outline: Color,
    val outlineVariant: Color
)

internal enum class DesktopContainerTintRole {
    PRIMARY,
    SECONDARY,
    TERTIARY,
    ERROR,
    OUTLINE,
    OUTLINE_VARIANT
}

internal fun resolveDesktopContainerTintAmount(role: DesktopContainerTintRole, isDark: Boolean): Float =
    when (role) {
        DesktopContainerTintRole.PRIMARY -> if (isDark) 0.28f else 0.14f
        DesktopContainerTintRole.SECONDARY -> if (isDark) 0.22f else 0.10f
        DesktopContainerTintRole.TERTIARY -> if (isDark) 0.18f else 0.10f
        DesktopContainerTintRole.ERROR -> if (isDark) 0.16f else 0.10f
        DesktopContainerTintRole.OUTLINE -> if (isDark) 0.45f else 0.32f
        DesktopContainerTintRole.OUTLINE_VARIANT -> if (isDark) 0.28f else 0.18f
    }

internal fun resolveBaseDesktopColors(
    kdeGlobals: KdeGlobals,
    uiColor: (String) -> Color?,
    fallbackBackground: Color,
    fallbackOnBackground: Color,
    fallbackSurfaceVariant: Color
): DesktopBaseColors {
    val background = kdeGlobals.color("Colors:Window", "BackgroundNormal") ?: uiColor("Panel.background") ?: fallbackBackground
    val onBackground = kdeGlobals.color("Colors:Window", "ForegroundNormal") ?: uiColor("Panel.foreground") ?: fallbackOnBackground
    val surface = kdeGlobals.color("Colors:View", "BackgroundNormal") ?: uiColor("TextField.background") ?: background
    val onSurface = kdeGlobals.color("Colors:View", "ForegroundNormal") ?: uiColor("TextField.foreground") ?: onBackground
    val surfaceVariant = kdeGlobals.color("Colors:View", "BackgroundAlternate")
        ?: kdeGlobals.color("Colors:Button", "BackgroundNormal")
        ?: uiColor("Button.background")
        ?: fallbackSurfaceVariant
    val onSurfaceVariant = kdeGlobals.color("Colors:Button", "ForegroundNormal")
        ?: kdeGlobals.color("Colors:Window", "ForegroundInactive")
        ?: uiColor("Button.foreground")
        ?: bestContrastOn(surfaceVariant, onSurface, onBackground)
    return DesktopBaseColors(background, onBackground, surface, onSurface, surfaceVariant, onSurfaceVariant)
}

internal fun resolvePrimary(
    kdeGlobals: KdeGlobals,
    uiColor: (String) -> Color?,
    fallbackPrimary: Color
): Color =
    kdeGlobals.color("Colors:Selection", "BackgroundNormal")
        ?: kdeGlobals.color("Colors:Window", "DecorationFocus")
        ?: kdeGlobals.color("Colors:Button", "DecorationFocus")
        ?: kdeGlobals.color("General", "LastUsedCustomAccentColor")
        ?: firstAvailableUiColor(uiColor, primaryUiColorKeys())
        ?: fallbackPrimary

internal fun resolveDesktopAccentRoles(
    kdeGlobals: KdeGlobals,
    uiColor: (String) -> Color?,
    surface: Color,
    surfaceVariant: Color,
    onSurface: Color,
    onBackground: Color,
    fallbackPrimary: Color,
    fallbackError: Color,
    isDark: Boolean
): DesktopAccentRoles {
    val primary = resolvePrimary(kdeGlobals, uiColor, fallbackPrimary)
    val preferredOnPrimary = resolvePreferredOnPrimary(kdeGlobals, uiColor, onSurface)
    val onPrimary = bestContrastOn(primary, preferredOnPrimary, onSurface, onBackground)
    val secondary = resolveSecondary(kdeGlobals, uiColor, surfaceVariant, primary, isDark)
    val onSecondary = bestContrastOn(secondary, onSurface, onBackground, onPrimary)
    val tertiary = resolveTertiary(kdeGlobals, uiColor, surface, primary, isDark)
    val onTertiary = bestContrastOn(tertiary, onSurface, onBackground, onPrimary)
    val error = resolveError(kdeGlobals, fallbackError)
    val onError = bestContrastOn(error, Color.White, Color.Black, onSurface)
    return DesktopAccentRoles(primary, onPrimary, secondary, onSecondary, tertiary, onTertiary, error, onError)
}

internal fun resolveDesktopContainerRoles(
    isDark: Boolean,
    background: Color,
    surface: Color,
    surfaceVariant: Color,
    onSurface: Color,
    onBackground: Color,
    onSurfaceVariant: Color,
    primary: Color,
    secondary: Color,
    tertiary: Color,
    error: Color,
    onError: Color
): DesktopContainerRoles {
    val primaryContainer = tintDesktopColors(
        background,
        primary,
        resolveDesktopContainerTintAmount(DesktopContainerTintRole.PRIMARY, isDark)
    )
    val secondaryContainer = tintDesktopColors(
        surfaceVariant,
        secondary,
        resolveDesktopContainerTintAmount(DesktopContainerTintRole.SECONDARY, isDark)
    )
    val tertiaryContainer = tintDesktopColors(
        surface,
        tertiary,
        resolveDesktopContainerTintAmount(DesktopContainerTintRole.TERTIARY, isDark)
    )
    val errorContainer = tintedRole(
        surface,
        error,
        resolveDesktopContainerTintAmount(DesktopContainerTintRole.ERROR, isDark)
    )
    val outline = tintedRole(
        surface,
        onSurfaceVariant,
        resolveDesktopContainerTintAmount(DesktopContainerTintRole.OUTLINE, isDark)
    )
    val outlineVariant = tintedRole(
        surface,
        onSurfaceVariant,
        resolveDesktopContainerTintAmount(DesktopContainerTintRole.OUTLINE_VARIANT, isDark)
    )
    val onPrimaryContainer = bestContrastOn(primaryContainer, onSurfaceVariant, onSurface)
    val onSecondaryContainer = bestContrastOn(secondaryContainer, onSurfaceVariant, onSurface)
    val onTertiaryContainer = bestContrastOn(tertiaryContainer, onSurfaceVariant, onSurface)
    val onErrorContainer = bestContrastOn(errorContainer, onSurface, onBackground, onError)
    return DesktopContainerRoles(
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        outline = outline,
        outlineVariant = outlineVariant
    )
}

internal data class DesktopColorSchemeRoles(
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val surfaceTint: Color
)

internal fun desktopMaterialSchemeFactory(isDark: Boolean): (DesktopColorSchemeRoles) -> ColorScheme =
    if (isDark) ::darkDesktopColorScheme else ::lightDesktopColorScheme

internal fun desktopColorScheme(roles: DesktopColorSchemeRoles, isDark: Boolean): ColorScheme =
    desktopMaterialSchemeFactory(isDark)(roles)

internal fun darkDesktopColorScheme(roles: DesktopColorSchemeRoles): ColorScheme = darkColorScheme(
    primary = roles.primary,
    onPrimary = roles.onPrimary,
    secondary = roles.secondary,
    onSecondary = roles.onSecondary,
    tertiary = roles.tertiary,
    onTertiary = roles.onTertiary,
    primaryContainer = roles.primaryContainer,
    onPrimaryContainer = roles.onPrimaryContainer,
    secondaryContainer = roles.secondaryContainer,
    onSecondaryContainer = roles.onSecondaryContainer,
    tertiaryContainer = roles.tertiaryContainer,
    onTertiaryContainer = roles.onTertiaryContainer,
    background = roles.background,
    onBackground = roles.onBackground,
    surface = roles.surface,
    onSurface = roles.onSurface,
    error = roles.error,
    onError = roles.onError,
    errorContainer = roles.errorContainer,
    onErrorContainer = roles.onErrorContainer,
    surfaceVariant = roles.surfaceVariant,
    onSurfaceVariant = roles.onSurfaceVariant,
    outline = roles.outline,
    outlineVariant = roles.outlineVariant,
    surfaceTint = roles.surfaceTint
)

internal fun lightDesktopColorScheme(roles: DesktopColorSchemeRoles): ColorScheme = lightColorScheme(
    primary = roles.primary,
    onPrimary = roles.onPrimary,
    secondary = roles.secondary,
    onSecondary = roles.onSecondary,
    tertiary = roles.tertiary,
    onTertiary = roles.onTertiary,
    primaryContainer = roles.primaryContainer,
    onPrimaryContainer = roles.onPrimaryContainer,
    secondaryContainer = roles.secondaryContainer,
    onSecondaryContainer = roles.onSecondaryContainer,
    tertiaryContainer = roles.tertiaryContainer,
    onTertiaryContainer = roles.onTertiaryContainer,
    background = roles.background,
    onBackground = roles.onBackground,
    surface = roles.surface,
    onSurface = roles.onSurface,
    error = roles.error,
    onError = roles.onError,
    errorContainer = roles.errorContainer,
    onErrorContainer = roles.onErrorContainer,
    surfaceVariant = roles.surfaceVariant,
    onSurfaceVariant = roles.onSurfaceVariant,
    outline = roles.outline,
    outlineVariant = roles.outlineVariant,
    surfaceTint = roles.surfaceTint
)

internal fun resolvePreferredOnPrimary(kdeGlobals: KdeGlobals, uiColor: (String) -> Color?, onSurface: Color): Color {
    val selectionForeground = kdeGlobals.color("Colors:Selection", "ForegroundNormal")
    if (selectionForeground != null) return selectionForeground

    val uiForeground = firstAvailableUiColor(uiColor, onPrimaryUiColorKeys())

    return uiForeground ?: onSurface
}

internal fun resolveSecondary(kdeGlobals: KdeGlobals, uiColor: (String) -> Color?, surfaceVariant: Color, primary: Color, isDark: Boolean): Color {
    val buttonHover = kdeGlobals.color("Colors:Button", "DecorationHover")
    if (buttonHover != null) return buttonHover

    val windowHover = kdeGlobals.color("Colors:Window", "DecorationHover")
    if (windowHover != null) return windowHover

    val selectionHover = kdeGlobals.color("Colors:Selection", "DecorationHover")
    if (selectionHover != null) return selectionHover

    val uiSelection = firstAvailableUiColor(uiColor, secondaryUiColorKeys())

    return uiSelection ?: tintDesktopColors(surfaceVariant, primary, if (isDark) 0.36f else 0.28f)
}

internal fun resolveTertiary(kdeGlobals: KdeGlobals, uiColor: (String) -> Color?, surface: Color, primary: Color, isDark: Boolean): Color {
    kdeGlobals.color("Colors:View", "ForegroundLink")?.let { return it }
    kdeGlobals.color("Colors:Window", "ForegroundLink")?.let { return it }
    firstAvailableUiColor(uiColor, tertiaryUiColorKeys())?.let { return it }
    return tintDesktopColors(surface, primary, if (isDark) 0.42f else 0.32f)
}

internal fun resolveError(kdeGlobals: KdeGlobals, fallback: Color): Color {
    return kdeGlobals.color("Colors:View", "ForegroundNegative")
        ?: kdeGlobals.color("Colors:Window", "ForegroundNegative")
        ?: fallback
}

private fun tintedRole(base: Color, accent: Color, amount: Float): Color =
    tintDesktopColors(base, accent, amount)

internal fun firstAvailableUiColor(uiColor: (String) -> Color?, keys: List<String>): Color? =
    keys.firstNotNullOfOrNull(uiColor)

internal fun primaryUiColorKeys(): List<String> = listOf(
    "Component.accentColor",
    "Menu.selectionBackground",
    "List.selectionBackground",
    "TextField.selectionBackground",
    "Button.default.background",
    "Button.background"
)

internal fun onPrimaryUiColorKeys(): List<String> = listOf(
    "TextField.selectionForeground",
    "Button.default.foreground",
    "Menu.selectionForeground",
    "List.selectionForeground",
    "Button.foreground"
)

internal fun secondaryUiColorKeys(): List<String> = listOf(
    "TabbedPane.selected",
    "ComboBox.selectionBackground",
    "Tree.selectionBackground"
)

internal fun tertiaryUiColorKeys(): List<String> = listOf(
    "Button.focus",
    "Focus.color",
    "nimbusFocus"
)

/** Converts Swing `java.awt.Color` values into Compose [Color] using the same mapping as `desktopUiColor`. */
internal fun awtColorToComposeColor(color: AwtColor): Color =
    Color(color.red, color.green, color.blue, color.alpha)

/** Linear blend between two Compose colors; ratio is clamped to `[0, 1]` for stable tint math. */
internal fun blendDesktopColors(first: Color, second: Color, ratio: Float): Color {
    val r = ratio.coerceIn(0f, 1f)
    return Color(
        red = first.red * (1f - r) + second.red * r,
        green = first.green * (1f - r) + second.green * r,
        blue = first.blue * (1f - r) + second.blue * r,
        alpha = first.alpha * (1f - r) + second.alpha * r
    )
}

internal fun tintDesktopColors(base: Color, accent: Color, amount: Float): Color =
    blendDesktopColors(base, accent, amount)

private fun bestContrastOn(background: Color, preferred: Color, fallback: Color): Color {
    return pickBestContrastOnBackground(background, listOf(preferred, fallback))
}

private fun bestContrastOn(background: Color, first: Color, second: Color, third: Color): Color {
    return pickBestContrastOnBackground(background, listOf(first, second, third))
}

/**
 * Picks the candidate (plus forced white/black from [bestContrastCandidates]) with the highest WCAG contrast ratio.
 */
internal fun pickBestContrastOnBackground(background: Color, candidates: List<Color>): Color {
    val resolvedCandidates = bestContrastCandidates(candidates)

    return resolvedCandidates.maxByOrNull { contrastRatio(background, it) } ?: Color.White
}

internal fun bestContrastCandidates(candidates: List<Color>): List<Color> = buildList {
    addAll(candidates)
    add(Color.White)
    add(Color.Black)
}

internal fun isLikelyDarkTheme(
    panelBackground: () -> AwtColor? = { UIManager.getColor("Panel.background") },
    panelForeground: () -> AwtColor? = { UIManager.getColor("Panel.foreground") }
): Boolean = isDarkFromPair(
    background = panelBackground(),
    foreground = panelForeground()
)

/** Plasma window background/foreground pair interpreted as dark mode when luminance ordering matches, or null if incomplete. */
internal fun plasmaDarkThemeFromKde(kdeGlobals: KdeGlobals): Boolean? = isDarkFromPairOrNull(
    background = kdeGlobals.color("Colors:Window", "BackgroundNormal"),
    foreground = kdeGlobals.color("Colors:Window", "ForegroundNormal")
)

internal fun isDarkFromPair(background: AwtColor?, foreground: AwtColor?): Boolean {
    if (background == null || foreground == null) return false
    return relativeLuminanceAwt(background) < relativeLuminanceAwt(foreground)
}

internal fun isDarkFromPairOrNull(background: Color?, foreground: Color?): Boolean? {
    if (background == null || foreground == null) return null
    return relativeLuminanceCompose(background) < relativeLuminanceCompose(foreground)
}

internal fun relativeLuminanceAwt(color: AwtColor): Double =
    relativeLuminanceCompose(awtColorToComposeColor(color))

internal fun relativeLuminanceCompose(color: Color): Double {
    val red = linearizeSrgbChannel(color.red)
    val green = linearizeSrgbChannel(color.green)
    val blue = linearizeSrgbChannel(color.blue)
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue
}

internal fun linearizeSrgbChannel(channel: Float): Double =
    if (channel <= 0.04045f) {
        channel / 12.92
    } else {
        ((channel + 0.055f) / 1.055f).toDouble().pow(2.4)
    }

internal fun contrastRatio(background: Color, foreground: Color): Double {
    val lighter = maxOf(relativeLuminanceCompose(background), relativeLuminanceCompose(foreground))
    val darker = minOf(relativeLuminanceCompose(background), relativeLuminanceCompose(foreground))
    return (lighter + 0.05) / (darker + 0.05)
}

internal data class KdeGlobals(private val sections: Map<String, Map<String, String>>) {
    fun color(section: String, key: String): Color? = parseRgb(sections[section]?.get(key))
}

internal fun readKdeGlobals(
    xdgConfigHome: () -> String? = { System.getenv("XDG_CONFIG_HOME") },
    userHome: () -> String = { System.getProperty("user.home") },
    fileExists: (File) -> Boolean = File::exists,
    readText: (File) -> String = File::readText
): KdeGlobals = readKdeGlobalsFromSources(
    xdgConfigHome = xdgConfigHome,
    userHome = userHome,
    fileExists = fileExists,
    readText = readText
)

internal fun readKdeGlobalsFromSources(
    xdgConfigHome: () -> String?,
    userHome: () -> String,
    fileExists: (File) -> Boolean,
    readText: (File) -> String
): KdeGlobals {
    val configHome = resolveKdeConfigHome(
        xdgConfigHome = xdgConfigHome(),
        userHome = userHome()
    )
    val kdeGlobalsFile = File(configHome, "kdeglobals")
    if (!fileExists(kdeGlobalsFile)) return KdeGlobals(emptyMap())

    return parseKdeGlobalsSafely { readText(kdeGlobalsFile) }
}

internal fun resolveKdeConfigHome(xdgConfigHome: String?, userHome: String): String =
    xdgConfigHome?.takeIf { it.isNotBlank() } ?: "$userHome/.config"

internal fun parseKdeGlobalsSafely(readKdeGlobalsText: () -> String): KdeGlobals =
    runCatching { parseKdeGlobals(readKdeGlobalsText()) }
        .getOrElse { KdeGlobals(emptyMap()) }

internal fun parseKdeGlobals(content: String): KdeGlobals {
    val parsed = mutableMapOf<String, MutableMap<String, String>>()
    var currentSection = ""

    content.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        if (shouldIgnoreKdeGlobalsLine(line)) {
            return@forEach
        }
        extractKdeSectionName(line)?.let { sectionName ->
            currentSection = sectionName
            parsed.putIfAbsent(currentSection, mutableMapOf())
            return@forEach
        }
        val separatorIndex = line.indexOf('=')
        if (separatorIndex <= 0) return@forEach
        val key = line.substring(0, separatorIndex)
        val value = line.substring(separatorIndex + 1)
        parsed.getOrPut(currentSection) { mutableMapOf() }[key] = value
    }

    return KdeGlobals(parsed)
}

internal fun shouldIgnoreKdeGlobalsLine(line: String): Boolean =
    line.isEmpty() || line.startsWith("#") || line.startsWith(";")

internal fun extractKdeSectionName(line: String): String? =
    if (line.startsWith("[") && line.endsWith("]")) {
        line.substring(1, line.length - 1)
    } else {
        null
    }

internal fun parseRgb(value: String?): Color? {
    if (value == null) return null
    val parts = value.split(",")
    if (parts.size < 3) return null
    val red = parts[0].trim().toIntOrNull()?.coerceIn(0, 255) ?: return null
    val green = parts[1].trim().toIntOrNull()?.coerceIn(0, 255) ?: return null
    val blue = parts[2].trim().toIntOrNull()?.coerceIn(0, 255) ?: return null
    val alpha = parts.getOrNull(3)?.trim()?.toIntOrNull()?.coerceIn(0, 255) ?: 255
    return Color(red = red / 255f, green = green / 255f, blue = blue / 255f, alpha = alpha / 255f)
}
