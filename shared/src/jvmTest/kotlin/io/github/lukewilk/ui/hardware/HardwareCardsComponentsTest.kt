package io.github.lukewilk.ui.hardware

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.lukewilk.shared.model.SerialPortSuggestion
import io.github.lukewilk.ui.ChannelState
import io.github.lukewilk.ui.hardware.boardControlCard.BoardControlCard
import io.github.lukewilk.ui.hardware.boardControlCard.ChannelTable
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers JVM-only hardware card composables that are composed by the shared hardware screen.
 */
@OptIn(ExperimentalTestApi::class)
class HardwareCardsComponentsTest {
    @Test
    fun `device selection card renders and dispatches connect refresh and disconnect actions`() = runComposeUiTest {
        var connectCalls = 0
        var disconnectCalls = 0
        var refreshCalls = 0

        setContent {
            MaterialTheme {
                Box(Modifier.size(900.dp, 1400.dp)) {
                    DeviceSelectionCard(
                    availableBoards = listOf("CYTON_BOARD"),
                    selectedBoard = 0,
                    onBoardSelected = {},
                    serialPort = "/dev/ttyACM0",
                    onSerialPortChange = {},
                    serialPortPlaceholder = "Auto",
                    serialPortSuggestions = listOf(
                        SerialPortSuggestion(path = "/dev/ttyACM0", displayName = "Primary", isRecommended = true)
                    ),
                    selectedSerialPortSuggestion = 0,
                    onSerialPortSuggestionSelected = {},
                    onRefreshSerialPorts = { refreshCalls += 1 },
                    isLoadingSerialPorts = false,
                    serialPortSupportText = "Detected serial device.",
                    timeout = "5",
                    onTimeoutChange = {},
                    isConnected = false,
                    isBusy = false,
                    onConnect = { connectCalls += 1 },
                    onDisconnect = { disconnectCalls += 1 }
                    )
                }
            }
        }

        onNodeWithText("Device Selection").assertIsDisplayed()
        onNodeWithText("Refresh Ports").performClick()
        waitForIdle()
        onNodeWithText("Connect").performClick()
        waitForIdle()
        assertEquals(1, refreshCalls)
        assertEquals(1, connectCalls)
        assertEquals(0, disconnectCalls)
    }

