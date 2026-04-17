plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("org.jetbrains.kotlinx.kover")
}

kover {
    currentProject {
        createVariant("coverage") {
            add("jvm", optional = true)
            add("desktop", optional = true)
            add("debug", optional = true)
        }
    }
}

kotlin {
    jvm()
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "HardwareBackend"
            isStatic = true
        }
    }
    sourceSets {
        named("commonMain") {
            dependencies {
                implementation(project(":shared"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kermit)
            }
        }
        named("commonTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        named("jvmMain") {
            dependencies {
                implementation(libs.brainflow)
                implementation(libs.jserialcomm)
                // internal FFT implementation used by the PSD pipeline; no external FFT dependency
                // JDSP - Java DSP library for Welch/PSD, windows, etc.
                implementation(libs.jdsp)
            }
        }
        named("jvmTest") {
            dependencies {
                // JVM-only test dependencies
                implementation(libs.brainflow)
                implementation(libs.mockito.core)
                // Allow static mocking for native/static helpers in tests
                implementation(libs.mockito.inline)
                implementation(libs.mockito.kotlin)
                implementation(libs.kotlin.testJunit)
                implementation(libs.junit.jupiter.api)
                runtimeOnly(libs.junit.jupiter.engine)
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

// Force ByteBuddy experimental flag for JVM tests to support newer Java versions when Mockito inlines
tasks.withType<Test>().configureEach {
    jvmArgs("-Dnet.bytebuddy.experimental=true")
    environment("LOG_LEVEL", "DEBUG")
}

