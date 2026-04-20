package io.github.lukewilk

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.ApplicationScope
import io.github.lukewilk.hardware.api.HardwareBackendApi
import io.github.lukewilk.theming.rememberDesktopSystemColorScheme
import javax.swing.UIManager

fun main() {
    runDesktopMain()
}

/** Applies the desktop look and feel first, then launches the Compose desktop shell. */
internal fun runDesktopMain(
    configureLookAndFeel: () -> Unit = { configureSystemLookAndFeel() },
    launchApp: () -> Unit = ::launchDesktopApp
) {
    configureLookAndFeel()
    launchApp()
}

/** Memoizes the backend API for the lifetime of the current desktop composition tree. */
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
    appIconProvider: @Composable (String) -> Painter = { resourcePath -> rememberDesktopAppIconPainter(resourcePath) },
    applicationHost: DesktopApplicationHost = ::defaultDesktopApplicationHost,
    windowHost: DesktopWindowHost = ApplicationScope::defaultDesktopWindowHost
) {
    applicationHost {
        // Resolve theme and icon inside the application composition so both can react to desktop configuration changes.
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

/** Attempts to apply the active system Swing look and feel before the Compose desktop window is shown. */
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

/** Result of trying to apply the system look and feel during desktop bootstrap. */
internal enum class LookAndFeelApplyResult {
    SKIPPED_ALREADY_ACTIVE,
    APPLIED,
    FAILED
}


