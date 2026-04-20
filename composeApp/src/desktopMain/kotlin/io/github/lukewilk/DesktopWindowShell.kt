package io.github.lukewilk

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

/** Abstraction seam for the desktop `application { ... }` host used by tests and the production launcher. */
internal typealias DesktopApplicationHost = (@Composable ApplicationScope.() -> Unit) -> Unit

/** Abstraction seam for the desktop window host so tests can validate launch wiring without opening a real window. */
internal typealias DesktopWindowHost = @Composable ApplicationScope.(
    windowSpec: DesktopWindowSpec,
    appIcon: Painter,
    content: @Composable () -> Unit
) -> Unit

/** Launches the desktop Compose application using the platform application host. */
internal fun defaultDesktopApplicationHost(content: @Composable ApplicationScope.() -> Unit) {
    application(content = content)
}

/** Creates the main desktop window with the published geometry and the caller-provided icon painter. */
@Composable
internal fun ApplicationScope.defaultDesktopWindowHost(
    windowSpec: DesktopWindowSpec,
    appIcon: Painter,
    content: @Composable () -> Unit
) {
    val windowState = rememberWindowState(width = windowSpec.widthDp.dp, height = windowSpec.heightDp.dp)
    Window(
        onCloseRequest = ::exitApplication,
        title = windowSpec.title,
        state = windowState,
        icon = appIcon
    ) {
        content()
    }
}

/** Immutable shell configuration describing the desktop window title, size, and icon resource path. */
internal data class DesktopWindowSpec(
    val title: String,
    val widthDp: Int,
    val heightDp: Int,
    val iconResourcePath: String
)

/** Returns the default desktop shell geometry and bundled icon used by the production launcher. */
internal fun defaultDesktopWindowSpec(): DesktopWindowSpec = DesktopWindowSpec(
    title = "NeuroRook",
    widthDp = 1440,
    heightDp = 960,
    iconResourcePath = DESKTOP_APP_ICON_RESOURCE
)


