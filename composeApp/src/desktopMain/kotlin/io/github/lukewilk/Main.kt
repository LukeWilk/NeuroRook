package io.github.lukewilk

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.lukewilk.hardware.api.HardwareBackendApi
import io.github.lukewilk.theming.rememberDesktopSystemColorScheme
import javax.swing.UIManager

fun main() {
    runDesktopMain()
}

internal typealias DesktopApplicationHost = (@Composable ApplicationScope.() -> Unit) -> Unit
internal typealias DesktopWindowHost = @Composable ApplicationScope.(
    windowSpec: DesktopWindowSpec,
    appIcon: Painter,
    content: @Composable () -> Unit
) -> Unit

internal fun runDesktopMain(
    configureLookAndFeel: () -> Unit = { configureSystemLookAndFeel() },
    launchApp: () -> Unit = ::launchDesktopApp
) {
    configureLookAndFeel()
    launchApp()
}

internal fun defaultDesktopApplicationHost(content: @Composable ApplicationScope.() -> Unit) {
    application(content = content)
}

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

@Composable
internal fun rememberDesktopBackendApi(backendFactory: () -> HardwareBackendApi): HardwareBackendApi =
    remember(backendFactory) { backendFactory() }

/**
 * Desktop window body: memoizes [HardwareBackendApi] then hosts the shared [App] inside the window shell.
 */
@Composable
internal fun DesktopAppWithinWindow(
    backendFactory: () -> HardwareBackendApi,
    colorScheme: ColorScheme,
    headerContent: (@Composable () -> Unit)? = null
) {
    val backendApi = rememberDesktopBackendApi(backendFactory)
    App(
        backendApi = backendApi,
        colorScheme = colorScheme,
        headerContent = headerContent
    )
}

internal fun launchDesktopApp(
    windowSpec: DesktopWindowSpec = defaultDesktopWindowSpec(),
    backendFactory: () -> HardwareBackendApi = ::HardwareBackendApi,
    colorSchemeProvider: @Composable () -> ColorScheme = { rememberDesktopSystemColorScheme() },
    appIconProvider: @Composable (String) -> Painter = { resourcePath -> painterResource(resourcePath) },
    applicationHost: DesktopApplicationHost = ::defaultDesktopApplicationHost,
    windowHost: DesktopWindowHost = ApplicationScope::defaultDesktopWindowHost
) {
    applicationHost {
        val colorScheme = colorSchemeProvider()
        val appIcon = appIconProvider(windowSpec.iconResourcePath)
        windowHost(this, windowSpec, appIcon) {
            DesktopAppWithinWindow(
                backendFactory = backendFactory,
                colorScheme = colorScheme,
                headerContent = { DesktopSidebarBrand() }
            )
        }
    }
}

internal fun configureSystemLookAndFeel(
    systemClassName: () -> String = UIManager::getSystemLookAndFeelClassName,
    activeClassName: () -> String? = { UIManager.getLookAndFeel()?.javaClass?.name },
    applyLookAndFeel: (String) -> Unit = { className -> UIManager.setLookAndFeel(className) }
): LookAndFeelApplyResult {
    val systemLookAndFeel = systemClassName()
    if (systemLookAndFeel == activeClassName()) {
        return LookAndFeelApplyResult.SKIPPED_ALREADY_ACTIVE
    }
    return if (runCatching { applyLookAndFeel(systemLookAndFeel) }.isSuccess) {
        LookAndFeelApplyResult.APPLIED
    } else {
        LookAndFeelApplyResult.FAILED
    }
}

internal enum class LookAndFeelApplyResult {
    SKIPPED_ALREADY_ACTIVE,
    APPLIED,
    FAILED
}

internal data class DesktopWindowSpec(
    val title: String,
    val widthDp: Int,
    val heightDp: Int,
    val iconResourcePath: String
)

internal fun defaultDesktopWindowSpec(): DesktopWindowSpec = DesktopWindowSpec(
    title = "NeuroRook",
    widthDp = 1440,
    heightDp = 960,
    iconResourcePath = "neuroRook.png"
)

