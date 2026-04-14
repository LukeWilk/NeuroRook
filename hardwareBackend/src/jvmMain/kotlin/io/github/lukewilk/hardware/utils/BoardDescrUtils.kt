package io.github.lukewilk.hardware.utils

import brainflow.BoardIds
import brainflow.BoardShim
import io.github.lukewilk.shared.logging.LoggerProvider

/**
 * Utilities for working with BrainFlow board descriptions.
 *
 * This singleton provides safe helpers to extract board descriptor information
 * from BrainFlow's native shim and to coerce frequently-used board descriptor
 * fields into Kotlin types.
 */
object BoardDescrUtils {
    private val logger = LoggerProvider.getLogger("BoardDescrUtils")

    /**
     * Try to obtain the BrainFlow board descriptor for the given [boardShim].
     *
     * This calls into BrainFlow's `BoardShim.get_board_descr` and returns the
     * resulting map when available. If any throwable is thrown (including
     * native Errors), a safe fallback descriptor for the synthetic board is
     * returned instead. The fallback is conservative and contains typical
     * fields used by the project (channel lists, names, sampling rate, etc.).
     *
     * The additional [forceFallback] flag is provided strictly for testing to
     * simulate native call failures and exercise the fallback path. It is
     * false by default and should not be used in production code.
     *
     * @param boardShim optional BrainFlow shim instance (not currently used but
     *                  kept for symmetry with potential future needs).
     * @param forceFallback when true throw deliberately to exercise fallback (test-only)
     * @return a map describing the board. Keys mirror BrainFlow's JSON keys.
     */
    @Suppress("UNUSED_PARAMETER")
    fun extractBoardDescr(boardShim: BoardShim?, forceFallback: Boolean = false): Map<String, Any?> {
        return try {
            if (forceFallback) throw RuntimeException("forced fallback for tests")
            @Suppress("UNCHECKED_CAST")
            BoardShim.get_board_descr(Map::class.java as Class<Map<String, Any?>>, BoardIds.SYNTHETIC_BOARD) as Map<String, Any?>
        } catch (t: Throwable) {
            // Catch Throwable to handle Errors like StackOverflowError from native calls
            logger.e(t) { "Failed to get board_descr via BrainFlow: ${t.message}. Falling back to defaults for synthetic board." }
            // Provide a safe fallback based on BrainFlow synthetic board known description
            val eegNames = "Fz,C3,Cz,C4,Pz,PO7,Oz,PO8,F5,F7,F3,F1,F2,F4,F6,F8"
            mapOf<String, Any?>(
                "name" to "Synthetic",
                "num_rows" to 32,
                "sampling_rate" to 250,
                "timestamp_channel" to 30,
                "eeg_channels" to (1..16).toList(),
                "eeg_names" to eegNames,
                "gyro_channels" to (20..22).toList(),
                "accel_channels" to (17..19).toList(),
                "ecg_channels" to (1..16).toList(),
                "emg_channels" to (1..16).toList(),
                "eog_channels" to (1..16).toList(),
                "ppg_channels" to listOf(24, 25),
                "eda_channels" to listOf(23),
                "resistance_channels" to listOf(27, 28),
                "battery_channel" to 29,
                "temperature_channels" to listOf(26),
                "package_num_channel" to 0,
                "marker_channel" to 31
            )
        }
    }

    /**
     * Coerce a descriptor field into a list of integers.
     *
     * BrainFlow descriptors sometimes store channel lists as different types
     * (List<Number>, IntArray, etc.). This helper normalizes those common
     * representations into a Kotlin List<Int>. Any values that cannot be
     * converted to integers are dropped.
     *
     * @param boardDescr the descriptor map returned by [extractBoardDescr]
     * @param key the descriptor key to extract (for example "eeg_channels")
     * @return a list of integers for the requested key, or an empty list if the
     * key is absent or cannot be converted.
     */
    fun asIntList(boardDescr: Map<String, Any?>, key: String): List<Int> {
        val raw = boardDescr[key]
        return when (raw) {
            is List<*> -> raw.mapNotNull { (it as? Number)?.toInt() }
            is IntArray -> raw.map { it }
            else -> emptyList()
        }
    }
}