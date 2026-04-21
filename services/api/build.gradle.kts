import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    id("spring-conventions")
    id("testing-conventions")
    id("org.graalvm.buildtools.native") version "0.10.6"

    val kotlinVersion = "2.3.10"
    kotlin("plugin.jpa") version kotlinVersion
    kotlin("plugin.allopen") version kotlinVersion
    kotlin("plugin.noarg") version kotlinVersion
    kotlin("kapt") version kotlinVersion

    java
}

group = "net.blueshell"
version = "1.1.1"

description = "The API for the Blueshell Esports website"

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

noArg {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
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
        objects.named(TargetJvmEnvironment.STANDARD_JVM),
    )
    exclude(group = "org.yaml", module = "snakeyaml")
}

dependencyLocking {
    lockAllConfigurations()
    // DEFAULT (not STRICT) so Gradle does not fail on configurations that
    // are declared lockable but have no state persisted yet (e.g. jacocoAgent,
    // installChromium classpath). Regenerate locks with
    // `./gradlew :services:api:<task> --write-locks` when adding new tasks
    // that pull fresh configurations.
    lockMode.set(LockMode.DEFAULT)
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation(kotlin("stdlib"))
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    implementation("com.nimbusds:nimbus-jose-jwt:10.8")
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

    implementation("com.google.apis:google-api-services-calendar:v3-rev20251207-2.0.0")
    implementation("com.google.apis:google-api-services-groupssettings:v1-rev20220614-2.0.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.43.0")

    compileOnly("jakarta.servlet:jakarta.servlet-api:6.1.0")
    implementation("jakarta.validation:jakarta.validation-api")
    implementation("jakarta.ws.rs:jakarta.ws.rs-api")
    implementation("jakarta.transaction:jakarta.transaction-api")
    implementation("org.springframework.data:spring-data-jpa")
    implementation("jakarta.persistence:jakarta.persistence-api")

    implementation("com.vladsch.flexmark:flexmark-all:0.64.8")
    implementation("org.apache.tika:tika-core:3.2.3")
    implementation("com.googlecode.libphonenumber:libphonenumber:9.0.24")
    implementation("com.github.scribejava:scribejava-apis:8.3.3")
    implementation("org.springframework.retry:spring-retry:2.0.12")
    implementation("org.springframework:spring-aop")
    implementation("org.aspectj:aspectjweaver")

    implementation("org.flywaydb:flyway-mysql:12.0.2")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.7")

    implementation("com.fasterxml.jackson.core:jackson-annotations")
    implementation("tools.jackson.module:jackson-module-kotlin")
    // Jackson 2.x Kotlin module — required for SpringDoc/swagger-core schema
    // generation, which uses its own com.fasterxml.jackson ObjectMapper
    // independently of our tools.jackson mapper.
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.0")
    implementation("org.openapitools:jackson-databind-nullable:0.2.9")

    implementation(project(":services:api:clients:brevo"))
    implementation(project(":services:api:clients:listmonk"))

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")

    implementation("org.commonmark:commonmark:0.27.1")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.27.1")
    implementation(files("libs/snakeyaml-2.5.jar"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.2.3")
    testImplementation("com.github.javafaker:javafaker:1.0.2")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:mariadb:1.21.4")
    testImplementation("io.rest-assured:spring-mock-mvc:6.0.0")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
    testImplementation("io.github.classgraph:classgraph:4.8.184")
    testImplementation("com.microsoft.playwright:playwright:1.58.0")
    testImplementation("io.mockk:mockk:1.14.9")

    mockitoAgent("org.mockito:mockito-core:5.21.0")
}

springBoot {
    mainClass.set("net.blueshell.api.ApiApplicationKt")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.compilerArgs.add("-parameters")
}

// Mockito inline-mock-maker requires an agent on JDK 21+.
tasks.withType<Test>().configureEach {
    systemProperty("spring.profiles.active", "test")
    jvmArgumentProviders += CommandLineArgumentProvider {
        listOf("-javaagent:${mockitoAgent.singleFile.absolutePath}")
    }
    testLogging {
        events(
            TestLogEvent.PASSED,
            TestLogEvent.FAILED,
            TestLogEvent.SKIPPED,
        )
        exceptionFormat = TestExceptionFormat.FULL
        showStackTraces = true
    }
}

// Live external-API tests — opt-in, never part of `check`.
val brevoLiveTest by tasks.registering(Test::class) {
    description =
        "Runs live Brevo API integration tests tagged with @Tag(\"brevo-live\"). Requires BREVO_API_KEY."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    shouldRunAfter(tasks.test)
    useJUnitPlatform { includeTags("brevo-live") }
}

val listmonkLiveTest by tasks.registering(Test::class) {
    description =
        "Runs live Listmonk API integration tests tagged with @Tag(\"listmonk-live\"). Requires a running Listmonk."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    shouldRunAfter(tasks.test)
    useJUnitPlatform { includeTags("listmonk-live") }
}

// Playwright install helpers — needed by the system tests (moved to
// :services:system-tests in the next step; the tasks stay here until the
// tests do).
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

tasks.withType<BootRun>().configureEach {
    jvmArgs("-Dspring.devtools.restart.enabled=true")
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

val compileKotlin: KotlinCompile by tasks
