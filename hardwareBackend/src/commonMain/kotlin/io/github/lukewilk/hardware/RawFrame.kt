package io.github.lukewilk.hardware

/**
 * RawFrame represents a single raw sample collected from the board.
 */
data class RawFrame(val timestampMs: Long, val channel: Int, val data: DoubleArray)
