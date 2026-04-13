package io.github.lukewilk.hardware.api

import com.fazecast.jSerialComm.SerialPort
import io.github.lukewilk.shared.model.SerialPortSuggestion
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.mockito.Mockito

/**
 * Adapter, default-bridge, and API delegation tests for JVM serial-port discovery.
 */
internal class SerialPortDiscoveryAdapterAndApiTest : SerialPortDiscoveryTestSupport() {
    /** Verifies the interface default argument path forwards a null board id to the implementation. */
    @Test
    fun `serial port discovery default board id delegates null`() {
        val expectedSuggestions = listOf(
            SerialPortSuggestion(
                path = "/dev/ttyACM0",
                displayName = "OpenBCI Cyton",
                details = "Location: usb-1",
                isUsbDevice = true,
                isRecommended = true
            )
        )
        val fakeDiscovery = RecordingSerialPortDiscovery(expectedSuggestions)
        val discovery: SerialPortDiscovery = fakeDiscovery

        // Calling the interface with omitted arguments should pass a null board id through the default bridge.
        assertEquals(expectedSuggestions, discovery.getSuggestions())
        assertEquals(null, fakeDiscovery.lastBoardId)
    }

    /** Verifies the generated Kotlin default bridge still reaches the implementation with a null board id. */
    @Test
    fun `serial port discovery default impl bridge delegates omitted board ids`() {
        val expectedSuggestions = listOf(
            SerialPortSuggestion(
                path = "/dev/fallback",
                displayName = "/dev/fallback",
                details = "",
                isUsbDevice = false,
                isRecommended = false
            )
        )
        val fakeDiscovery = RecordingSerialPortDiscovery(expectedSuggestions)
        val defaultImplsClass = Class.forName("io.github.lukewilk.hardware.api.SerialPortDiscovery\$DefaultImpls")
        val defaultMethod = defaultImplsClass.getDeclaredMethod(
            "getSuggestions\$default",
            SerialPortDiscovery::class.java,
            String::class.java,
            Int::class.javaPrimitiveType,
            Any::class.java
        )

        // Calling the generated default bridge reflectively ensures the synthetic helper stays covered too.
        @Suppress("UNCHECKED_CAST")
        val actualSuggestions = defaultMethod.invoke(null, fakeDiscovery, "ignored", 1, null) as List<SerialPortSuggestion>

        assertEquals(expectedSuggestions, actualSuggestions)
        assertEquals(null, fakeDiscovery.lastBoardId)
    }

    /** Verifies the jSerialComm adapter converts real library ports into ranked suggestions deterministically. */
    @Test
    fun `jserialcomm discovery converts ports into ranked suggestions`() {
        val musePort = mockPort(
            systemPortPath = "/dev/ttyACM0",
            systemPortName = "ttyACM0",
            descriptivePortName = "Muse Device",
            portDescription = "USB Serial",
            portLocation = "usb-1",
            vendorId = 0x1234,
            productId = 0x5678,
            isOpen = true
        )
        val fallbackPort = mockPort(
            systemPortPath = "",
            systemPortName = "COM7",
            descriptivePortName = null,
            portDescription = null,
            portLocation = null,
            vendorId = -1,
            productId = -1,
            isOpen = false
        )

        Mockito.mockStatic(SerialPort::class.java).use { mockStatic ->
            // The mocked hardware list lets the test cover descriptor conversion without relying on local devices.
            mockStatic.`when`<Array<SerialPort>> { SerialPort.getCommPorts() }
                .thenReturn(arrayOf(fallbackPort, musePort))

            val suggestions = JSerialCommSerialPortDiscovery().getSuggestions("MUSE_BOARD")

            assertEquals(listOf("/dev/ttyACM0", "COM7"), suggestions.map { it.path })
            assertRecommendedFirstPath(suggestions, "/dev/ttyACM0")
            assertTrue(suggestions.first().isUsbDevice)
            assertEquals("Muse Device", suggestions.first().displayName)
            assertEquals("USB Serial • Location: usb-1 • VID:PID 1234:5678", suggestions.first().details)
            assertEquals("COM7", suggestions.last().displayName)
            assertEquals("", suggestions.last().details)
        }
    }

    /** Verifies the backend API exposes discovery results unchanged and forwards the selected board id. */
    @Test
    fun `hardware backend api delegates serial port suggestions to discovery`() {
        val expectedSuggestions = listOf(
            SerialPortSuggestion(
                path = "/dev/ttyACM0",
                displayName = "OpenBCI Cyton",
                details = "Location: usb-1",
                isUsbDevice = true,
                isRecommended = true
            )
        )
        val fakeDiscovery = RecordingSerialPortDiscovery(expectedSuggestions)
        val api = HardwareBackendApi(serialPortDiscovery = fakeDiscovery)

        val actualSuggestions = api.getSerialPortSuggestions("CYTON_BOARD")

        assertEquals("CYTON_BOARD", fakeDiscovery.lastBoardId)
        assertEquals(expectedSuggestions, actualSuggestions)
    }
}

/** Records the last requested board id so delegation tests can assert the selection context. */
private class RecordingSerialPortDiscovery(
    private val suggestions: List<SerialPortSuggestion>
) : SerialPortDiscovery {
    var lastBoardId: String? = null

    override fun getSuggestions(boardId: String?): List<SerialPortSuggestion> {
        // Record the board id so the test can verify the API passes selection context through.
        lastBoardId = boardId
        return suggestions
    }
}


