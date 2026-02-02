rootProject.name = "website"

include("api")

// Allow Gradle to auto-provision JDKs for toolchains via Foojay.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
