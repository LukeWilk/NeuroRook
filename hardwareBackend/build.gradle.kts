plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("org.jetbrains.kotlinx.kover")
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
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("co.touchlab:kermit:2.0.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("org.brainflow:brainflow:5.19.0")
                implementation(project(":shared"))
                // internal FFT implementation used by the PSD pipeline; no external FFT dependency
                // JDSP - Java DSP library for Welch/PSD, windows, etc.
                implementation("com.github.psambit9791:jdsp:3.1.0")
            }
        }
        val jvmTest by getting {
            dependencies {
                // JVM-only test dependencies
                implementation("org.brainflow:brainflow:5.19.0")
                implementation("org.mockito:mockito-core:5.2.0")
                implementation("org.mockito.kotlin:mockito-kotlin:5.2.0")
                implementation("org.jetbrains.kotlin:kotlin-test-junit:1.9.0")
                implementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.2")
            }
        }
    }
}

// Force ByteBuddy experimental flag for JVM tests to support newer Java versions when Mockito inlines
tasks.withType<Test>().configureEach {
    jvmArgs = (jvmArgs ?: listOf()) + listOf("-Dnet.bytebuddy.experimental=true")
}

koverReport {
    filters {
        excludes {
            classes (
            )
        }
    }
}
