package io.github.lukewilk.ui.hardware

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * JVM UI coverage tests for the shared hardware-focused cards and tables.
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
    fun `board control card renders statuses and forwards toggles`() {
        // Exercises the channel table, status-badge branches, and streaming indicator from the parent control card.
        val toggledChannels = mutableListOf<Pair<Int, Boolean>>()
        val toggledRlds = mutableListOf<Pair<Int, Boolean>>()

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
                            channels = listOf(
                                ChannelState(id = 0, name = "Channel 1", enabled = true, rld = false, status = "Configured"),
                                ChannelState(id = 1, name = "Channel 2", enabled = false, rld = true, status = "Not configured")
                            ),
                            onChannelToggle = { id, enabled -> toggledChannels += id to enabled },
                            onRldToggle = { id, enabled -> toggledRlds += id to enabled },
                            onVerifyChannels = {},
                            onStartStreaming = {},
                            onStopStreaming = {}
                        )
                    }
                }
            }

            onNodeWithText("Cyton Control").assertIsDisplayed()
            onNodeWithText("Channel Configuration").assertIsDisplayed()
        }

        assertEquals(emptyList(), toggledChannels)
        assertEquals(emptyList(), toggledRlds)
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
}







