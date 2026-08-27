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
include(":libs:clients:brevo")
include(":libs:clients:discord")
include(":services:api")
include(":services:system-tests")
