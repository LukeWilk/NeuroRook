package io.github.lukewilk.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class WaveSpecTest {
    @Test
    fun testWaveSpecEquality() {
        val w1 = WaveSpec(true, WaveType.SINE, 1.0, 10.0, 0.0)
        val w2 = WaveSpec(true, WaveType.SINE, 1.0, 10.0, 0.0)
        val w3 = WaveSpec(false, WaveType.SQUARE, 2.0, 20.0, 1.0)
        assertEquals(w1, w2, "WaveSpecs with same values should be equal")
        assertNotEquals(w1, w3, "WaveSpecs with different values should not be equal")
    }

    @Test
    fun testWaveSpecFields() {
        val ws = WaveSpec(true, WaveType.TRIANGLE, 2.5, 15.0, 0.5)
        assertEquals(true, ws.enabled)
        assertEquals(WaveType.TRIANGLE, ws.type)
        assertEquals(2.5, ws.amplitude)
        assertEquals(15.0, ws.frequencyHz)
        assertEquals(0.5, ws.phaseShiftRad)
    }
}

