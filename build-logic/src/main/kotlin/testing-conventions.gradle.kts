plugins {
    java
    jacoco
    id("test-logging-conventions")
}

jacoco {
    toolVersion = "0.8.14"
}

// Unit + integration tests aggregate into one jacoco report and gate.
// PR3 splits this into separate per-source-set reports + gates.
tasks.jacocoTestReport {
    dependsOn(tasks.test, tasks.named("integrationTest"))
    executionData.setFrom(
        fileTree(layout.buildDirectory) { include("jacoco/*.exec") },
    )
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(
        classDirectories.files.map { dir ->
            fileTree(dir) {
                exclude("**/generated/**")
            }
        },
    )
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test, tasks.named("integrationTest"))
    executionData.setFrom(
        fileTree(layout.buildDirectory) { include("jacoco/*.exec") },
    )
    classDirectories.setFrom(
        classDirectories.files.map { dir ->
            fileTree(dir) {
                exclude("**/generated/**")
            }
        },
    )
    violationRules {
        rule {
            limit {
                // Gate is intentionally loose for now — PR3 splits unit vs
                // integration coverage into independent gates, each with
                // their own target.
                minimum = "0.40".toBigDecimal()
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform {
        excludeTags("system", "brevo-live", "listmonk-live")
    }
}

// Integration test source set and task, rooted at src/integrationTest/.
// The `integration` tag filter keeps the task targeted and lets the same
// code be run from the `test` task when a developer wants everything.
sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
        runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    }
}

configurations["integrationTestImplementation"].extendsFrom(configurations.testImplementation.get())
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations.testRuntimeOnly.get())

tasks.register<Test>("integrationTest") {
    description = "Runs integration tests (src/integrationTest, @Tag(\"integration\"))."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform {
        includeTags("integration")
        excludeTags("system", "brevo-live", "listmonk-live")
    }
    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(tasks.named("integrationTest"))
}
