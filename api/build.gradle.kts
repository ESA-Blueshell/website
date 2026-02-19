import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import org.springframework.boot.gradle.tasks.run.BootRun
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    id("org.springframework.boot") version "3.5.7"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.openapi.generator") version "7.15.0"
    jacoco

    val kotlinVersion = "2.3.10"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.spring") version kotlinVersion
    kotlin("plugin.jpa") version kotlinVersion
    kotlin("plugin.allopen") version kotlinVersion
    kotlin("plugin.noarg") version kotlinVersion
    kotlin("kapt") version kotlinVersion

    java
}

group = "net.blueshell"
version = "1.0.0"

description = "The API for the Blueshell Esports website"

// Configure kotlin-allopen plugin to make JPA entities non-final for Hibernate proxies
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(24))
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
    testCompileOnly {
        extendsFrom(configurations.testAnnotationProcessor.get())
    }
}

val mockitoAgent by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

configurations.configureEach {
    attributes.attribute(
        TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
        objects.named(TargetJvmEnvironment.STANDARD_JVM)
    )
}

configurations.configureEach {
    exclude(group = "org.yaml", module = "snakeyaml")
}

repositories {
    mavenCentral()
}
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation(kotlin("stdlib"))
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    mockitoAgent("org.mockito:mockito-core")

    implementation("com.nimbusds:nimbus-jose-jwt:10.5")
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

    implementation("com.google.apis:google-api-services-calendar:v3-rev20250404-2.0.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.39.1")

    implementation("org.flywaydb:flyway-core")

    compileOnly("jakarta.servlet:jakarta.servlet-api:6.1.0")
    implementation("jakarta.validation:jakarta.validation-api")
    implementation("jakarta.ws.rs:jakarta.ws.rs-api")
    implementation("jakarta.transaction:jakarta.transaction-api")

    implementation("org.springframework.boot:spring-boot-starter-validation")

    implementation("com.vladsch.flexmark:flexmark-all:0.64.8")
    implementation("org.apache.tika:tika-core:3.2.3")
    implementation("com.googlecode.libphonenumber:libphonenumber:9.0.15")
    implementation("com.github.scribejava:scribejava-apis:8.3.1")

    implementation("org.springframework.retry:spring-retry")
    implementation("org.springframework.boot:spring-boot-starter-aop")

    implementation("org.springframework.data:spring-data-jpa")
    implementation("jakarta.persistence:jakarta.persistence-api")

    implementation("org.flywaydb:flyway-mysql:11.13.2")

    implementation("com.fasterxml.jackson.core:jackson-annotations")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.0")
    implementation("org.openapitools:jackson-databind-nullable:0.2.7")

    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.5")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.14")

    implementation("com.github.javafaker:javafaker:1.0.2")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:mariadb")
    testImplementation("io.rest-assured:spring-mock-mvc:5.5.6")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
    testImplementation("io.github.classgraph:classgraph:4.8.179")
    testImplementation("com.microsoft.playwright:playwright:1.52.0")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("io.mockk:mockk:1.13.13")

    implementation("org.springframework.boot:spring-boot-starter-actuator")

    implementation("com.google.apis:google-api-services-groupssettings:v1-rev20220614-2.0.0")

    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-mail")

    implementation("org.commonmark:commonmark:0.21.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.21.0")
    implementation(files("libs/snakeyaml-2.5.jar"))
}

springBoot {
    mainClass.set("net.blueshell.api.ApiApplicationKt")
}

noArg {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(24)
    options.compilerArgs.add("-parameters")
}

tasks.register<JavaExec>("installPlaywrightDeps") {
    group = "playwright"
    description = "Installs Playwright OS dependencies"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.microsoft.playwright.CLI")
    args("install-deps")
}

tasks.register<JavaExec>("installChromium") {
    group = "playwright"
    description = "Installs Chromium (Playwright)"
    dependsOn("installPlaywrightDeps")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.microsoft.playwright.CLI")
    args("install", "chromium")
}

jacoco {
    toolVersion = "0.8.13"
}

val frontendCoverageRawDir = layout.buildDirectory.dir("coverage/frontend-system/raw")
val jacocoExecDir = layout.buildDirectory.dir("jacoco")
val backendCoveragePackagePath = "net/blueshell/api/**"
val backendCoverageClassTree = files(sourceSets["main"].output.classesDirs).asFileTree.matching {
    include(backendCoveragePackagePath)
}
val backendCoverageSourceDir = layout.projectDirectory.dir("src/main/kotlin/net/blueshell/api")

