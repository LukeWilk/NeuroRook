package io.github.lukewilk.hardware.devInfo

import brainflow.BoardIds
import co.touchlab.kermit.Logger
import io.github.lukewilk.hardware.BoardConnectionManager
import io.github.lukewilk.hardware.utils.BoardDescrUtils
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Developer example test that demonstrates how to obtain board metadata and
 * sample synthetic data using the project's `BoardConnectionManager` and
 * `BoardDescrUtils` helpers.
 *
 * This test is intentionally exploratory: it connects to the synthetic
 * BrainFlow board, logs representative descriptor fields (sampling rate,
 * timestamp channel, EEG channel indices and names), and shows how to
 * request a small number of synthetic samples using
 * `BoardConnectionManager.generateSyntheticData`.
 *
 * It can be used as a reference for developers who want example values and
 * a minimal recipe for retrieving board information without relying on
 * external hardware.
 */
class InfoSyntheticBoardTest {
    private val logger = Logger.withTag("InfoSyntheticBoardTest")

    val stateStore = StateStore(HardwareState())

    /** Short example: log basic descriptor fields and a few synthetic samples. */
    @Test
    fun infoSyntheticBoardMethods() = runBlocking {
        val manager = BoardConnectionManager(stateStore)
        try {
            val connected = manager.connect(BoardIds.SYNTHETIC_BOARD, "")
            assertTrue(connected, "Should connect to synthetic board")
            val boardDescr = BoardDescrUtils.extractBoardDescr(null)
            val samplingRateFromDescr = (boardDescr["sampling_rate"] as? Number)?.toInt()
            val timestampChannelFromDescr = (boardDescr["timestamp_channel"] as? Number)?.toInt()
            val eegChannelsFromDescr = BoardDescrUtils.asIntList(boardDescr, "eeg_channels")
            val eegNames = boardDescr["eeg_names"] as? String

            // Generate a small number of synthetic samples via manager (safe, no native calls)
            val gen = manager.generateSyntheticData(10)

            logger.i { "--- Synthetic Board Info ---" }
            logger.i { "Sampling rate: $samplingRateFromDescr" }
            logger.i { "Timestamp channel: $timestampChannelFromDescr" }
            logger.i { "EEG channels (descr): $eegChannelsFromDescr" }
            logger.i { "EEG names: $eegNames" }
            logger.i { "synthetic get_board_data(10) (first 3 per channel): ${gen.map { it.take(3) }}" }
            logger.i { "--- End Synthetic Board Info ---" }
        } finally {
            manager.close()
        }
    }
}