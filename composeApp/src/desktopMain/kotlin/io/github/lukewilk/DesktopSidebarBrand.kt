package io.github.lukewilk

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.lukewilk.theming.pickBestContrastOnBackground
import io.github.lukewilk.theming.contrastRatio
import java.io.ByteArrayInputStream

/**
 * Renders the desktop sidebar brand using the bundled NeuroRook logo with a text fallback when the asset is unavailable.
 */
internal const val DESKTOP_SIDEBAR_LOGO_RESOURCE = "neuroRook.svg"
private const val DESKTOP_SIDEBAR_FALLBACK_LABEL = "NeuroRook"
private const val DESKTOP_SIDEBAR_MIN_LOGO_CONTRAST = 4.5

/** Entry point for the desktop sidebar brand, allowing tests to inject a custom logo painter. */
@Composable
fun DesktopSidebarBrand(
    modifier: Modifier = Modifier,
    logoPainterProvider: @Composable () -> Painter? = { rememberDesktopSidebarLogo() }
) {
    DesktopSidebarBrandContent(
        logoPainter = logoPainterProvider(),
        modifier = modifier
    )
}

/** Renders either the logo asset or a text fallback, both tinted for readable contrast on the sidebar surface. */
@Composable
internal fun DesktopSidebarBrandContent(logoPainter: Painter?, modifier: Modifier = Modifier) {
    val brandTint = desktopSidebarBrandTintColor(
        containerColor = MaterialTheme.colorScheme.surface,
        preferredContentColor = MaterialTheme.colorScheme.onSurface
    )
    if (logoPainter != null) {
        Image(
            painter = logoPainter,
            contentDescription = "NeuroRook logo",
            colorFilter = ColorFilter.tint(brandTint),
            modifier = modifier
                .fillMaxWidth()
                .height(88.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    } else {
        Text(
            text = DESKTOP_SIDEBAR_FALLBACK_LABEL,
            style = MaterialTheme.typography.titleLarge,
            color = brandTint,
            textAlign = TextAlign.Center,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp)
        )
    }
}

/** Loads the bundled desktop sidebar logo from the Compose resources path. */
@Composable
private fun rememberDesktopSidebarLogo(): Painter? {
    val density = LocalDensity.current
    return remember(density) {
        loadDesktopSidebarLogoPainter(
            bytes = readDesktopSidebarLogoBytes(Thread.currentThread().contextClassLoader),
            density = density
        )
    }
}

internal fun readDesktopSidebarResourceBytes(
    classLoader: ClassLoader?,
    resourcePath: String
): ByteArray? =
    classLoader
        ?.getResourceAsStream(resourcePath)
        ?.use { it.readBytes() }

/** Reads the bundled desktop sidebar logo bytes from the classpath. */
internal fun readDesktopSidebarLogoBytes(classLoader: ClassLoader?): ByteArray? =
    readDesktopSidebarResourceBytes(classLoader, DESKTOP_SIDEBAR_LOGO_RESOURCE)

/** Attempts to decode the bundled sidebar SVG into a painter, or returns `null` when decoding fails. */
internal fun loadDesktopSidebarLogoPainter(bytes: ByteArray?, density: Density): Painter? =
    bytes?.let { payload ->
        runCatching { loadSvgPainter(ByteArrayInputStream(payload), density) }.getOrNull()
    }

/** Picks a readable brand tint for the sidebar logo or fallback label against the active sidebar surface color. */
internal fun desktopSidebarBrandTintColor(
    containerColor: Color,
    preferredContentColor: Color,
    minimumContrast: Double = DESKTOP_SIDEBAR_MIN_LOGO_CONTRAST
): Color = if (contrastRatio(containerColor, preferredContentColor) >= minimumContrast) {
    preferredContentColor
} else {
    pickBestContrastOnBackground(containerColor, listOf(preferredContentColor))
}

