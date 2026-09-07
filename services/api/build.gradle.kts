import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    id("spring-conventions")
    id("testing-conventions")
    id("org.graalvm.buildtools.native") version "1.1.11"
    `java-test-fixtures`

    val kotlinVersion = "2.4.10"
    kotlin("plugin.jpa") version kotlinVersion
    kotlin("plugin.allopen") version kotlinVersion
    kotlin("plugin.noarg") version kotlinVersion
    kotlin("kapt") version kotlinVersion

    java
}

group = "net.blueshell"
version = "1.8.0" // x-release-please-version

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

// Client versions are bumped by Dependabot like any other dependency; the
// nightly spec sync in each client repository is what decides whether a bump
// is a patch, a minor or a major.
val brevoClientVersion = "1.0.1"
val discordClientVersion = "1.0.1"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-authorization-server")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // Valkey-backed server-side HTTP sessions + a Valkey cache layer. Lettuce
    // is the default client pulled in by spring-boot-starter-data-redis.
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.session:spring-session-data-redis")
    implementation(platform("org.springframework.modulith:spring-modulith-bom:2.1.1"))
    implementation("org.springframework.modulith:spring-modulith-starter-jdbc")
    // Module detection pulls in ArchUnit, which has no business in the production jar —
    // the detection strategy is only ever instantiated by ApplicationModules in a test.
    compileOnly("org.springframework.modulith:spring-modulith-core")
    implementation("org.springframework.cloud:spring-cloud-starter-vault-config:5.0.2")
    implementation(project(":libs:kotlin-common"))
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation(kotlin("stdlib"))
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    implementation("com.nimbusds:nimbus-jose-jwt:10.9.1")
    // Nimbus RSAKey.parseFromPEMEncodedObjects needs JcaPEMKeyConverter (bcpkix); bcprov alone is insufficient.
    runtimeOnly("org.bouncycastle:bcpkix-jdk18on:1.85")
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

    implementation("com.google.apis:google-api-services-calendar:v3-rev20251207-2.0.0")
    implementation("com.google.apis:google-api-services-groupssettings:v1-rev20220614-2.0.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.51.0")

    compileOnly("jakarta.servlet:jakarta.servlet-api:6.1.0")
    implementation("jakarta.validation:jakarta.validation-api")
    implementation("jakarta.ws.rs:jakarta.ws.rs-api")
    implementation("jakarta.transaction:jakarta.transaction-api")
    implementation("org.springframework.data:spring-data-jpa")
    implementation("jakarta.persistence:jakarta.persistence-api")

    implementation("com.vladsch.flexmark:flexmark-all:0.64.8")
    implementation("org.apache.tika:tika-core:4.0.0")
    implementation("com.googlecode.libphonenumber:libphonenumber:9.0.38")
    implementation("com.github.scribejava:scribejava-apis:8.3.3")
    implementation("org.springframework.retry:spring-retry:2.0.13")
    implementation("org.springframework:spring-aop")
    implementation("org.aspectj:aspectjweaver")

    implementation("org.flywaydb:flyway-mysql:13.4.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.10")

    implementation("com.fasterxml.jackson.core:jackson-annotations")
    implementation("tools.jackson.module:jackson-module-kotlin")
    // Jackson 2.x Kotlin module — required for SpringDoc/swagger-core schema
    // generation, which uses its own com.fasterxml.jackson ObjectMapper
    // independently of our tools.jackson mapper.
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.22.2")
    implementation("org.openapitools:jackson-databind-nullable:0.2.11")

    // Generated clients, published from ESA-Blueshell/{brevo,discord}-client.
    // They were generated in-repo under libs/clients until their specs, their
    // filtering and their release cadence moved to repositories of their own,
    // where a nightly job re-derives them from upstream and versions them by
    // what actually changed on the surface this application consumes.
    implementation("net.blueshell.clients:brevo-client:$brevoClientVersion")
    implementation("net.blueshell.clients:discord-client:$discordClientVersion")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0")

    implementation("org.commonmark:commonmark:0.30.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.30.0")
    implementation(files("libs/snakeyaml-2.5.jar"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.3.0")
    testImplementation("com.github.javafaker:javafaker:1.0.2")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-mariadb:2.0.5")
    testImplementation("io.rest-assured:spring-mock-mvc:6.0.1")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.0")
    testImplementation("org.springframework.modulith:spring-modulith-core")
    testImplementation("io.github.classgraph:classgraph:4.8.194")
    testImplementation("io.mockk:mockk:1.14.11")
    // H2 in-memory database for OpenAPI spec generation (test-scoped only).
    testImplementation("com.h2database:h2:2.4.240")

    // Shared test-fixture consumers expose main starter deps so factories
    // and support classes compile against Spring / JPA / Jackson / Security.
    testFixturesApi("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testFixturesApi("org.springframework.security:spring-security-test")
    testFixturesApi("org.springframework.boot:spring-boot-starter-data-jpa")
    testFixturesApi("org.springframework.boot:spring-boot-starter-web")
    testFixturesApi("org.springframework.boot:spring-boot-starter-flyway")
    testFixturesApi("org.flywaydb:flyway-mysql:13.4.0")
    testFixturesApi("com.github.javafaker:javafaker:1.0.2")
    // Shared test base boots a throwaway Valkey via @ServiceConnection so the
    // Redis-backed HTTP session path is exercised under the real prod config.
    testFixturesApi("org.springframework.boot:spring-boot-testcontainers")
    testFixturesApi("org.testcontainers:testcontainers:2.0.5")
    testFixturesCompileOnly("jakarta.servlet:jakarta.servlet-api:6.1.0")

    mockitoAgent("org.mockito:mockito-core:5.23.0")
}

springBoot {
    mainClass.set("net.blueshell.api.ApiApplicationKt")
}

// java-test-fixtures consumers need a plain jar with no classifier. Spring
// Boot disables the default `jar` task in favour of `bootJar`; re-enable
// both with distinct classifiers so consumers get the plain jar and
// `bootJar` remains the runnable artifact.
tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveClassifier.set("boot")
}

// Spring Boot registers `bootArchives` as a consumable configuration with
// the same attributes as the default `archives`, which collides with
// java-test-fixtures' artifact publishing. Mark bootArchives as
// non-consumable — it is only used internally for `bootJar`.
configurations.named("bootArchives") {
    isCanBeConsumed = false
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
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

// Exclude openapi-gen tag from normal test task so it only runs when explicitly invoked.
// The shared convention's project-wide 40% floor cannot fail because of one new
// package, so the signup classes get their own gate. CLASS element rather than
// PACKAGE so it picks up new Signup* classes without dragging in existing ones.
val signupCoverageIncludes = listOf(
    "net.blueshell.api.auth.domain.Signup*",
    "net.blueshell.api.auth.web.Signup*",
    "net.blueshell.api.user.api.Signup*",
    "net.blueshell.api.user.web.Signup*",
)

fun JacocoCoverageVerification.requireSignupCoverage() {
    violationRules {
        rule {
            element = "CLASS"
            includes = signupCoverageIncludes
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
        }
        rule {
            element = "CLASS"
            includes = signupCoverageIncludes
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal()
            }
        }
    }
}

tasks.jacocoTestCoverageVerification { requireSignupCoverage() }
tasks.named<JacocoCoverageVerification>("jacocoIntegrationTestCoverageVerification") {
    requireSignupCoverage()
}

tasks.named<Test>("test") {
    useJUnitPlatform { excludeTags("openapi-gen") }
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

val discordLiveTest by tasks.registering(Test::class) {
    description =
        "Runs live Discord API integration tests tagged with @Tag(\"discord-live\"). Requires DISCORD_BOT_TOKEN."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    shouldRunAfter(tasks.named("integrationTest"))
    useJUnitPlatform { includeTags("discord-live") }
}

// Generate OpenAPI spec via in-memory H2 (no MariaDB required).
// Runs the openapi-gen-tagged test, which writes sorted block YAML to
// build/openapi.raw.yaml, and copies that to services/api/openapi.yaml.
val openApiGenTest by tasks.registering(Test::class) {
    description = "Runs the OpenAPI spec generation test tagged with @Tag(\"openapi-gen\")."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("openapi-gen") }
    // Disable caching so the test always runs and recreates openapi.raw.yaml.
    // The raw file is not declared as a cacheable output; it's a side effect
    // of the test used by dumpOpenApiSpec. Always running is fine since this
    // task only runs when dumpOpenApiSpec is invoked (openapi-sync CI + local regen).
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
}

val dumpOpenApiSpec by tasks.registering {
    description = "Generates the OpenAPI spec via in-memory H2 without a database, into services/api/openapi.yaml."
    group = "verification"

    dependsOn(openApiGenTest)

    doLast {
        // The generator test already sorts the keys and writes block YAML, so there is
        // nothing to normalise here and no external tool to depend on.
        val rawFile = File(buildDir, "openapi.raw.yaml")
        val outputFile = File(projectDir, "openapi.yaml")

        if (!rawFile.exists()) {
            throw RuntimeException("OpenAPI raw spec not found at ${rawFile.absolutePath}")
        }

        rawFile.copyTo(outputFile, overwrite = true)
        rawFile.delete()

        println("OpenAPI spec written to ${outputFile.absolutePath}")
    }
}

tasks.withType<BootRun>().configureEach {
    outputs.upToDateWhen { false }
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

kotlin {
    compilerOptions {
        // Without this, `List<@Positive Long>` compiles to bytecode carrying no
        // RuntimeVisibleTypeAnnotations, so Hibernate Validator never sees the element
        // constraint and the endpoint advertises a rule it does not have.
        freeCompilerArgs.add("-Xemit-jvm-type-annotations")
    }
}
