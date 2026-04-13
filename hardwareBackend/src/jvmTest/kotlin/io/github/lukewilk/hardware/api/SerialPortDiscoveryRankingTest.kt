package io.github.lukewilk.hardware.api

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * Ranking and recommendation heuristics tests for JVM serial-port discovery.
 */
internal class SerialPortDiscoveryRankingTest : SerialPortDiscoveryTestSupport() {

    @Test
    fun `rank serial port suggestions prefers a board specific usb port`() {
        // A board-specific USB descriptor should be ranked ahead of generic serial devices.
        val suggestions = rankSerialPortSuggestions(
            boardId = "CYTON_BOARD",
            ports = listOf(
                descriptor(path = "/dev/ttyUSB0", displayName = "USB Serial Adapter"),
                descriptor(path = "/dev/ttyACM0", displayName = "OpenBCI Cyton", description = "OpenBCI Cyton USB Dongle")
            )
        )

        assertRecommendedFirstPath(suggestions, "/dev/ttyACM0")
        assertTrue(suggestions.first().isUsbDevice)
        assertFalse(suggestions.drop(1).any { it.isRecommended })
    }

    @Test
    fun `rank serial port suggestions keeps detected ports selectable`() {
        // Every distinct detected port should remain selectable, even when only one is recommended.
        val suggestions = rankSerialPortSuggestions(
            boardId = "FREEEEG32_BOARD",
            ports = listOf(
                descriptor(path = "/dev/ttyUSB1", displayName = "FreeEEG32"),
                descriptor(path = "/dev/ttyS0", displayName = "On-board UART", vendorId = -1, productId = -1, location = "soc0")
            )
        )

        assertEquals(listOf("/dev/ttyUSB1", "/dev/ttyS0"), suggestions.map { it.path })
        assertRecommendedFirstPath(suggestions, "/dev/ttyUSB1")
    }

    @Test
    fun `rank serial port suggestions filters blank paths deduplicates and leaves zero score ports unrecommended`() {
        val suggestions = rankSerialPortSuggestions(
            boardId = "   ",
            ports = listOf(
                descriptor(path = "", displayName = "Ignored", description = "", location = "", vendorId = -1, productId = -1),
                descriptor(path = "/dev/pts/1", displayName = "Pseudo Terminal", description = "Pseudo Terminal", location = "", vendorId = -1, productId = -1),
                descriptor(path = "/dev/pts/1", displayName = "Duplicate USB Adapter", description = "USB Serial", location = "", vendorId = 0x10C4, productId = 0xEA60)
            )
        )

        // Blank and duplicate paths should be removed before recommendation logic runs.
        assertEquals(1, suggestions.size)
        assertEquals("/dev/pts/1", suggestions.single().path)
        assertFalse(suggestions.single().isRecommended)
        assertFalse(suggestions.single().isUsbDevice)
        assertEquals("", suggestions.single().details)
    }

    @Test
    fun `rank serial port suggestions falls back to the path when names are blank`() {
        val suggestions = rankSerialPortSuggestions(
            boardId = null,
            ports = listOf(
                SerialPortDescriptor(
                    path = "/dev/fallback",
                    systemName = "",
                    displayName = "",
                    description = "",
                    location = "",
                    vendorId = -1,
                    productId = -1
                )
            )
        )

        // When both display and system names are blank, the ranking output should fall back to the port path.
        assertEquals("/dev/fallback", suggestions.single().displayName)
        assertFalse(suggestions.single().isRecommended)
    }

    @Test
    fun `rank serial port suggestions recognizes supported board keyword families`() {
        val scenarios = listOf(
            "GANGLION_BOARD" to descriptor(path = "/dev/ttyS10", displayName = "Ganglion Adapter", description = "Ganglion Adapter", location = "rack-1", vendorId = -1, productId = -1),
            "MUSE_2_BOARD" to descriptor(path = "/dev/ttyS11", displayName = "Muse S", description = "Muse S Headband", location = "rack-1", vendorId = -1, productId = -1),
            "UNICORN_BOARD" to descriptor(path = "/dev/ttyS12", displayName = "Unicorn Hybrid", description = "Unicorn Hybrid Black", location = "rack-1", vendorId = -1, productId = -1),
            "CALLIBRI_BOARD" to descriptor(path = "/dev/ttyS13", displayName = "Callibri EEG", description = "Callibri EEG", location = "rack-1", vendorId = -1, productId = -1)
        )

        scenarios.forEach { (boardId, preferredPort) ->
            // Each board family should boost its matching descriptor ahead of a neutral pseudo-terminal entry.
            val suggestions = rankSerialPortSuggestions(
                boardId = boardId,
                ports = listOf(
                    descriptor(path = "/dev/pts/9", displayName = "Pseudo Terminal", description = "", location = "", vendorId = -1, productId = -1),
                    preferredPort
                )
            )

            assertRecommendedFirstPath(suggestions, preferredPort.path)
        }
    }

