plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
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
    // ktlint is applied to the three Kotlin subprojects; detekt is still
    // opt-in per service and applied nowhere.
    implementation("dev.detekt:detekt-gradle-plugin:2.0.0-alpha.6")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:14.2.0")
    implementation("org.openapitools:openapi-generator-gradle-plugin:7.25.0")
}
