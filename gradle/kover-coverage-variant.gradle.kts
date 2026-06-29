extensions.getByName("kover").withGroovyBuilder {
    "currentProject" {
        "createVariant"("coverage") {
            "add"(listOf("jvm"), true)
            "add"(listOf("desktop"), true)
            "add"(listOf("debug"), true)
        }
    }
}
