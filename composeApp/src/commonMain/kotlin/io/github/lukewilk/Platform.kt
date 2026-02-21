package io.github.lukewilk

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform