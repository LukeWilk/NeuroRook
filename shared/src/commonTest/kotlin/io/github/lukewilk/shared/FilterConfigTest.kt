package io.github.lukewilk.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class FilterConfigTest {
    @Test
    fun testDefaultFilterConfig() {
        val config = FilterConfig(null, emptyList())
        assertNull(config.bandpass, "Default bandpass should be null")
        assertEquals(0, config.bandstopFilters.size, "Default bandstopFilters should be empty")
    }

    @Test
    fun testBandpassConfigEquality() {
        val bp1 = BandpassConfig(1.0, 2.0, 2, 100, 0, 0.1)
        val bp2 = BandpassConfig(1.0, 2.0, 2, 100, 0, 0.1)
        val bp3 = BandpassConfig(2.0, 3.0, 2, 100, 0, 0.1)
        assertEquals(bp1, bp2, "Bandpass configs with same values should be equal")
        assertNotEquals(bp1, bp3, "Bandpass configs with different values should not be equal")
    }

    @Test
    fun testBandstopConfigEquality() {
        val bs1 = BandstopConfig(1.0, 2.0, 2, 100, 0, 0.1)
        val bs2 = BandstopConfig(1.0, 2.0, 2, 100, 0, 0.1)
        val bs3 = BandstopConfig(2.0, 3.0, 2, 100, 0, 0.1)
        assertEquals(bs1, bs2, "Bandstop configs with same values should be equal")
        assertNotEquals(bs1, bs3, "Bandstop configs with different values should not be equal")
    }

    @Test
    fun testFilterConfigWithBandpassAndBandstop() {
        val bp = BandpassConfig(1.0, 2.0, 2, 100, 0, 0.1)
        val bs = BandstopConfig(1.0, 2.0, 2, 100, 0, 0.1)
        val config = FilterConfig(bp, listOf(bs))
        assertEquals(bp, config.bandpass, "Bandpass should match")
        assertEquals(1, config.bandstopFilters.size, "Should have one bandstop filter")
        assertEquals(bs, config.bandstopFilters[0], "Bandstop filter should match")
    }
}

