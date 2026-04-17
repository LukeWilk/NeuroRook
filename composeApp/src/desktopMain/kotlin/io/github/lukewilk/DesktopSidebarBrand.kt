package io.github.lukewilk

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.ByteArrayInputStream

/**
 * Renders the desktop sidebar brand using the bundled NeuroRook logo with a text fallback when the asset is unavailable.
 */
internal const val DESKTOP_SIDEBAR_LOGO_RESOURCE = "neuroRook.svg"
private const val DESKTOP_SIDEBAR_FALLBACK_LABEL = "NeuroRook"

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

@Composable
internal fun DesktopSidebarBrandContent(logoPainter: Painter?, modifier: Modifier = Modifier) {
    if (logoPainter != null) {
        Image(
            painter = logoPainter,
            contentDescription = "NeuroRook logo",
            modifier = modifier
                .fillMaxWidth()
                .height(88.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    } else {
        Text(
            text = DESKTOP_SIDEBAR_FALLBACK_LABEL,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
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

internal fun readDesktopSidebarLogoBytes(classLoader: ClassLoader?): ByteArray? =
    readDesktopSidebarResourceBytes(classLoader, DESKTOP_SIDEBAR_LOGO_RESOURCE)

internal fun loadDesktopSidebarLogoPainter(bytes: ByteArray?, density: Density): Painter? =
    bytes?.let { payload ->
        runCatching { loadSvgPainter(ByteArrayInputStream(payload), density) }.getOrNull()
    }
