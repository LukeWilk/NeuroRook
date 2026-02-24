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
            }
        }
    }
}

koverReport {
    filters {
        excludes {
            classes (
            )
        }
    }
}
