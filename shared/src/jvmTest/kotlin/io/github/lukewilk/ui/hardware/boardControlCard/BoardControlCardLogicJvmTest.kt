package io.github.lukewilk.ui.hardware.boardControlCard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoardControlCardLogicJvmTest {

    @Test
    fun `channelTableRowUiState covers stripe divider and configured branches`() {
        val first = channelTableRowUiState(index = 0, lastIndex = 1, status = "Configured")
        assertTrue(first.usesEvenBackground)
        assertTrue(first.showDivider)
        assertTrue(first.isConfigured)

        val second = channelTableRowUiState(index = 1, lastIndex = 1, status = "Not configured")
        assertFalse(second.usesEvenBackground)
        assertFalse(second.showDivider)
        assertFalse(second.isConfigured)

        val onlyRow = channelTableRowUiState(index = 0, lastIndex = 0, status = "Configured")
        assertFalse(onlyRow.showDivider)

        val middleOddStripe = channelTableRowUiState(index = 1, lastIndex = 2, status = "Idle")
        assertFalse(middleOddStripe.usesEvenBackground)
        assertTrue(middleOddStripe.showDivider)
        assertFalse(middleOddStripe.isConfigured)
    }

    @Test
    fun `channelStatusTone maps configured flag to enum variants`() {
        assertEquals(ChannelStatusTone.CONFIGURED, channelStatusTone(isConfigured = true))
        assertEquals(ChannelStatusTone.UNCONFIGURED, channelStatusTone(isConfigured = false))
    }

    @Test
    fun `boardControlUiState covers connection streaming and busy branches`() {
        val disconnected = boardControlUiState(
            availableBoards = listOf("B"),
            selectedBoard = 0,
            isConnected = false,
            isStreaming = false,
            isBusy = false
        )
        assertEquals("B Control", disconnected.title)
        assertFalse(disconnected.channelsEnabled)
        assertFalse(disconnected.startStreamEnabled)
        assertFalse(disconnected.stopStreamEnabled)
        assertFalse(disconnected.showStreamingIndicator)

        val live = boardControlUiState(
            availableBoards = listOf("B"),
            selectedBoard = 0,
            isConnected = true,
            isStreaming = false,
            isBusy = false
        )
        assertTrue(live.channelsEnabled)
        assertTrue(live.verifyChannelsEnabled)
        assertTrue(live.startStreamEnabled)
        assertFalse(live.stopStreamEnabled)

        val streaming = boardControlUiState(
            availableBoards = listOf("B"),
            selectedBoard = 0,
            isConnected = true,
            isStreaming = true,
            isBusy = false
        )
        assertFalse(streaming.startStreamEnabled)
        assertTrue(streaming.stopStreamEnabled)
        assertTrue(streaming.showStreamingIndicator)

        val busy = boardControlUiState(
            availableBoards = listOf("B"),
            selectedBoard = 0,
            isConnected = true,
            isStreaming = false,
            isBusy = true
        )
        assertFalse(busy.channelsEnabled)
        assertFalse(busy.verifyChannelsEnabled)
        assertFalse(busy.startStreamEnabled)

        val missingBoard = boardControlUiState(
            availableBoards = emptyList(),
            selectedBoard = 2,
            isConnected = false,
            isStreaming = false,
            isBusy = false
        )
        assertEquals("Board Control", missingBoard.title)
    }
}