    @Test
    fun `rank serial port suggestions still uses generic board tokens when no family matches`() {
        val preferredPort = descriptor(
            path = "/dev/ttyS14",
            displayName = "Neuropawn Knight",
            description = "Neuropawn Knight",
            location = "rack-2",
            vendorId = -1,
            productId = -1
        )
        val suggestions = rankSerialPortSuggestions(
            boardId = "NEUROPAWN_KNIGHT_BOARD",
            ports = listOf(
                descriptor(path = "/dev/pts/10", displayName = "Pseudo Terminal", description = "", location = "", vendorId = -1, productId = -1),
                preferredPort
            )
        )

        // Board ids without a dedicated family should still contribute their generic tokenized keywords.
        assertRecommendedFirstPath(suggestions, preferredPort.path)
    }

    @Test
    fun `rank serial port suggestions treats synthetic board ids as generic hints only`() {
        val suggestions = rankSerialPortSuggestions(
            boardId = "SYNTHETIC_BOARD",
            ports = listOf(
                descriptor(path = "/dev/pts/2", displayName = "Virtual Port", description = "", location = "", vendorId = -1, productId = -1)
            )
        )

        // Synthetic board ids should not introduce hardware-specific keyword boosts.
        assertFalse(suggestions.single().isRecommended)
    }

    @Test
    fun `rank serial port suggestions uses usb heuristics and path priorities`() {
        val suggestions = rankSerialPortSuggestions(
            boardId = null,
            ports = listOf(
                descriptor(path = "/dev/cu.usbserial-1410", displayName = "FTDI Adapter", description = "FTDI Adapter", location = "", vendorId = -1, productId = -1),
                descriptor(path = "/dev/tty.usbserial-1420", displayName = "TTY USB Adapter", description = "TTY USB Adapter", location = "", vendorId = -1, productId = -1),
                descriptor(path = "COM9", displayName = "CP2102 Bridge", description = "CP2102 Bridge", location = "", vendorId = -1, productId = -1),
                descriptor(path = "/opt/devices/usb-probe", displayName = "USB Probe", description = "USB Probe", location = "", vendorId = -1, productId = -1),
                descriptor(path = "/dev/ttyS2", displayName = "Legacy Port", description = "Legacy Port", location = "", vendorId = -1, productId = -1),
                descriptor(path = "/dev/ttyS3", displayName = "CH340 Bridge", description = "CH340 Bridge", location = "", vendorId = -1, productId = -1)
            )
        )
        val byPath = suggestions.associateBy { it.path }

        // Platform-specific path shapes and common bridge-chip names should all be recognized consistently.
        assertEquals("/dev/cu.usbserial-1410", suggestions.first().path)
        assertTrue(byPath.getValue("/dev/cu.usbserial-1410").isUsbDevice)
        assertTrue(byPath.getValue("/dev/tty.usbserial-1420").isUsbDevice)
        assertTrue(byPath.getValue("COM9").isUsbDevice)
        assertTrue(byPath.getValue("/opt/devices/usb-probe").isUsbDevice)
        assertTrue(byPath.getValue("/dev/ttyS3").isUsbDevice)
        assertFalse(byPath.getValue("/dev/ttyS2").isUsbDevice)
    }

    @Test
    fun `rank serial port suggestions recognizes serial acm and uart usb hints`() {
        val suggestions = rankSerialPortSuggestions(
            boardId = null,
            ports = listOf(
                descriptor(path = "/dev/ttyS5", displayName = "Serial Bridge", description = "Serial Bridge", location = "", vendorId = -1, productId = -1),
                descriptor(path = "/dev/ttyACM5", displayName = "Adapter", description = "Adapter", location = "", vendorId = -1, productId = -1),
                descriptor(path = "/dev/ttyS7", displayName = "UART Bridge", description = "UART Bridge", location = "", vendorId = -1, productId = -1),
                descriptor(path = "/dev/ttyS8", displayName = "Half Known Device", description = "Half Known Device", location = "", vendorId = 0x10C4, productId = -1),
                descriptor(path = "/dev/ttyS9", displayName = "FTDI Bridge", description = "FTDI Bridge", location = "", vendorId = -1, productId = -1)
            )
        )
        val byPath = suggestions.associateBy { it.path }

        // Non-USB transports that expose serial, ACM, or UART markers should still be recognized as USB-style devices.
        assertTrue(byPath.getValue("/dev/ttyS5").isUsbDevice)
        assertTrue(byPath.getValue("/dev/ttyACM5").isUsbDevice)
        assertTrue(byPath.getValue("/dev/ttyS7").isUsbDevice)
        assertTrue(byPath.getValue("/dev/ttyS9").isUsbDevice)
        // A partially known VID or PID should not be formatted into a VID:PID details suffix.
        assertEquals("", byPath.getValue("/dev/ttyS8").details)
    }
}
