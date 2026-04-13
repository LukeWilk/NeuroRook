package io.github.lukewilk.hardware.api

import com.fazecast.jSerialComm.SerialPort
import io.github.lukewilk.shared.model.SerialPortSuggestion
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.mockito.Mockito

/**
 * Shared serial-port test fixtures so ranking and adapter suites reuse the same deterministic descriptors.
 */
internal abstract class SerialPortDiscoveryTestSupport {
    /** Builds a deterministic descriptor fixture for ranking-oriented tests. */
    internal fun descriptor(
        path: String,
        displayName: String,
        description: String = displayName,
        location: String = "usb-1",
        vendorId: Int = 0x10C4,
        productId: Int = 0xEA60,
        isOpen: Boolean = false
    ): SerialPortDescriptor = SerialPortDescriptor(
        path = path,
        systemName = path.substringAfterLast('/'),
        displayName = displayName,
        description = description,
        location = location,
        vendorId = vendorId,
        productId = productId,
        isOpen = isOpen
    )

    /** Builds a mocked jSerialComm port so JVM discovery tests stay independent from local hardware. */
    internal fun mockPort(
        systemPortPath: String,
        systemPortName: String,
        descriptivePortName: String?,
        portDescription: String?,
        portLocation: String?,
        vendorId: Int,
        productId: Int,
        isOpen: Boolean
    ): SerialPort = Mockito.mock(SerialPort::class.java).apply {
        Mockito.`when`(this.systemPortPath).thenReturn(systemPortPath)
        Mockito.`when`(this.systemPortName).thenReturn(systemPortName)
        Mockito.`when`(this.descriptivePortName).thenReturn(descriptivePortName)
        Mockito.`when`(this.portDescription).thenReturn(portDescription)
        Mockito.`when`(this.portLocation).thenReturn(portLocation)
        Mockito.`when`(this.vendorID).thenReturn(vendorId)
        Mockito.`when`(this.productID).thenReturn(productId)
        Mockito.`when`(this.isOpen).thenReturn(isOpen)
    }

    /** Asserts the first suggestion is both first in order and marked as the recommended choice. */
    internal fun assertRecommendedFirstPath(suggestions: List<SerialPortSuggestion>, expectedPath: String) {
        assertEquals(expectedPath, suggestions.first().path)
        assertTrue(suggestions.first().isRecommended)
    }
}

