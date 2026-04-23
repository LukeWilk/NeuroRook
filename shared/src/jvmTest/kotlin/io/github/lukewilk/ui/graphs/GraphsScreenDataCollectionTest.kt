package io.github.lukewilk.ui.graphs

import kotlin.test.Test
import kotlin.test.assertContentEquals

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
}


