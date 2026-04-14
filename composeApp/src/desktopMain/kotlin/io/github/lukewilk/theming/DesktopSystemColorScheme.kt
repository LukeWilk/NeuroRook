package io.github.lukewilk.theming

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import java.io.File
import java.awt.Color as AwtColor
import javax.swing.UIManager
import kotlin.math.pow

@Composable
fun rememberDesktopSystemColorScheme() = remember {
    buildDesktopSystemColorScheme()
}

internal fun buildDesktopSystemColorScheme(
    kdeGlobals: KdeGlobals = readKdeGlobals(),
    uiColor: (String) -> Color? = { key -> UIManager.getColor(key)?.toComposeColor() },
    isDarkFallback: Boolean = isLikelyDarkTheme()
): ColorScheme {
    val isDark = isPlasmaDarkTheme(kdeGlobals) ?: isDarkFallback
    val fallback = if (isDark) darkColorScheme() else lightColorScheme()

    val background = kdeGlobals.color("Colors:Window", "BackgroundNormal") ?: uiColor("Panel.background") ?: fallback.background
    val onBackground = kdeGlobals.color("Colors:Window", "ForegroundNormal") ?: uiColor("Panel.foreground") ?: fallback.onBackground
    val surface = kdeGlobals.color("Colors:View", "BackgroundNormal") ?: uiColor("TextField.background") ?: background
    val onSurface = kdeGlobals.color("Colors:View", "ForegroundNormal") ?: uiColor("TextField.foreground") ?: onBackground
    val surfaceVariant = kdeGlobals.color("Colors:View", "BackgroundAlternate") ?: kdeGlobals.color("Colors:Button", "BackgroundNormal") ?: uiColor("Button.background") ?: fallback.surfaceVariant
    val onSurfaceVariant = kdeGlobals.color("Colors:Button", "ForegroundNormal") ?: kdeGlobals.color("Colors:Window", "ForegroundInactive") ?: uiColor("Button.foreground") ?: bestContrastOn(surfaceVariant, onSurface, onBackground)
    val primary = kdeGlobals.color("General", "LastUsedCustomAccentColor") ?: kdeGlobals.color("Colors:Selection", "BackgroundNormal") ?: kdeGlobals.color("Colors:Window", "DecorationFocus") ?: kdeGlobals.color("Colors:Button", "DecorationFocus") ?: listOf("Component.accentColor", "Menu.selectionBackground", "List.selectionBackground", "TextField.selectionBackground", "Button.default.background", "Button.background").firstNotNullOfOrNull(uiColor) ?: fallback.primary
    val preferredOnPrimary = resolvePreferredOnPrimary(kdeGlobals, uiColor, onSurface)
    val onPrimary = bestContrastOn(primary, preferredOnPrimary, onSurface, onBackground)
    val secondary = resolveSecondary(kdeGlobals, uiColor, surfaceVariant, primary, isDark)
    val onSecondary = bestContrastOn(secondary, onSurface, onBackground, onPrimary)
    val tertiary = resolveTertiary(kdeGlobals, uiColor, surface, primary, isDark)
    val onTertiary = bestContrastOn(tertiary, onSurface, onBackground, onPrimary)
    val error = resolveError(kdeGlobals, fallback.error)
    val onError = bestContrastOn(error, Color.White, Color.Black, onSurface)
    val primaryContainer = tint(background, primary, if (isDark) 0.28f else 0.14f)
    val secondaryContainer = tint(surfaceVariant, secondary, if (isDark) 0.22f else 0.10f)
    val tertiaryContainer = tint(surface, tertiary, if (isDark) 0.18f else 0.10f)
    val errorContainer = tintedRole(surface, error, if (isDark) 0.16f else 0.10f)
    val outline = tintedRole(surface, onSurfaceVariant, if (isDark) 0.45f else 0.32f)
    val outlineVariant = tintedRole(surface, onSurfaceVariant, if (isDark) 0.28f else 0.18f)
    val onPrimaryContainer = bestContrastOn(primaryContainer, onSurfaceVariant, onSurface)
    val onSecondaryContainer = bestContrastOn(secondaryContainer, onSurfaceVariant, onSurface)
    val onTertiaryContainer = bestContrastOn(tertiaryContainer, onSurfaceVariant, onSurface)
    val onErrorContainer = bestContrastOn(errorContainer, onSurface, onBackground, onError)

    return desktopColorScheme(isDark, primary, onPrimary, secondary, onSecondary, tertiary, onTertiary, primaryContainer, onPrimaryContainer, secondaryContainer, onSecondaryContainer, tertiaryContainer, onTertiaryContainer, background, onBackground, surface, onSurface, error, onError, errorContainer, onErrorContainer, surfaceVariant, onSurfaceVariant, outline, outlineVariant, primary)
}

