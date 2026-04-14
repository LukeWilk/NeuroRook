plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    id("org.jetbrains.compose") version "1.9.3" apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.8"
}

// Set project version from gradle.properties (version key)
version = findProperty("version") as String


//kover {
//    reports {
//        filters {
//            excludes {
//                classes(
//                    "io.github.lukewilk.hardware.MainKt",
//                    "io.github.lukewilk.hardware.MainKt\$main\$1",
//                    "neurorook.composeapp.generated.resources.ActualResourceCollectorsKt",
//                    "neurorook.composeapp.generated.resources.Res"
//                )
//            }
//        }
//    }
//}



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
