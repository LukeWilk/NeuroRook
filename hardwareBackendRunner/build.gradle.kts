plugins {
    id("application")
    kotlin("jvm")
    id("org.jetbrains.kotlinx.kover")
}

dependencies {
    implementation(project(":hardwareBackend"), {
        targetConfiguration = "jvmRuntimeElements"
    })
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("io.github.lukewilk.hardwareRunner.MainKt")
}


tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
