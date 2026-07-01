plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kover)
}

apply(from = rootProject.file("gradle/kover-coverage-variant.gradle.kts"))

tasks.register("coverageHtmlReport") {
    group = "verification"
    description = "Generate the aggregate Kover HTML report for the custom coverage variant."
    dependsOn("koverHtmlReportCoverage")
}

tasks.register("coverageVerify") {
    group = "verification"
    description = "Run Kover verification against the custom coverage variant."
    dependsOn("koverVerifyCoverage")
}

tasks.register("composeAppCoverageHtmlReport") {
    group = "verification"
    description = "Generate the composeApp Kover HTML report for the custom coverage variant."
    dependsOn(":composeApp:koverHtmlReportCoverage")
}

tasks.register("composeAppCoverageVerify") {
    group = "verification"
    description = "Run Kover verification for composeApp against the custom coverage variant."
    dependsOn(":composeApp:koverVerifyCoverage")
}

tasks.register("sharedCoverageHtmlReport") {
    group = "verification"
    description = "Generate the shared module Kover HTML report for the custom coverage variant."
    dependsOn(":shared:koverHtmlReportCoverage")
}

tasks.register("sharedCoverageVerify") {
    group = "verification"
    description = "Run Kover verification for shared against the custom coverage variant."
    dependsOn(":shared:koverVerifyCoverage")
}

// Set project version from gradle.properties (version key)
version = findProperty("version") as String

dependencies {
    listOf(
        ":hardwareBackend",
        ":hardwareBackendRunner",
        ":shared",
        ":composeApp",
        ":androidApp"
    ).forEach { path ->
        if (findProject(path) != null) {
            kover(project(path))
        }
    }
}