fun JacocoReport.configureBackendCoverageLayout() {
    classDirectories.setFrom(backendCoverageClassTree)
    sourceDirectories.setFrom(files(backendCoverageSourceDir))
    additionalSourceDirs.setFrom(files(backendCoverageSourceDir))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("spring.profiles.active", "test")
    jvmArgumentProviders += CommandLineArgumentProvider {
        listOf("-javaagent:${mockitoAgent.singleFile.absolutePath}")
    }
    extensions.configure(JacocoTaskExtension::class) {
        destinationFile = jacocoExecDir.get().file("$name.exec").asFile
        isIncludeNoLocationClasses = false
        excludes = listOf("jdk.internal.*", "jdk.proxy*.*")
    }

    testLogging {
        events(
            TestLogEvent.PASSED,
            TestLogEvent.FAILED,
            TestLogEvent.SKIPPED,
        )

        exceptionFormat = TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
        showStandardStreams = true
    }

    afterSuite(KotlinClosure2({ desc: TestDescriptor, result: TestResult ->
        if (desc.parent == null) {
            println(
                "Test result: ${result.resultType} " +
                        "(${result.testCount} tests, " +
                        "${result.successfulTestCount} passed, " +
                        "${result.failedTestCount} failed, " +
                        "${result.skippedTestCount} skipped)"
            )
        }
    }))
}

tasks.named<Test>("test") {
    description = "Runs API unit and integration tests excluding frontend system tests."
    useJUnitPlatform {
        excludeTags("system")
    }
    finalizedBy(tasks.named("jacocoTestReport"))
}

val systemTest by tasks.registering(Test::class) {
    description = "Runs API-owned frontend system tests tagged with @Tag(\"system\")."
    group = "verification"

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    shouldRunAfter(tasks.named("test"))

    useJUnitPlatform {
        includeTags("system")
    }

    systemProperty("frontend.coverage.rawDir", frontendCoverageRawDir.get().asFile.absolutePath)
     systemProperty("frontend.coverage.enabled", System.getProperty("frontend.coverage.enabled", "true"))
    systemProperty("frontend.coverage.required", System.getProperty("frontend.coverage.required", "true"))

    val frontendUrlOverride = System.getProperty("system.frontend.url")
    if (!frontendUrlOverride.isNullOrBlank()) {
        systemProperty("system.frontend.url", frontendUrlOverride)
    }

    doFirst {
        val rawDir = frontendCoverageRawDir.get().asFile
        if (rawDir.exists()) {
            rawDir.deleteRecursively()
        }
        rawDir.mkdirs()
    }

    finalizedBy(tasks.named("jacocoSystemTestReport"))
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    executionData(jacocoExecDir.map { it.file("test.exec") })

    configureBackendCoverageLayout()

    reports {
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/jacocoTestReport/jacocoTestReport.xml"))
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/jacocoTestReport/html"))
        csv.required.set(false)
    }
}

val jacocoSystemTestReport by tasks.registering(JacocoReport::class) {
    dependsOn(systemTest)
    executionData(jacocoExecDir.map { it.file("systemTest.exec") })

    configureBackendCoverageLayout()

    reports {
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/jacocoSystemTestReport/jacocoSystemTestReport.xml"))
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/jacocoSystemTestReport/html"))
        csv.required.set(false)
    }
}

val jacocoCombinedReport by tasks.registering(JacocoReport::class) {
    dependsOn(tasks.named("test"), systemTest)
    executionData(
        jacocoExecDir.map { it.file("test.exec") },
        jacocoExecDir.map { it.file("systemTest.exec") },
    )

    configureBackendCoverageLayout()

    reports {
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/jacocoCombinedReport/jacocoCombinedReport.xml"))
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/jacocoCombinedReport/html"))
        csv.required.set(false)
    }
}

tasks.withType<BootRun>().configureEach {
    jvmArgs("-Dspring.devtools.restart.enabled=true")
}

val brevoOutputDir: Provider<Directory> = layout.buildDirectory.dir("generated/sources/openapi/brevo")

sourceSets["main"].kotlin.srcDir(brevoOutputDir.map { it.dir("src/main/kotlin") })

