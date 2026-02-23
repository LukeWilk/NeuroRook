plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    id("org.jetbrains.kotlinx.kover") version "0.7.5"
}

// Set project version from gradle.properties (version key)
version = findProperty("version") as String

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/brainflow-dev/brainflow")
        credentials {
            username = findProperty("githubUser") as String? ?: ""
            password = findProperty("githubToken") as String? ?: ""
        }
    }
}

dependencies {
    kover(project(":androidApp"))
    kover(project(":composeApp"))
    kover(project(":hardwareBackend"))
    kover(project(":hardwareBackendRunner"))
    kover(project(":shared"))
}