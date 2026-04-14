package io.github.lukewilk.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class BandTest {
    @Test
    fun `band equality stays value based`() {
        // Confirms Band can be compared safely in state snapshots and reducer assertions.
        val b1 = Band("Alpha", 8.0, 12.0)
        val b2 = Band("Alpha", 8.0, 12.0)
        val b3 = Band("Beta", 12.0, 30.0)
        assertEquals(b1, b2, "Bands with same values should be equal")
        assertNotEquals(b1, b3, "Bands with different values should not be equal")
    }

    @Test
    fun `band exposes the configured frequency range`() {
        // Documents the mapping between constructor arguments and the public fields used by the UI.
        val band = Band("Theta", 4.0, 8.0)
        assertEquals("Theta", band.name)
        assertEquals(4.0, band.lowHz)
        assertEquals(8.0, band.highHz)
    }
}
