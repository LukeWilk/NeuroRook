package io.github.lukewilk

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import io.github.lukewilk.shared.api.BackendApi
import io.github.lukewilk.ui.HardwareScreen
import io.github.lukewilk.ui.MainScaffold
import io.github.lukewilk.ui.graphs.GraphsScreen

internal typealias AppScaffoldRenderer = @Composable (
    hardwareScreen: @Composable () -> Unit,
    graphsScreen: @Composable () -> Unit,
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
    hardwareScreenProvider: (BackendApi?) -> (@Composable () -> Unit) = ::appHardwareScreen,
    graphsScreenProvider: (BackendApi?) -> (@Composable () -> Unit) = ::appGraphsScreen
) {
    val resolvedColorScheme = colorScheme ?: MaterialTheme.colorScheme
    val hardwareScreen = hardwareScreenProvider(backendApi)
    val graphsScreen = graphsScreenProvider(backendApi)
    val scaffoldRenderer = renderScaffold ?: ::defaultAppScaffoldRenderer
    MaterialTheme(colorScheme = resolvedColorScheme) {
        scaffoldRenderer(hardwareScreen, graphsScreen, headerContent)
    }
}

internal fun appHardwareScreen(backendApi: BackendApi?): @Composable () -> Unit = {
    HardwareScreen(backendApi = backendApi)
}

internal fun appGraphsScreen(backendApi: BackendApi?): @Composable () -> Unit = {
    GraphsScreen(backendApi = backendApi)
}

@Composable
internal fun defaultAppScaffoldRenderer(
    hardwareScreen: @Composable () -> Unit,
    graphsScreen: @Composable () -> Unit,
    headerContent: (@Composable () -> Unit)?
) {
    MainScaffold(
        hardwareScreen = hardwareScreen,
        graphsScreen = graphsScreen,
        headerContent = headerContent
    )
}

