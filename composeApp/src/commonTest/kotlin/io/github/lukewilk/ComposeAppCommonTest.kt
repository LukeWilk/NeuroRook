package io.github.lukewilk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComposeAppCommonTest {

    @Test
    fun example() {
        assertEquals(3, 1 + 2)
    }

    @Test
    fun greetingReturnsHelloWithPlatform() {
        val greeting = Greeting().greet()
        // Platform name is dynamic, just check prefix
        assertTrue(greeting.startsWith("Hello, "))
        assertTrue(greeting.endsWith("!"))
    }

    @Test
    fun platformNameIsNotBlank() {
        val platform = getPlatform()
        assertTrue(platform.name.isNotBlank())
    }
}