plugins {
    java
    jacoco
    id("test-logging-conventions")
}

// Where a report or a floor reads its execution data from. CI shards the
// integration suite, so each shard's `.exec` covers only its own slice and no
// floor holds against one alone; the fan-in job points this at a directory of
// every shard's file and the merged picture is what gets measured. Unset, it is
// the single local file, which is what a developer's run produces.
val mergedExecDir: String? = providers.gradleProperty("jacocoExecDir").orNull

fun Project.executionDataFor(defaultFile: String): Any {
    val dir = mergedExecDir ?: return layout.buildDirectory.file(defaultFile)
    // Resolved against the root, not this project: a relative path would land
    // under services/api/ and quietly find nothing.
    val resolved = rootProject.file(dir)
    val found = fileTree(resolved) { include("**/*.exec") }
    // An empty set is not an empty report — JacocoReport goes NO-SOURCE and the
    // verification passes with nothing to measure, so a typo in the path reads
    // as a green coverage gate. Refuse instead.
    check(!found.isEmpty) { "No .exec files under $resolved — the merged coverage would measure nothing." }
    return found
}

// Without merged data a report or a floor runs the suite it measures. With it,
// the suite has already run on the shards and the .exec files are all there is
// to read, so the fan-in must not re-run anything.
// Generated code, and the Spring Modulith markers that only carry an annotation: a line in either
// holds no behaviour, so counting it only asks for a test that can fail by not compiling.
val notMeasured = listOf("**/generated/**", "**/ModuleMetadata*", "**/PackageMetadata*")

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

    // A test JVM carries both the project's jar and its `build/resources/main`, so
    // the changelog is on the classpath twice and Liquibase refuses an ambiguous
    // path. The two copies are the same file. Production runs the jar alone.
    environment("LIQUIBASE_DUPLICATE_FILE_MODE", "WARN")
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
            exclude(notMeasured)
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
tasks.register<JacocoReport>("jacocoIntegrationTestReport") {
    dependsOnUnlessMerged(tasks.named("integrationTest"))
    executionData.setFrom(executionDataFor("jacoco/integrationTest.exec"))
    sourceDirectories.setFrom(sourceSets.main.get().allSource.srcDirs)
    classDirectories.setFrom(
        files(sourceSets.main.get().output.classesDirs).asFileTree.matching {
            exclude(notMeasured)
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
            exclude(notMeasured)
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

tasks.register<JacocoCoverageVerification>("jacocoIntegrationTestCoverageVerification") {
    dependsOnUnlessMerged(tasks.named("integrationTest"))
    executionData.setFrom(executionDataFor("jacoco/integrationTest.exec"))
    sourceDirectories.setFrom(sourceSets.main.get().allSource.srcDirs)
    classDirectories.setFrom(
        files(sourceSets.main.get().output.classesDirs).asFileTree.matching {
            exclude(notMeasured)
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
