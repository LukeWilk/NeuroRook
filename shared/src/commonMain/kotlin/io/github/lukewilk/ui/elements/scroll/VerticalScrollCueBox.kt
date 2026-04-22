package io.github.lukewilk.ui.elements.scroll

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal data class VerticalScrollCueVisibility(
    val showTopCue: Boolean,
    val showBottomCue: Boolean
)

internal fun verticalScrollCueVisibility(scrollValue: Int, maxScrollValue: Int): VerticalScrollCueVisibility =
    VerticalScrollCueVisibility(
        showTopCue = maxScrollValue > 0 && scrollValue > 0,
        showBottomCue = maxScrollValue > 0 && scrollValue < maxScrollValue
    )

internal fun verticalScrollCueContentDescription(isTopCue: Boolean): String =
    if (isTopCue) "More content above" else "More content below"

/**
 * Wraps a vertically scrollable region with subtle top/bottom edge cues whenever overflow exists.
 */
@Composable
internal fun VerticalScrollCueBox(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    clipContentToBounds: Boolean = true,
    content: @Composable () -> Unit
) {
    val cueVisibility = verticalScrollCueVisibility(
        scrollValue = scrollState.value,
        maxScrollValue = scrollState.maxValue
    )

    Box(
        modifier = if (clipContentToBounds) modifier.clipToBounds() else modifier
    ) {
        content()

        if (cueVisibility.showTopCue) {
            ScrollCueOverlay(
                modifier = Modifier.align(Alignment.TopCenter),
                isTopCue = true
            )
        }

        if (cueVisibility.showBottomCue) {
            ScrollCueOverlay(
                modifier = Modifier.align(Alignment.BottomCenter),
                isTopCue = false
            )
        }
    }
}

@Composable
private fun ScrollCueOverlay(
    modifier: Modifier = Modifier,
    isTopCue: Boolean
) {
    val overlayColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    val iconTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f)
    val gradient = if (isTopCue) {
        Brush.verticalGradient(colors = listOf(overlayColor, Color.Transparent))
    } else {
        Brush.verticalGradient(colors = listOf(Color.Transparent, overlayColor))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(gradient)
            .semantics { contentDescription = verticalScrollCueContentDescription(isTopCue) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (isTopCue) Arrangement.Top else Arrangement.Bottom
    ) {
        Icon(
            imageVector = if (isTopCue) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.padding(vertical = 2.dp)
        )
    }
}
