plugins {
    id("application")
    kotlin("jvm")
    id("org.jetbrains.kotlinx.kover")
}

dependencies {
    implementation(project(":hardwareBackend"), {
        targetConfiguration = "jvmRuntimeElements"
    })
}

application {
    mainClass.set("io.github.lukewilk.hardware.MainKt")
}
