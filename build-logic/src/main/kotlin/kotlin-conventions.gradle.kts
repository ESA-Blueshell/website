import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        // -Werror is intentionally off for now — the current api tree still
        // emits deprecation warnings that will be cleaned up incrementally.
        freeCompilerArgs.addAll("-Xjsr305=strict")
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Cache-warming hook for CI. The `api-static` job runs this on every
// Kotlin subproject so the Gradle cache it persists is a superset of
// what every downstream Test/IntegrationTest job needs — including
// `jacocoAgent` / `jacocoAnt`, which are otherwise resolved lazily
// the first time a Test task is configured. Without this, the api-tests
// job is the canary that eats every transient repo.maven.apache.org 403.
tasks.register("resolveAllDependencies") {
    description = "Resolves every resolvable configuration to warm the Gradle cache."
    group = "build setup"
    notCompatibleWithConfigurationCache("Resolves configurations at execution time.")
    doLast {
        configurations
            .matching { it.isCanBeResolved }
            .forEach { cfg ->
                runCatching { cfg.resolve() }
                    .onFailure { logger.warn("Skipping ${cfg.name}: ${it.message}") }
            }
    }
}
