plugins {
    id("application")
    kotlin("jvm")
    id("org.jetbrains.kotlinx.kover")
}

kover {
    currentProject {
        createVariant("coverage") {
            add("jvm", optional = true)
            add("desktop", optional = true)
            add("debug", optional = true)
        }
    }
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

tasks.withType<Test>().configureEach {
    environment("LOG_LEVEL", "DEBUG")
}
