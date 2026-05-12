import java.io.File
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
    testImplementation("com.microsoft.playwright:playwright:1.59.0")
    testImplementation("com.github.javafaker:javafaker:1.0.2")
    testImplementation("io.github.classgraph:classgraph:4.8.184")

    // JUnit 6 does not automatically put the platform launcher on the
    // runtime classpath; Gradle 9's test-engine selection needs it.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    mockitoAgent("org.mockito:mockito-core:5.21.0")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform {
        // System tests are tagged @Tag("system") — include only those here.
        includeTags("system")
    }
    systemProperty("spring.profiles.active", "test")
    // Propagate -Dsystem.frontend.url=… from the gradle invocation to the
    // test JVM. Without this the forked test picks up the default from
    // application.properties (http://frontend:3000) which is the dev-compose
    // hostname, not valid in CI where the frontend is served on localhost.
    System.getProperty("system.frontend.url")
        ?.takeIf { it.isNotBlank() }
        ?.let { systemProperty("system.frontend.url", it) }
    jvmArgumentProviders += CommandLineArgumentProvider {
        listOf("-javaagent:${mockitoAgent.singleFile.absolutePath}")
    }
    testLogging {
        events(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
        exceptionFormat = TestExceptionFormat.FULL
        showStackTraces = true
    }

    // Sharding via env vars `SHARD_TOTAL` / `SHARD_INDEX` (1-based).
    // CI runs N parallel matrix jobs — each scans the test classes
    // directory deterministically (sorted by FQCN), partitions by
    // `index = absoluteHash(fqcn) % SHARD_TOTAL`, and only includes
    // its own slice via Gradle's `--tests` filter (`includeTestsMatching`).
    // Locally, leaving the env vars unset runs every test in one JVM
    // exactly as before. The hash partition is stable across runs so
    // a class always lands on the same shard — useful for triage.
    val shardTotal = System.getenv("SHARD_TOTAL")?.toIntOrNull()?.takeIf { it > 1 }
    val shardIndex = System.getenv("SHARD_INDEX")?.toIntOrNull()
    if (shardTotal != null && shardIndex != null && shardIndex in 1..shardTotal) {
        doFirst {
            val classes = testClassesDirs.asFileTree
                .matching { include("**/*Test.class", "**/*SystemTest.class", "**/*IT.class") }
                .files
                .map { f ->
                    // class file path → FQCN: strip the classes-dirs root and
                    // .class suffix, swap separators for dots.
                    val root = testClassesDirs.firstOrNull { f.startsWith(it) } ?: return@map null
                    f.relativeTo(root).path.removeSuffix(".class").replace(File.separatorChar, '.')
                }
                .filterNotNull()
                .filter { !it.contains('$') } // skip anonymous / nested $-classes
                .sorted()
            val mine = classes.filter { Math.floorMod(it.hashCode(), shardTotal) == shardIndex - 1 }
            logger.lifecycle("Shard $shardIndex/$shardTotal — ${mine.size}/${classes.size} test classes")
            filter {
                isFailOnNoMatchingTests = false
                if (mine.isEmpty()) {
                    // No classes assigned — exclude everything by including a
                    // pattern that can't match. Without this, an empty include
                    // list would match everything.
                    includeTestsMatching("__no_match__shard_${shardIndex}__")
                } else {
                    mine.forEach { includeTestsMatching(it) }
                }
            }
        }
    }
}

// Drives `/oauth2/jwks` against a real api wired to Vault Transit. Requires
// the compose stack to be up via the oidc-e2e profile (see
// docker-compose.oidc-e2e.yml). Excluded from `:check`.
val vaultOidcLiveTest by tasks.registering(Test::class) {
    description =
        "Runs the Vault-Transit JWKS regression test against a live api on :8080. " +
            "Bring up docker-compose.oidc-e2e.yml first."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    shouldRunAfter(tasks.test)
    useJUnitPlatform {
        // Override the project-wide includeTags("system") set in
        // tasks.withType<Test>().configureEach — we only want vault-oidc-live.
        includeTags.clear()
        includeTags("vault-oidc-live")
    }
    jvmArgumentProviders += CommandLineArgumentProvider {
        listOf("-javaagent:${mockitoAgent.singleFile.absolutePath}")
    }
    testLogging {
        events(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
        exceptionFormat = TestExceptionFormat.FULL
        showStackTraces = true
    }
}
