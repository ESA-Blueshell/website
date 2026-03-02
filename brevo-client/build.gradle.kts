import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    id("org.openapi.generator") version "7.20.0"
    kotlin("jvm")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(24))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-cio:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")

    implementation(platform("tools.jackson:jackson-bom:3.1.0"))
    implementation("io.ktor:ktor-serialization-jackson:2.3.12")
    implementation("tools.jackson.module:jackson-module-kotlin")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
}

// ---- OpenAPI generation locations ----
val openApiSpec = rootProject.layout.projectDirectory.file("openapi/brevo.yml")
val generatedRoot = layout.buildDirectory.dir("generated/openapi/brevo")
val generatedKotlinSrc = generatedRoot.map { it.dir("src/main/kotlin") }

tasks.register<GenerateTask>("generate") {
    group = "openapi"
    description = "Generates the Brevo Kotlin client into build/generated/…"

    validateSpec.set(false)
    generatorName.set("kotlin")
    library.set("jvm-ktor")

    inputSpec.set(openApiSpec.asFile.absolutePath)
    outputDir.set(generatedRoot.get().asFile.absolutePath)

    configOptions.set(
        mapOf(
            "sourceFolder" to "src/main/kotlin",
            "jackson" to "true",
            "serializationLibrary" to "jackson",
            "modelMutable" to "true",
            "enumPropertyNaming" to "UPPERCASE",
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
            "getContactInfo_identifier_parameter" to "kotlin.String",
            "updateContact_identifier_parameter" to "kotlin.String",
            "createDoiContact_attributes_value" to "kotlin.Any",
            "getContactInfo_identifierType_parameter" to "kotlin.String",
            "updateContact_identifierType_parameter" to "kotlin.String",
            "TemplatePreviewRequestBody" to "net.blueshell.clients.brevo.model.TemplatePreviewRequestBody",
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

// Add generated sources to the main source set
kotlin {
    sourceSets {
        val main by getting {
            kotlin.srcDir(generatedKotlinSrc)
        }
    }
}

// Make compilation use generated sources, but do not regenerate unless needed
tasks.withType<KotlinCompile>().configureEach {
    dependsOn(tasks.named("generate"))
}
