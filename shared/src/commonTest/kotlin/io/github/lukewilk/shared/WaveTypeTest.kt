package io.github.lukewilk.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class WaveTypeTest {
    @Test
    fun testWaveTypeEnumValues() {
        assertEquals(WaveType.SINE, WaveType.valueOf("SINE"))
        assertEquals(WaveType.SQUARE, WaveType.valueOf("SQUARE"))
        assertEquals(WaveType.SAWTOOTH, WaveType.valueOf("SAWTOOTH"))
        assertEquals(WaveType.TRIANGLE, WaveType.valueOf("TRIANGLE"))
        assertEquals(WaveType.NOISE, WaveType.valueOf("NOISE"))
    }

    @Test
    fun testWaveTypeEnumOrdinal() {
        assertEquals(0, WaveType.SINE.ordinal)
        assertEquals(1, WaveType.SQUARE.ordinal)
        assertEquals(2, WaveType.SAWTOOTH.ordinal)
        assertEquals(3, WaveType.TRIANGLE.ordinal)
        assertEquals(4, WaveType.NOISE.ordinal)
    }
}

