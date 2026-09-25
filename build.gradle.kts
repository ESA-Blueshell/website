// Root project — no build logic of its own. Each module picks the
// convention plugins it needs from :build-logic.
//
// Declared here, unapplied, so build-logic and the Kotlin Gradle plugin it
// carries load once for every subproject, and a subproject names a Kotlin
// plugin without a version. A version there loads the plugin a second time.
plugins {
    id("kotlin-conventions") apply false
}

group = "net.blueshell"
version = "1.12.0" // x-release-please-version
