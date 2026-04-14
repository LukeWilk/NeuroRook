package io.github.lukewilk

import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.lukewilk.hardware.api.HardwareBackendApi
import io.github.lukewilk.theming.rememberDesktopSystemColorScheme
import javax.swing.UIManager

/**
 * Desktop entry point that hosts the shared NeuroRook Compose UI.
 */
fun main() {
    configureSystemLookAndFeel()

    application {
        val windowState = rememberWindowState(width = 1440.dp, height = 960.dp)
        val colorScheme = rememberDesktopSystemColorScheme()
        val appIcon = painterResource("neuroRook.png")

        Window(
            onCloseRequest = ::exitApplication,
            title = "NeuroRook",
            state = windowState,
            icon = appIcon
        ) {
            val backendApi = remember { HardwareBackendApi() }
            App(
                backendApi = backendApi,
                colorScheme = colorScheme,
                headerContent = { DesktopSidebarBrand() }
            )
        }
    }
}

private fun configureSystemLookAndFeel() {
    val systemLookAndFeel = UIManager.getSystemLookAndFeelClassName()
    val activeLookAndFeel = UIManager.getLookAndFeel()?.javaClass?.name
    if (systemLookAndFeel == activeLookAndFeel) return

    runCatching {
        UIManager.setLookAndFeel(systemLookAndFeel)
    }
}

