rootProject.name = "api"

include("brevo-client")
include("listmonk-client")

// Allow Gradle to auto-provision JDKs for toolchains via Foojay.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
