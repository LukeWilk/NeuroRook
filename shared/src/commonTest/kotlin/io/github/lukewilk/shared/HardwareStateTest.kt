package io.github.lukewilk.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class HardwareStateTest {
    @Test
    fun defaultStateIsDisconnected() {
        val state = HardwareState()
        assertEquals(false, state.connected, "Default connected state should be false")
    }

    @Test
    fun copyWithConnectedTrue() {
        val state = HardwareState()
        val newState = state.copy(connected = true)
        assertEquals(true, newState.connected, "Connected should be true after copy")
        assertNotEquals(state, newState, "States should not be equal after change")
    }

    @Test
    fun equalityAndHashCode() {
        val state1 = HardwareState(connected = true)
        val state2 = HardwareState(connected = true)
        val state3 = HardwareState(connected = false)
        assertEquals(state1, state2, "States with same values should be equal")
        assertEquals(state1.hashCode(), state2.hashCode(), "Hash codes should match for equal states")
        assertNotEquals(state1, state3, "States with different values should not be equal")
    }
}

