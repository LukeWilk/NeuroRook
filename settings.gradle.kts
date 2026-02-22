rootProject.name = "NeuroRook"
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

dependencyResolutionManagement {
    val githubUser = System.getenv("githubUser") ?: error("Environment variable githubUser is not set!")
    val githubToken = System.getenv("githubToken") ?: error("Environment variable githubToken is not set!")
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
                username = githubUser
                password = githubToken
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
