rootProject.name = "website"

include("api")
include("api:brevo-client")
include("api:listmonk-client")

// Allow Gradle to auto-provision JDKs for toolchains via Foojay.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
