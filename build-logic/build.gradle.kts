plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

// The convention plugins compile under the same rule as the code they build:
// see kotlin-conventions.
kotlin {
    compilerOptions {
        allWarningsAsErrors.set(
            providers.gradleProperty("warningsAsErrors").map(String::toBoolean).orElse(false),
        )
    }
}

// Versions pinned across convention plugins.
// Kotlin / Spring / JPA / KAPT versions are kept in sync with services/api's
// historical versions so applying the conventions does not silently upgrade
// compiler output.
dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    implementation("org.jetbrains.kotlin:kotlin-allopen:2.4.10")
    implementation("org.jetbrains.kotlin:kotlin-noarg:2.4.10")
    implementation("org.springframework.boot:spring-boot-gradle-plugin:4.1.1")
    implementation("io.spring.dependency-management:io.spring.dependency-management.gradle.plugin:1.1.7")
    // ktlint comes in through kotlin-conventions, so every Kotlin project is
    // format-checked without asking; detekt is applied project by project.
    implementation("dev.detekt:detekt-gradle-plugin:2.0.0-alpha.6")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:14.2.0")
}
