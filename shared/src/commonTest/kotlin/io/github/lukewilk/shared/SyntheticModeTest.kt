package io.github.lukewilk.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SyntheticModeTest {
    @Test
    fun testSyntheticModeEnumValues() {
        assertEquals(SyntheticMode.SYNTHETIC_EEG_SIGNAL, SyntheticMode.valueOf("SYNTHETIC_EEG_SIGNAL"))
        assertEquals(SyntheticMode.WAVE_GENERATOR, SyntheticMode.valueOf("WAVE_GENERATOR"))
    }

    @Test
    fun testSyntheticModeEnumOrdinal() {
        assertEquals(0, SyntheticMode.SYNTHETIC_EEG_SIGNAL.ordinal)
        assertEquals(1, SyntheticMode.WAVE_GENERATOR.ordinal)
    }
}

