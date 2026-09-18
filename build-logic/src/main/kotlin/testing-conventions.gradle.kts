import java.io.File

plugins {
    java
    jacoco
    id("test-logging-conventions")
}

// Sharding via env vars `SHARD_TOTAL` / `SHARD_INDEX` (1-based), the same
// partition :tests:system uses: scan the test classes directory sorted by FQCN,
// take `absoluteHash(fqcn) % SHARD_TOTAL`, and include only that slice through
// Gradle's `--tests` filter. A class always lands on the same shard, so a
// failure is easy to place, and leaving the env vars unset runs everything in
// one JVM exactly as before.
fun Test.shardByTestClass() {
    val shardTotal = System.getenv("SHARD_TOTAL")?.toIntOrNull()?.takeIf { it > 1 } ?: return
    val shardIndex = System.getenv("SHARD_INDEX")?.toIntOrNull()?.takeIf { it in 1..shardTotal } ?: return
    // The filter below is applied in doFirst, which Gradle does not hash, so
    // without these every shard has identical inputs and one shard's result is
    // served to the next FROM-CACHE. Naming them makes each slice its own task.
    inputs.property("shardTotal", shardTotal)
    inputs.property("shardIndex", shardIndex)
    doFirst {
        val classes =
            testClassesDirs.asFileTree
                .matching { include("**/*Test.class", "**/*IT.class") }
                .files
                .mapNotNull { f ->
                    val root = testClassesDirs.firstOrNull { f.startsWith(it) } ?: return@mapNotNull null
                    f.relativeTo(root).path.removeSuffix(".class").replace(File.separatorChar, '.')
                }.filter { !it.contains('$') } // skip anonymous / nested $-classes
                .sorted()
        val mine = classes.filter { Math.floorMod(it.hashCode(), shardTotal) == shardIndex - 1 }
        logger.lifecycle("Shard $shardIndex/$shardTotal — ${mine.size}/${classes.size} test classes")
        filter {
            isFailOnNoMatchingTests = false
            if (mine.isEmpty()) {
                // No classes assigned — exclude everything by including a pattern
                // that cannot match. An empty include list would match all.
                includeTestsMatching("__no_match__shard_${shardIndex}__")
            } else {
                mine.forEach { includeTestsMatching(it) }
            }
        }
    }
}

// Where a report or a floor reads its execution data from. CI shards the
// integration suite, so each shard's `.exec` covers only its own slice and no
// floor holds against one alone; the fan-in job points this at a directory of
// every shard's file and the merged picture is what gets measured. Unset, it is
// the single local file, which is what a developer's run produces.
val mergedExecDir: String? = providers.gradleProperty("jacocoExecDir").orNull

fun Project.executionDataFor(defaultFile: String): Any =
    mergedExecDir?.let { fileTree(it) { include("**/*.exec") } }
        ?: layout.buildDirectory.file(defaultFile)

// Without merged data a report or a floor runs the suite it measures. With it,
// the suite has already run on the shards and the .exec files are all there is
// to read, so the fan-in must not re-run anything.
fun Task.dependsOnUnlessMerged(producer: Any) {
    if (mergedExecDir == null) dependsOn(producer)
}

jacoco {
    toolVersion = "0.8.14"
}

// Unit tests live in src/test/; integration tests live in src/integrationTest/.
// Each source set produces its own JaCoCo report and each has its own coverage
// gate. System tests are extracted into :tests:system and are not
// covered by any gate here.

sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
        runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    }
}

configurations["integrationTestImplementation"].extendsFrom(configurations.testImplementation.get())
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations.testRuntimeOnly.get())

tasks.withType<Test> {
    useJUnitPlatform {
        excludeTags("system", "brevo-live", "discord-live")
    }
    // Gradle forks test JVMs with a 512 MB default heap, and GRADLE_OPTS sizes the
    // daemon rather than the fork. Spring's TestContext framework caches one
    // ApplicationContext per distinct configuration for the life of the JVM, so a
    // suite with many @SpringBootTest classes exhausts that default and fails on
    // context load rather than on an assertion.
    maxHeapSize = "2g"
}

