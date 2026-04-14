package io.github.lukewilk

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import io.github.lukewilk.shared.api.BackendApi
import io.github.lukewilk.ui.HardwareScreen
import io.github.lukewilk.ui.MainScaffold

/**
 * Root NeuroRook application composable shared by desktop and Android hosts.
 */
@Composable
fun App(backendApi: BackendApi? = null) {
    MaterialTheme {
        MainScaffold(
            hardwareScreen = {
                HardwareScreen(backendApi = backendApi)
            }
        )
    }
}

