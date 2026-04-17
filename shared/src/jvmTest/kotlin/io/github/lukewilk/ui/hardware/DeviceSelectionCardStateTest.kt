package io.github.lukewilk.ui.hardware

import io.github.lukewilk.shared.model.SerialPortSuggestion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * JVM state tests for the pure DeviceSelectionCard logic.
 */
class DeviceSelectionCardStateTest {
    @Test
    fun `device selection enablement logic covers connected busy loading and allowed states`() {
        val suggestions = listOf(SerialPortSuggestion(path = "/dev/ttyUSB0", displayName = "USB Adapter"))

        assertTrue(isBoardSelectionEnabled(isConnected = false, isBusy = false, canSelectBoard = true))
        assertFalse(isBoardSelectionEnabled(isConnected = true, isBusy = false, canSelectBoard = true))
        assertFalse(isBoardSelectionEnabled(isConnected = false, isBusy = true, canSelectBoard = true))
        assertFalse(isBoardSelectionEnabled(isConnected = false, isBusy = false, canSelectBoard = false))

        assertTrue(isSerialSuggestionSelectionEnabled(suggestions, isConnected = false, isBusy = false, isLoadingSerialPorts = false))
        assertFalse(isSerialSuggestionSelectionEnabled(emptyList(), isConnected = false, isBusy = false, isLoadingSerialPorts = false))
        assertFalse(isSerialSuggestionSelectionEnabled(suggestions, isConnected = true, isBusy = false, isLoadingSerialPorts = false))
        assertFalse(isSerialSuggestionSelectionEnabled(suggestions, isConnected = false, isBusy = true, isLoadingSerialPorts = false))
        assertFalse(isSerialSuggestionSelectionEnabled(suggestions, isConnected = false, isBusy = false, isLoadingSerialPorts = true))

        assertTrue(isRefreshPortsEnabled(isConnected = false, isBusy = false, isLoadingSerialPorts = false))
        assertFalse(isRefreshPortsEnabled(isConnected = true, isBusy = false, isLoadingSerialPorts = false))
        assertFalse(isRefreshPortsEnabled(isConnected = false, isBusy = true, isLoadingSerialPorts = false))
        assertFalse(isRefreshPortsEnabled(isConnected = false, isBusy = false, isLoadingSerialPorts = true))

        assertTrue(isConnectEnabled(isConnected = false, isBusy = false, canConnect = true))
        assertFalse(isConnectEnabled(isConnected = true, isBusy = false, canConnect = true))
        assertFalse(isConnectEnabled(isConnected = false, isBusy = true, canConnect = true))
        assertFalse(isConnectEnabled(isConnected = false, isBusy = false, canConnect = false))

        assertTrue(isDisconnectEnabled(isConnected = true, isBusy = false))
        assertFalse(isDisconnectEnabled(isConnected = false, isBusy = false))
        assertFalse(isDisconnectEnabled(isConnected = true, isBusy = true))

        assertTrue(areSerialInputFieldsEnabled(isConnected = false, isBusy = false))
        assertFalse(areSerialInputFieldsEnabled(isConnected = true, isBusy = false))
        assertFalse(areSerialInputFieldsEnabled(isConnected = false, isBusy = true))
    }

    @Test
    fun `serial suggestion descriptor logic prefers details then display name then blank`() {
        val withDetails = SerialPortSuggestion(path = "/dev/ttyUSB0", displayName = "USB Adapter", details = "FTDI bridge")
        val withDisplayNameOnly = SerialPortSuggestion(path = "/dev/ttyUSB1", displayName = "Cyton Adapter")
        val pathOnly = SerialPortSuggestion(path = "/dev/ttyUSB2", displayName = "/dev/ttyUSB2")
        val blankDisplayName = SerialPortSuggestion(path = "/dev/ttyUSB3", displayName = "")

        assertEquals("FTDI bridge", serialPortSuggestionDescriptor(withDetails))
        assertEquals("Cyton Adapter", serialPortSuggestionDescriptor(withDisplayNameOnly))
        assertEquals("", serialPortSuggestionDescriptor(pathOnly))
        assertEquals("", serialPortSuggestionDescriptor(blankDisplayName))
    }

    @Test
    fun `device selection state texts cover selected index support copy refresh label and connection status`() {
        val suggestions = listOf(
            SerialPortSuggestion(path = "/dev/ttyUSB0", displayName = "USB Adapter"),
            SerialPortSuggestion(path = "/dev/ttyUSB1", displayName = "Backup Adapter")
        )

        assertEquals(0, selectedSerialPortSuggestionIndexValue(-1, suggestions))
        assertEquals(1, selectedSerialPortSuggestionIndexValue(1, suggestions))
        assertEquals(1, selectedSerialPortSuggestionIndexValue(7, suggestions))
        assertEquals(0, selectedSerialPortSuggestionIndexValue(7, emptyList()))

        assertEquals("Refresh Ports", refreshPortsButtonText(isLoadingSerialPorts = false))
        assertEquals("Refreshing...", refreshPortsButtonText(isLoadingSerialPorts = true))

        assertEquals("Helpful copy", visibleSerialPortSupportText("Helpful copy"))
        assertEquals(null, visibleSerialPortSupportText("   "))
        assertEquals(null, visibleSerialPortSupportText(null))

        assertEquals("Device Connected", deviceConnectionStatusText(isConnected = true))
        assertEquals("No Device Connected", deviceConnectionStatusText(isConnected = false))
    }
}
