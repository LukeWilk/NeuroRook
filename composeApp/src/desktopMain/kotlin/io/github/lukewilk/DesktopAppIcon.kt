package io.github.lukewilk

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.loadImageBitmap
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.math.min

/** Bundled raster icon used for the desktop window and taskbar entry. */
internal const val DESKTOP_APP_ICON_RESOURCE = "neuroRook.png"

/** Remembers a desktop app icon painter that preserves the source bitmap aspect ratio inside square window icon bounds. */
@Composable
internal fun rememberDesktopAppIconPainter(resourcePath: String): Painter = remember(resourcePath) {
    loadDesktopAppIconPainter(
        bytes = readDesktopAppIconBytes(Thread.currentThread().contextClassLoader, resourcePath)
    ) ?: TransparentDesktopIconPainter
}

/** Reads raw icon bytes from the desktop classpath so tests and callers can share the same resource lookup path. */
internal fun readDesktopAppIconBytes(classLoader: ClassLoader?, resourcePath: String): ByteArray? =
    classLoader?.getResourceAsStream(resourcePath)?.use(InputStream::readBytes)

/** Decodes bundled icon bytes into a fitted bitmap painter, or returns `null` when decoding fails. */
internal fun loadDesktopAppIconPainter(bytes: ByteArray?): Painter? = bytes?.let { payload ->
    runCatching {
        DesktopFittedBitmapPainter(BitmapPainter(loadImageBitmap(ByteArrayInputStream(payload))))
    }.getOrNull()
}

/** Rectangle describing how a source icon should be letterboxed inside the destination draw area. */
internal data class DesktopIconDrawRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

/** Fits an icon into the destination bounds while preserving aspect ratio and centering any remaining padding. */
internal fun fitDesktopIconRect(
    sourceWidth: Float,
    sourceHeight: Float,
    destinationWidth: Float,
    destinationHeight: Float
): DesktopIconDrawRect {
    if (sourceWidth <= 0f || sourceHeight <= 0f || destinationWidth <= 0f || destinationHeight <= 0f) {
        return DesktopIconDrawRect(left = 0f, top = 0f, width = 0f, height = 0f)
    }

    val scale = min(destinationWidth / sourceWidth, destinationHeight / sourceHeight)
    val fittedWidth = sourceWidth * scale
    val fittedHeight = sourceHeight * scale
    return DesktopIconDrawRect(
        left = (destinationWidth - fittedWidth) / 2f,
        top = (destinationHeight - fittedHeight) / 2f,
        width = fittedWidth,
        height = fittedHeight
    )
}

/** Painter wrapper that centers a decoded bitmap icon without stretching non-square assets. */
internal class DesktopFittedBitmapPainter(
    private val delegate: BitmapPainter
) : Painter() {
    override val intrinsicSize: Size = delegate.intrinsicSize

    override fun DrawScope.onDraw() {
        // Reuse the same fit math the tests cover so the titlebar icon remains centered for portrait assets.
        val drawRect = fitDesktopIconRect(
            sourceWidth = intrinsicSize.width,
            sourceHeight = intrinsicSize.height,
            destinationWidth = size.width,
            destinationHeight = size.height
        )
        withTransform({
            translate(left = drawRect.left, top = drawRect.top)
        }) {
            with(delegate) {
                draw(
                    size = Size(drawRect.width, drawRect.height),
                    alpha = 1f
                )
            }
        }
    }
}

/** No-op painter used when the icon resource is unavailable so desktop startup can proceed safely. */
internal object TransparentDesktopIconPainter : Painter() {
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() = Unit
}



