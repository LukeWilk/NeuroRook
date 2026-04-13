package io.github.lukewilk.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class WaveTypeTest {
    @Test
    fun `wave type can be reconstructed from serialized names`() {
        // Verifies persisted wave type names still map back to the expected enum constants.
        assertEquals(WaveType.SINE, WaveType.valueOf("SINE"))
        assertEquals(WaveType.SQUARE, WaveType.valueOf("SQUARE"))
        assertEquals(WaveType.SAWTOOTH, WaveType.valueOf("SAWTOOTH"))
        assertEquals(WaveType.TRIANGLE, WaveType.valueOf("TRIANGLE"))
        assertEquals(WaveType.NOISE, WaveType.valueOf("NOISE"))
    }

    @Test
    fun `wave type declaration order remains stable`() {
        // Keeps the presentation order explicit for callers that iterate over the enum constants.
        assertEquals(0, WaveType.SINE.ordinal)
        assertEquals(1, WaveType.SQUARE.ordinal)
        assertEquals(2, WaveType.SAWTOOTH.ordinal)
        assertEquals(3, WaveType.TRIANGLE.ordinal)
        assertEquals(4, WaveType.NOISE.ordinal)
    }
}
