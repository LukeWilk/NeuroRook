package io.github.lukewilk.hardware

import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import io.github.lukewilk.shared.SyntheticMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for private helpers on BoardConnectionManager accessed via reflection when mocking native
 * board behavior is undesirable. These ensure connect-time defaults follow the expected policy.
 */
class BoardConnectionManagerPrivateTest {
    @Test
    fun `applySyntheticDevWaveDefaults seeds wave spec and leaves enabledChannels unchanged`() {
        val store = StateStore(HardwareState(channels = 16))
        val mgr = BoardConnectionManager(store)

        // use reflection to call private method applySyntheticDevWaveDefaults
        val method = mgr.javaClass.getDeclaredMethod("applySyntheticDevWaveDefaults", HardwareState::class.java, java.lang.Boolean.TYPE)
        method.isAccessible = true

        val inputState = HardwareState(channels = 16, waveSpecs = emptyList(), enabledChannels = emptyList(), syntheticMode = SyntheticMode.SYNTHETIC_EEG_SIGNAL)
        val result = method.invoke(mgr, inputState, java.lang.Boolean.TRUE) as HardwareState

        // Expect synthetic mode switched to WAVE_GENERATOR and a default wave spec seeded
        assertEquals(SyntheticMode.WAVE_GENERATOR, result.syntheticMode)
        assertTrue(result.waveSpecs.isNotEmpty(), "Expected a default synthetic wave to be seeded")

        // Channels should remain disabled by default (empty list)
        assertEquals(emptyList<Int>(), result.enabledChannels)
    }
}

