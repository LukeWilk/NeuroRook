package io.github.lukewilk.shared

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Generic, functional, observable state store for multiplatform projects.
 * State is updated only by emitting a new copy (pure function).
 */
class StateStore<T : Any>(initial: T) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<T> = _state

    fun update(reducer: (T) -> T) {
        _state.value = reducer(_state.value)
    }

    fun get(): T = _state.value
}

