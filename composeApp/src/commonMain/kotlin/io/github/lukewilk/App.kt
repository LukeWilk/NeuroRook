package io.github.lukewilk

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import io.github.lukewilk.shared.api.BackendApi
import io.github.lukewilk.ui.HardwareScreen
import io.github.lukewilk.ui.MainScaffold

internal typealias AppScaffoldRenderer = @Composable (
    hardwareScreen: @Composable () -> Unit,
    headerContent: (@Composable () -> Unit)?
) -> Unit

/**
 * Root NeuroRook application composable shared by desktop and Android hosts.
 */
@Composable
fun App(
    backendApi: BackendApi? = null,
    colorScheme: ColorScheme? = null,
    headerContent: (@Composable () -> Unit)? = null,
    renderScaffold: AppScaffoldRenderer? = null,
    hardwareScreenProvider: (BackendApi?) -> (@Composable () -> Unit) = ::appHardwareScreen
) {
    val resolvedColorScheme = colorScheme ?: MaterialTheme.colorScheme
    val hardwareScreen = hardwareScreenProvider(backendApi)
    val scaffoldRenderer = renderScaffold ?: ::defaultAppScaffoldRenderer
    MaterialTheme(colorScheme = resolvedColorScheme) {
        scaffoldRenderer(hardwareScreen, headerContent)
    }
}

internal fun appHardwareScreen(backendApi: BackendApi?): @Composable () -> Unit = {
    HardwareScreen(backendApi = backendApi)
}

@Composable
internal fun defaultAppScaffoldRenderer(
    hardwareScreen: @Composable () -> Unit,
    headerContent: (@Composable () -> Unit)?
) {
    MainScaffold(
        hardwareScreen = hardwareScreen,
        headerContent = headerContent
    )
}

