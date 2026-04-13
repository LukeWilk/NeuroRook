rootProject.name = "NeuroRook"
include(":shared")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
    }
}

val localProperties = java.util.Properties()
val localPropertiesFile = java.io.File(rootDir, "local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}
val GHUSER = System.getenv("GHUSER") ?: localProperties.getProperty("GHUSER")
val GHTOKEN = System.getenv("GHTOKEN") ?: localProperties.getProperty("GHTOKEN")

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
        if (GHUSER != null && GHTOKEN != null) {
            maven {
                url = uri("https://maven.pkg.github.com/brainflow-dev/brainflow")
                credentials {
                    username = GHUSER
                    password = GHTOKEN
                }
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

fun includeIfPresent(path: String) {
    val projectDir = file(path.removePrefix(":"))
    if (projectDir.isDirectory) {
        include(path)
    }
}

val hasComposeApp = file("composeApp").isDirectory

if (hasComposeApp) {
    include(":composeApp")
    includeIfPresent(":androidApp")
}
includeIfPresent(":hardwareBackend")
includeIfPresent(":hardwareBackendRunner")