    @Test
    fun `device selection card shows refreshing ports label while a scan is active`() = runComposeUiTest {
        // Covers the refreshPortsButtonText(isLoadingSerialPorts = true) branch inside the composed card shell.
        setContent {
            MaterialTheme {
                Box(Modifier.size(700.dp, 900.dp)) {
                    DeviceSelectionCard(
                        availableBoards = listOf("Cyton"),
                        selectedBoard = 0,
                        onBoardSelected = {},
                        serialPort = "",
                        onSerialPortChange = {},
                        serialPortPlaceholder = "Auto",
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
        }

        onNodeWithText("Refreshing...").assertIsDisplayed()
        onNodeWithText("Refresh Ports").assertDoesNotExist()
    }

    @Test
    fun `device selection card renders path only labels when descriptors are blank`() = runComposeUiTest {
        // Covers serialPortSuggestionLabelsFor mapping when details are blank and displayName matches the path (no descriptor suffix).
        setContent {
            MaterialTheme {
                Box(Modifier.size(700.dp, 900.dp)) {
                    DeviceSelectionCard(
                        availableBoards = listOf("Cyton"),
                        selectedBoard = 0,
                        onBoardSelected = {},
                        serialPort = "",
                        onSerialPortChange = {},
                        serialPortPlaceholder = "Auto",
                        serialPortSuggestions = listOf(
                            SerialPortSuggestion(path = "/dev/ttyCUSTOM", displayName = "/dev/ttyCUSTOM", details = "")
                        ),
                        selectedSerialPortSuggestion = 0,
                        onSerialPortSuggestionSelected = {},
                        onRefreshSerialPorts = {},
                        isLoadingSerialPorts = false,
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
        }

        onNodeWithText("/dev/ttyCUSTOM", substring = false).assertIsDisplayed()
    }

    @Test
    fun `device selection card formats recommended rows with descriptor suffixes`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(Modifier.size(700.dp, 900.dp)) {
                    DeviceSelectionCard(
                        availableBoards = listOf("Cyton"),
                        selectedBoard = 0,
                        onBoardSelected = {},
                        serialPort = "",
                        onSerialPortChange = {},
                        serialPortPlaceholder = "Auto",
                        serialPortSuggestions = listOf(
                            SerialPortSuggestion(
                                path = "/dev/ttyREC",
                                displayName = "Board Name",
                                details = "USB serial bridge",
                                isRecommended = true
                            )
                        ),
                        selectedSerialPortSuggestion = 0,
                        onSerialPortSuggestionSelected = {},
                        onRefreshSerialPorts = {},
                        isLoadingSerialPorts = false,
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
        }

        onNodeWithText("Recommended • /dev/ttyREC — USB serial bridge").assertIsDisplayed()
    }

    @Test
    fun `device selection card shows connected status styling copy`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(Modifier.size(700.dp, 900.dp)) {
                    DeviceSelectionCard(
                        availableBoards = listOf("Cyton"),
                        selectedBoard = 0,
                        onBoardSelected = {},
                        serialPort = "/dev/ttyACM0",
                        onSerialPortChange = {},
                        serialPortPlaceholder = "Auto",
                        serialPortSuggestions = emptyList(),
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
                        onDisconnect = {}
                    )
                }
            }
        }

        onNodeWithText("Device Connected").assertIsDisplayed()
    }

    @Test
    fun `device selection card shows idle empty serial suggestions placeholder`() = runComposeUiTest {
        // Covers the non-loading empty-suggestion path so the serial dropdown shows the idle placeholder label.
        setContent {
            MaterialTheme {
                Box(Modifier.size(700.dp, 900.dp)) {
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
                        isLoadingSerialPorts = false,
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
        }

        onNodeWithText("No active serial devices detected").assertIsDisplayed()
        onNodeWithText("Refresh Ports").assertIsDisplayed()
    }

    @Test
    fun `device selection card supports omitted optional arguments`() = runComposeUiTest {
        // Exercises the default serialPortSupportText/capability argument path on the top-level composable wrapper.
        setContent {
            MaterialTheme {
                Box(Modifier.size(700.dp, 900.dp)) {
                    DeviceSelectionCard(
                        availableBoards = listOf("Cyton"),
                        selectedBoard = 0,
                        onBoardSelected = {},
                        serialPort = "",
                        onSerialPortChange = {},
                        serialPortPlaceholder = "Auto",
                        serialPortSuggestions = emptyList(),
                        selectedSerialPortSuggestion = 0,
                        onSerialPortSuggestionSelected = {},
                        onRefreshSerialPorts = {},
                        isLoadingSerialPorts = false,
                        timeout = "0",
                        onTimeoutChange = {},
                        isConnected = false,
                        isBusy = false,
                        onConnect = {},
                        onDisconnect = {}
                    )
                }
            }
        }

        onNodeWithText("Device Selection").assertIsDisplayed()
        onNodeWithText("Connect").assertIsDisplayed()
    }

    @Test
    fun `board control card shows streaming indicator when hardware is streaming`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(Modifier.size(900.dp, 2000.dp)) {
                    BoardControlCard(
                        availableBoards = listOf("CYTON_BOARD"),
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

        waitForIdle()
        onNodeWithText("Stop Stream").assertIsDisplayed()
        onNodeWithText("Streaming Active", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `channel table renders header and status row`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(Modifier.size(800.dp, 400.dp)) {
                    ChannelTable(
                        channels = listOf(
                            ChannelState(3, "Channel 4", enabled = true, rld = true, status = "Configured")
                        ),
                        enabled = true,
                        onChannelToggle = { _, _ -> },
                        onRldToggle = { _, _ -> }
                    )
                }
            }
        }

        onNodeWithText("Enable").assertIsDisplayed()
        onNodeWithText("RLD").assertIsDisplayed()
    }

    @Test
    fun `channel table renders non configured status badge copy`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(Modifier.size(800.dp, 280.dp)) {
                    ChannelTable(
                        channels = listOf(
                            ChannelState(0, "Row A", enabled = false, rld = false, status = "NC")
                        ),
                        enabled = true,
                        onChannelToggle = { _, _ -> },
                        onRldToggle = { _, _ -> }
                    )
                }
            }
        }

        onNodeWithText("NC", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `channel table forwards enable and rld toggles for single row`() = runComposeUiTest {
        val toggledChannels = mutableListOf<Pair<Int, Boolean>>()
        val toggledRlds = mutableListOf<Pair<Int, Boolean>>()

        setContent {
            MaterialTheme {
                Box(Modifier.size(800.dp, 280.dp)) {
                    ChannelTable(
                        channels = listOf(
                            ChannelState(9, "Channel A", enabled = true, rld = false, status = "Configured")
                        ),
                        enabled = true,
                        onChannelToggle = { id, enabled -> toggledChannels += id to enabled },
                        onRldToggle = { id, enabled -> toggledRlds += id to enabled }
                    )
                }
            }
        }

        waitForIdle()
        assertEquals(2, onAllNodes(isToggleable()).fetchSemanticsNodes().size)
        onAllNodes(isToggleable())[0].performClick()
        waitForIdle()
        onAllNodes(isToggleable())[1].performClick()
        waitForIdle()

        assertEquals(listOf(9 to false), toggledChannels)
        assertEquals(listOf(9 to true), toggledRlds)
    }

    @Test
    fun `channel table renders two rows with alternating backgrounds and divider between them`() = runComposeUiTest {
        // Exercises forEachIndexed with index < lastIndex (horizontal divider) plus even/odd row background branches and both StatusBadge tones.
        setContent {
            MaterialTheme {
                Box(Modifier.size(900.dp, 1400.dp)) {
                    ChannelTable(
                        channels = listOf(
                            ChannelState(0, "First row", enabled = true, rld = false, status = "Configured"),
                            ChannelState(1, "Second row", enabled = false, rld = true, status = "Idle")
                        ),
                        enabled = true,
                        onChannelToggle = { _, _ -> },
                        onRldToggle = { _, _ -> }
                    )
                }
            }
        }

        waitForIdle()
        onNodeWithText("Status").assertIsDisplayed()
        assertEquals(4, onAllNodes(isToggleable()).fetchSemanticsNodes().size)
    }

    @Test
    fun `channel table disables checkboxes when not enabled`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(Modifier.size(800.dp, 400.dp)) {
                    ChannelTable(
                        channels = listOf(
                            ChannelState(0, "Channel 1", enabled = true, rld = false, status = "Configured")
                        ),
                        enabled = false,
                        onChannelToggle = { _, _ -> },
                        onRldToggle = { _, _ -> }
                    )
                }
            }
        }

        onAllNodes(isToggleable())[0].assertIsNotEnabled()
        onAllNodes(isToggleable())[1].assertIsNotEnabled()
    }

    @Test
    fun `device selection card hides support text for blank strings and shows connected styling`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(Modifier.size(900.dp, 1400.dp)) {
                    DeviceSelectionCard(
                        availableBoards = listOf("CYTON_BOARD"),
                        selectedBoard = 0,
                        onBoardSelected = {},
                        serialPort = "/dev/ttyACM0",
                        onSerialPortChange = {},
                        serialPortPlaceholder = "Port",
                        serialPortSuggestions = emptyList(),
                        selectedSerialPortSuggestion = 0,
                        onSerialPortSuggestionSelected = {},
                        onRefreshSerialPorts = {},
                        isLoadingSerialPorts = false,
                        serialPortSupportText = "   ",
                        timeout = "0",
                        onTimeoutChange = {},
                        isConnected = true,
                        isBusy = false,
                        onConnect = {},
                        onDisconnect = {},
                        canSelectBoard = false,
                        canConnect = false
                    )
                }
            }
        }

        onNodeWithText("Device Connected").assertIsDisplayed()
        onNodeWithText("Connect").assertIsNotEnabled()
        onNodeWithText("   ").assertDoesNotExist()
    }

    @Test
    fun `device selection card lists non-recommended ports with details suffix`() = runComposeUiTest {
        // Covers serialPortSuggestionLabelsFor when isRecommended is false but details supply the descriptor segment.
        setContent {
            MaterialTheme {
                Box(Modifier.size(700.dp, 900.dp)) {
                    DeviceSelectionCard(
                        availableBoards = listOf("Cyton"),
                        selectedBoard = 0,
                        onBoardSelected = {},
                        serialPort = "",
                        onSerialPortChange = {},
                        serialPortPlaceholder = "Auto",
                        serialPortSuggestions = listOf(
                            SerialPortSuggestion(
                                path = "/dev/ttyUSB0",
                                displayName = "Backup",
                                details = "FTDI bridge",
                                isRecommended = false
                            )
                        ),
                        selectedSerialPortSuggestion = 0,
                        onSerialPortSuggestionSelected = {},
                        onRefreshSerialPorts = {},
                        isLoadingSerialPorts = false,
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
        }

        onNodeWithText("/dev/ttyUSB0 — FTDI bridge", substring = false).assertIsDisplayed()
    }

    @Test
    fun `device selection card enables disconnect while connected and idle`() = runComposeUiTest {
        var disconnectCalls = 0
        setContent {
            MaterialTheme {
                Box(Modifier.size(900.dp, 1400.dp)) {
                    DeviceSelectionCard(
                        availableBoards = listOf("CYTON_BOARD"),
                        selectedBoard = 0,
                        onBoardSelected = {},
                        serialPort = "/dev/ttyACM0",
                        onSerialPortChange = {},
                        serialPortPlaceholder = "Port",
                        serialPortSuggestions = emptyList(),
                        selectedSerialPortSuggestion = 0,
                        onSerialPortSuggestionSelected = {},
                        onRefreshSerialPorts = {},
                        isLoadingSerialPorts = false,
                        serialPortSupportText = "Helpful hint",
                        timeout = "0",
                        onTimeoutChange = {},
                        isConnected = true,
                        isBusy = false,
                        onConnect = {},
                        onDisconnect = { disconnectCalls += 1 }
                    )
                }
            }
        }

        onNodeWithText("Helpful hint").assertIsDisplayed()
        onNodeWithText("Disconnect").assertIsDisplayed().performClick()
        waitForIdle()
        assertEquals(1, disconnectCalls)
    }
}

