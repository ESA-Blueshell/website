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
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    implementation("org.jetbrains.kotlin:kotlin-allopen:2.3.21")
    implementation("org.jetbrains.kotlin:kotlin-noarg:2.3.21")
    implementation("org.springframework.boot:spring-boot-gradle-plugin:4.0.6")
    implementation("io.spring.dependency-management:io.spring.dependency-management.gradle.plugin:1.1.7")
    // detekt + ktlint are available as convention plugins but not applied
    // anywhere in this PR — they are opt-in per service to avoid a wall of
    // style failures on the existing tree. Introduce per service later.
    implementation("dev.detekt:detekt-gradle-plugin:2.0.0-alpha.2")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:14.2.0")
    implementation("org.openapitools:openapi-generator-gradle-plugin:7.22.0")
}
