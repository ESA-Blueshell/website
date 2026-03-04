import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    id("org.openapi.generator") version "7.20.0"
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(24))
    }
}

repositories {
    mavenCentral()
}

val springWebVersion = "7.0.5"
val jacksonVersion = "3.1.0"
val jakartaValidationVersion = "3.1.1"
val jakartaAnnotationVersion = "3.0.0"

dependencies {
    implementation("org.springframework:spring-web:$springWebVersion")
    implementation("org.springframework:spring-context:$springWebVersion")

    implementation(platform("tools.jackson:jackson-bom:$jacksonVersion"))
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.21")
    implementation("tools.jackson.core:jackson-core")
    implementation("tools.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.21.1")
    implementation("org.openapitools:jackson-databind-nullable:0.2.9")

    compileOnly("jakarta.validation:jakarta.validation-api:$jakartaValidationVersion")
    compileOnly("jakarta.annotation:jakarta.annotation-api:$jakartaAnnotationVersion")
}

// ---- OpenAPI generation locations ----
val openApiSpec = rootProject.layout.projectDirectory.file("openapi/listmonk.yaml")
val generatedRoot = layout.buildDirectory.dir("generated/openapi/listmonk")
val generatedJavaSrc = generatedRoot.map { it.dir("src/main/java") }

tasks.register<GenerateTask>("generate") {
    group = "openapi"
    description = "Generates the Listmonk Java client into build/generated/…"

    validateSpec.set(false)
    generatorName.set("java")
    library.set("restclient")

    inputSpec.set(openApiSpec.asFile.absolutePath)
    outputDir.set(generatedRoot.get().asFile.absolutePath)

    configOptions.set(
        mapOf(
            "sourceFolder" to "src/main/java",
            "serializationLibrary" to "jackson",
            "dateLibrary" to "java8",
            "useJakartaEe" to "true",
            "useBeanValidation" to "true",
            "useJackson3" to "true",
            "enumPropertyNaming" to "MACRO_CASE",
        )
    )

    apiPackage.set("net.blueshell.clients.listmonk.api")
    modelPackage.set("net.blueshell.clients.listmonk.model")
    packageName.set("net.blueshell.clients.listmonk.invoker")

    generateModelTests.set(false)
    generateApiTests.set(false)
    generateApiDocumentation.set(true)
    generateModelDocumentation.set(true)

    inlineSchemaOptions.set(mapOf("RESOLVE_INLINE_ENUMS" to "true"))

    schemaMappings.set(
        mapOf(
            // The per_page param uses oneOf(integer, string "all") — map to Object to avoid generation issues
            "getBounces_per_page_parameter" to "java.lang.Object",
        )
    )

    globalProperties.set(
        mapOf(
            "apis" to "Transactional,Bounces,Templates",
            "models" to "",
            "supportingFiles" to "",
        )
    )

    // Ensure Gradle can properly decide UP-TO-DATE and skip this task
    inputs.file(openApiSpec)

    // These properties affect output; declaring them keeps the task incremental
    inputs.property("generatorName", generatorName.get())
    inputs.property("library", library.get())
    inputs.property("apiPackage", apiPackage.get())
    inputs.property("modelPackage", modelPackage.get())
    inputs.property("packageName", packageName.get())
    inputs.property("configOptions", configOptions.get())
    inputs.property("inlineSchemaOptions", inlineSchemaOptions.get())
    inputs.property("schemaMappings", schemaMappings.get())
    inputs.property("globalProperties", globalProperties.get())

    outputs.dir(generatedRoot)

    // Helps with remote/local build cache if you use it
    outputs.cacheIf { true }
}

sourceSets {
    named("main") {
        java.srcDir(generatedJavaSrc)
    }
}

tasks.withType<JavaCompile>().configureEach {
    dependsOn(tasks.named("generate"))
}