private fun desktopColorScheme(isDark: Boolean, primary: Color, onPrimary: Color, secondary: Color, onSecondary: Color, tertiary: Color, onTertiary: Color, primaryContainer: Color, onPrimaryContainer: Color, secondaryContainer: Color, onSecondaryContainer: Color, tertiaryContainer: Color, onTertiaryContainer: Color, background: Color, onBackground: Color, surface: Color, onSurface: Color, error: Color, onError: Color, errorContainer: Color, onErrorContainer: Color, surfaceVariant: Color, onSurfaceVariant: Color, outline: Color, outlineVariant: Color, surfaceTint: Color): ColorScheme = if (isDark) darkColorScheme(primary = primary, onPrimary = onPrimary, secondary = secondary, onSecondary = onSecondary, tertiary = tertiary, onTertiary = onTertiary, primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer, secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer, tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer, background = background, onBackground = onBackground, surface = surface, onSurface = onSurface, error = error, onError = onError, errorContainer = errorContainer, onErrorContainer = onErrorContainer, surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant, outline = outline, outlineVariant = outlineVariant, surfaceTint = surfaceTint) else lightColorScheme(primary = primary, onPrimary = onPrimary, secondary = secondary, onSecondary = onSecondary, tertiary = tertiary, onTertiary = onTertiary, primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer, secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer, tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer, background = background, onBackground = onBackground, surface = surface, onSurface = onSurface, error = error, onError = onError, errorContainer = errorContainer, onErrorContainer = onErrorContainer, surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant, outline = outline, outlineVariant = outlineVariant, surfaceTint = surfaceTint)

internal fun resolvePreferredOnPrimary(kdeGlobals: KdeGlobals, uiColor: (String) -> Color?, onSurface: Color): Color {
    val selectionForeground = kdeGlobals.color("Colors:Selection", "ForegroundNormal")
    if (selectionForeground != null) return selectionForeground

    val uiForeground = listOf(
        "TextField.selectionForeground",
        "Button.default.foreground",
        "Menu.selectionForeground",
        "List.selectionForeground",
        "Button.foreground"
    ).firstNotNullOfOrNull(uiColor)

    return uiForeground ?: onSurface
}

internal fun resolveSecondary(kdeGlobals: KdeGlobals, uiColor: (String) -> Color?, surfaceVariant: Color, primary: Color, isDark: Boolean): Color {
    val buttonHover = kdeGlobals.color("Colors:Button", "DecorationHover")
    if (buttonHover != null) return buttonHover

    val windowHover = kdeGlobals.color("Colors:Window", "DecorationHover")
    if (windowHover != null) return windowHover

    val selectionHover = kdeGlobals.color("Colors:Selection", "DecorationHover")
    if (selectionHover != null) return selectionHover

    val uiSelection = listOf(
        "TabbedPane.selected",
        "ComboBox.selectionBackground",
        "Tree.selectionBackground"
    ).firstNotNullOfOrNull(uiColor)

    return uiSelection ?: tint(surfaceVariant, primary, if (isDark) 0.36f else 0.28f)
}

