package io.github.lukewilk.ui

import io.github.lukewilk.shared.model.SerialPortSuggestion
import io.github.lukewilk.ui.elements.navigation.menuItemHasDivider
import io.github.lukewilk.ui.elements.navigation.menuShowsTitle
import io.github.lukewilk.ui.elements.navigation.menuSidebarItemHasDivider
import io.github.lukewilk.ui.elements.navigation.menuSidebarSystemModeLabel
import io.github.lukewilk.ui.elements.navigation.menuSidebarWidth
import io.github.lukewilk.ui.hardware.boardControlCard.boardControlUiState
import io.github.lukewilk.ui.hardware.boardControlCard.channelTableRowUiState
import io.github.lukewilk.ui.hardware.deviceConnectionStatusText
import io.github.lukewilk.ui.hardware.deviceSelectionUiState
import io.github.lukewilk.ui.hardware.refreshPortsButtonText
import io.github.lukewilk.ui.hardware.selectedSerialPortSuggestionIndexValue
import io.github.lukewilk.ui.hardware.serialPortSuggestionDescriptor
import io.github.lukewilk.ui.hardware.visibleSerialPortSupportText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * JVM tests for shared UI state that is easier to validate without a Compose harness.
 */
class UiStateTest {
    @Test
    fun `menu state returns expected title and divider visibility`() {
        assertTrue(menuShowsTitle("Hardware"))
        assertFalse(menuShowsTitle(null))
        assertTrue(menuItemHasDivider(index = 0, lastIndex = 2))
        assertFalse(menuItemHasDivider(index = 2, lastIndex = 2))
    }

    @Test
    fun `menu sidebar state covers width mode labels and divider logic`() {
        assertEquals(220, menuSidebarWidth(expanded = true, collapsedWidth = 56, expandedWidth = 220))
        assertEquals(56, menuSidebarWidth(expanded = false, collapsedWidth = 56, expandedWidth = 220))
        assertEquals(180, menuSidebarWidth(expanded = true, collapsedWidth = 48, expandedWidth = 180))
        assertEquals(48, menuSidebarWidth(expanded = false, collapsedWidth = 48, expandedWidth = 180))
        assertEquals("System: Dark mode", menuSidebarSystemModeLabel(true))
        assertEquals("System: Light mode", menuSidebarSystemModeLabel(false))
        assertNull(menuSidebarSystemModeLabel(null))
        assertTrue(menuSidebarItemHasDivider(index = 0, lastIndex = 1))
        assertFalse(menuSidebarItemHasDivider(index = 1, lastIndex = 1))
    }

    @Test
    fun `device selection state covers suggestion parsing and flags`() {
        val suggested = SerialPortSuggestion(
            path = "/dev/ttyUSB0",
            displayName = "Cyton Adapter",
            details = "",
            isRecommended = true
        )
        val withDetails = SerialPortSuggestion(
            path = "/dev/ttyACM0",
            displayName = "Ignored",
            details = "USB Serial"
        )

        assertEquals("Cyton Adapter", serialPortSuggestionDescriptor(suggested))
        assertEquals("USB Serial", serialPortSuggestionDescriptor(withDetails))
        assertEquals(0, selectedSerialPortSuggestionIndexValue(-1, listOf(suggested)))
        assertEquals(0, selectedSerialPortSuggestionIndexValue(0, listOf(suggested)))
        assertEquals(0, selectedSerialPortSuggestionIndexValue(3, emptyList()))
        assertEquals("Refreshing...", refreshPortsButtonText(true))
        assertEquals("Refresh Ports", refreshPortsButtonText(false))
        assertEquals("hint", visibleSerialPortSupportText("hint"))
        assertNull(visibleSerialPortSupportText(" "))
        assertEquals("Device Connected", deviceConnectionStatusText(true))
        assertEquals("No Device Connected", deviceConnectionStatusText(false))

        val disconnectedState = deviceSelectionUiState(
            serialPortSuggestions = listOf(suggested),
            selectedSerialPortSuggestion = 0,
            isConnected = false,
            isBusy = false,
            isLoadingSerialPorts = false,
            serialPortSupportText = "adapter detected",
            canSelectBoard = true,
            canConnect = true
        )
        assertTrue(disconnectedState.boardSelectionEnabled)
        assertTrue(disconnectedState.serialSuggestionSelectionEnabled)
        assertTrue(disconnectedState.refreshPortsEnabled)
        assertTrue(disconnectedState.connectEnabled)
        assertFalse(disconnectedState.disconnectEnabled)

        val connectedBusyState = deviceSelectionUiState(
            serialPortSuggestions = emptyList(),
            selectedSerialPortSuggestion = 99,
            isConnected = true,
            isBusy = true,
            isLoadingSerialPorts = true,
            serialPortSupportText = " ",
            canSelectBoard = false,
            canConnect = false
        )
        assertFalse(connectedBusyState.boardSelectionEnabled)
        assertFalse(connectedBusyState.serialSuggestionSelectionEnabled)
        assertFalse(connectedBusyState.refreshPortsEnabled)
        assertFalse(connectedBusyState.connectEnabled)
        assertFalse(connectedBusyState.serialInputFieldsEnabled)
        assertTrue(connectedBusyState.disconnectEnabled.not())
        assertNull(connectedBusyState.visibleSerialPortSupportText)
        assertEquals(0, connectedBusyState.selectedSerialPortSuggestion)
    }

    @Test
    fun `board control and channel table state covers streaming and row variants`() {
        val readyState = boardControlUiState(
            availableBoards = listOf("CYTON_BOARD"),
            selectedBoard = 0,
            isConnected = true,
            isStreaming = false,
            isBusy = false
        )
        assertEquals("CYTON_BOARD Control", readyState.title)
        assertTrue(readyState.channelsEnabled)
        assertTrue(readyState.verifyChannelsEnabled)
        assertTrue(readyState.startStreamEnabled)
        assertFalse(readyState.stopStreamEnabled)
        assertFalse(readyState.showStreamingIndicator)

        val streamingState = boardControlUiState(
            availableBoards = emptyList(),
            selectedBoard = 5,
            isConnected = true,
            isStreaming = true,
            isBusy = false
        )
        assertEquals("Board Control", streamingState.title)
        assertFalse(streamingState.startStreamEnabled)
        assertTrue(streamingState.stopStreamEnabled)
        assertTrue(streamingState.showStreamingIndicator)

        val disconnectedBusyStreamingState = boardControlUiState(
            availableBoards = listOf("CYTON_BOARD"),
            selectedBoard = 0,
            isConnected = false,
            isStreaming = true,
            isBusy = true
        )
        assertFalse(disconnectedBusyStreamingState.channelsEnabled)
        assertFalse(disconnectedBusyStreamingState.verifyChannelsEnabled)
        assertFalse(disconnectedBusyStreamingState.startStreamEnabled)
        assertFalse(disconnectedBusyStreamingState.stopStreamEnabled)
        assertTrue(disconnectedBusyStreamingState.showStreamingIndicator)

        val configuredFirstRow = channelTableRowUiState(index = 0, lastIndex = 1, status = "Configured")
        assertTrue(configuredFirstRow.usesEvenBackground)
        assertTrue(configuredFirstRow.showDivider)
        assertTrue(configuredFirstRow.isConfigured)

        val unconfiguredLastRow = channelTableRowUiState(index = 1, lastIndex = 1, status = "Not Configured")
        assertFalse(unconfiguredLastRow.usesEvenBackground)
        assertFalse(unconfiguredLastRow.showDivider)
        assertFalse(unconfiguredLastRow.isConfigured)
    }
}