tasks.register<GenerateTask>("generateBrevoClient") {
    validateSpec.set(false)
    generatorName.set("kotlin")
    library.set("jvm-spring-restclient")
    inputSpec.set(layout.projectDirectory.file("../openapi/brevo.yml").asFile.absolutePath)
    outputDir.set(brevoOutputDir.get().asFile.absolutePath)
    apiPackage.set("net.blueshell.clients.brevo.api")
    modelPackage.set("net.blueshell.clients.brevo.model")
    packageName.set("net.blueshell.clients.brevo.invoker")
    generateModelTests.set(false)
    generateApiTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
    configOptions.set(
        mapOf(
            "jackson" to "true",
            "serializationLibrary" to "jackson",
            "modelMutable" to "true",
            "enumPropertyNaming" to "UPPERCASE",
        )
    )
    additionalProperties.set(
        mapOf(
            "withXml" to "false",
            "jackson" to "true",
            "serializationLibrary" to "jackson",
            "useSpringBoot3" to "true",
        )
    )
    inlineSchemaOptions.set(
        mapOf(
            "RESOLVE_INLINE_ENUMS" to "true",
        )
    )
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
    doLast {
        val overridesSrc = file("openapi-overrides/net/blueshell/clients/brevo/model/TemplatePreviewRequestBody.kt")
        val overridesDestDir = brevoOutputDir.get().dir("src/main/kotlin/net/blueshell/clients/brevo/model").asFile
        val overridesDestFile = overridesDestDir.resolve("TemplatePreviewRequestBody.kt")
        overridesDestDir.mkdirs()
        overridesSrc.copyTo(overridesDestFile, overwrite = true)

        val generatedApiFiles = listOf(
            "net/blueshell/clients/brevo/api/ContactsApi.kt",
            "net/blueshell/clients/brevo/api/TransactionalEmailsApi.kt",
        )
        generatedApiFiles.forEach { relativePath ->
            val apiFile = brevoOutputDir.get().file("src/main/kotlin/$relativePath").asFile
            if (!apiFile.exists()) return@forEach

            val content = apiFile.readText()
            if (content.contains("\"REDUNDANT_CALL_OF_CONVERSION_METHOD\"")) return@forEach

            val updated = content.replace(
                "\"UnusedImport\"\n)",
                "\"UnusedImport\",\n    \"REDUNDANT_CALL_OF_CONVERSION_METHOD\"\n)",
            )
            apiFile.writeText(updated)
        }
    }
}

val classDependencyOutputDir = layout.buildDirectory.dir("reports/class-dependencies")

tasks.register<JavaExec>("classDependencyGraph") {
    description =
        "Generates a Graphviz dot file (and SVG if Graphviz is installed) for internal Blueshell API class dependencies."
    group = "reporting"
    dependsOn(tasks.named("testClasses"))
    mainClass.set("net.blueshell.tools.ClassDependencyGraphKt")
    classpath = sourceSets["test"].runtimeClasspath
    args(
        "--dot-output",
        classDependencyOutputDir.get().file("blueshell-api.dot").asFile.absolutePath,
        "--svg-output",
        classDependencyOutputDir.get().file("blueshell-api.svg").asFile.absolutePath,
        "--base-package",
        "net.blueshell.api",
    )
}

tasks.register<JavaExec>("seed") {
    description = "Seeds the currently configured database using factories and YAML configuration."
    group = "application"
    dependsOn(tasks.named("testClasses"))
    mainClass.set("net.blueshell.tools.DatabaseSeedToolKt")
    classpath = sourceSets["test"].runtimeClasspath

    val seedConfigPath = findProperty("config")?.toString()
    if (!seedConfigPath.isNullOrBlank()) {
        args("--config", seedConfigPath)
    }

    val seedProfile = findProperty("profile")?.toString()
    if (!seedProfile.isNullOrBlank()) {
        args("--profile", seedProfile)
    }
}

tasks.named<JavaCompile>("compileJava") {
    options.annotationProcessorPath = configurations.annotationProcessor.get()
}

tasks.named<JavaCompile>("compileTestJava") {
    options.annotationProcessorPath = configurations.testAnnotationProcessor.get()
    options.compilerArgs = options.compilerArgs.filter { it != "-proc:none" }.toMutableList()
    doFirst {
        options.compilerArgs.removeAll(listOf("-proc:none"))
    }
}

tasks.matching { it.name.contains("Kotlin") }.configureEach {
    dependsOn(tasks.named("generateBrevoClient"))
    inputs.dir(brevoOutputDir)
}

val compileKotlin: KotlinCompile by tasks