internal fun resolveTertiary(kdeGlobals: KdeGlobals, uiColor: (String) -> Color?, surface: Color, primary: Color, isDark: Boolean): Color {
    kdeGlobals.color("Colors:View", "ForegroundLink")?.let { return it }
    kdeGlobals.color("Colors:Window", "ForegroundLink")?.let { return it }
    listOf("Button.focus", "Focus.color", "nimbusFocus").firstNotNullOfOrNull(uiColor)?.let { return it }
    return tint(surface, primary, if (isDark) 0.42f else 0.32f)
}

internal fun resolveError(kdeGlobals: KdeGlobals, fallback: Color): Color {
    return kdeGlobals.color("Colors:View", "ForegroundNegative")
        ?: kdeGlobals.color("Colors:Window", "ForegroundNegative")
        ?: fallback
}

private fun tintedRole(base: Color, accent: Color, amount: Float): Color = tint(base, accent, amount)

private fun AwtColor.toComposeColor(): Color {
    return Color(red, green, blue, alpha)
}

private fun blend(first: Color, second: Color, ratio: Float): Color {
    val r = ratio.coerceIn(0f, 1f)
    return Color(
        red = first.red * (1f - r) + second.red * r,
        green = first.green * (1f - r) + second.green * r,
        blue = first.blue * (1f - r) + second.blue * r,
        alpha = first.alpha * (1f - r) + second.alpha * r
    )
}

private fun tint(base: Color, accent: Color, amount: Float): Color = blend(base, accent, amount)

private fun bestContrastOn(background: Color, preferred: Color, fallback: Color): Color {
    return bestContrastOn(background, listOf(preferred, fallback))
}

private fun bestContrastOn(background: Color, first: Color, second: Color, third: Color): Color {
    return bestContrastOn(background, listOf(first, second, third))
}

private fun bestContrastOn(background: Color, candidates: List<Color>): Color {
    val resolvedCandidates = buildList {
        addAll(candidates)
        add(Color.White)
        add(Color.Black)
    }

    return resolvedCandidates.maxByOrNull { contrastRatio(background, it) } ?: Color.White
}

private fun isLikelyDarkTheme(): Boolean {
    val background = UIManager.getColor("Panel.background") ?: return false
    val foreground = UIManager.getColor("Panel.foreground") ?: return false
    return relativeLuminance(background) < relativeLuminance(foreground)
}

private fun isPlasmaDarkTheme(kdeGlobals: KdeGlobals): Boolean? {
    val background = kdeGlobals.color("Colors:Window", "BackgroundNormal") ?: return null
    val foreground = kdeGlobals.color("Colors:Window", "ForegroundNormal") ?: return null
    return relativeLuminance(background) < relativeLuminance(foreground)
}

private fun relativeLuminance(color: AwtColor): Double {
    return relativeLuminance(color.toComposeColor())
}

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

private fun contrastRatio(background: Color, foreground: Color): Double {
    val lighter = maxOf(relativeLuminance(background), relativeLuminance(foreground))
    val darker = minOf(relativeLuminance(background), relativeLuminance(foreground))
    return (lighter + 0.05) / (darker + 0.05)
}

internal class KdeGlobals(private val sections: Map<String, Map<String, String>>) {
    fun color(section: String, key: String): Color? = parseRgb(sections[section]?.get(key))
}

private fun readKdeGlobals(): KdeGlobals {
    val configHome = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
        ?: "${System.getProperty("user.home")}/.config"
    val kdeGlobalsFile = File(configHome, "kdeglobals")
    if (!kdeGlobalsFile.exists()) return KdeGlobals(emptyMap())

    return runCatching { parseKdeGlobals(kdeGlobalsFile.readText()) }.getOrElse { KdeGlobals(emptyMap()) }
}

internal fun parseKdeGlobals(content: String): KdeGlobals {
    val parsed = mutableMapOf<String, MutableMap<String, String>>()
    var currentSection = ""

    content.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
            return@forEach
        }
        if (line.startsWith("[") && line.endsWith("]")) {
            currentSection = line.substring(1, line.length - 1)
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
