package io.github.lukewilk.ui.hardware

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.lukewilk.shared.model.SerialPortSuggestion
import io.github.lukewilk.shared.model.SystemLogEntry
import io.github.lukewilk.shared.model.SystemLogSeverity
import io.github.lukewilk.ui.ChannelState
import io.github.lukewilk.ui.elements.layout.VerticalSpacer
import io.github.lukewilk.ui.hardware.boardControlCard.BoardControlCard
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * JVM UI tests for the shared hardware-focused cards and tables.
 */
@OptIn(ExperimentalTestApi::class)
class HardwareComponentsTest {
    @Test
    fun `device selection card renders empty suggestions and refresh state`() {
        // Covers the fallback label path when no detected serial devices are available yet.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    DeviceSelectionCard(
                        availableBoards = listOf("Cyton"),
                        selectedBoard = 0,
                        onBoardSelected = {},
                        serialPort = "",
                        onSerialPortChange = {},
                        serialPortPlaceholder = "Auto-detect active USB serial device",
                        serialPortSuggestions = emptyList(),
                        selectedSerialPortSuggestion = 0,
                        onSerialPortSuggestionSelected = {},
                        onRefreshSerialPorts = {},
                        isLoadingSerialPorts = true,
                        serialPortSupportText = null,
                        timeout = "0",
                        onTimeoutChange = {},
                        isConnected = false,
                        isBusy = false,
                        onConnect = {},
                        onDisconnect = {}
                    )
                }
            }

            onNodeWithText("No active serial devices detected").assertIsDisplayed()
            onNodeWithText("Refreshing...").assertIsDisplayed()
        }
    }

    @Test
    fun `device selection card renders recommended serial labels and forwards button clicks`() {
        // Verifies the richer serial-device labels and action buttons visible during normal board setup.
        var refreshClicks = 0
        var connectClicks = 0

        runComposeUiTest {
            setContent {
                MaterialTheme {
                    DeviceSelectionCard(
                        availableBoards = listOf("Cyton", "Synthetic"),
                        selectedBoard = 0,
                        onBoardSelected = {},
                        serialPort = "/dev/ttyUSB0",
                        onSerialPortChange = {},
                        serialPortPlaceholder = "/dev/ttyUSB0",
                        serialPortSuggestions = listOf(
                            SerialPortSuggestion(
                                path = "/dev/ttyUSB0",
                                displayName = "Cyton Port",
                                details = "USB bridge",
                                isUsbDevice = true,
                                isRecommended = true
                            )
                        ),
                        selectedSerialPortSuggestion = 0,
                        onSerialPortSuggestionSelected = {},
                        onRefreshSerialPorts = { refreshClicks += 1 },
                        isLoadingSerialPorts = false,
                        serialPortSupportText = "A likely board connection was preselected for you. You can choose any detected port or type a custom path.",
                        timeout = "7",
                        onTimeoutChange = {},
                        isConnected = false,
                        isBusy = false,
                        onConnect = { connectClicks += 1 },
                        onDisconnect = {}
                    )
                }
            }

            onNodeWithText("Recommended • /dev/ttyUSB0 — USB bridge").assertIsDisplayed()
            onNodeWithText("A likely board connection was preselected for you. You can choose any detected port or type a custom path.").assertIsDisplayed()
            onNodeWithText("Refresh Ports").performClick()
            onNodeWithText("Connect").performClick()
        }

        assertEquals(1, connectClicks)
        assertEquals(1, refreshClicks)
    }

    @Test
    fun `device selection card falls back to display name when serial details are blank`() {
        // Covers the label-building branch that uses a friendly device name when no explicit details are available.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    DeviceSelectionCard(
                        availableBoards = listOf("Cyton"),
                        selectedBoard = 0,
                        onBoardSelected = {},
                        serialPort = "/dev/ttyUSB1",
                        onSerialPortChange = {},
                        serialPortPlaceholder = "/dev/ttyUSB1",
                        serialPortSuggestions = listOf(
                            SerialPortSuggestion(
                                path = "/dev/ttyUSB1",
                                displayName = "USB Serial Adapter"
                            )
                        ),
                        selectedSerialPortSuggestion = 0,
                        onSerialPortSuggestionSelected = {},
                        onRefreshSerialPorts = {},
                        isLoadingSerialPorts = false,
                        serialPortSupportText = "Choose any detected port or type a custom path.",
                        timeout = "0",
                        onTimeoutChange = {},
                        isConnected = false,
                        isBusy = false,
                        onConnect = {},
                        onDisconnect = {}
                    )
                }
            }

            onNodeWithText("/dev/ttyUSB1 — USB Serial Adapter").assertIsDisplayed()
        }
    }

    @Test
    fun `device selection card omits support copy and forwards disconnect clicks when connected`() {
        // Covers the connected-state status and disconnect action while using a bare serial path with no extra descriptor text.
        var disconnectClicks = 0

        runComposeUiTest {
            setContent {
                MaterialTheme {
                    DeviceSelectionCard(
                        availableBoards = listOf("Cyton"),
                        selectedBoard = 0,
                        onBoardSelected = {},
                        serialPort = "/dev/ttyUSB2",
                        onSerialPortChange = {},
                        serialPortPlaceholder = "/dev/ttyUSB2",
                        serialPortSuggestions = listOf(
                            SerialPortSuggestion(
                                path = "/dev/ttyUSB2",
                                displayName = "/dev/ttyUSB2"
                            )
                        ),
                        selectedSerialPortSuggestion = 0,
                        onSerialPortSuggestionSelected = {},
                        onRefreshSerialPorts = {},
                        isLoadingSerialPorts = false,
                        serialPortSupportText = null,
                        timeout = "0",
                        onTimeoutChange = {},
                        isConnected = true,
                        isBusy = false,
                        onConnect = {},
                        onDisconnect = { disconnectClicks += 1 }
                    )
                }
            }

            onNodeWithText("Device Connected").assertIsDisplayed()
            onNodeWithText("Connect").assertIsNotEnabled()
            onNodeWithText("Disconnect").performClick()
        }

        assertEquals(1, disconnectClicks)
    }

    @Test
    fun `board control card shows streaming active indicator when streaming`() {
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.size(900.dp, 700.dp)) {
                        BoardControlCard(
                            availableBoards = listOf("Cyton"),
                            selectedBoard = 0,
                            isConnected = true,
                            isStreaming = true,
                            isBusy = false,
                            channels = emptyList(),
                            onChannelToggle = { _, _ -> },
                            onRldToggle = { _, _ -> },
                            onVerifyChannels = {},
                            onStartStreaming = {},
                            onStopStreaming = {}
                        )
                    }
                }
            }

            onNodeWithText("Streaming Active", useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test
    fun `board control card forwards verify and start stream clicks when connected`() {
        var verifyClicks = 0
        var startClicks = 0

        runComposeUiTest {
            setContent {
                MaterialTheme {
                    androidx.compose.foundation.layout.Column {
                        VerticalSpacer(height = 4.dp)
                        BoardControlCard(
                            availableBoards = listOf("Cyton"),
                            selectedBoard = 0,
                            isConnected = true,
                            isStreaming = false,
                            isBusy = false,
                            channels = emptyList(),
                            onChannelToggle = { _, _ -> },
                            onRldToggle = { _, _ -> },
                            onVerifyChannels = { verifyClicks += 1 },
                            onStartStreaming = { startClicks += 1 },
                            onStopStreaming = {}
                        )
                    }
                }
            }

            onNodeWithText("Cyton Control").assertIsDisplayed()
            onNodeWithText("Verify Channels").assertIsDisplayed()
            onNodeWithText("Verify Channels").performClick()
            waitForIdle()
            onNodeWithText("Start Stream").assertIsDisplayed()
            onNodeWithText("Start Stream").performClick()
            waitForIdle()
        }

        assertEquals(1, verifyClicks)
        assertEquals(1, startClicks)
    }

    @Test
    fun `board control card forwards stop stream when streaming`() {
        var stopClicks = 0

        runComposeUiTest {
            setContent {
                MaterialTheme {
                    androidx.compose.foundation.layout.Column {
                        VerticalSpacer(height = 4.dp)
                        BoardControlCard(
                            availableBoards = listOf("Cyton"),
                            selectedBoard = 0,
                            isConnected = true,
                            isStreaming = true,
                            isBusy = false,
                            channels = emptyList(),
                            onChannelToggle = { _, _ -> },
                            onRldToggle = { _, _ -> },
                            onVerifyChannels = {},
                            onStartStreaming = {},
                            onStopStreaming = { stopClicks += 1 }
                        )
                    }
                }
            }

            onNodeWithText("Stop Stream").assertIsDisplayed()
            onNodeWithText("Stop Stream").performClick()
            waitForIdle()
        }

        assertEquals(1, stopClicks)
    }

    @Test
    fun `board control card falls back to a generic title when no board label is selected`() {
        // Covers the safe-title fallback branch used before board metadata is available.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    BoardControlCard(
                        availableBoards = emptyList(),
                        selectedBoard = 4,
                        isConnected = false,
                        isStreaming = false,
                        isBusy = false,
                        channels = listOf(ChannelState(id = 0, name = "Channel 1", enabled = false, rld = false, status = "Not configured")),
                        onChannelToggle = { _, _ -> },
                        onRldToggle = { _, _ -> },
                        onVerifyChannels = {},
                        onStartStreaming = {},
                        onStopStreaming = {}
                    )
                }
            }

            onNodeWithText("Board Control").assertIsDisplayed()
        }
    }

    @Test
    fun `system log card renders empty placeholder and newest log entries`() {
        // Confirms both the idle placeholder branch and the populated log-list branch remain visible to callers.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    androidx.compose.foundation.layout.Column {
                        SystemLogCard(logs = emptyList())
                        SystemLogCard(
                            logs = listOf(
                                SystemLogEntry(1L, SystemLogSeverity.INFO, "Startup complete"),
                                SystemLogEntry(2L, SystemLogSeverity.WARN, "Voltage dip"),
                                SystemLogEntry(3L, SystemLogSeverity.ERROR, "Board disconnected")
                            )
                        )
                    }
                }
            }

            onNodeWithText("Waiting for activity...").assertIsDisplayed()
            onNodeWithText("INFO • Startup complete", substring = true).assertIsDisplayed()
            onNodeWithText("WARN • Voltage dip", substring = true).assertIsDisplayed()
            onNodeWithText("ERROR • Board disconnected", substring = true).assertIsDisplayed()
        }
    }

    @Test
    fun `system log card renders a standalone error entry`() {
        // Forces the error-only severity branch so the card still renders the newest failure entry without sibling rows.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    SystemLogCard(
                        logs = listOf(SystemLogEntry(5L, SystemLogSeverity.ERROR, "Standalone failure"))
                    )
                }
            }

            onNodeWithText("ERROR • Standalone failure", substring = true).assertIsDisplayed()
        }
    }

    @Test
    fun `system log card accepts an explicit modifier`() {
        // Exercises the explicit modifier argument path on the composable wrapper rather than relying only on the default.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    SystemLogCard(
                        logs = listOf(SystemLogEntry(9L, SystemLogSeverity.INFO, "Explicit modifier")),
                        modifier = Modifier
                    )
                }
            }

            onNodeWithText("INFO • Explicit modifier", substring = true).assertIsDisplayed()
        }
    }

    @Test
    fun `system log card keeps older entries reachable through vertical scrolling`() {
        // Confirms the bounded log surface stays scrollable so older entries remain reachable in long histories.
        val logs = (0..40).map { index ->
            SystemLogEntry(
                timestampEpochMillis = index.toLong(),
                severity = SystemLogSeverity.INFO,
                message = "Log entry $index"
            )
        }

        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.size(600.dp, 320.dp)) {
                        SystemLogCard(logs = logs)
                    }
                }
            }

            waitForIdle()
            onNodeWithContentDescription("More content below", useUnmergedTree = true).assertIsDisplayed()
            kotlin.test.assertFalse(
                runCatching {
                    onNodeWithContentDescription("More content above", useUnmergedTree = true).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            )
            onNodeWithText("INFO • Log entry 40", substring = true).assertIsDisplayed()
            onNodeWithText("INFO • Log entry 0", substring = true).performScrollTo().assertIsDisplayed()
            onNodeWithContentDescription("More content above", useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test
    fun `system log card copies newest-first log history when copy all is pressed`() {
        // Verifies the copy action exposes the same newest-first history users see in the scrollable log surface.
        val logs = listOf(
            SystemLogEntry(1L, SystemLogSeverity.INFO, "Startup complete"),
            SystemLogEntry(2L, SystemLogSeverity.WARN, "Voltage dip"),
            SystemLogEntry(3L, SystemLogSeverity.ERROR, "Board disconnected")
        )
        var copiedText: String? = null

        runComposeUiTest {
            setContent {
                MaterialTheme {
                    SystemLogCard(
                        logs = logs,
                        onCopyAllLogs = { copiedText = it }
                    )
                }
            }

            onNodeWithText("Copy all").assertIsDisplayed().performClick()
        }

        val copiedLogText = copiedText ?: error("Expected the system log copy action to provide text.")
        val newestEntryIndex = copiedLogText.indexOf("ERROR • Board disconnected")
        val middleEntryIndex = copiedLogText.indexOf("WARN • Voltage dip")
        val oldestEntryIndex = copiedLogText.indexOf("INFO • Startup complete")

        assertEquals(3, copiedLogText.count { it == '\n' } + 1)
        kotlin.test.assertTrue(newestEntryIndex in 0 until middleEntryIndex)
        kotlin.test.assertTrue(middleEntryIndex in 0 until oldestEntryIndex)
    }

    @Test
    fun `system log helpers keep monospace styling and newline separated clipboard payloads`() {
        // Locks in the copy-friendly log typography and newline-separated export text used by the copy action.
        val logs = listOf(
            SystemLogEntry(11L, SystemLogSeverity.INFO, "First"),
            SystemLogEntry(12L, SystemLogSeverity.ERROR, "Second")
        )

        assertEquals(FontFamily.Monospace, systemLogFontFamily)

        val clipboardText = systemLogClipboardText(logs)
        val copiedLines = clipboardText.lines()

        assertEquals(2, copiedLines.size)
        kotlin.test.assertTrue(copiedLines[0].contains("ERROR • Second"))
        kotlin.test.assertTrue(copiedLines[1].contains("INFO • First"))
    }

    @Test
    fun `system log severity helper maps info warn and error to the material color scheme`() {
        // Verifies the extracted helper returns the exact scheme colors used for each supported severity.
        val colorScheme = lightColorScheme()

        assertEquals(colorScheme.primary, severityColorFor(colorScheme, SystemLogSeverity.INFO))
        assertEquals(colorScheme.tertiary, severityColorFor(colorScheme, SystemLogSeverity.WARN))
        assertEquals(colorScheme.error, severityColorFor(colorScheme, SystemLogSeverity.ERROR))
    }
}







