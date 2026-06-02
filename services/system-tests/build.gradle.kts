import java.io.File
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("kotlin-conventions")
    id("test-logging-conventions")
    java
}

group = "net.blueshell"
version = "1.1.1"

description = "End-to-end system tests that drive the full stack through Playwright."

dependencies {
    // The system-tests project no longer hosts ApiApplication in-process
    // — the api runs as a docker-compose container the test JVM reaches
    // through localhost:8080. So no project(":services:api") dep, no
    // Spring Boot starters; just an HTTP client, JDBC, Playwright, and
    // IMAP/JSON helpers for assertions.
    testImplementation("org.springframework.security:spring-security-crypto:7.0.5")
    testImplementation("org.mariadb.jdbc:mariadb-java-client:3.5.8")
    testImplementation("tools.jackson.module:jackson-module-kotlin:3.1.3")

    testImplementation("io.rest-assured:rest-assured:6.0.0")
    testImplementation("com.microsoft.playwright:playwright:1.60.0")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.0")
    testImplementation("org.assertj:assertj-core:3.27.7")

    // IMAP access for StalwartMailClient — used by tests that need to
    // assert what the api delivered to the mail server.
    testImplementation("org.eclipse.angus:jakarta.mail:2.0.5")

    // JUnit 6 does not automatically put the platform launcher on the
    // runtime classpath; Gradle 9's test-engine selection needs it.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform {
        // System tests are tagged @Tag("system") — include only those here.
        includeTags("system")
    }
    // Propagate every `-Dsystem.*` and `-Dtest.*` flag from the gradle
    // invocation into the forked test JVM. Without this the JVM falls
    // back to its built-in defaults — the frontend reads as the
    // dev-compose hostname instead of the CI loopback, and `TestHelper`'s
    // JDBC URL points at the `blueshell` schema rather than `blueshell-test`.
    gradle.startParameter.systemPropertiesArgs.forEach { (key, value) ->
        if (key.startsWith("test.") || key.startsWith("system.")) {
            systemProperty(key, value)
        }
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
    testLogging {
        events(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
        exceptionFormat = TestExceptionFormat.FULL
        showStackTraces = true
    }
}
