plugins {
    id("application")
    kotlin("jvm")
}

dependencies {
    implementation(project(":hardwareBackend"), {
        targetConfiguration = "jvmRuntimeElements"
    })
}

application {
    mainClass.set("io.github.lukewilk.hardware.MainKt")
}
