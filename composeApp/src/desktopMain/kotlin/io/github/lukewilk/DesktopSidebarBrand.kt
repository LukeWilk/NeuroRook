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
import androidx.compose.ui.unit.dp
import java.io.ByteArrayInputStream

/**
 * Renders the desktop sidebar brand using the bundled NeuroRook logo with a text fallback when the asset is unavailable.
 */
@Composable
fun DesktopSidebarBrand(modifier: Modifier = Modifier) {
    DesktopSidebarBrandContent(
        logoPainter = rememberDesktopSidebarLogo(),
        modifier = modifier
    )
}

/** Renders either the loaded desktop logo or the text fallback so tests can exercise both sidebar branding branches. */
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
            text = "NeuroRook",
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
    val logoBytes = remember {
        Thread.currentThread().contextClassLoader
            ?.getResourceAsStream("neuroRook.svg")
            ?.use { it.readBytes() }
    }

    return logoBytes?.let { bytes ->
        runCatching { loadSvgPainter(ByteArrayInputStream(bytes), density) }.getOrNull()
    }
}



