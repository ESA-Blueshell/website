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
val openApiSpec = rootProject.layout.projectDirectory.file("../shared/openapi/brevo.yml")
val generatedRoot = layout.buildDirectory.dir("generated/openapi/brevo")
val generatedJavaSrc = generatedRoot.map { it.dir("src/main/java") }

tasks.register<GenerateTask>("generate") {
    group = "openapi"
    description = "Generates the Brevo Java client into build/generated/…"

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

    apiPackage.set("net.blueshell.clients.brevo.api")
    modelPackage.set("net.blueshell.clients.brevo.model")
    packageName.set("net.blueshell.clients.brevo.invoker")

    generateModelTests.set(false)
    generateApiTests.set(false)
    generateApiDocumentation.set(true)
    generateModelDocumentation.set(true)

    inlineSchemaOptions.set(mapOf("RESOLVE_INLINE_ENUMS" to "true"))

    schemaMappings.set(
        mapOf(
            "getContactInfo_identifier_parameter" to "java.lang.String",
            "updateContact_identifier_parameter" to "java.lang.String",
            "createDoiContact_attributes_value" to "java.lang.Object",
            "getContactInfo_identifierType_parameter" to "java.lang.String",
            "updateContact_identifierType_parameter" to "java.lang.String",
            "TemplatePreviewRequestBody" to "net.blueshell.clients.brevo.model.TemplatePreviewRequestBody",
            "postContactInfo_contacts_success" to "java.lang.Object",
            "postContactInfo_contacts_failure" to "java.lang.Object",
        )
    )

    globalProperties.set(
        mapOf(
            "apis" to "TransactionalEmails,Contacts",
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
