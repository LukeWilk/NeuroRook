package io.github.lukewilk

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.lukewilk.hardware.api.HardwareBackendApi

/**
 * Desktop entry point that hosts the shared NeuroRook Compose UI.
 */
fun main() = application {
    val windowState = rememberWindowState(width = 1440.dp, height = 960.dp)

    Window(
        onCloseRequest = ::exitApplication,
        title = "NeuroRook",
        state = windowState
    ) {
        val backendApi = remember { HardwareBackendApi() }
        App(backendApi = backendApi)
    }
}

