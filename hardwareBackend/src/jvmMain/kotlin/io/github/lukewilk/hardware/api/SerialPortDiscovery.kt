package io.github.lukewilk.hardware.api

import com.fazecast.jSerialComm.SerialPort
import io.github.lukewilk.shared.model.SerialPortSuggestion
import java.util.Locale

/**
 * Enumerates currently available serial devices and ranks the most likely USB board connection first.
 */
interface SerialPortDiscovery {
    fun getSuggestions(boardId: String? = null): List<SerialPortSuggestion>
}

/**
 * JVM serial-port discovery backed by jSerialComm so Linux, macOS, and Windows devices can be listed.
 */
internal class JSerialCommSerialPortDiscovery : SerialPortDiscovery {
    override fun getSuggestions(boardId: String?): List<SerialPortSuggestion> = rankSerialPortSuggestions(
        boardId = boardId,
        ports = SerialPort.getCommPorts().map(::toSerialPortDescriptor)
    )
}

/**
 * Lightweight serial-port snapshot used for deterministic ranking tests.
 */
internal data class SerialPortDescriptor(
    val path: String,
    val systemName: String = path,
    val displayName: String = "",
    val description: String = "",
    val location: String = "",
    val vendorId: Int = -1,
    val productId: Int = -1,
    val isOpen: Boolean = false
)

internal fun rankSerialPortSuggestions(
    boardId: String?,
    ports: List<SerialPortDescriptor>
): List<SerialPortSuggestion> {
    val boardKeywords = boardKeywordsFor(boardId)
    return ports
        .distinctBy { it.path }
        .filter { it.path.isNotBlank() }
        .map { descriptor ->
            val score = scoreSerialPort(descriptor, boardKeywords)
            RankedSerialPortSuggestion(
                suggestion = SerialPortSuggestion(
                    path = descriptor.path,
                    displayName = descriptor.displayName.ifBlank {
                        descriptor.systemName.ifBlank { descriptor.path }
                    },
                    details = serialPortDetailsFor(descriptor),
                    isUsbDevice = isUsbSerialDevice(descriptor)
                ),
                score = score
            )
        }
        .sortedWith(::compareRankedSuggestions)
        .mapIndexed { index, ranked ->
            ranked.suggestion.copy(isRecommended = index == 0 && ranked.score > 0)
        }
}

private data class RankedSerialPortSuggestion(
    val suggestion: SerialPortSuggestion,
    val score: Int
)

private fun compareRankedSuggestions(
    left: RankedSerialPortSuggestion,
    right: RankedSerialPortSuggestion
): Int {
    val scoreComparison = right.score.compareTo(left.score)
    if (scoreComparison != 0) return scoreComparison

    return left.suggestion.path.lowercase(Locale.US)
        .compareTo(right.suggestion.path.lowercase(Locale.US))
}

private fun toSerialPortDescriptor(port: SerialPort): SerialPortDescriptor = SerialPortDescriptor(
    path = port.systemPortPath.takeIf { it.isNotBlank() } ?: port.systemPortName,
    systemName = port.systemPortName,
    displayName = port.descriptivePortName.orEmpty(),
    description = port.portDescription.orEmpty(),
    location = port.portLocation.orEmpty(),
    vendorId = port.vendorID,
    productId = port.productID,
    isOpen = port.isOpen
)

private fun scoreSerialPort(
    descriptor: SerialPortDescriptor,
    boardKeywords: Set<String>
): Int {
    val searchableText = listOf(
        descriptor.path,
        descriptor.systemName,
        descriptor.displayName,
        descriptor.description,
        descriptor.location,
        descriptor.vendorId.takeIf { it >= 0 }?.toString().orEmpty(),
        descriptor.productId.takeIf { it >= 0 }?.toString().orEmpty()
    ).joinToString(" ").lowercase(Locale.US)

    val boardMatchBoost = if (boardKeywords.any(searchableText::contains)) 80 else 0
    val usbBoost = if (isUsbSerialDevice(descriptor)) 40 else 0
    val activeBoost = if (descriptor.isOpen) 5 else 0

    return boardMatchBoost + usbBoost + activeBoost + pathPriority(descriptor.path)
}

private fun boardKeywordsFor(boardId: String?): Set<String> {
    if (boardId.isNullOrBlank()) return emptySet()
    val normalizedBoardId = boardId.uppercase(Locale.US)
    if (normalizedBoardId == "SYNTHETIC_BOARD") return emptySet()

    val keywords = normalizedBoardId
        .removeSuffix("_BOARD")
        .split('_')
        .filter { it.length > 2 }
        .mapTo(linkedSetOf()) { it.lowercase(Locale.US) }

    when {
        normalizedBoardId.contains("CYTON") || normalizedBoardId.contains("GANGLION") -> {
            keywords += setOf("openbci", "cyton", "ganglion")
        }

        normalizedBoardId.contains("FREEEEG") -> {
            keywords += setOf("freeeeg", "free eeg")
        }

        normalizedBoardId.contains("MUSE") -> {
            keywords += "muse"
        }

        normalizedBoardId.contains("UNICORN") -> {
            keywords += "unicorn"
        }

        normalizedBoardId.contains("CALLIBRI") -> {
            keywords += "callibri"
        }
    }

    return keywords
}

private fun serialPortDetailsFor(descriptor: SerialPortDescriptor): String = buildList {
    descriptor.description
        .takeIf { it.isNotBlank() && !it.equals(descriptor.displayName, ignoreCase = true) }
        ?.let(::add)
    descriptor.location.takeIf { it.isNotBlank() }?.let { add("Location: $it") }
    if (descriptor.vendorId >= 0 && descriptor.productId >= 0) {
        add("VID:PID ${descriptor.vendorId.toHexId()}:${descriptor.productId.toHexId()}")
    }
}.joinToString(" • ")

private fun isUsbSerialDevice(descriptor: SerialPortDescriptor): Boolean {
    val searchableText = listOf(
        descriptor.path,
        descriptor.systemName,
        descriptor.displayName,
        descriptor.description,
        descriptor.location
    ).joinToString(" ").lowercase(Locale.US)

    return descriptor.vendorId >= 0 ||
        searchableText.contains("usb") ||
        searchableText.contains("serial") ||
        searchableText.contains("acm") ||
        searchableText.contains("uart") ||
        searchableText.contains("cp210") ||
        searchableText.contains("ch340") ||
        searchableText.contains("ftdi")
}

private fun pathPriority(path: String): Int {
    val normalizedPath = path.lowercase(Locale.US)
    return when {
        normalizedPath.contains("/dev/ttyacm") -> 35
        normalizedPath.contains("/dev/ttyusb") -> 30
        normalizedPath.contains("/dev/cu.usb") || normalizedPath.contains("/dev/tty.usb") -> 28
        Regex("^com\\d+$", RegexOption.IGNORE_CASE).matches(path) -> 24
        normalizedPath.contains("usb") -> 18
        else -> 0
    }
}

private fun Int.toHexId(): String = toString(16).uppercase(Locale.US).padStart(4, '0')