tasks.register<Test>("integrationTest") {
    description = "Runs integration tests (src/integrationTest/kotlin)."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform {
        excludeTags("system", "brevo-live", "discord-live")
    }
    shouldRunAfter(tasks.test)
    shardByTestClass()
}

// JaCoCo: unit coverage report — wired to the default `test` task only.
tasks.jacocoTestReport {
    dependsOnUnlessMerged(tasks.test)
    executionData.setFrom(executionDataFor("jacoco/test.exec"))
    sourceDirectories.setFrom(sourceSets.main.get().allSource.srcDirs)
    classDirectories.setFrom(
        files(sourceSets.main.get().output.classesDirs).asFileTree.matching {
            exclude("**/generated/**")
        },
    )
    reports {
        xml.required.set(true)
        // Named after the task rather than the source set, because the CI step that
        // uploads it reads this path. It defaulted to reports/jacoco/test/, which no
        // glob matched, so the unit report was generated and then silently dropped.
        xml.outputLocation.set(
            layout.buildDirectory.file("reports/jacoco/jacocoTestReport/jacocoTestReport.xml"),
        )
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/jacocoTestReport/html"))
    }
}

// JaCoCo: integration coverage report — independent file and HTML output.
val jacocoIntegrationTestReport by tasks.registering(JacocoReport::class) {
    dependsOnUnlessMerged(tasks.named("integrationTest"))
    executionData.setFrom(executionDataFor("jacoco/integrationTest.exec"))
    sourceDirectories.setFrom(sourceSets.main.get().allSource.srcDirs)
    classDirectories.setFrom(
        files(sourceSets.main.get().output.classesDirs).asFileTree.matching {
            exclude("**/generated/**")
        },
    )
    reports {
        xml.required.set(true)
        xml.outputLocation.set(
            layout.buildDirectory.file("reports/jacoco/jacocoIntegrationTestReport/jacocoIntegrationTestReport.xml"),
        )
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/jacocoIntegrationTestReport/html"))
    }
}

// Two independent gates. Unit coverage is the stricter target since
// integration tests by nature touch more code paths but lean on real
// infrastructure and are slower to run.
tasks.jacocoTestCoverageVerification {
    dependsOnUnlessMerged(tasks.test)
    executionData.setFrom(executionDataFor("jacoco/test.exec"))
    sourceDirectories.setFrom(sourceSets.main.get().allSource.srcDirs)
    classDirectories.setFrom(
        files(sourceSets.main.get().output.classesDirs).asFileTree.matching {
            exclude("**/generated/**")
        },
    )
    violationRules {
        rule {
            limit {
                // 0.35, set 2026-09-18 when CI first ran this task (#1224). The unit
                // suite covered 0.38 of instructions on that run — the 0.40 here before
                // it was a placeholder nobody had ever measured against. This floor
                // catches a collapse; raising it toward 0.38 and past it is its own
                // piece of work, not a number to move quietly.
                minimum = "0.35".toBigDecimal()
            }
        }
    }
}

val jacocoIntegrationTestCoverageVerification by tasks.registering(JacocoCoverageVerification::class) {
    dependsOnUnlessMerged(tasks.named("integrationTest"))
    executionData.setFrom(executionDataFor("jacoco/integrationTest.exec"))
    sourceDirectories.setFrom(sourceSets.main.get().allSource.srcDirs)
    classDirectories.setFrom(
        files(sourceSets.main.get().output.classesDirs).asFileTree.matching {
            exclude("**/generated/**")
        },
    )
    violationRules {
        rule {
            limit {
                minimum = "0.40".toBigDecimal()
            }
        }
    }
}

// The floors are named by CI rather than reached through `check`, because `check`
// does not pull a JacocoCoverageVerification task in and adding one here would make
// every `check` wait on the full integration run.
tasks.check {
    dependsOn(tasks.named("integrationTest"))
}
