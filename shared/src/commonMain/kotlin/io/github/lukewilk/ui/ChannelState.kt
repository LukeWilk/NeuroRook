package io.github.lukewilk.ui

import io.github.lukewilk.shared.HardwareState

data class ChannelState(
    val id: Int,
    val name: String,
    val enabled: Boolean,
    val rld: Boolean,
    val status: String
)

/** Builds shared channel UI rows from the current hardware state so multiple screens stay in sync. */
internal fun channelStatesFor(hardwareState: HardwareState): List<ChannelState> {
    val channelCount = hardwareState.channels.takeIf { it > 0 } ?: 8
    return List(channelCount) { index ->
        ChannelState(
            id = index,
            name = "Channel ${index + 1}",
            enabled = index in hardwareState.enabledChannels,
            rld = index in hardwareState.rldEnabled,
            status = if (index in hardwareState.verifiedChannels) "Configured" else "Not configured"
        )
    }
}
