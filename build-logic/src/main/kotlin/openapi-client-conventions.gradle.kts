import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    id("org.openapi.generator")
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
}

// Shared Jackson 3 + Jakarta dependency shape used by every generated client.
// Kept here rather than duplicated across each client's build file.
dependencies {
    "implementation"("org.springframework:spring-web:7.0.5")
    "implementation"("org.springframework:spring-context:7.0.5")

    "implementation"(platform("tools.jackson:jackson-bom:3.1.0"))
    "implementation"("com.fasterxml.jackson.core:jackson-annotations:2.21")
    "implementation"("tools.jackson.core:jackson-core")
    "implementation"("tools.jackson.core:jackson-databind")
    "implementation"("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.21.1")
    "implementation"("org.openapitools:jackson-databind-nullable:0.2.9")

    "compileOnly"("jakarta.validation:jakarta.validation-api:3.1.1")
    "compileOnly"("jakarta.annotation:jakarta.annotation-api:3.0.0")
}

// Extension that client subprojects configure to describe their generator run.
// Mirrors the subset of GenerateTask options the website actually uses;
// expand on demand rather than exposing the entire upstream plugin surface.
abstract class OpenApiClientExtension {
    /** Absolute path (or path relative to rootDir) to the spec file. */
    abstract val specPath: Property<String>
    abstract val apiPackage: Property<String>
    abstract val modelPackage: Property<String>
    abstract val packageName: Property<String>

    /** Comma-joined list of API classes to generate (openapi-generator `apis` global). */
    abstract val apis: ListProperty<String>

    /** Schema mappings passed through to the generator. */
    abstract val schemaMappings: MapProperty<String, String>
}

val openApiClient = extensions.create<OpenApiClientExtension>("openApiClient").apply {
    apis.convention(emptyList())
    schemaMappings.convention(emptyMap())
}

val generatedRoot = layout.buildDirectory.dir("generated/openapi")
val generatedJavaSrc = generatedRoot.map { it.dir("src/main/java") }

tasks.register<GenerateTask>("generate") {
    group = "openapi"
    description = "Generates the Java client from the configured OpenAPI spec."

    validateSpec.set(false)
    generatorName.set("java")
    library.set("restclient")

    // Resolve specPath against the root dir at task execution time so
    // clients can use repo-root-relative paths (libs/openapi-specs/foo.yml).
    inputSpec.set(
        openApiClient.specPath.map { path ->
            rootProject.layout.projectDirectory.file(path).asFile.absolutePath
        },
    )
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
        ),
    )

    apiPackage.set(openApiClient.apiPackage)
    modelPackage.set(openApiClient.modelPackage)
    packageName.set(openApiClient.packageName)

    generateModelTests.set(false)
    generateApiTests.set(false)
    generateApiDocumentation.set(true)
    generateModelDocumentation.set(true)

    inlineSchemaOptions.set(mapOf("RESOLVE_INLINE_ENUMS" to "true"))
    schemaMappings.set(openApiClient.schemaMappings)

    globalProperties.set(
        openApiClient.apis.map { apiList ->
            mapOf(
                "apis" to apiList.joinToString(","),
                "models" to "",
                "supportingFiles" to "",
            )
        },
    )

    // Inputs that affect output — declared so Gradle can reason about UP-TO-DATE.
    inputs.file(
        openApiClient.specPath.map {
            rootProject.layout.projectDirectory.file(it)
        },
    )
    inputs.property("apiPackage", openApiClient.apiPackage)
    inputs.property("modelPackage", openApiClient.modelPackage)
    inputs.property("packageName", openApiClient.packageName)
    inputs.property("apis", openApiClient.apis)
    inputs.property("schemaMappings", openApiClient.schemaMappings)

    outputs.dir(generatedRoot)
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
