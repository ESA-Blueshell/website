pluginManagement {
    includeBuild("build-logic")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "blueshell-website"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":libs:kotlin-common")
include(":services:api")
include(":services:api:clients:brevo")
include(":services:system-tests")

// libs:kotlin-common is an empty skeleton in this PR. When the OIDC / Vault
// helpers land there, api's build.gradle.kts can add
// implementation(project(":libs:kotlin-common")).
