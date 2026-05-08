package io.github.lukewilk.ui.graphs

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JVM tests for Graphs-specific filtered-history collection helpers.
 */
class GraphsScreenDataCollectionTest {
    @Test
    fun `filtered history keeps the first full payload before scrolling`() {
        // Verifies the first received filtered window seeds the whole visible history in sample order.
        val history = updatedFilteredSignalHistory(
            previousSamples = null,
            incomingSamples = doubleArrayOf(1.0, 2.0, 3.0, 4.0),
            filteredHistorySize = 4,
            filteredOverlap = 2
        )

        assertContentEquals(doubleArrayOf(1.0, 2.0, 3.0, 4.0), history)
    }

    @Test
    fun `filtered history appends only new tail samples from overlapping windows`() {
        // Verifies previously displayed samples keep their original values while the newest tail scrolls in on the right.
        val history = updatedFilteredSignalHistory(
            previousSamples = doubleArrayOf(1.0, 2.0, 3.0, 4.0),
            incomingSamples = doubleArrayOf(3.0, 4.0, 5.0, 6.0),
            filteredHistorySize = 4,
            filteredOverlap = 2
        )

        assertContentEquals(doubleArrayOf(3.0, 4.0, 5.0, 6.0), history)
    }

    @Test
    fun `filtered history trims to the configured display size while preserving newest samples`() {
        // Verifies repeated updates keep the latest rendered samples on screen and discard only the oldest history.
        val history = updatedFilteredSignalHistory(
            previousSamples = doubleArrayOf(1.0, 2.0, 3.0, 4.0),
            incomingSamples = doubleArrayOf(5.0, 6.0, 7.0, 8.0),
            filteredHistorySize = 5,
            filteredOverlap = 3
        )

        assertContentEquals(doubleArrayOf(1.0, 2.0, 3.0, 4.0, 8.0), history)
    }

    @Test
    fun `filtered history keeps previous data unchanged when incoming payload is fully overlapped`() {
        // Verifies repeated full-window payloads do not force-append duplicate seam samples.
        val history = updatedFilteredSignalHistory(
            previousSamples = doubleArrayOf(1.0, 2.0, 3.0, 4.0),
            incomingSamples = doubleArrayOf(1.0, 2.0, 3.0, 4.0),
            filteredHistorySize = 4,
            filteredOverlap = 4
        )

        assertContentEquals(doubleArrayOf(1.0, 2.0, 3.0, 4.0), history)
    }

    @Test
    fun `filtered history honors overlap despite tiny floating point drift`() {
        // Verifies overlap matching remains stable when boundary samples differ only by numeric jitter.
        val history = updatedFilteredSignalHistory(
            previousSamples = doubleArrayOf(1.0, 2.0, 3.0, 4.0),
            incomingSamples = doubleArrayOf(3.000001, 4.000001, 5.0, 6.0),
            filteredHistorySize = 6,
            filteredOverlap = 2
        )

        assertContentEquals(doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0), history)
    }

    @Test
    fun `filtered history blends seam jump for long retained windows`() {
        // Verifies large boundary jumps are softened when the retained history is much longer than one payload.
        val history = updatedFilteredSignalHistory(
            previousSamples = doubleArrayOf(0.0, 1.0, 2.0, 3.0),
            incomingSamples = doubleArrayOf(10.0, 11.0, 12.0, 13.0),
            filteredHistorySize = 12,
            filteredOverlap = 2
        )

        assertEquals(6, history.size)
        val rawSeamJump = kotlin.math.abs(3.0 - 12.0)
        val blendedSeamJump = kotlin.math.abs(history[3] - history[4])
        assertTrue(
            blendedSeamJump < rawSeamJump,
            "Expected seam jump to shrink after blending: raw=$rawSeamJump blended=$blendedSeamJump"
        )
    }

    @Test
    fun `resolveFilteredHistorySize enforces the configured minimum duration`() {
        // At 256 Hz and a 5-second minimum, the DSP window (256) is raised to 1 280 samples.
        assertEquals(
            1280,
            resolveFilteredHistorySize(
                requestedHistorySize = 256,
                samplingRateHz = 256,
                minimumDurationSeconds = 5
            )
        )

        // A caller that explicitly requests a larger window should keep their larger value.
        assertEquals(
            2000,
            resolveFilteredHistorySize(
                requestedHistorySize = 2000,
                samplingRateHz = 256,
                minimumDurationSeconds = 5
            )
        )

        // A shorter minimum duration selection should allow shorter histories when desired.
        assertEquals(
            256,
            resolveFilteredHistorySize(
                requestedHistorySize = 256,
                samplingRateHz = 256,
                minimumDurationSeconds = 1
            )
        )

        // When the sampling rate is unknown (0), the result is clamped to at least the requested size.
        assertEquals(
            256,
            resolveFilteredHistorySize(
                requestedHistorySize = 256,
                samplingRateHz = 0,
                minimumDurationSeconds = 5
            )
        )
    }
}


