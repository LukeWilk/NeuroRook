package io.github.lukewilk

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import io.github.lukewilk.hardware.api.HardwareBackendApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Compose-backed desktop tests for the window body and backend memoization behavior.
 */
@OptIn(ExperimentalTestApi::class)
class MainDesktopComposeTest {
    @Test
    fun `desktop app within window matches the production app layout`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                DesktopAppWithinWindow(
                    backendFactory = { HardwareBackendApi() },
                    colorScheme = lightColorScheme(primary = Color.Cyan),
                    headerContent = null
                )
            }
        }
        assertTrue(onAllNodesWithText("Neuro Rook").fetchSemanticsNodes().isEmpty())
        onNodeWithText("Hardware").assertIsDisplayed()
    }

    @Test
    fun `desktop app within window forwards custom header content`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                DesktopAppWithinWindow(
                    backendFactory = { HardwareBackendApi() },
                    colorScheme = lightColorScheme(primary = Color.Cyan),
                    headerContent = { Text("HeaderInWindowBody") }
                )
            }
        }
        onNodeWithText("HeaderInWindowBody").assertIsDisplayed()
        onNodeWithText("Hardware").assertIsDisplayed()
    }

    @Test
    fun `remember desktop backend api memoizes the same factory instance across recompositions`() = runComposeUiTest {
        var factoryInvocations = 0
        val factory: () -> HardwareBackendApi = {
            factoryInvocations++
            HardwareBackendApi()
        }
        val counter = mutableIntStateOf(0)
        setContent {
            MaterialTheme {
                counter.intValue
                val api = rememberDesktopBackendApi(factory)
                Text("hash:${System.identityHashCode(api)}|tick:${counter.intValue}")
            }
        }
        assertEquals(1, factoryInvocations)
        counter.intValue = 1
        waitForIdle()
        assertEquals(1, factoryInvocations)
    }

    @Test
    fun `remember desktop backend api rebuilds when the factory identity changes`() = runComposeUiTest {
        var firstFactoryInvocations = 0
        var secondFactoryInvocations = 0
        val activeFactory = mutableStateOf<() -> HardwareBackendApi>({
            firstFactoryInvocations++
            HardwareBackendApi()
        })

        setContent {
            MaterialTheme {
                val api = rememberDesktopBackendApi(activeFactory.value)
                Text("hash:${System.identityHashCode(api)}")
            }
        }

        assertEquals(1, firstFactoryInvocations)
        activeFactory.value = {
            secondFactoryInvocations++
            HardwareBackendApi()
        }
        waitForIdle()

        assertEquals(1, firstFactoryInvocations)
        assertEquals(1, secondFactoryInvocations)
    }
}
