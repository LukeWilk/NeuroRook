package io.github.lukewilk.hardware

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RawFrameTest {
    @Test
    fun `raw frame fields can be compared by value even when payload uses arrays`() {
        // Documents the equality caveat for DoubleArray-backed data classes used in acquisition tests.
        val arr1 = doubleArrayOf(1.0, 2.0)
        val arr2 = doubleArrayOf(1.0, 2.0)
        val arr3 = doubleArrayOf(3.0, 4.0)
        val rf1 = RawFrame(123L, 0, arr1)
        val rf2 = RawFrame(123L, 0, arr2)
        val rf3 = RawFrame(124L, 1, arr3)
        // Data class equality will fail for arrays, so check fields
        assertEquals(rf1.timestampMs, rf2.timestampMs)
        assertEquals(rf1.channel, rf2.channel)
        assertTrue(rf1.data.contentEquals(rf2.data), "RawFrame data arrays should be content-equal")
        assertNotEquals(rf1.timestampMs, rf3.timestampMs)
        assertNotEquals(rf1.channel, rf3.channel)
        assertTrue(!rf1.data.contentEquals(rf3.data), "RawFrame data arrays should not be content-equal")
    }

    @Test
    fun `raw frame exposes the supplied timestamp channel and payload`() {
        // Confirms constructor arguments are preserved when frames move through the pipeline.
        val arr = doubleArrayOf(5.0, 6.0)
        val rf = RawFrame(999L, 2, arr)
        assertEquals(999L, rf.timestampMs)
        assertEquals(2, rf.channel)
        assertTrue(rf.data.contentEquals(arr))
    }
}
