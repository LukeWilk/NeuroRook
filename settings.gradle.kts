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

include(":composeApp")
include(":androidApp")
include(":hardwareBackend")
include(":hardwareBackendRunner")
