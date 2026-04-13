package io.github.lukewilk.shared

import kotlin.test.Test
import kotlin.test.assertEquals
class SyntheticModeTest {
    @Test
    fun `synthetic mode can be resolved from persisted names`() {
        // Guards the enum names that are stored and restored across layers.
        assertEquals(SyntheticMode.SYNTHETIC_EEG_SIGNAL, SyntheticMode.valueOf("SYNTHETIC_EEG_SIGNAL"))
        assertEquals(SyntheticMode.WAVE_GENERATOR, SyntheticMode.valueOf("WAVE_GENERATOR"))
    }

    @Test
    fun `synthetic mode order remains stable`() {
        // Keeps the enum ordering explicit for callers that present modes in declaration order.
        assertEquals(0, SyntheticMode.SYNTHETIC_EEG_SIGNAL.ordinal)
        assertEquals(1, SyntheticMode.WAVE_GENERATOR.ordinal)
    }
}
