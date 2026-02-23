package io.github.lukewilk.shared

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StateStoreTest {
    data class TestState(val value: Int = 0, val flag: Boolean = false)

    @Test
    fun initialStateIsCorrect() {
        val store = StateStore(TestState(42, true))
        assertEquals(TestState(42, true), store.get())
        assertEquals(TestState(42, true), store.state.value)
    }

    @Test
    fun updateEmitsNewState() {
        val store = StateStore(TestState(1, false))
        store.update { it.copy(value = 2, flag = true) }
        assertEquals(TestState(2, true), store.get())
        assertEquals(TestState(2, true), store.state.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun stateFlowEmitsUpdates() = runTest {
        val store = StateStore(TestState(0, false))
        store.update { it.copy(value = 99) }
        val emitted = store.state.first { it.value == 99 }
        assertEquals(99, emitted.value)
    }
}

