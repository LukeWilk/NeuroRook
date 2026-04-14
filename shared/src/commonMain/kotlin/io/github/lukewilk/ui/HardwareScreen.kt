package io.github.lukewilk.ui

import androidx.compose.runtime.Composable
import io.github.lukewilk.shared.api.BackendApi

@Composable
expect fun HardwareScreen(backendApi: BackendApi?)

