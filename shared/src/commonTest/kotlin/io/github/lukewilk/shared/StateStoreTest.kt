package io.github.lukewilk.shared

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StateStoreTest {
    data class TestState(val value: Int = 0, val flag: Boolean = false)

    @Test
    fun `state store exposes the initial snapshot`() {
        // Confirms callers can synchronously read the seed state from both accessors.
        val store = StateStore(TestState(42, true))
        assertEquals(TestState(42, true), store.get())
        assertEquals(TestState(42, true), store.state.value)
    }

    @Test
    fun `update replaces the stored state with reducer output`() {
        // Documents the immutable reducer contract used by view models and presenters.
        val store = StateStore(TestState(1, false))
        store.update { it.copy(value = 2, flag = true) }
        assertEquals(TestState(2, true), store.get())
        assertEquals(TestState(2, true), store.state.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `state flow emits updated snapshots`() = runTest {
        // Verifies observers receive reducer updates through the exposed StateFlow.
        val store = StateStore(TestState(0, false))
        store.update { it.copy(value = 99) }
        val emitted = store.state.first { it.value == 99 }
        assertEquals(99, emitted.value)
    }
}
