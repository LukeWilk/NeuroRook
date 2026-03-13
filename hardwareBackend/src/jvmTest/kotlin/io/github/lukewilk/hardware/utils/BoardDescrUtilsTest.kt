package io.github.lukewilk.hardware.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BoardDescrUtilsTest {

    @Test
    fun testExtractBoardDescrHasExpectedStructure() {
        val descr = BoardDescrUtils.extractBoardDescr(null)
        // Name should be present and a string
        val name = descr["name"]
        assertTrue(name is String, "name should be a String")

        // sampling_rate should be numeric and positive when present
        val sr = descr["sampling_rate"]
        assertTrue(sr == null || sr is Number, "sampling_rate should be a Number or absent")
        if (sr is Number) assertTrue(sr.toDouble() > 0.0, "sampling_rate should be > 0")

        // eeg_channels should coerce to a non-empty int list for synthetic fallback
        val eeg = BoardDescrUtils.asIntList(descr, "eeg_channels")
        assertTrue(eeg is List<*>, "asIntList should return a List")
        assertTrue(eeg.isNotEmpty(), "eeg_channels should not be empty in fallback descriptor")
    }

    @Test
    fun testAsIntListHandlesVariousTypes() {
        // List<Number>
        val m1 = mapOf<String, Any?>("a" to listOf(1, 2L, 3.0))
        val r1 = BoardDescrUtils.asIntList(m1, "a")
        assertEquals(listOf(1, 2, 3), r1)

        // IntArray
        val m2 = mapOf<String, Any?>("b" to intArrayOf(4, 5))
        val r2 = BoardDescrUtils.asIntList(m2, "b")
        assertEquals(listOf(4, 5), r2)

        // Mixed / invalid entries are dropped
        val m3 = mapOf<String, Any?>("c" to listOf<Any?>(1, "x", 2.7))
        val r3 = BoardDescrUtils.asIntList(m3, "c")
        assertEquals(listOf(1, 2), r3)

        // Missing key -> empty
        val m4 = mapOf<String, Any?>(
        )
        val r4 = BoardDescrUtils.asIntList(m4, "missing")
        assertTrue(r4.isEmpty())
    }

    @Test
    fun testForceFallbackPathProducesSyntheticDescriptor() {
        val descr = BoardDescrUtils.extractBoardDescr(null, forceFallback = true)
        assertEquals("Synthetic", descr["name"])
        assertEquals(250, (descr["sampling_rate"] as? Number)?.toInt())
        val eeg = BoardDescrUtils.asIntList(descr, "eeg_channels")
        assertTrue(eeg.size >= 1)
        assertEquals("Fz,C3,Cz,C4,Pz,PO7,Oz,PO8,F5,F7,F3,F1,F2,F4,F6,F8", descr["eeg_names"])
    }
}
