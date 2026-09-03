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

        // The Brevo and Discord clients are published from
        // ESA-Blueshell/{brevo,discord}-client rather than generated here.
        // GitHub Packages requires a token even for public packages, so CI
        // passes the workflow token and developers set gpr.user / gpr.token in
        // ~/.gradle/gradle.properties (a classic PAT with read:packages).
        listOf("brevo-client", "discord-client").forEach { client ->
            maven {
                name = "ESABlueshell${client.replaceFirstChar(Char::uppercase).replace("-", "")}"
                url = uri("https://maven.pkg.github.com/ESA-Blueshell/$client")
                credentials {
                    username = providers.gradleProperty("gpr.user")
                        .orElse(providers.environmentVariable("GITHUB_ACTOR")).orNull
                    password = providers.gradleProperty("gpr.token")
                        .orElse(providers.environmentVariable("GITHUB_TOKEN")).orNull
                }
            }
        }
    }
}

include(":libs:kotlin-common")
include(":services:api")
include(":tests:system")
