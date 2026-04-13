package io.github.lukewilk.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class HardwareStateTest {
    @Test
    fun `default hardware state starts disconnected`() {
        // Protects the initial UI state before any backend connection attempt has been made.
        val state = HardwareState()
        assertEquals(false, state.connected, "Default connected state should be false")
    }

    @Test
    fun `copy can flip the connected flag without mutating the original`() {
        // Demonstrates the immutable update pattern used by reducers and view models.
        val state = HardwareState()
        val newState = state.copy(connected = true)
        assertEquals(true, newState.connected, "Connected should be true after copy")
        assertNotEquals(state, newState, "States should not be equal after change")
    }

    @Test
    fun `equal hardware states share equality and hash code`() {
        // Keeps HardwareState safe to use in caching, snapshot comparison, and collection lookups.
        val state1 = HardwareState(connected = true)
        val state2 = HardwareState(connected = true)
        val state3 = HardwareState(connected = false)
        assertEquals(state1, state2, "States with same values should be equal")
        assertEquals(state1.hashCode(), state2.hashCode(), "Hash codes should match for equal states")
        assertNotEquals(state1, state3, "States with different values should not be equal")
    }

    @Test
    fun `sampling rate is preserved for synthetic and hardware configurations`() {
        // Documents that the same state object can represent either synthetic or physical devices.
        val stateSynthetic = HardwareState(connected = true, synthetic = true, samplingRateHz = 120)
        assertEquals(120, stateSynthetic.samplingRateHz, "Synthetic board should have 120Hz sampling rate")
        val stateHardware = HardwareState(connected = true, synthetic = false, samplingRateHz = 256)
        assertEquals(256, stateHardware.samplingRateHz, "Hardware board should have correct sampling rate")
    }
}
