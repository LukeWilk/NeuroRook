package io.github.lukewilk.hardware.synthetic

import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * Shared waveform test helpers so shape and phase suites compare generated samples consistently.
 */
internal abstract class WaveGeneratorTestSupport {
    /** Asserts every sample pair stays within the floating-point tolerance used by synthetic signal tests. */
    protected fun assertSamplesClose(actual: DoubleArray, expected: DoubleArray, tolerance: Double = 1e-12) {
        assertTrue(
            actual.size == expected.size,
            "Expected arrays of equal size but found ${actual.size} and ${expected.size}"
        )
        for (index in actual.indices) {
            assertTrue(
                abs(actual[index] - expected[index]) < tolerance,
                "Sample $index differs: ${actual[index]} vs ${expected[index]}"
            )
        }
    }

    /** Asserts two generated blocks represent the same signal sample-by-sample within tolerance. */
    protected fun assertSamplesClose(actual: DoubleArray, expected: DoubleArray, label: String, tolerance: Double = 1e-12) {
        assertTrue(
            actual.size == expected.size,
            "$label size differs: ${actual.size} vs ${expected.size}"
        )
        for (index in actual.indices) {
            assertTrue(
                abs(actual[index] - expected[index]) < tolerance,
                "$label sample $index differs: ${actual[index]} vs ${expected[index]}"
            )
        }
    }

    /** Concatenates sequential blocks so continuity tests can compare them against one combined generation. */
    protected fun concatenateBlocks(first: DoubleArray, second: DoubleArray): DoubleArray = DoubleArray(first.size + second.size).also {
        first.copyInto(it, startIndex = 0)
        second.copyInto(it, destinationOffset = first.size)
    }
}

