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
val GHUSER = localProperties.getProperty("GHUSER") ?: error("Property GHUSER is not set in local.properties!")
val GHTOKEN = localProperties.getProperty("GHTOKEN") ?: error("Property GHTOKEN is not set in local.properties!")

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
        maven {
            url = uri("https://maven.pkg.github.com/brainflow-dev/brainflow")
            metadataSources {
                mavenPom()
                artifact()
            }
            credentials {
                username = GHUSER
                password = GHTOKEN
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
