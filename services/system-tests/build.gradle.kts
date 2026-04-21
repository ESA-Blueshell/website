import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("kotlin-conventions")
    id("org.jetbrains.kotlin.plugin.spring") version "2.3.10"
    id("io.spring.dependency-management") version "1.1.7"
    id("test-logging-conventions")
    java
}

group = "net.blueshell"
version = "1.1.1"

description = "End-to-end system tests that drive the full stack through Playwright."

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.3")
        mavenBom("tools.jackson:jackson-bom:3.1.0")
        mavenBom("org.testcontainers:testcontainers-bom:1.21.4")
    }
}

configurations.configureEach {
    // The api's main code pulls a SnakeYAML replacement via a vendored jar
    // under services/api/libs/. Exclude the Maven coordinates so transitive
    // resolution from :services:api never tries to pull the android jar.
    exclude(group = "org.yaml", module = "snakeyaml")
}

val mockitoAgent by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    // Depend on the api so the in-process Spring Boot system tests can
    // bootstrap ApiApplication and reach its repositories / services.
    // testFixtures pulls in the shared factories + TestCleanUpListener.
    testImplementation(project(":services:api"))
    testImplementation(testFixtures(project(":services:api")))

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testImplementation("org.springframework.boot:spring-boot-starter-security")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway")
    testImplementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    testImplementation("org.flywaydb:flyway-mysql:12.0.2")
    testImplementation("org.mariadb.jdbc:mariadb-java-client:3.5.7")
    testImplementation("tools.jackson.module:jackson-module-kotlin")

    testImplementation("io.rest-assured:spring-mock-mvc:6.0.0")
    testImplementation("io.mockk:mockk:1.14.9")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.2.3")
    testImplementation("com.microsoft.playwright:playwright:1.58.0")
    testImplementation("com.github.javafaker:javafaker:1.0.2")
    testImplementation("io.github.classgraph:classgraph:4.8.184")

    mockitoAgent("org.mockito:mockito-core:5.21.0")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform {
        // System tests are tagged @Tag("system") — include only those here.
        includeTags("system")
    }
    systemProperty("spring.profiles.active", "test")
    jvmArgumentProviders += CommandLineArgumentProvider {
        listOf("-javaagent:${mockitoAgent.singleFile.absolutePath}")
    }
    testLogging {
        events(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
        exceptionFormat = TestExceptionFormat.FULL
        showStackTraces = true
    }
}

// Playwright install helpers — required by CI before `test` runs.
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
