plugins {
    java
    jacoco
    id("test-logging-conventions")
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
}

// JaCoCo: unit coverage report — wired to the default `test` task only.
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    executionData.setFrom(layout.buildDirectory.file("jacoco/test.exec"))
    sourceDirectories.setFrom(sourceSets.main.get().allSource.srcDirs)
    classDirectories.setFrom(
        files(sourceSets.main.get().output.classesDirs).asFileTree.matching {
            exclude("**/generated/**")
        },
    )
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// JaCoCo: integration coverage report — independent file and HTML output.
val jacocoIntegrationTestReport by tasks.registering(JacocoReport::class) {
    dependsOn(tasks.named("integrationTest"))
    executionData.setFrom(layout.buildDirectory.file("jacoco/integrationTest.exec"))
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
    dependsOn(tasks.test)
    executionData.setFrom(layout.buildDirectory.file("jacoco/test.exec"))
    sourceDirectories.setFrom(sourceSets.main.get().allSource.srcDirs)
    classDirectories.setFrom(
        files(sourceSets.main.get().output.classesDirs).asFileTree.matching {
            exclude("**/generated/**")
        },
    )
    violationRules {
        rule {
            limit {
                // Temporary floor while unit coverage is being re-established
                // after the test split. Bump to 0.80 once owners finish
                // re-categorising tests into the correct source set.
                minimum = "0.40".toBigDecimal()
            }
        }
    }
}

val jacocoIntegrationTestCoverageVerification by tasks.registering(JacocoCoverageVerification::class) {
    dependsOn(tasks.named("integrationTest"))
    executionData.setFrom(layout.buildDirectory.file("jacoco/integrationTest.exec"))
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

tasks.check {
    dependsOn(tasks.named("integrationTest"))
}
